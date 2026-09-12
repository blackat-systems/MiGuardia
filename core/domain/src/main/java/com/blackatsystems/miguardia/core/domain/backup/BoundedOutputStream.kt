package com.blackatsystems.miguardia.core.domain.backup

import java.io.FilterOutputStream
import java.io.OutputStream

/** Rejects the first byte beyond [maximumBytes] before forwarding it. */
internal class BoundedOutputStream(
    output: OutputStream,
    private val maximumBytes: Long,
    private val section: String,
) : FilterOutputStream(output) {
    var writtenBytes: Long = 0L
        private set

    init {
        if (maximumBytes < 0L) {
            throw InvalidBackupException("El límite de $section es inválido.")
        }
    }

    override fun write(value: Int) {
        reserve(1L)
        out.write(value)
    }

    override fun write(buffer: ByteArray) {
        write(buffer, 0, buffer.size)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset > buffer.size - length) {
            throw IndexOutOfBoundsException()
        }
        reserve(length.toLong())
        out.write(buffer, offset, length)
    }

    private fun reserve(bytes: Long) {
        if (bytes < 0L || writtenBytes > maximumBytes - bytes) {
            throw InvalidBackupException("La sección de $section supera el límite seguro.")
        }
        writtenBytes += bytes
    }
}
