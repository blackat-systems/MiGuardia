package com.blackatsystems.miguardia.core.domain.backup

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.ZoneId
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupPhotoAsset(
    val metadata: BackupPhotoMetadata,
    val file: File,
)

data class BackupContainerHeader(
    val encrypted: Boolean,
    val formatVersion: Int,
    val minimumReaderVersion: Int,
    val createdAtEpochMillis: Long,
    val pbkdf2Iterations: Int,
)

class ExtractedBackup internal constructor(
    val manifest: BackupManifest,
    val payload: BackupPayload,
    val photoDirectory: File,
    private val extractionRoot: File,
) : Closeable {
    fun photoFile(storageKey: String): File = File(photoDirectory, storageKey).also { candidate ->
        if (candidate.parentFile?.canonicalFile != photoDirectory.canonicalFile) {
            throw InvalidBackupException("La copia contiene una ruta de fotografía inválida.")
        }
    }

    override fun close() {
        extractionRoot.deletePrivateTreeChecked("la extracción privada de la copia")
    }
}

private fun File.deletePrivateFileChecked(label: String) {
    if (exists() && !delete()) throw IOException("No se pudo retirar $label.")
}

private fun File.deletePrivateTreeChecked(label: String) {
    if (exists() && !deleteRecursively()) throw IOException("No se pudo retirar $label.")
}

/** Streaming, authenticated container for the `.miguardia-backup` contract. */
object BackupContainer {
    /** Stable prefix written last when publishing through the Android document provider. */
    const val HEADER_BYTES: Int = 78

    fun create(
        target: File,
        workingDirectory: File,
        backupId: UUID,
        createdAtEpochMillis: Long,
        zoneId: ZoneId,
        database: BackupDatabaseSnapshot,
        preferences: List<BackupPreference>,
        photoMode: BackupPhotoMode,
        photoAssets: List<BackupPhotoAsset>,
        password: CharArray?,
        secureRandom: SecureRandom = SecureRandom(),
    ): BackupManifest {
        rejectEmptyPassword(password)
        try {
            MiGuardiaBackupSchemaV6.requireValid(database)
            require(createdAtEpochMillis > 0L)
            require(photoAssets.size <= MiGuardiaBackupContract.MAX_PHOTO_COUNT)
            if (photoMode == BackupPhotoMode.OMITTED && photoAssets.isNotEmpty()) {
                throw InvalidBackupException("Una copia sin fotos no puede contener archivos de fotos.")
            }
        } catch (error: Exception) {
            password?.fill('\u0000')
            throw error
        }
        val operation = try {
            scopedOperationDirectory(workingDirectory, "create")
        } catch (error: Exception) {
            password?.fill('\u0000')
            throw error
        }
        val temporaryTarget = File(operation, "container.tmp")
        var operationFailure: Throwable? = null
        try {
            val databaseFile = File(operation, DATABASE_ENTRY).also { file ->
                file.outputStream().use { BackupPayloadCodec.writeDatabase(database, it) }
            }
            val preferencesFile = File(operation, PREFERENCES_ENTRY).also { file ->
                file.outputStream().use { BackupPayloadCodec.writePreferences(preferences, it) }
            }
            val normalizedAssets = validateAndNormalizeAssets(photoMode, database, photoAssets)
            val photosFile = File(operation, PHOTOS_ENTRY).also { file ->
                file.outputStream().use { output ->
                    BackupPayloadCodec.writePhotos(normalizedAssets.map(BackupPhotoAsset::metadata), output)
                }
            }
            val contentFiles = linkedMapOf(
                DATABASE_ENTRY to databaseFile,
                PHOTOS_ENTRY to photosFile,
                PREFERENCES_ENTRY to preferencesFile,
            )
            normalizedAssets.forEach { asset ->
                contentFiles["$PHOTO_PREFIX${asset.metadata.storageKey}"] = asset.file
            }
            val entries = contentFiles.entries
                .map { (name, file) ->
                    enforceEntryLimit(name, file.length())
                    BackupEntryManifest(name, file.length(), file.sha256())
                }
                .sortedBy(BackupEntryManifest::name)
            val manifest = BackupManifest(
                backupId = backupId.toString(),
                createdAtEpochMillis = createdAtEpochMillis,
                zoneId = zoneId.id,
                roomVersion = database.roomVersion,
                timelineId = database.timelineId,
                photoMode = photoMode,
                tableCounts = database.tables.associate { it.name to it.records.size },
                entries = entries,
            )
            val manifestFile = File(operation, MANIFEST_ENTRY).also { file ->
                file.outputStream().use { BackupPayloadCodec.writeManifest(manifest, it) }
            }
            val payloadFile = File(operation, "payload.zip")
            writeCanonicalZip(payloadFile, manifestFile, contentFiles)
            writeContainer(temporaryTarget, payloadFile, createdAtEpochMillis, password, secureRandom)
            if (temporaryTarget.length() !in 1..MiGuardiaBackupContract.MAX_CONTAINER_BYTES) {
                throw InvalidBackupException("La copia supera el tamaño máximo permitido.")
            }
            target.parentFile?.mkdirs()
            moveReplacing(temporaryTarget, target)
            return manifest
        } catch (error: Throwable) {
            operationFailure = error
            throw error
        } finally {
            password?.fill('\u0000')
            val cleanupFailure = runCatching {
                operation.deletePrivateTreeChecked("la preparación privada de la copia")
            }.exceptionOrNull()
            if (cleanupFailure != null) {
                operationFailure?.addSuppressed(cleanupFailure) ?: throw cleanupFailure
            }
        }
    }

