package com.blackatsystems.miguardia.weather

/** Presentation credits only; provider access and weather calculations stay with their owners. */
internal object WeatherCredits {
    const val SUMMARY = "Open-Meteo · CC BY 4.0"
    const val PRESENTATION = "MiGuardia resume los datos de Open-Meteo para la jornada, redondea valores y convierte la temperatura a °F si elegís esa unidad."
    const val OPEN = "Ver créditos y licencia en Clima"

    fun forDisplayedWeather(weatherText: String?): String? =
        SUMMARY.takeUnless { weatherText.isNullOrBlank() }
}

internal enum class WeatherCreditLink(val label: String, val url: String) {
    PROVIDER("Datos meteorológicos: Open-Meteo", "https://open-meteo.com/"),
    LICENCE("Licencia de los datos: CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"),
    TERMS("Ver términos y privacidad", "https://open-meteo.com/en/terms"),
}
