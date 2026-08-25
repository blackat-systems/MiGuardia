package com.blackatsystems.miguardia.core.domain.model

import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IndependentExtraWorkTest {
    @Test
    fun finishedAtTheCurrentMinuteIsAcceptedAndTemplateSuppliesTheColor() {
        val record = buildIndependentExtraWorkRecord(
            draft = draft(start = instant("2026-08-24T22:00:00Z"), end = NOW, color = 0),
            selection = selection(
                configuration = ResolvedWorkConfigurationRevision.resolve(
                    HISTORY,
                    LocalDate.of(2026, 8, 24),
                ),
            ),
            clock = CLOCK,
            timestamp = NOW,
        )

        assertEquals(14L * 60L, record.durationMinutes)
        assertEquals(TEMPLATE_COLOR, record.snapshot.colorArgb)
        assertEquals(LocalDate.of(2026, 8, 24), record.ownerLocalDate)
    }

    @Test
    fun manualColorAndIntervalsLongerThanOneDayArePreservedWithoutCreatingATemplate() {
        val record = buildIndependentExtraWorkRecord(
            draft = draft(
                start = instant("2026-08-22T23:00:00Z"),
                end = instant("2026-08-24T12:00:00Z"),
                color = MANUAL_COLOR,
            ),
            selection = selection(
                template = null,
                configuration = ResolvedWorkConfigurationRevision.resolve(
                    HISTORY,
                    LocalDate.of(2026, 8, 22),
                ),
            ),
            clock = CLOCK,
            timestamp = NOW,
        )

        assertEquals(37L * 60L, record.durationMinutes)
        assertEquals(MANUAL_COLOR, record.snapshot.colorArgb)
        assertEquals(null, record.templateId)
    }

    @Test
    fun currentOrFutureWorkAndSubMinuteIntervalsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            buildIndependentExtraWorkRecord(
                draft(NOW.minusSeconds(60), NOW.plusSeconds(60), TEMPLATE_COLOR),
                selection(),
                CLOCK,
                NOW,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildIndependentExtraWorkRecord(
                draft(NOW.minusSeconds(61), NOW, TEMPLATE_COLOR),
                selection(),
                CLOCK,
                NOW,
            )
        }
    }

    @Test
    fun archivedSourcesCanOnlyPreserveTheSameHistoricalClassification() {
        val original = buildIndependentExtraWorkRecord(
            draft(NOW.minusSeconds(7_200), NOW.minusSeconds(3_600), TEMPLATE_COLOR),
            selection(),
            CLOCK,
            NOW,
        )
        val archived = selection(
            placeActive = false,
            typeActive = false,
            templateActive = false,
            classActive = false,
        )
        val corrected = buildIndependentExtraWorkRecord(
            draft = draft(
                NOW.minusSeconds(7_200),
                NOW.minusSeconds(3_540),
                MANUAL_COLOR,
                id = original.id,
            ),
            selection = archived,
            clock = CLOCK,
            timestamp = NOW.plusMillis(1),
            previous = original,
            preserveHistoricalSnapshot = true,
        )

        assertEquals(original.snapshot, corrected.snapshot)
        assertEquals(original.createdAt, corrected.createdAt)
        assertTrue(corrected.updatedAt.isAfter(original.updatedAt))
        val differentArchivedClass = archived.extraWorkClass.copy(id = uuid(99))
        assertThrows(IllegalArgumentException::class.java) {
            buildIndependentExtraWorkRecord(
                draft = draft(
                    NOW.minusSeconds(7_200),
                    NOW.minusSeconds(3_540),
                    MANUAL_COLOR,
                    id = original.id,
                ),
                selection = archived.copy(extraWorkClass = differentArchivedClass),
                clock = CLOCK,
                timestamp = NOW.plusMillis(1),
                previous = original,
            )
        }
    }

    @Test
    fun snapshotKeepsClassMeaningAfterTheLiveClassIsRenamed() {
        val original = buildIndependentExtraWorkRecord(
            draft(NOW.minusSeconds(7_200), NOW.minusSeconds(3_600), TEMPLATE_COLOR),
            selection(),
            CLOCK,
            NOW,
        )
        val renamed = EXTRA_CLASS.updated(
            name = "Servicio especial",
            helpsMeetHoursReference = false,
            timestamp = NOW.plusMillis(1),
        )

        assertEquals("Horas extra", original.snapshot.className)
        assertTrue(original.snapshot.helpsMeetHoursReference)
        assertEquals("Servicio especial", renamed.name)
        assertFalse(renamed.helpsMeetHoursReference)
    }

    @Test
    fun correctionWithoutTemplateCanChangeOnlyRecordColorAndPositionWhileSourcesStayHistorical() {
        val original = buildIndependentExtraWorkRecord(
            draft(NOW.minusSeconds(7_200), NOW.minusSeconds(3_600), MANUAL_COLOR),
            selection(template = null),
            CLOCK,
            NOW,
        )
        val correctedDraft = draft(
            NOW.minusSeconds(7_200),
            NOW.minusSeconds(3_540),
            TEMPLATE_COLOR,
            original.id,
        ).copy(position = "  Puesto corregido  ")

        val corrected = buildIndependentExtraWorkRecord(
            correctedDraft,
            selection(template = null, placeActive = false, typeActive = false, classActive = false),
            CLOCK,
            NOW.plusMillis(1),
            previous = original,
            preserveHistoricalSnapshot = true,
        )

        assertEquals(TEMPLATE_COLOR, corrected.snapshot.colorArgb)
        assertEquals("Puesto corregido", corrected.snapshot.position)
        assertEquals(original.snapshot.workPlaceName, corrected.snapshot.workPlaceName)
        assertEquals(original.snapshot.className, corrected.snapshot.className)
    }

    @Test
    fun referenceOnlyConfigurationRevisionDoesNotRewriteHistoricalSourcesOrRevision() {
        val original = buildIndependentExtraWorkRecord(
            draft(NOW.minusSeconds(7_200), NOW.minusSeconds(3_600), TEMPLATE_COLOR),
            selection(),
            CLOCK,
            NOW,
        )
        val referenceRevision = EffectiveRevision(
            uuid(9),
            DATE.plusDays(1),
            WorkConfiguration(WorkSector.NURSING, HoursReference.NotUsed, null),
        )
        val changedHistory = WorkConfigurationHistory(
            EffectiveDateTimeline(TIMELINE_ID, HISTORY.timeline.revisions + referenceRevision),
            PerPeriodHoursValues(emptyList()),
        )
        val currentConfiguration = ResolvedWorkConfigurationRevision.resolve(
            changedHistory,
            original.ownerLocalDate,
        )

        val corrected = buildIndependentExtraWorkRecord(
            draft(
                original.start,
                original.end.plusSeconds(60),
                TEMPLATE_COLOR,
                original.id,
            ),
            selection(
                configuration = currentConfiguration,
                placeActive = false,
                typeActive = false,
                templateActive = false,
                classActive = false,
            ),
            CLOCK,
            NOW.plusMillis(1),
            previous = original,
            preserveHistoricalSnapshot = true,
        )

        assertEquals(original.configurationRevisionId, corrected.configurationRevisionId)
        assertEquals(original.snapshot, corrected.snapshot)
    }

    @Test
    fun configurationMustBeResolvedForTheExactOwnerDate() {
        val otherDate = DATE
        val wrongConfiguration = ResolvedWorkConfigurationRevision.resolve(HISTORY, otherDate)

        assertThrows(IllegalArgumentException::class.java) {
            buildIndependentExtraWorkRecord(
                draft(NOW.minusSeconds(7_200), NOW.minusSeconds(3_600), TEMPLATE_COLOR),
                selection(configuration = wrongConfiguration),
                CLOCK,
                NOW,
            )
        }
    }

    @Test
    fun sameSourceIdsAlwaysPreserveHistoricalSnapshotAfterActiveRenames() {
        val original = buildIndependentExtraWorkRecord(
            draft(NOW.minusSeconds(7_200), NOW.minusSeconds(3_600), TEMPLATE_COLOR),
            selection(),
            CLOCK,
            NOW,
        )
        val renamedClass = EXTRA_CLASS.updated(
            name = "Servicio renombrado",
            helpsMeetHoursReference = false,
            timestamp = NOW.plusMillis(1),
        )
        val renamedSelection = IndependentExtraWorkSelection(
            CONFIGURATION,
            PLACE,
            OBJECTIVE.copy(fullName = "Hospital renombrado"),
            TYPE,
            TEMPLATE,
            renamedClass,
        )

        val corrected = buildIndependentExtraWorkRecord(
            draft(
                original.start,
                original.end.plusSeconds(60),
                TEMPLATE_COLOR,
                original.id,
            ),
            renamedSelection,
            CLOCK,
            NOW.plusMillis(2),
            previous = original,
            preserveHistoricalSnapshot = false,
        )

        assertEquals(original.snapshot, corrected.snapshot)
    }

    @Test
    fun capturedOccupancySetsCannotBeMutatedAfterCasCapture() {
        val occupied = IndependentExtraOccupancyVersion(
            uuid(90),
            NOW.minusSeconds(7_200),
            NOW.minusSeconds(3_600),
            NOW,
        )
        val expectation = IndependentExtraWorkExpectation.capture(
            previous = null,
            selection = selection(),
            windowStart = NOW.minusSeconds(10_800),
            windowEnd = NOW,
            windowStartDate = DATE,
            windowEndDateInclusive = DATE,
            observedShifts = emptyList(),
            observedExtras = listOf(occupied),
            protectionFingerprint = "",
        )

        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (expectation.observedExtras as MutableSet<IndependentExtraOccupancyVersion>).clear()
        }
        assertEquals(setOf(occupied), expectation.observedExtras)
    }

    @Test
    fun multidayMutationRequiresProtectionWindowToCoverEveryWorkedDate() {
        val start = instant("2026-08-22T23:00:00Z")
        val end = instant("2026-08-24T12:00:00Z")
        val exactSelection = selection(
            configuration = ResolvedWorkConfigurationRevision.resolve(HISTORY, LocalDate.of(2026, 8, 22)),
        )
        val record = buildIndependentExtraWorkRecord(
            draft(start, end, TEMPLATE_COLOR),
            exactSelection,
            CLOCK,
            NOW,
        )
        val incomplete = IndependentExtraWorkExpectation.capture(
            previous = null,
            selection = exactSelection,
            windowStart = start,
            windowEnd = end,
            windowStartDate = LocalDate.of(2026, 8, 22),
            windowEndDateInclusive = LocalDate.of(2026, 8, 22),
            observedShifts = emptyList(),
            observedExtras = emptyList(),
            protectionFingerprint = "",
        )

        assertThrows(IllegalArgumentException::class.java) {
            IndependentExtraWorkMutation(incomplete, record, true, true)
        }
    }

    @Test
    fun intervalEndingExactlyAtMidnightDoesNotOccupyTheFollowingDate() {
        val start = instant("2026-08-24T20:00:00Z")
        val end = instant("2026-08-25T00:00:00Z")
        val ownerDate = LocalDate.of(2026, 8, 24)
        val exactSelection = selection(
            configuration = ResolvedWorkConfigurationRevision.resolve(HISTORY, ownerDate),
        )
        val record = buildIndependentExtraWorkRecord(
            draft(start, end, TEMPLATE_COLOR),
            exactSelection,
            CLOCK,
            NOW,
        )
        val exactWindow = IndependentExtraWorkExpectation.capture(
            previous = null,
            selection = exactSelection,
            windowStart = start,
            windowEnd = end,
            windowStartDate = ownerDate,
            windowEndDateInclusive = ownerDate,
            observedShifts = emptyList(),
            observedExtras = emptyList(),
            protectionFingerprint = "",
        )

        IndependentExtraWorkMutation(exactWindow, record, true, true)
    }

    @Test
    fun warningsIgnoreWorkAndProtectionsThatExistOnlyInTheCorrectionGap() {
        val start = instant("2026-08-22T08:00:00Z")
        val end = instant("2026-08-22T09:00:00Z")
        val ownerDate = LocalDate.of(2026, 8, 22)
        val exactSelection = selection(
            configuration = ResolvedWorkConfigurationRevision.resolve(HISTORY, ownerDate),
        )
        val record = buildIndependentExtraWorkRecord(
            draft(start, end, TEMPLATE_COLOR),
            exactSelection,
            CLOCK,
            NOW,
        )
        val wideCasWindow = IndependentExtraWorkExpectation.capture(
            previous = null,
            selection = exactSelection,
            windowStart = start,
            windowEnd = instant("2026-08-24T21:00:00Z"),
            windowStartDate = ownerDate,
            windowEndDateInclusive = LocalDate.of(2026, 8, 24),
            observedShifts = listOf(
                ShiftOccupancyVersion(
                    uuid(91),
                    LocalDate.of(2026, 8, 23),
                    instant("2026-08-23T12:00:00Z"),
                    instant("2026-08-23T13:00:00Z"),
                    ShiftStatus.PLANNED,
                    NOW,
                ),
            ),
            observedExtras = emptyList(),
            observedProtectedDateRanges = listOf(
                IndependentExtraProtectedDateRange(
                    LocalDate.of(2026, 8, 24),
                    LocalDate.of(2026, 8, 24),
                ),
            ),
            protectionFingerprint = "protegido-en-la-cola-anterior",
        )

        assertFalse(wideCasWindow.hasOverlappingWorkFor(record))
        assertFalse(wideCasWindow.hasProtectedDatesFor(record))
    }

    private fun draft(
        start: Instant,
        end: Instant,
        color: Int,
        id: UUID = RECORD_ID,
    ) = IndependentExtraWorkDraft(
        id = id,
        ownerLocalDate = start.atZone(ZONE).toLocalDate(),
        zoneId = ZONE,
        start = start,
        end = end,
        colorArgb = color,
        position = "Puesto 1",
    )

    private fun selection(
        template: WorkTemplate? = template(),
        configuration: ResolvedWorkConfigurationRevision = CONFIGURATION,
        placeActive: Boolean = true,
        typeActive: Boolean = true,
        templateActive: Boolean = true,
        classActive: Boolean = true,
    ): IndependentExtraWorkSelection {
        val objective = OBJECTIVE.copy(isActive = placeActive)
        val place = PLACE.copy(isActive = placeActive)
        val type = TYPE.copy(isActive = typeActive)
        val selectedTemplate = template?.copy(isActive = templateActive)
        val extraClass = EXTRA_CLASS.copy(isActive = classActive)
        return IndependentExtraWorkSelection(configuration, place, objective, type, selectedTemplate, extraClass)
    }

    private fun template() = TEMPLATE

    private companion object {
        val NOW: Instant = instant("2026-08-25T12:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val ZONE: ZoneId = ZoneOffset.UTC
        val DATE: LocalDate = LocalDate.of(2026, 8, 20)
        const val TEMPLATE_COLOR: Int = 0xFF334455.toInt()
        const val MANUAL_COLOR: Int = 0xFFAA5500.toInt()
        val TIMELINE_ID: UUID = uuid(1)
        val REVISION_ID: UUID = uuid(2)
        val OBJECTIVE_ID: UUID = uuid(3)
        val PLACE_ID: UUID = uuid(4)
        val TYPE_ID: UUID = uuid(5)
        val TEMPLATE_ID: UUID = uuid(6)
        val CLASS_ID: UUID = uuid(7)
        val RECORD_ID: UUID = uuid(8)
        val OBJECTIVE = Objective(
            OBJECTIVE_ID,
            "Hospital Central",
            "HCE",
            "Calle 1",
            null,
            true,
            NOW.minusSeconds(86_400),
            NOW.minusSeconds(86_400),
        )
        val PLACE = WorkPlace(
            PLACE_ID,
            TIMELINE_ID,
            WorkSector.NURSING,
            OBJECTIVE_ID,
            true,
            NOW.minusSeconds(86_400),
            NOW.minusSeconds(86_400),
        )
        val TYPE = WorkType.create(
            TYPE_ID,
            TIMELINE_ID,
            WorkSector.NURSING,
            "Turno extra",
            NOW.minusSeconds(86_400),
        )
        val TEMPLATE = WorkTemplate(
            TEMPLATE_ID,
            TIMELINE_ID,
            WorkSector.NURSING,
            PLACE_ID,
            OBJECTIVE_ID,
            TYPE_ID,
            LocalTime.of(20, 0),
            LocalTime.of(8, 0),
            TEMPLATE_COLOR,
            true,
            NOW.minusSeconds(86_400),
            NOW.minusSeconds(86_400),
        )
        val EXTRA_CLASS = ExtraWorkClass.create(
            CLASS_ID,
            TIMELINE_ID,
            WorkSector.NURSING,
            "Horas extra",
            true,
            true,
            NOW.minusSeconds(86_400),
        )
        val HISTORY = WorkConfigurationHistory(
            EffectiveDateTimeline(
                TIMELINE_ID,
                listOf(
                    EffectiveRevision(
                        REVISION_ID,
                        DATE,
                        WorkConfiguration(WorkSector.NURSING, HoursReference.PendingSetup, null),
                    ),
                ),
            ),
            PerPeriodHoursValues(emptyList()),
        )
        val CONFIGURATION = ResolvedWorkConfigurationRevision.resolve(
            HISTORY,
            NOW.atZone(ZONE).toLocalDate(),
        )

        fun instant(value: String): Instant = Instant.parse(value)
        fun uuid(value: Int): UUID = UUID.fromString("91000000-0000-0000-0000-${value.toString().padStart(12, '0')}")
    }
}
