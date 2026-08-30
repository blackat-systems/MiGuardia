package com.blackatsystems.miguardia.reports

import java.io.File

data class FrozenReportPhoto(
    val stableOrder: Int,
    val file: File,
    val mimeType: String,
    val caption: String,
)

data class FrozenReportAssets(
    val photos: List<FrozenReportPhoto>,
    internal val stagingDirectory: File?,
) {
    init {
        require(photos.map { it.stableOrder } == photos.indices.toList())
        require(photos.all { it.file.isFile && it.file.canRead() })
    }

    companion object {
        val EMPTY: FrozenReportAssets = FrozenReportAssets(emptyList(), null)
    }
}