    fun readHeader(source: File): BackupContainerHeader {
        if (source.length() !in HEADER_BYTES.toLong()..MiGuardiaBackupContract.MAX_CONTAINER_BYTES) {
            throw InvalidBackupException("El archivo no tiene un tamaño válido para una copia de MiGuardia.")
        }
        val raw = ByteArray(HEADER_BYTES)
        FileInputStream(source).use { input ->
            if (input.readFullyOrFalse(raw).not()) {
                throw InvalidBackupException("La cabecera de la copia está truncada.")
            }
        }
        return parseHeader(raw).publicHeader
    }

    fun extract(
        source: File,
        workingDirectory: File,
        password: CharArray?,
        decodedMemoryLimitBytes: Long = BackupMemoryBudget.operationalHeapBytes(),
    ): ExtractedBackup {
        rejectEmptyPassword(password)
        if (source.length() !in HEADER_BYTES.toLong()..MiGuardiaBackupContract.MAX_CONTAINER_BYTES) {
            password?.fill('\u0000')
            throw InvalidBackupException("El archivo no tiene un tamaño válido para una copia de MiGuardia.")
        }
        val operation = try {
            scopedOperationDirectory(workingDirectory, "read")
        } catch (error: Exception) {
            password?.fill('\u0000')
            throw error
        }
        try {
            val payloadZip = File(operation, "authenticated-payload.zip")
            readAuthenticatedPayload(source, payloadZip, password)
            val extracted = File(operation, "entries").also { it.mkdirs() }
            extractZip(payloadZip, extracted)
            payloadZip.deletePrivateFileChecked("el payload temporal verificado")

            val manifestFile = File(extracted, MANIFEST_ENTRY)
            val sourceManifest = manifestFile.inputStream().use(BackupPayloadCodec::readManifest)
            val sourceDatabase = File(extracted, DATABASE_ENTRY).inputStream().use { input ->
                BackupPayloadCodec.readDatabase(input, decodedMemoryLimitBytes)
            }
            val preferences = File(extracted, PREFERENCES_ENTRY).inputStream().use(
                BackupPayloadCodec::readPreferences,
            )
            val photos = File(extracted, PHOTOS_ENTRY).inputStream().use(BackupPayloadCodec::readPhotos)
            val sourcePayload = BackupPayload(sourceDatabase, preferences, sourceManifest.photoMode, photos)
            verifyExtracted(sourceManifest, sourcePayload, extracted)
            val database = upgradeSupportedBackupSchema(sourceDatabase)
            val payload = sourcePayload.copy(database = database)
            return ExtractedBackup(
                manifest = sourceManifest,
                payload = payload,
                photoDirectory = File(extracted, "photos"),
                extractionRoot = operation,
            )
        } catch (error: Throwable) {
            runCatching {
                operation.deletePrivateTreeChecked("la extracción privada incompleta")
            }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        } finally {
            password?.fill('\u0000')
        }
    }

