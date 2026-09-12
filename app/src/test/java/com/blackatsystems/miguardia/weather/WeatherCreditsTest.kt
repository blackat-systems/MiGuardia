package com.blackatsystems.miguardia.weather

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherCreditsTest {
    @Test
    fun `absent or blank weather has no credit`() {
        listOf(null, "", " ", "\n\t").forEach { assertNull(WeatherCredits.forDisplayedWeather(it)) }
    }

    @Test
    fun `credit is independent of forecast length and temperature units`() {
        listOf("Clima: lluvia · 12–18 °C", "Clima: lluvia · 54–64 °F", "Pronóstico largo ".repeat(500)).forEach {
            val credit = WeatherCredits.forDisplayedWeather(it)!!
            assertEquals("Open-Meteo · CC BY 4.0", credit)
            assertEquals(1, Regex("Open-Meteo").findAll(credit).count())
        }
    }

    @Test
    fun `credit links are fixed HTTPS destinations with no private query or duplicate`() {
        val urls = WeatherCreditLink.entries.map { it.url }
        assertEquals(
            setOf("https://open-meteo.com/", "https://creativecommons.org/licenses/by/4.0/", "https://open-meteo.com/en/terms"),
            urls.toSet(),
        )
        assertEquals(urls.size, urls.toSet().size)
        urls.map(::URI).forEach {
            assertEquals("https", it.scheme)
            assertNull(it.rawQuery)
            assertNull(it.userInfo)
            assertNull(it.fragment)
            assertTrue(it.host in setOf("open-meteo.com", "creativecommons.org"))
        }
    }
}
