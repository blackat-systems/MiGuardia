package com.blackatsystems.miguardia.core.domain.backup

/**
 * Operational memory policy for logical backups.
 *
 * Format limits remain stable and provider-independent. This budget is deliberately
 * runtime-dependent so callers can reject work before retaining several decoded
 * snapshots on a constrained Android heap.
 */
object BackupMemoryBudget {
    const val RECOMMENDED_PEAK_FACTOR: Int = 6

    fun operationalHeapBytes(
        maxHeapBytes: Long = Runtime.getRuntime().maxMemory(),
    ): Long {
        if (maxHeapBytes < 4L) {
            throw InvalidBackupException("El límite de memoria disponible para la copia es inválido.")
        }
        return minOf(
            MiGuardiaBackupContract.MAX_LOGICAL_BYTES,
            maxHeapBytes / 4L,
        )
    }

    fun perSnapshotBytes(
        operationalBytes: Long,
        peakFactor: Int,
    ): Long {
        if (operationalBytes <= 0L || peakFactor <= 0) {
            throw InvalidBackupException("El presupuesto de memoria de la copia es inválido.")
        }
        return (operationalBytes / peakFactor.toLong()).also { bytes ->
            if (bytes <= 0L) {
                throw InvalidBackupException("No hay memoria suficiente para abrir la copia con seguridad.")
            }
        }
    }

    fun requireSnapshotFits(
        snapshot: BackupDatabaseSnapshot,
        maximumBytes: Long,
    ): Long {
        if (maximumBytes <= 0L) {
            throw InvalidBackupException("El presupuesto de memoria de la copia es inválido.")
        }
        val estimatedBytes = estimateDecodedDatabaseBytes(snapshot)
        if (estimatedBytes > maximumBytes) {
            throw InvalidBackupException(
                "La base lógica requiere más memoria de la permitida para abrirla con seguridad.",
            )
        }
        return estimatedBytes
    }

    /**
     * Conservative preflight used before allocating a merged snapshot.
     *
     * A conflict-free merge can retain every record from both inputs. Reject it
     * before building lists when that upper bound cannot fit as one snapshot.
     */
    fun requirePotentialMergeFits(
        current: BackupDatabaseSnapshot,
        incoming: BackupDatabaseSnapshot,
        maximumSnapshotBytes: Long,
    ): Long {
        if (maximumSnapshotBytes <= 0L) {
            throw InvalidBackupException("El presupuesto de memoria de la copia es inválido.")
        }
        val estimatedBytes = try {
            Math.addExact(
                estimateDecodedDatabaseBytes(current),
                estimateDecodedDatabaseBytes(incoming),
            )
        } catch (_: ArithmeticException) {
            throw InvalidBackupException("La combinación requiere más memoria de la permitida.")
        }
        if (estimatedBytes > maximumSnapshotBytes) {
            throw InvalidBackupException(
                "La combinación requiere más memoria de la disponible para construirla con seguridad.",
            )
        }
        return estimatedBytes
    }

    /**
     * Validates the conservative peak retained by MAIN while comparing and merging.
     *
     * The largest decoded snapshot is multiplied by [peakFactor]. That covers the
     * current, incoming and merged snapshots plus the transient indexes used by the
     * comparator without depending on their implementation details.
     *
     * @return the conservative peak estimate in bytes.
     */
    fun requirePeakFits(
        current: BackupDatabaseSnapshot?,
        incoming: BackupDatabaseSnapshot,
        merged: BackupDatabaseSnapshot? = null,
        operationalBytes: Long = operationalHeapBytes(),
        peakFactor: Int = RECOMMENDED_PEAK_FACTOR,
    ): Long {
        if (operationalBytes <= 0L || peakFactor <= 0) {
            throw InvalidBackupException("El presupuesto de memoria de la copia es inválido.")
        }
        val largestSnapshot = listOfNotNull(current, incoming, merged)
            .maxOf(::estimateDecodedDatabaseBytes)
        val peakBytes = try {
            Math.multiplyExact(largestSnapshot, peakFactor.toLong())
        } catch (_: ArithmeticException) {
            throw InvalidBackupException("La restauración requiere más memoria de la permitida.")
        }
        if (peakBytes > operationalBytes) {
            throw InvalidBackupException(
                "La restauración requiere más memoria de la disponible para procesarla con seguridad.",
            )
        }
        return peakBytes
    }
}

/** Conservative peak estimate shared by decoding, Room capture and MAIN. */
fun estimateDecodedDatabaseBytes(snapshot: BackupDatabaseSnapshot): Long {
    val estimator = BackupDatabaseMemoryEstimator(Long.MAX_VALUE)
    estimator.consumeSnapshotObject()
    estimator.consumeString(snapshot.roomIdentityHash)
    snapshot.timelineId?.let(estimator::consumeString)
    estimator.consumeTableReferences(snapshot.tables.size)
    snapshot.tables.forEach { table ->
        estimator.consumeTableMetadata(table.name, table.columns, table.primaryKey)
        table.records.forEach { record -> estimator.consumeRecord(record) }
    }
    return estimator.estimatedBytes
}

/**
 * Incremental form of [estimateDecodedDatabaseBytes].
 *
 * It checks before every allocation-sized step and therefore rejects a hostile
 * encoded section or an unexpectedly large Room result with a controlled error.
 */
