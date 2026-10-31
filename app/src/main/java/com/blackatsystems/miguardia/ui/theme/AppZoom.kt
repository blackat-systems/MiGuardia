package com.blackatsystems.miguardia.ui.theme

/** Legacy value retained only to preserve existing portable Copias preferences. UI ignores it. */
enum class AppZoom(
    val percent: Int,
    val scale: Float,
) {
    STANDARD(percent = 100, scale = 1f),
    LARGE(percent = 150, scale = 1.5f),
    EXTRA_LARGE(percent = 200, scale = 2f),
    ;

    val label: String
        get() = "$percent %"

    companion object {
        fun fromPercent(percent: Int): AppZoom = entries.firstOrNull {
            it.percent == percent
        } ?: STANDARD
    }
}
