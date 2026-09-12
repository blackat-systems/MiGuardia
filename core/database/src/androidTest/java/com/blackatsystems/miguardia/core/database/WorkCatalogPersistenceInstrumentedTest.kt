package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup
import com.blackatsystems.miguardia.core.domain.repository.DuplicateWorkTemplateException
import com.blackatsystems.miguardia.core.domain.repository.DuplicateWorkTypeNameException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.RetroactiveWorkplaceRuleException
import com.blackatsystems.miguardia.core.domain.work.HolidayRule
import com.blackatsystems.miguardia.core.domain.work.NewV2Backfill
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleBackfill
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkCatalogPersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: MiGuardiaV2Database
    private lateinit var store: LocalDataStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB)
        openStore()
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(DB)
    }

    @Test
    fun firstV2WorkSetIsAtomicAndRoundTripsAcrossReopen() = runBlocking {
        val fixture = store.seedV2Catalog()
        assertEquals(fixture.objective, store.objectives.getById(fixture.objective.id))
        assertEquals(fixture.place, store.workCatalog.getWorkPlace(fixture.place.id))
        assertEquals(fixture.type, store.workCatalog.getWorkType(fixture.type.id))
        assertEquals(fixture.template, store.workCatalog.getWorkTemplate(fixture.template.id))

        store.close()
        openStore()
        assertEquals(fixture.objective, store.objectives.getById(fixture.objective.id))
        val catalog = store.workCatalog.observeCatalog(V2TestIds.TIMELINE, fixture.place.sector).first()
        assertEquals(listOf(fixture.place), catalog.workPlaces)
        assertEquals(listOf(fixture.type), catalog.workTypes)
        assertEquals(listOf(fixture.template), catalog.workTemplates)
        assertEquals(listOf(fixture.rule), catalog.workplaceRuleRevisions)
    }

    @Test
    fun failedFirstWorkSetRollsBackEveryCatalogRow() = runBlocking {
        val fixture = buildV2CatalogFixture()
        store.workConfiguration.createInitial(V2TestIds.TIMELINE, fixture.revision)
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER force_first_work_set_rollback
                BEFORE INSERT ON work_templates
                WHEN NEW.id = '${fixture.template.id}'
                BEGIN
                    SELECT RAISE(ABORT, 'forced template failure');
                END""",
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.createFirstWorkSet(fixture.toFirstWorkSet())
        }

        assertNull(store.objectives.getById(fixture.objective.id))
        assertNull(store.workCatalog.getWorkPlace(fixture.place.id))
        assertNull(store.workCatalog.getWorkType(fixture.type.id))
        assertNull(store.workCatalog.getWorkTemplate(fixture.template.id))
        val catalog = store.workCatalog
            .observeCatalog(V2TestIds.TIMELINE, fixture.place.sector)
            .first()
        assertTrue(catalog.workPlaces.isEmpty())
        assertTrue(catalog.workTypes.isEmpty())
        assertTrue(catalog.workTemplates.isEmpty())
        assertTrue(catalog.workplaceRuleRevisions.isEmpty())
    }

    @Test
    fun duplicateTypeAndTemplateAreRejectedWithoutChangingTheCatalog() = runBlocking {
        val fixture = store.seedV2Catalog()
        val duplicateType = WorkType.create(
            id = V2TestIds.uuid(310),
            timelineId = V2TestIds.TIMELINE,
            sector = fixture.place.sector,
            rawName = fixture.type.name.lowercase(),
            timestamp = V2TestIds.NOW.plusSeconds(1),
        )
        assertSuspendThrows<DuplicateWorkTypeNameException> {
            store.workCatalog.createWorkType(duplicateType)
        }
        val duplicateTemplate = WorkTemplate(
            id = V2TestIds.uuid(311),
            timelineId = V2TestIds.TIMELINE,
            sector = fixture.place.sector,
            workPlaceId = fixture.place.id,
            objectiveId = fixture.objective.id,
            workTypeId = fixture.type.id,
            startTime = fixture.template.startTime,
            endTime = fixture.template.endTime,
            colorArgb = 0xFF775533.toInt(),
            isActive = true,
            createdAt = V2TestIds.NOW.plusSeconds(1),
            updatedAt = V2TestIds.NOW.plusSeconds(1),
        )
        assertSuspendThrows<DuplicateWorkTemplateException> {
            store.workCatalog.createWorkTemplate(duplicateTemplate)
        }
        val catalog = store.workCatalog.observeCatalog(V2TestIds.TIMELINE, fixture.place.sector).first()
        assertEquals(1, catalog.workTypes.size)
        assertEquals(1, catalog.workTemplates.size)
    }

    @Test
    fun retroactiveRuleIsRejectedWithoutPersistingIt() = runBlocking {
        val fixture = store.seedV2Catalog()
        val started = store.buildTestV2Write(
            fixture,
            V2TestIds.uuid(312),
            V2TestIds.SHIFT_DATE,
        )
        store.v2Shifts.insert(started)
        val candidate = fixture.rule.copy(
            id = V2TestIds.uuid(313),
            effectiveFrom = started.shift.localStartDate,
            rules = fixture.rule.rules.copy(
                holiday = HolidayRule(differentTreatment = true, showDedicatedSummary = true),
            ),
            createdAt = V2TestIds.NOW.plusSeconds(2),
        )

        assertSuspendThrows<RetroactiveWorkplaceRuleException> {
            store.workCatalog.addWorkplaceRuleRevision(
                revision = candidate,
                confirmationNow = started.shift.startAt,
            )
        }

        assertEquals(listOf(fixture.rule), store.workCatalog.getRuleRevisions(fixture.place.id))
    }

    @Test
    fun ruleLookupUsesExactEffectiveBoundaries() = runBlocking {
        val fixture = store.seedV2Catalog()
        val secondDate = V2TestIds.SHIFT_DATE
        val second = fixture.rule.copy(
            id = V2TestIds.uuid(314),
            effectiveFrom = secondDate,
            rules = fixture.rule.rules.copy(
                holiday = HolidayRule(differentTreatment = true, showDedicatedSummary = false),
            ),
            createdAt = V2TestIds.NOW.plusSeconds(3),
        )
        store.workCatalog.addWorkplaceRuleRevision(second, V2TestIds.NOW)

        assertNull(
            store.workCatalog.getRuleRevisionAt(
                fixture.place.id,
                fixture.rule.effectiveFrom.minusDays(1),
            ),
        )
        assertEquals(
            fixture.rule,
            store.workCatalog.getRuleRevisionAt(fixture.place.id, secondDate.minusDays(1)),
        )
        assertEquals(second, store.workCatalog.getRuleRevisionAt(fixture.place.id, secondDate))
    }

    @Test
    fun overnightShiftKeepsRuleCoverageAcrossAnEffectiveBoundary() = runBlocking {
        val fixture = store.seedV2Catalog()
        val secondRule = fixture.rule.copy(
            id = V2TestIds.uuid(315),
            effectiveFrom = V2TestIds.SHIFT_DATE.plusDays(1),
            rules = fixture.rule.rules.copy(
                holiday = HolidayRule(differentTreatment = true, showDedicatedSummary = true),
            ),
            createdAt = V2TestIds.NOW.plusSeconds(4),
        )
        store.workCatalog.addWorkplaceRuleRevision(secondRule, V2TestIds.NOW)
        val overnight = fixture.template.copy(
            id = V2TestIds.uuid(316),
            startTime = LocalTime.of(20, 0),
            endTime = LocalTime.of(8, 0),
            colorArgb = 0xFF553377.toInt(),
            createdAt = V2TestIds.NOW.plusSeconds(5),
            updatedAt = V2TestIds.NOW.plusSeconds(5),
        )
        store.workCatalog.createWorkTemplate(overnight)
        val write = store.buildTestV2Write(
            fixture.copy(template = overnight),
            V2TestIds.uuid(317),
            V2TestIds.SHIFT_DATE,
        )

        store.v2Shifts.insert(write)

        assertEquals(V2ShiftLookup.V2(write), store.v2Shifts.getShift(write.shift.id))
        assertEquals(
            fixture.rule,
            store.workCatalog.getRuleRevisionAt(fixture.place.id, V2TestIds.SHIFT_DATE),
        )
        assertEquals(
            secondRule,
            store.workCatalog.getRuleRevisionAt(
                fixture.place.id,
                V2TestIds.SHIFT_DATE.plusDays(1),
            ),
        )
    }

    @Test
    fun consciousBackfillExtendsConfigurationAndRulesTogether() = runBlocking {
        val fixture = store.seedV2Catalog()
        val current = requireNotNull(store.workConfiguration.get())
        val earlierDate = V2TestIds.CONFIGURATION_DATE.minusDays(10)
        val earlierConfiguration = fixture.revision.copy(
            id = V2TestIds.uuid(320),
            effectiveFrom = earlierDate,
        )
        val earlierRule = fixture.rule.copy(
            id = V2TestIds.uuid(321),
            effectiveFrom = earlierDate,
        )
        val updated = store.workCatalog.extendNewV2Backward(
            NewV2Backfill(
                currentHistory = current,
                configurationRevision = earlierConfiguration,
                workplaceRuleBackfills = listOf(WorkplaceRuleBackfill(fixture.rule, earlierRule)),
            ),
        )

        assertEquals(earlierConfiguration, updated.timeline.revisions.first())
        assertEquals(earlierRule, store.workCatalog.getRuleRevisionAt(fixture.place.id, earlierDate))
        assertNull(store.workCatalog.getRuleRevisionAt(fixture.place.id, earlierDate.minusDays(1)))
        assertEquals(
            fixture.rule,
            store.workCatalog.getRuleRevisionAt(fixture.place.id, LocalDate.of(2026, 8, 1)),
        )
    }

    @Test
    fun failedBackfillRollsBackConfigurationAndRulesTogether() = runBlocking {
        val fixture = store.seedV2Catalog()
        val current = requireNotNull(store.workConfiguration.get())
        val earlierDate = V2TestIds.CONFIGURATION_DATE.minusDays(10)
        val earlierConfiguration = fixture.revision.copy(
            id = V2TestIds.uuid(322),
            effectiveFrom = earlierDate,
        )
        val earlierRule = fixture.rule.copy(
            id = V2TestIds.uuid(323),
            effectiveFrom = earlierDate,
        )
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER force_backfill_rollback
                BEFORE INSERT ON workplace_rule_revisions
                WHEN NEW.id = '${earlierRule.id}'
                BEGIN
                    SELECT RAISE(ABORT, 'forced rule failure');
                END""",
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.extendNewV2Backward(
                NewV2Backfill(
                    currentHistory = current,
                    configurationRevision = earlierConfiguration,
                    workplaceRuleBackfills = listOf(
                        WorkplaceRuleBackfill(fixture.rule, earlierRule),
                    ),
                ),
            )
        }

        val persisted = requireNotNull(store.workConfiguration.get())
        assertEquals(current.timeline.id, persisted.timeline.id)
        assertEquals(current.timeline.revisions, persisted.timeline.revisions)
        assertEquals(
            current.perPeriodHoursValues.entries,
            persisted.perPeriodHoursValues.entries,
        )
        assertEquals(listOf(fixture.rule), store.workCatalog.getRuleRevisions(fixture.place.id))
    }

    @Test
    fun unknownRuleCodeProducesAControlledInvalidDataError() = runBlocking {
        val fixture = store.seedV2Catalog()
        database.openHelper.writableDatabase.execSQL(
            "UPDATE workplace_rule_revisions SET nightRuleCode = 'LEGACY' " +
                "WHERE id = '${fixture.rule.id}'",
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.getRuleRevisions(fixture.place.id)
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.observeCatalog(V2TestIds.TIMELINE, fixture.place.sector).first()
        }
    }

    @Test
    fun orphanCatalogRowProducesAControlledInvalidDataError() = runBlocking {
        val orphanId = V2TestIds.uuid(324)
        val orphanTimeline = V2TestIds.uuid(325)
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL("PRAGMA foreign_keys = OFF")
        try {
            sqlite.execSQL(
                """INSERT INTO work_types(
                    id, timelineId, sector, name, normalizedNameKey, behavior,
                    isActive, createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (
                    '$orphanId', '$orphanTimeline', 'PRIVATE_SECURITY',
                    'Tipo huérfano', 'TIPO HUÉRFANO', 'ACTIVE_WORK', 1,
                    ${V2TestIds.NOW.toEpochMilli()}, ${V2TestIds.NOW.toEpochMilli()}
                )""",
            )
        } finally {
            sqlite.execSQL("PRAGMA foreign_keys = ON")
        }

        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.getWorkType(orphanId)
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog
                .observeCatalog(orphanTimeline, buildV2CatalogFixture().place.sector)
                .first()
        }
    }

    @Test
    fun placeWithoutAnyRuleCoverageProducesAControlledInvalidDataError() = runBlocking {
        val fixture = store.seedV2Catalog()
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM workplace_rule_revisions WHERE id = '${fixture.rule.id}'",
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.getWorkPlace(fixture.place.id)
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.getRuleRevisionAt(fixture.place.id, V2TestIds.SHIFT_DATE)
        }
    }

    @Test
    fun nonNormalizedObjectiveProducesAControlledInvalidDataError() = runBlocking {
        val fixture = store.seedV2Catalog()
        database.openHelper.writableDatabase.execSQL(
            "UPDATE objectives SET fullName = '  Lugar sin normalizar  ' " +
                "WHERE id = '${fixture.objective.id}'",
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.objectives.getById(fixture.objective.id)
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.getWorkPlace(fixture.place.id)
        }
    }

    @Test
    fun recentTemplatesAreGroupedOrderedLimitedReopenedAndFilteredWhenArchived() = runBlocking {
        val fixture = store.seedV2Catalog()
        val secondTemplate = fixture.template.copy(
            id = V2TestIds.uuid(330),
            startTime = LocalTime.of(16, 0),
            endTime = LocalTime.MIDNIGHT,
            colorArgb = 0xFF884422.toInt(),
            createdAt = V2TestIds.NOW.plusSeconds(1),
            updatedAt = V2TestIds.NOW.plusSeconds(1),
        )
        store.workCatalog.createWorkTemplate(secondTemplate)
        val firstOlder = store.buildTestV2Write(
            fixture,
            V2TestIds.uuid(331),
            V2TestIds.SHIFT_DATE,
            timestamp = V2TestIds.NOW.plusSeconds(100),
        )
        val second = store.buildTestV2Write(
            fixture.copy(template = secondTemplate),
            V2TestIds.uuid(332),
            V2TestIds.SHIFT_DATE.plusDays(1),
            timestamp = V2TestIds.NOW.plusSeconds(200),
        )
        val firstLatest = store.buildTestV2Write(
            fixture,
            V2TestIds.uuid(333),
            V2TestIds.SHIFT_DATE.plusDays(2),
            timestamp = V2TestIds.NOW.plusSeconds(300),
        )
        store.v2Shifts.insert(firstOlder)
        store.v2Shifts.insert(second)
        store.v2Shifts.insert(firstLatest)

        var recent = store.workCatalog
            .observeRecentlyUsed(V2TestIds.TIMELINE, fixture.place.sector, 5)
            .first()
        assertEquals(listOf(fixture.template.id, secondTemplate.id), recent.map { it.template.id })
        assertEquals(fixture.objective, recent.first().objective)
        assertEquals(firstLatest.shift.createdAt, recent[0].lastUsedAt)
        assertEquals(second.shift.createdAt, recent[1].lastUsedAt)
        assertEquals(
            listOf(fixture.template.id),
            store.workCatalog
                .observeRecentlyUsed(V2TestIds.TIMELINE, fixture.place.sector, 1)
                .first()
                .map { it.template.id },
        )

        store.close()
        openStore()
        recent = store.workCatalog
            .observeRecentlyUsed(V2TestIds.TIMELINE, fixture.place.sector, 5)
            .first()
        assertEquals(listOf(fixture.template.id, secondTemplate.id), recent.map { it.template.id })
        assertEquals(fixture.objective, recent.first().objective)

        store.workCatalog.setWorkTemplateActive(
            fixture.template.id,
            false,
            V2TestIds.NOW.plusSeconds(400),
        )
        recent = store.workCatalog
            .observeRecentlyUsed(V2TestIds.TIMELINE, fixture.place.sector, 5)
            .first()
        assertEquals(listOf(secondTemplate.id), recent.map { it.template.id })
    }

    private fun openStore() {
        database = MiGuardiaV2Database.build(context, DB)
        store = LocalDataStore(database)
    }

    private suspend inline fun <reified T : Throwable> assertSuspendThrows(
        crossinline block: suspend () -> Unit,
    ) {
        try {
            block()
            throw AssertionError("Se esperaba ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            if (error !is T) throw error
        }
    }

    private companion object {
        const val DB = "work-catalog-v2-test.db"
    }
}
