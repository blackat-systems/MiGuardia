package com.blackatsystems.miguardia.stage12

import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Calendar-only use of an existing own fixture; not a new C026 acceptance or a device clock override. */
internal object Stage12CalendarDetailContract {
    const val SELECTOR = "com.blackatsystems.miguardia.stage12.Stage12CalendarDetailNativeTest#ownedWeatherReturnAndTodayFutureReopenStayStable"
    const val SLOT = "calendar-detail-weather"
    const val ACTIVATION_SELECTOR = "com.blackatsystems.miguardia.stage12.Stage12CalendarDetailNativeTest#enablingWeatherFromDayCardPersistsAndReopensWithoutLocation"
    const val ACTIVATION_SLOT = "calendar-weather-activation"
    val activationKeys = listOf(
        "C081-habilitar-clima-desde-tarjeta.ui_compose",
        "C081-habilitar-clima-desde-tarjeta.activity_native",
        "C081-reabrir-clima-habilitado.ui_compose",
        "C081-reabrir-clima-habilitado.activity_native",
    )
    const val SCHEMA = "stage12-calendar-detail-v1"
    const val FIXTURE_ROLE = "CALENDAR_ONLY"
    val today: LocalDate = LocalDate.of(2026, 10, 23)
    val future: LocalDate = LocalDate.of(2026, 10, 24)
    val reference: Instant = Instant.parse("2026-10-23T10:00:00Z")
    fun clock(): Clock = Clock.fixed(reference, Stage12TemporalLiteralOracle.zone)
    fun scope() = Stage12TemporalScope(Stage12TemporalVariant.SAME_DAY)
    const val STABLE_MINIMUM_MS = 1200L
    const val DEADLINE_MS = 30000L
    val keys = listOf(
        "C030-abrir-clima-desde-fecha.ui_compose",
        "C030-abrir-clima-desde-fecha.activity_native",
        "C030-cerrar-retorna-mismo-dia.ui_compose",
        "C030-cerrar-retorna-mismo-dia.activity_native",
        "C034-hoy-cerrar-reabrir-nativo.ui_compose",
        "C034-hoy-cerrar-reabrir-nativo.activity_native",
        "C034-futuro-cerrar-reabrir-nativo.ui_compose",
        "C034-futuro-cerrar-reabrir-nativo.activity_native",
        "C034-clima-retorno-animacion-real.ui_compose",
        "C034-clima-retorno-animacion-real.activity_native",
    )
}