    private fun validateAndNormalizeAssets(
        photoMode: BackupPhotoMode,
        database: BackupDatabaseSnapshot,
        assets: List<BackupPhotoAsset>,
    ): List<BackupPhotoAsset> {
        val photoTable = database.table("schedule_photos")
        if (photoMode == BackupPhotoMode.OMITTED) {
            if (photoTable.records.isNotEmpty()) {
                throw InvalidBackupException("La copia sin fotos todavía contiene metadatos de fotos.")
            }
            return emptyList()
        }
        val columns = photoTable.columns
        val rowsByStorageKey = photoTable.records.associateBy { record ->
            (record.values[columns.indexOf("storageKey")] as? BackupValue.Text)?.value
                ?: throw InvalidBackupException("Una foto no tiene una referencia de archivo válida.")
        }
        if (rowsByStorageKey.size != photoTable.records.size || assets.size != rowsByStorageKey.size) {
            throw InvalidBackupException("Los registros y archivos de fotos no coinciden.")
        }
        var totalBytes = 0L
        return assets.sortedBy { it.metadata.storageKey }.map { asset ->
            val row = rowsByStorageKey[asset.metadata.storageKey]
                ?: throw InvalidBackupException("Falta el registro lógico de una fotografía.")
            if (!asset.file.isFile) throw InvalidBackupException("Falta el archivo privado de una fotografía.")
            val actualSize = asset.file.length()
            totalBytes += actualSize
            if (totalBytes > MiGuardiaBackupContract.MAX_ALL_PHOTOS_BYTES) {
                throw InvalidBackupException("Las fotografías superan el límite total seguro.")
            }
            val normalized = asset.metadata.copy(byteSize = actualSize, sha256 = asset.file.sha256())
            requirePhotoMatchesRow(normalized, row, columns)
            requireImageSignature(asset.file, normalized.mimeType)
            asset.copy(metadata = normalized)
        }
    }

    private fun verifyExtracted(
        manifest: BackupManifest,
        payload: BackupPayload,
        extracted: File,
    ) {
        requireSupportedBackupSchema(payload.database)
        if (manifest.roomVersion != payload.database.roomVersion ||
            manifest.timelineId != payload.database.timelineId ||
            manifest.photoMode != payload.photoMode ||
            manifest.tableCounts != payload.database.tables.associate { it.name to it.records.size }
        ) {
            throw InvalidBackupException("El manifiesto no coincide con los datos lógicos.")
        }
        val actualNames = extracted.walkTopDown()
            .filter(File::isFile)
            .map { it.relativeTo(extracted).invariantSeparatorsPath }
            .filter { it != MANIFEST_ENTRY }
            .toSet()
        if (actualNames != manifest.entries.mapTo(linkedSetOf()) { it.name }) {
            throw InvalidBackupException("El contenido de la copia no coincide con su manifiesto.")
        }
        manifest.entries.forEach { entry ->
            val file = safeEntryFile(extracted, entry.name)
            enforceEntryLimit(entry.name, file.length())
            if (file.length() != entry.uncompressedBytes || file.sha256() != entry.sha256) {
                throw InvalidBackupException("La entrada ${entry.name} no supera la verificación de integridad.")
            }
        }
        val photoTable = payload.database.table("schedule_photos")
        if (payload.photoMode == BackupPhotoMode.OMITTED) {
            if (payload.photos.isNotEmpty() || photoTable.records.isNotEmpty() ||
                actualNames.any { it.startsWith(PHOTO_PREFIX) }
            ) {
                throw InvalidBackupException("Una copia declarada sin fotos contiene datos de fotografías.")
            }
            return
        }
        val columns = photoTable.columns
        val rowsByStorageKey = photoTable.records.associateBy { row ->
            (row.values[columns.indexOf("storageKey")] as? BackupValue.Text)?.value
                ?: throw InvalidBackupException("Una foto no tiene una referencia válida.")
        }
        if (rowsByStorageKey.size != payload.photos.size) {
            throw InvalidBackupException("Los metadatos y registros de fotografías no coinciden.")
        }
        val expectedPhotoEntries = payload.photos
            .mapTo(linkedSetOf()) { photo -> "$PHOTO_PREFIX${photo.storageKey}" }
        val actualPhotoEntries = actualNames.filterTo(linkedSetOf()) { it.startsWith(PHOTO_PREFIX) }
        if (actualPhotoEntries != expectedPhotoEntries) {
            throw InvalidBackupException("Los archivos físicos y metadatos de fotografías no coinciden.")
        }
        var totalBytes = 0L
        payload.photos.forEach { photo ->
            val row = rowsByStorageKey[photo.storageKey]
                ?: throw InvalidBackupException("Falta el registro de una fotografía.")
            requirePhotoMatchesRow(photo, row, columns)
            val file = safeEntryFile(extracted, "$PHOTO_PREFIX${photo.storageKey}")
            if (file.length() != photo.byteSize || file.sha256() != photo.sha256) {
                throw InvalidBackupException("Una fotografía no coincide con su huella.")
            }
            requireImageSignature(file, photo.mimeType)
            totalBytes += file.length()
            if (totalBytes > MiGuardiaBackupContract.MAX_ALL_PHOTOS_BYTES) {
                throw InvalidBackupException("Las fotografías superan el límite total seguro.")
            }
        }
    }

