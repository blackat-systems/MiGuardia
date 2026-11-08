package com.blackatsystems.miguardia.ui.theme

enum class AppThemeMode(
    val label: String,
) {
    SYSTEM("Seguir el sistema"),
    LIGHT("Claro"),
    DARK("Oscuro"),
    ;

    fun resolve(systemDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        val DEFAULT: AppThemeMode = DARK

        fun fromStorage(value: String?): AppThemeMode = entries.firstOrNull {
            it.name == value
        } ?: DEFAULT
    }
}

data class VigiliaSystemBarStyle(
    val backgroundArgb: Int,
    val useDarkIcons: Boolean,
)

fun vigiliaSystemBarStyle(darkTheme: Boolean): VigiliaSystemBarStyle = if (darkTheme) {
    VigiliaSystemBarStyle(
        backgroundArgb = 0xFF1A1B26.toInt(),
        useDarkIcons = false,
    )
} else {
    VigiliaSystemBarStyle(
        backgroundArgb = 0xFFE1E2E7.toInt(),
        useDarkIcons = true,
    )
}
