package com.blackatsystems.miguardia.stage12.c096

import java.time.*
import java.util.UUID

internal enum class C096Variant(val run: String, val component: String) {
    VIEW_DETAIL("7b18b0dd-7816-4dd8-a1c0-9a13486ec7cf", "com.blackatsystems.miguardia.stage12.c096.C096ViewDetailActivity"),
    EDIT_DRAFT("73f40b86-1cd2-4417-a430-3eafd77ac939", "com.blackatsystems.miguardia.stage12.c096.C096EditDraftActivity");
}
internal object C096Recipe {
    const val PROGRAM = "ec22c846-41d8-409e-9d64-d3cbe87095a8"
    const val INITIAL_IMAGE_INPUT = "HUMAN_PNG_ONCE_EDIT_A"
    const val QA = "com.blackatsystems.miguardia.qa"
    const val CERT = "b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617"
    val zone: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
    val month: YearMonth = YearMonth.of(2026, 10)
    val detailDate: LocalDate = LocalDate.of(2026, 10, 23)
    val selectedDates = setOf(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 26))
    val stamp: Instant = Instant.parse("2026-10-01T00:00:00Z")
    const val NOTE = "C096_NOTA_PROPIA"
    const val POSITION = "C096_PUESTO_PROPIO"
    const val PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII="
    val png: ByteArray get() = java.util.Base64.getDecoder().decode(PNG)
    fun id(v: C096Variant, role: String): UUID = UUID.nameUUIDFromBytes((v.run + ":C096:" + role).toByteArray(Charsets.UTF_8))
}