    private fun requirePhotoMatchesRow(
        photo: BackupPhotoMetadata,
        row: BackupRecord,
        columns: List<String>,
    ) {
        fun text(name: String) = (row.values[columns.indexOf(name)] as? BackupValue.Text)?.value
        fun integer(name: String) = (row.values[columns.indexOf(name)] as? BackupValue.Integer)?.value
        if (text("id") != photo.recordId || text("storageKey") != photo.storageKey ||
            text("mimeType") != photo.mimeType || integer("byteSize") != photo.byteSize ||
            integer("pixelWidth") != photo.pixelWidth.toLong() ||
            integer("pixelHeight") != photo.pixelHeight.toLong()
        ) {
            throw InvalidBackupException("Los metadatos de una fotografía no coinciden con Room.")
        }
    }

    private fun writeCanonicalZip(
        target: File,
        manifest: File,
        contentFiles: Map<String, File>,
    ) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zip ->
            zip.setLevel(6)
            zip.putFile(MANIFEST_ENTRY, manifest)
            contentFiles.toSortedMap().forEach { (name, file) -> zip.putFile(name, file) }
        }
        if (target.length() > MiGuardiaBackupContract.MAX_CONTAINER_BYTES) {
            throw InvalidBackupException("La carga comprimida supera el límite seguro.")
        }
    }

    private fun ZipOutputStream.putFile(name: String, file: File) {
        requireSafeEntryName(name)
        val entry = ZipEntry(name).apply { time = ZIP_EPOCH_MILLIS }
        putNextEntry(entry)
        file.inputStream().buffered().use { input -> input.copyTo(this) }
        closeEntry()
    }

    private fun writeContainer(
        target: File,
        payload: File,
        createdAtEpochMillis: Long,
        password: CharArray?,
        secureRandom: SecureRandom,
    ) {
        val encrypted = password != null
        val passwordlessSealed = !encrypted
        val salt = ByteArray(MiGuardiaBackupContract.SALT_BYTES).also {
            secureRandom.nextBytes(it)
        }
        val nonce = ByteArray(MiGuardiaBackupContract.NONCE_BYTES).also {
            secureRandom.nextBytes(it)
        }
        val header = encodeHeader(
            encrypted = encrypted,
            passwordlessSealed = passwordlessSealed,
            createdAtEpochMillis = createdAtEpochMillis,
            iterations = if (encrypted) MiGuardiaBackupContract.PBKDF2_ITERATIONS else 0,
            salt = salt,
            nonce = nonce,
            payloadBytes = payload.length() + GCM_TAG_BYTES,
        )
        FileOutputStream(target).use { rawOutput ->
            rawOutput.write(header)
            val cipher = if (encrypted) {
                createCipher(Cipher.ENCRYPT_MODE, requireNotNull(password), salt, nonce, header)
            } else {
                createPasswordlessSealedCipher(Cipher.ENCRYPT_MODE, salt, nonce, header)
            }
            payload.inputStream().buffered().use { input ->
                writeCiphered(input, rawOutput, cipher, MiGuardiaBackupContract.MAX_CONTAINER_BYTES)
            }
            rawOutput.fd.sync()
        }
    }

    private fun readAuthenticatedPayload(source: File, target: File, password: CharArray?) {
        FileInputStream(source).use { rawInput ->
            val rawHeader = ByteArray(HEADER_BYTES)
            if (!rawInput.readFullyOrFalse(rawHeader)) {
                throw InvalidBackupException("La cabecera de la copia está truncada.")
            }
            val header = parseHeader(rawHeader)
            val remaining = source.length() - HEADER_BYTES
            if (header.payloadBytes != remaining || remaining <= 0L) {
                throw InvalidBackupException("La longitud declarada de la copia no coincide.")
            }
            FileOutputStream(target).use { output ->
                if (header.encrypted) {
                    val supplied = password ?: throw BackupPasswordRequiredException()
                    val cipher = createCipher(
                        Cipher.DECRYPT_MODE,
                        supplied,
                        header.salt,
                        header.nonce,
                        rawHeader,
                    )
                    try {
                        writeCiphered(rawInput, output, cipher, MiGuardiaBackupContract.MAX_CONTAINER_BYTES)
                    } catch (error: AEADBadTagException) {
                        throw BackupAuthenticationException(error)
                    } catch (error: GeneralSecurityException) {
                        throw BackupAuthenticationException(error)
                    }
                } else if (header.passwordlessSealed) {
                    val cipher = createPasswordlessSealedCipher(
                        Cipher.DECRYPT_MODE,
                        header.salt,
                        header.nonce,
                        rawHeader,
                    )
                    try {
                        writeCiphered(rawInput, output, cipher, MiGuardiaBackupContract.MAX_CONTAINER_BYTES)
                    } catch (error: GeneralSecurityException) {
                        throw InvalidBackupException(
                            "La copia sin contraseña está dañada o no coincide con su comprobación de integridad.",
                            error,
                        )
                    }
                } else {
                    rawInput.copyBoundedTo(output, header.payloadBytes, MiGuardiaBackupContract.MAX_CONTAINER_BYTES)
                }
                output.fd.sync()
            }
        }
    }

    private fun writeCiphered(
        input: InputStream,
        output: FileOutputStream,
        cipher: Cipher,
        maxOutput: Long,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var written = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            cipher.update(buffer, 0, count)?.let { transformed ->
                written += transformed.size
                if (written > maxOutput) throw InvalidBackupException("La copia supera el límite seguro.")
                output.write(transformed)
            }
        }
        val final = cipher.doFinal()
        written += final.size
        if (written > maxOutput) throw InvalidBackupException("La copia supera el límite seguro.")
        output.write(final)
    }

    private fun createCipher(
        mode: Int,
        password: CharArray,
        salt: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
    ): Cipher {
        val spec = PBEKeySpec(
            password,
            salt,
            MiGuardiaBackupContract.PBKDF2_ITERATIONS,
            MiGuardiaBackupContract.AES_KEY_BITS,
        )
        var encoded: ByteArray? = null
        try {
            encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(encoded, "AES"), GCMParameterSpec(MiGuardiaBackupContract.GCM_TAG_BITS, nonce))
                updateAAD(aad)
            }
        } finally {
            spec.clearPassword()
            encoded?.fill(0)
        }
    }

    private fun createPasswordlessSealedCipher(
        mode: Int,
        salt: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
    ): Cipher {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(PASSWORDLESS_SEAL_DOMAIN)
        digest.update(salt)
        digest.update(nonce)
        val encoded = digest.digest()
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(
                    mode,
                    SecretKeySpec(encoded, "AES"),
                    GCMParameterSpec(MiGuardiaBackupContract.GCM_TAG_BITS, nonce),
                )
                updateAAD(aad)
            }
        } finally {
            encoded.fill(0)
        }
    }

    private fun extractZip(payload: File, destination: File) {
        val seen = linkedSetOf<String>()
        var totalBytes = 0L
        var photoBytes = 0L
        var entryCount = 0
        val proportionalLimit = MiGuardiaBackupContract.maximumExpandedPayloadBytes(payload.length())
        ZipInputStream(BufferedInputStream(FileInputStream(payload))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                if (entryCount > MiGuardiaBackupContract.MAX_PHOTO_COUNT + 4) {
                    throw InvalidBackupException("La copia contiene demasiadas entradas.")
                }
                if (entry.isDirectory) throw InvalidBackupException("La copia contiene directorios no permitidos.")
                val name = entry.name
                requireSafeEntryName(name)
                if (!seen.add(name)) throw InvalidBackupException("La copia contiene entradas duplicadas.")
                if (entryCount == 1 && name != MANIFEST_ENTRY) {
                    throw InvalidBackupException("El manifiesto no es la primera entrada canónica.")
                }
                val target = safeEntryFile(destination, name)
                target.parentFile?.mkdirs()
                val entryLimit = entryLimit(name)
                var entryBytes = 0L
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        entryBytes += count
                        totalBytes += count
                        if (name.startsWith(PHOTO_PREFIX)) photoBytes += count
                        if (entryBytes > entryLimit ||
                            totalBytes > MiGuardiaBackupContract.MAX_CONTAINER_BYTES ||
                            photoBytes > MiGuardiaBackupContract.MAX_ALL_PHOTOS_BYTES ||
                            totalBytes > proportionalLimit
                        ) {
                            throw InvalidBackupException("La copia expande más allá de los límites seguros.")
                        }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
                zip.closeEntry()
            }
        }
        val required = setOf(MANIFEST_ENTRY, DATABASE_ENTRY, PREFERENCES_ENTRY, PHOTOS_ENTRY)
        if (!seen.containsAll(required)) {
            throw InvalidBackupException("La copia no contiene todas las secciones obligatorias.")
        }
    }

    private fun rejectEmptyPassword(password: CharArray?) {
        if (password != null && password.isEmpty()) {
            password.fill('\u0000')
            throw InvalidBackupException(
                "Una contraseña vacía no es válida. Elegí una contraseña o confirmá la copia sin cifrar.",
            )
        }
    }

    private fun encodeHeader(
        encrypted: Boolean,
        passwordlessSealed: Boolean,
        createdAtEpochMillis: Long,
        iterations: Int,
        salt: ByteArray,
        nonce: ByteArray,
        payloadBytes: Long,
    ): ByteArray = ByteArrayOutputStream(HEADER_BYTES).use { bytes ->
        DataOutputStream(bytes).use { data ->
            data.write(MAGIC)
            data.writeInt(MiGuardiaBackupContract.FORMAT_VERSION)
            data.writeInt(MiGuardiaBackupContract.MIN_READER_VERSION)
            data.writeInt(
                when {
                    encrypted -> FLAG_ENCRYPTED
                    passwordlessSealed -> FLAG_PASSWORDLESS_SEALED
                    else -> 0
                },
            )
            data.writeLong(createdAtEpochMillis)
            data.writeInt(iterations)
            if (passwordlessSealed) {
                // The self-opening material is deliberately last. Until the complete stable
                // header is published, a staged SAF document contains authenticated ciphertext
                // but not all parameters required to derive its transport key.
                data.writeLong(payloadBytes)
                data.writeByte(salt.size)
                data.write(salt)
                data.writeByte(nonce.size)
                data.write(nonce)
            } else {
                data.writeByte(salt.size)
                data.write(salt)
                data.writeByte(nonce.size)
                data.write(nonce)
                data.writeLong(payloadBytes)
            }
        }
        bytes.toByteArray().also { check(it.size == HEADER_BYTES) }
    }

    private fun parseHeader(raw: ByteArray): ParsedHeader {
        if (raw.size != HEADER_BYTES) throw InvalidBackupException("La cabecera tiene un tamaño inválido.")
        return DataInputStream(ByteArrayInputStream(raw)).use { data ->
            val magic = ByteArray(MAGIC.size).also(data::readFully)
            if (!magic.contentEquals(MAGIC)) throw InvalidBackupException("El archivo no es una copia de MiGuardia.")
            val version = data.readInt()
            val minimum = data.readInt()
            if (version != MiGuardiaBackupContract.FORMAT_VERSION ||
                minimum > MiGuardiaBackupContract.FORMAT_VERSION
            ) {
                throw InvalidBackupException("La versión de la copia no es compatible.")
            }
            val flags = data.readInt()
            if (flags and ALLOWED_FLAGS.inv() != 0 ||
                flags and FLAG_ENCRYPTED != 0 && flags and FLAG_PASSWORDLESS_SEALED != 0
            ) {
                throw InvalidBackupException("La cabecera contiene opciones desconocidas.")
            }
            val encrypted = flags and FLAG_ENCRYPTED != 0
            val passwordlessSealed = flags and FLAG_PASSWORDLESS_SEALED != 0
            val createdAt = data.readLong()
            if (createdAt <= 0L) throw InvalidBackupException("La fecha de la copia es inválida.")
            val iterations = data.readInt()
            val payloadBytes: Long
            val saltSize: Int
            val salt: ByteArray
            val nonceSize: Int
            val nonce: ByteArray
            if (passwordlessSealed) {
                payloadBytes = data.readLong()
                saltSize = data.readUnsignedByte()
                salt = ByteArray(saltSize).also(data::readFully)
                nonceSize = data.readUnsignedByte()
                nonce = ByteArray(nonceSize).also(data::readFully)
            } else {
                saltSize = data.readUnsignedByte()
                salt = ByteArray(saltSize).also(data::readFully)
                nonceSize = data.readUnsignedByte()
                nonce = ByteArray(nonceSize).also(data::readFully)
                payloadBytes = data.readLong()
            }
            if (saltSize != MiGuardiaBackupContract.SALT_BYTES ||
                nonceSize != MiGuardiaBackupContract.NONCE_BYTES || payloadBytes <= 0L || data.available() != 0
            ) {
                throw InvalidBackupException("Los parámetros criptográficos de la copia son inválidos.")
            }
            if (encrypted) {
                if (iterations != MiGuardiaBackupContract.PBKDF2_ITERATIONS ||
                    salt.all { it == 0.toByte() } || nonce.all { it == 0.toByte() }
                ) {
                    throw InvalidBackupException("Los parámetros de cifrado no son válidos.")
                }
            } else if (passwordlessSealed) {
                if (iterations != 0 || salt.all { it == 0.toByte() } || nonce.all { it == 0.toByte() }) {
                    throw InvalidBackupException(
                        "Los parámetros de comprobación de integridad de la copia no son válidos.",
                    )
                }
            } else if (iterations != 0 || salt.any { it != 0.toByte() } || nonce.any { it != 0.toByte() }) {
                throw InvalidBackupException("Una copia sin cifrar declara parámetros criptográficos.")
            }
            ParsedHeader(
                encrypted,
                passwordlessSealed,
                version,
                minimum,
                createdAt,
                iterations,
                salt,
                nonce,
                payloadBytes,
            )
        }
    }

    private fun requireSafeEntryName(name: String) {
        val validFixed = name in setOf(MANIFEST_ENTRY, DATABASE_ENTRY, PREFERENCES_ENTRY, PHOTOS_ENTRY)
        val validPhoto = name.startsWith(PHOTO_PREFIX) &&
            SAFE_STORAGE_KEY.matches(name.removePrefix(PHOTO_PREFIX))
        if ((!validFixed && !validPhoto) || name.startsWith('/') || name.startsWith('\\') ||
            name.contains("..") || name.contains('\\') || name.contains(':')
        ) {
            throw InvalidBackupException("La copia contiene una ruta no permitida.")
        }
    }

    private fun safeEntryFile(root: File, name: String): File {
        requireSafeEntryName(name)
        val candidate = File(root, name).canonicalFile
        val canonicalRoot = root.canonicalFile
        if (candidate.toPath().startsWith(canonicalRoot.toPath()).not()) {
            throw InvalidBackupException("La copia intenta salir del directorio privado.")
        }
        return candidate
    }

    private fun enforceEntryLimit(name: String, bytes: Long) {
        if (bytes !in 0..entryLimit(name)) {
            throw InvalidBackupException("La entrada $name supera el límite seguro.")
        }
    }

    private fun entryLimit(name: String): Long = when {
        name == DATABASE_ENTRY -> MiGuardiaBackupContract.MAX_LOGICAL_BYTES
        name == PREFERENCES_ENTRY -> MiGuardiaBackupContract.MAX_PREFERENCES_BYTES
        name == MANIFEST_ENTRY || name == PHOTOS_ENTRY -> MAX_METADATA_BYTES
        name.startsWith(PHOTO_PREFIX) -> MiGuardiaBackupContract.MAX_SINGLE_PHOTO_BYTES
        else -> throw InvalidBackupException("La copia contiene una entrada desconocida.")
    }

    private fun requireImageSignature(file: File, mimeType: String) {
        val prefix = ByteArray(12)
        val count = file.inputStream().use { it.read(prefix) }
        val valid = when (mimeType) {
            "image/jpeg" -> count >= 3 && prefix[0] == 0xFF.toByte() &&
                prefix[1] == 0xD8.toByte() && prefix[2] == 0xFF.toByte()
            "image/png" -> count >= PNG_SIGNATURE.size &&
                prefix.copyOf(PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)
            "image/webp" -> count >= 12 && String(prefix, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(prefix, 8, 4, Charsets.US_ASCII) == "WEBP"
            else -> false
        }
        if (!valid) throw InvalidBackupException("La firma de una fotografía no coincide con su tipo.")
    }

    private fun scopedOperationDirectory(root: File, prefix: String): File {
        root.mkdirs()
        if (!root.isDirectory) throw IOException("No se pudo preparar el área privada de copias.")
        return File(root, "$prefix-${UUID.randomUUID()}").also { directory ->
            if (!directory.mkdir()) throw IOException("No se pudo preparar el área privada de copias.")
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun InputStream.copyBoundedTo(output: FileOutputStream, expected: Long, max: Long) {
        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > expected || total > max) throw InvalidBackupException("La copia supera el límite seguro.")
            output.write(buffer, 0, count)
        }
        if (total != expected) throw InvalidBackupException("La copia está truncada.")
    }

    private fun InputStream.readFullyOrFalse(target: ByteArray): Boolean {
        var offset = 0
        while (offset < target.size) {
            val read = read(target, offset, target.size - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private data class ParsedHeader(
        val encrypted: Boolean,
        val passwordlessSealed: Boolean,
        val version: Int,
        val minimum: Int,
        val createdAt: Long,
        val iterations: Int,
        val salt: ByteArray,
        val nonce: ByteArray,
        val payloadBytes: Long,
    ) {
        val publicHeader: BackupContainerHeader = BackupContainerHeader(
            encrypted,
            version,
            minimum,
            createdAt,
            iterations,
        )
    }

    private const val MANIFEST_ENTRY = "manifest.bin"
    private const val DATABASE_ENTRY = "database.bin"
    private const val PREFERENCES_ENTRY = "preferences.bin"
    private const val PHOTOS_ENTRY = "photos.bin"
    private const val PHOTO_PREFIX = "photos/"
    private val MAGIC = "MIGUARDIA-BACKUP".toByteArray(Charsets.US_ASCII)
    private const val FLAG_ENCRYPTED = 1
    private const val FLAG_PASSWORDLESS_SEALED = 1 shl 1
    private const val ALLOWED_FLAGS = FLAG_ENCRYPTED or FLAG_PASSWORDLESS_SEALED
    private const val GCM_TAG_BYTES = 16L
    private val PASSWORDLESS_SEAL_DOMAIN =
        "MiGuardia backup passwordless transport seal v1".toByteArray(Charsets.US_ASCII)
    private const val MAX_METADATA_BYTES = 4L * 1024L * 1024L
    private const val ZIP_EPOCH_MILLIS = 0L
    private val SAFE_STORAGE_KEY = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?:_[0-9a-f]{8})?\\.(?:jpg|jpeg|png|webp)")
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )
}
