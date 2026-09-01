package com.blackatsystems.miguardia.backup

import java.io.File
import java.io.IOException

internal fun File.deletePrivateFileChecked(label: String) {
    if (exists() && !delete()) {
        throw IOException("No se pudo retirar $label.")
    }
}

internal fun File.deletePrivateTreeChecked(label: String) {
    if (exists() && !deleteRecursively()) {
        throw IOException("No se pudo retirar $label.")
    }
}

internal fun File.addPrivateFileCleanupFailure(error: Throwable, label: String) {
    runCatching { deletePrivateFileChecked(label) }.exceptionOrNull()?.let(error::addSuppressed)
}

internal fun File.addPrivateTreeCleanupFailure(error: Throwable, label: String) {
    runCatching { deletePrivateTreeChecked(label) }.exceptionOrNull()?.let(error::addSuppressed)
}