class BackupDatabaseMemoryEstimator(
    private val maximumBytes: Long,
) {
    var estimatedBytes: Long = 0L
        private set

    init {
        if (maximumBytes <= 0L) {
            throw InvalidBackupException("El presupuesto de memoria de la copia es inválido.")
        }
    }

    fun consumeSnapshotObject() {
        consume(SNAPSHOT_BASE_BYTES)
    }

    fun consumeTableReferences(tableCount: Int) {
        if (tableCount < 0) throw InvalidBackupException("La cantidad de tablas es inválida.")
        consume(LIST_BASE_BYTES)
        consumeProduct(tableCount.toLong(), REFERENCE_BYTES)
    }

    fun consumeTableMetadata(
        name: String,
        columns: List<String>,
        primaryKey: List<String>,
    ) {
        consumeTableObject()
        consumeString(name)
        consumeStringList(columns)
        consumeStringList(primaryKey)
    }

    fun consumeTableObject() {
        consume(TABLE_BASE_BYTES)
        consume(LIST_BASE_BYTES)
    }

    fun consumeListReferences(count: Int) {
        if (count < 0) throw InvalidBackupException("La cantidad de elementos es inválida.")
        consume(LIST_BASE_BYTES)
        consumeProduct(count.toLong(), REFERENCE_BYTES)
    }

    /** Reserves only the backing references before an ArrayList(recordCount) allocation. */
    fun reserveRecordReferences(recordCount: Int) {
        if (recordCount < 0) throw InvalidBackupException("La cantidad de filas es inválida.")
        consumeProduct(recordCount.toLong(), REFERENCE_BYTES)
    }

    fun consumeRecord(
        record: BackupRecord,
        recordReferenceAlreadyReserved: Boolean = false,
    ) {
        consumeRecordObject(record.values.size, recordReferenceAlreadyReserved)
        record.values.forEach(::consumeValue)
    }

    fun consumeRecordObject(
        valueCount: Int,
        recordReferenceAlreadyReserved: Boolean = false,
    ) {
        if (valueCount < 0) throw InvalidBackupException("La cantidad de celdas es inválida.")
        if (!recordReferenceAlreadyReserved) consume(REFERENCE_BYTES)
        consume(RECORD_BASE_BYTES)
        consume(LIST_BASE_BYTES)
        consumeProduct(valueCount.toLong(), REFERENCE_BYTES)
    }

    private fun consumeStringList(values: List<String>) {
        consume(LIST_BASE_BYTES)
        consumeProduct(values.size.toLong(), REFERENCE_BYTES)
        values.forEach(::consumeString)
    }

    fun consumeValue(value: BackupValue) {
        when (value) {
            BackupValue.Null -> Unit
            is BackupValue.Integer, is BackupValue.Real -> consumeValueObject()
            is BackupValue.Text -> {
                consumeValueObject()
                consumeString(value.value)
            }
            is BackupValue.Binary -> {
                consumeBinaryValue(value.value.size)
            }
        }
    }

    fun consumeValueObject() {
        consume(VALUE_OBJECT_BYTES)
    }

    fun consumeBinaryValue(encodedBytes: Int) {
        if (encodedBytes < 0) throw InvalidBackupException("El tamaño binario es inválido.")
        consumeValueObject()
        consume(ARRAY_BASE_BYTES * 2L)
        // Cursor/decoder source plus BackupValue.Binary's defensive copy.
        consumeProduct(encodedBytes.toLong(), 2L)
    }

    fun consumeString(value: String) {
        consumeEncodedString(value.utf8Length())
    }

    fun consumeEncodedString(encodedBytes: Int) {
        if (encodedBytes < 0) throw InvalidBackupException("El tamaño de texto es inválido.")
        consume(STRING_BASE_BYTES)
        // Input bytes, retained UTF-16 String and the canonical UTF-8 verification copy.
        consumeProduct(encodedBytes.toLong(), STRING_ENCODED_PEAK_FACTOR)
    }

    private fun consumeProduct(count: Long, unitBytes: Long) {
        val bytes = try {
            Math.multiplyExact(count, unitBytes)
        } catch (_: ArithmeticException) {
            throw InvalidBackupException("La base lógica requiere más memoria de la permitida.")
        }
        consume(bytes)
    }

    private fun consume(bytes: Long) {
        if (bytes < 0L || estimatedBytes > maximumBytes - bytes) {
            throw InvalidBackupException(
                "La base lógica requiere más memoria de la permitida para abrirla con seguridad.",
            )
        }
        estimatedBytes += bytes
    }

    private fun String.utf8Length(): Int {
        var bytes = 0L
        var index = 0
        while (index < length) {
            val char = this[index]
            val encodedBytes = when {
                char.code < 0x80 -> 1
                char.code < 0x800 -> 2
                Character.isHighSurrogate(char) &&
                    index + 1 < length && Character.isLowSurrogate(this[index + 1]) -> {
                    index += 1
                    4
                }
                Character.isSurrogate(char) -> {
                    // The JVM UTF-8 encoder replaces an unpaired surrogate with '?'.
                    1
                }
                else -> 3
            }
            bytes += encodedBytes
            if (bytes > Int.MAX_VALUE) {
                throw InvalidBackupException("Un texto supera el límite seguro de memoria.")
            }
            index += 1
        }
        return bytes.toInt()
    }

    private companion object {
        const val SNAPSHOT_BASE_BYTES = 64L
        const val TABLE_BASE_BYTES = 64L
        const val RECORD_BASE_BYTES = 40L
        const val LIST_BASE_BYTES = 32L
        const val STRING_BASE_BYTES = 40L
        const val VALUE_OBJECT_BYTES = 24L
        const val ARRAY_BASE_BYTES = 24L
        const val REFERENCE_BYTES = 8L
        const val STRING_ENCODED_PEAK_FACTOR = 4L
    }
}
