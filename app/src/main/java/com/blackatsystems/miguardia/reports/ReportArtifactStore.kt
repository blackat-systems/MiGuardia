package com.blackatsystems.miguardia.reports

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.blackatsystems.miguardia.core.domain.report.ReportFormat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ReportArtifact(
    val file: File,
    val format: ReportFormat,
    val suggestedFileName: String,
    val byteSize: Long,
) {
    init {
        require(file.isFile && file.canRead() && file.length() == byteSize && byteSize > 0L)
        require(file.extension == format.extension)
    }
}

class ReportArtifactStore(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val root = File(context.filesDir, ARTIFACT_DIRECTORY)

    suspend fun create(
        format: ReportFormat,
        suggestedFileName: String,
        protectedArtifact: File?,
        write: (FileOutputStream) -> Unit,
    ): ReportArtifact = withContext(Dispatchers.IO) {
        ensureRoot()
        cleanup(protectedArtifact, preparingNewArtifact = true)
        val opaqueStem = UUID.randomUUID().toString().replace("-", "")
        val temporary = File(root, "$opaqueStem.${format.extension}.tmp")
        val target = File(root, "$opaqueStem.${format.extension}")
        requireScoped(temporary)
        requireScoped(target)
        try {
            FileOutputStream(temporary).use { output ->
                write(output)
                output.flush()
                output.fd.sync()
            }
            validate(temporary, format)
            moveSafely(temporary, target)
            validate(target, format)
            cleanup(target, preparingNewArtifact = false)
            ReportArtifact(
                file = target,
                format = format,
                suggestedFileName = suggestedFileName,
                byteSize = target.length(),
            )
        } catch (error: Exception) {
            temporary.delete()
            if (target.exists() && !runCatching { validate(target, format) }.isSuccess) target.delete()
            throw when (error) {
                is ReportArtifactException -> error
                else -> ReportArtifactException("No pudimos completar el archivo privado del informe.", error)
            }
        }
    }

    suspend fun cleanup(protectedArtifact: File? = null) = withContext(Dispatchers.IO) {
        ensureRoot()
        cleanup(protectedArtifact, preparingNewArtifact = false)
    }

    fun validate(file: File, format: ReportFormat) {
        requireScoped(file)
        if (!file.isFile || !file.canRead() || file.length() <= 0L) {
            throw ReportArtifactException("El artefacto del informe está vacío o no se puede leer.")
        }
        when (format) {
            ReportFormat.PDF -> validatePdf(file)
            ReportFormat.XLSX -> validateXlsx(file)
        }
    }

    private fun validatePdf(file: File) {
        val header = ByteArray(PDF_HEADER.size)
        val read = FileInputStream(file).use { it.read(header) }
        if (read != header.size || !header.contentEquals(PDF_HEADER)) {
            throw ReportArtifactException("El archivo generado no tiene una firma PDF válida.")
        }
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount <= 0) {
                        throw ReportArtifactException("El PDF generado no contiene páginas legibles.")
                    }
                    repeat(renderer.pageCount) { index -> renderer.openPage(index).close() }
                }
            }
        } catch (error: ReportArtifactException) {
            throw error
        } catch (error: Exception) {
            throw ReportArtifactException("El PDF generado no tiene una estructura legible.", error)
        }
    }

    private fun validateXlsx(file: File) {
        val signature = ByteArray(ZIP_HEADER.size)
        val read = FileInputStream(file).use { it.read(signature) }
        if (read != signature.size || !signature.contentEquals(ZIP_HEADER)) {
            throw ReportArtifactException("El archivo generado no tiene una firma ZIP válida.")
        }
        try {
            ZipFile(file).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toSet()
                val required = setOf(
                    "[Content_Types].xml",
                    "_rels/.rels",
                    "xl/workbook.xml",
                    "xl/_rels/workbook.xml.rels",
                    "xl/styles.xml",
                    "xl/worksheets/sheet1.xml",
                    "xl/worksheets/sheet2.xml",
                    "xl/worksheets/sheet3.xml",
                    "xl/worksheets/sheet4.xml",
                )
                if (!names.containsAll(required)) {
                    throw ReportArtifactException("El XLSX no contiene su estructura OOXML mínima.")
                }
                if (names.any { it.contains("externalLink", ignoreCase = true) || it.endsWith("vbaProject.bin") }) {
                    throw ReportArtifactException("El XLSX contiene vínculos externos o macros no permitidos.")
                }
                names.filter { it.endsWith(".xml") || it.endsWith(".rels") }.sorted().forEach { name ->
                    val entry = zip.getEntry(name)
                    if (entry.size == 0L) {
                        throw ReportArtifactException("La parte OOXML $name está vacía.")
                    }
                    zip.getInputStream(entry).use(::parseXmlSecurely)
                }
            }
        } catch (error: ReportArtifactException) {
            throw error
        } catch (error: Exception) {
            throw ReportArtifactException("El paquete XLSX generado no es un OOXML legible.", error)
        }
    }

    private fun parseXmlSecurely(input: java.io.InputStream) {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { isXIncludeAware = false }
            runCatching { setExpandEntityReferences(false) }
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        factory.newDocumentBuilder().parse(input)
    }

    private fun moveSafely(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath())
        }
    }

    private fun cleanup(protectedArtifact: File?, preparingNewArtifact: Boolean) {
        val protectedCanonical = protectedArtifact
            ?.takeIf { it.exists() }
            ?.canonicalFile
            ?.also(::requireScoped)
        val now = clock.instant().toEpochMilli()
        root.listFiles().orEmpty()
            .filter { it.isFile && it.canonicalFile != protectedCanonical }
            .forEach { candidate ->
                val isTemporary = candidate.name.endsWith(".tmp")
                val isExpired = now - candidate.lastModified() > RETENTION.toMillis()
                if ((isTemporary || isExpired) && !candidate.delete()) {
                    throw ReportArtifactException("No pudimos limpiar un temporal antiguo de Informes.")
                }
            }
        val maximumBeforeReturn = if (preparingNewArtifact) MAX_ARTIFACTS - 1 else MAX_ARTIFACTS
        val validArtifacts = root.listFiles().orEmpty()
            .filter { it.isFile && !it.name.endsWith(".tmp") }
            .sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name })
            .toMutableList()
        while (validArtifacts.size > maximumBeforeReturn) {
            val removable = validArtifacts.firstOrNull { it.canonicalFile != protectedCanonical }
                ?: throw ReportArtifactException("No hay un artefacto antiguo removible dentro de la retención.")
            if (!removable.delete()) {
                throw ReportArtifactException("No pudimos aplicar la retención privada de Informes.")
            }
            validArtifacts.remove(removable)
        }
    }

    private fun ensureRoot() {
        if (!(root.mkdirs() || root.isDirectory)) {
            throw ReportArtifactException("No se pudo preparar el directorio privado de Informes.")
        }
    }

    private fun requireScoped(file: File) {
        if (file.canonicalFile.parentFile != root.canonicalFile) {
            throw ReportArtifactException("Ruta de artefacto fuera del directorio privado de Informes.")
        }
    }

    companion object {
        const val ARTIFACT_DIRECTORY: String = "reports/artifacts"
        const val MAX_ARTIFACTS: Int = 3
        val RETENTION: Duration = Duration.ofHours(24)
        private val PDF_HEADER = "%PDF-".toByteArray(Charsets.US_ASCII)
        private val ZIP_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    }
}

class ReportArtifactException(message: String, cause: Throwable? = null) : IOException(message, cause)
