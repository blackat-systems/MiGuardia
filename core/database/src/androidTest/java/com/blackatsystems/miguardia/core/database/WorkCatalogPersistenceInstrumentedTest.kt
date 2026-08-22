package com.blackatsystems.miguardia.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.database.entity.WorkConfigurationRootEntity
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.core.domain.repository.AdoptedObjectiveInUseException
import com.blackatsystems.miguardia.core.domain.repository.DuplicateWorkTemplateException
import com.blackatsystems.miguardia.core.domain.repository.DuplicateWorkTypeNameException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.RetroactiveWorkplaceRuleException
import com.blackatsystems.miguardia.core.domain.shift.buildV2ShiftWrite
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.FirstWorkSet
import com.blackatsystems.miguardia.core.domain.work.HolidayRule
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.NightHoursRule
import com.blackatsystems.miguardia.core.domain.work.NewV2Backfill
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WeekendRule
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkPlaceAdoption
import com.blackatsystems.miguardia.core.domain.work.WorkPlaceAdoptionResult
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkTemplateUpdate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleBackfill
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRules
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkCatalogPersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: MiGuardiaDatabase
    private lateinit var store: LocalDataStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
        openStore()
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun firstWorkSetIsAtomicAndRoundTripsAcrossReopen() = runBlocking {
        val configuration = configure()
        val firstSet = firstWorkSet(configuration)
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER reject_first_work_type
                BEFORE INSERT ON work_types
                BEGIN SELECT RAISE(ABORT, 'forced first set failure'); END""".trimIndent(),
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.createFirstWorkSet(firstSet)
        }

        assertNull(store.objectives.getById(firstSet.objective.id))
        assertEquals(0, rowCount("work_places"))
        assertEquals(0, rowCount("workplace_rule_revisions"))
        assertEquals(0, rowCount("work_types"))
        assertEquals(0, rowCount("work_templates"))

        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_first_work_type")
        store.workCatalog.createFirstWorkSet(firstSet)
        store.close()
        openStore()

        val reopened = store.workCatalog.observeCatalog(TIMELINE_ID, SECTOR).first()
        assertEquals(listOf(firstSet.workPlace), reopened.workPlaces)
        assertEquals(listOf(firstSet.workType), reopened.workTypes)
        assertEquals(listOf(firstSet.workTemplate), reopened.workTemplates)
        assertEquals(listOf(firstSet.firstRuleRevision), reopened.workplaceRuleRevisions)
        assertEquals(firstSet.objective, store.objectives.getById(firstSet.objective.id))
        assertEquals(7, database.openHelper.readableDatabase.version)
    }

    @Test
    fun newV2AbbreviationCountsUnicodeCodePointsConsistently() = runBlocking {
        val configuration = configure()
        val candidate = firstWorkSet(configuration)
        val unicodeSet = candidate.copy(
            objective = candidate.objective.copy(abbreviation = "🩺🩺🩺"),
        )

        store.workCatalog.createFirstWorkSet(unicodeSet)

        assertEquals("🩺🩺🩺", store.objectives.getById(unicodeSet.objective.id)?.abbreviation)
        assertEquals(unicodeSet.workPlace, store.workCatalog.getWorkPlace(unicodeSet.workPlace.id))
    }

    @Test
    fun foreignKeyValidCatalogWithoutAnyV2RevisionIsRejected() = runBlocking {
        database.workConfigurationDao().insertRoot(
            WorkConfigurationRootEntity(
                timelineId = TIMELINE_ID.toString(),
                singletonSlot = 1,
                origin = "MIGRATED_V1",
            ),
        )
        val place = insertCatalogDirectly(
            sector = SECTOR,
            effectiveFrom = CONFIGURATION_DATE,
            idOffset = 700,
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.observeCatalog(TIMELINE_ID, SECTOR).first()
            Unit
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.setWorkPlaceActive(
                place.id,
                isActive = false,
                updatedAt = FIXED_INSTANT.plusSeconds(1),
            )
        }
        Unit
    }

    @Test
    fun foreignKeyValidCatalogForNeverConfiguredSectorIsRejected() = runBlocking {
        configure()
        insertCatalogDirectly(
            sector = WorkSector.NURSING,
            effectiveFrom = CONFIGURATION_DATE,
            idOffset = 810,
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.observeCatalog(TIMELINE_ID, WorkSector.NURSING).first()
            Unit
        }
        Unit
    }

    @Test
    fun foreignKeyValidFirstRuleOutsideConfigurationBoundaryIsRejected() = runBlocking {
        configure()
        insertCatalogDirectly(
            sector = SECTOR,
            effectiveFrom = LocalDate.of(2026, 1, 15),
            idOffset = 820,
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.observeCatalog(TIMELINE_ID, SECTOR).first()
            Unit
        }
        Unit
    }

    @Test
    fun legacyAdoptionIsValidatedIdempotentAndProtectsHistoricalObjective() = runBlocking {
        val configuration = configure()
        val legacyObjective = objective(uuid(101), "Lugar  legado", "AB").copy(
            address = "Calle\u00a0Ficticia  100",
        )
        val legacySchedule = schedule(uuid(102), legacyObjective.id)
        store.objectives.create(legacyObjective)
        store.scheduleCombinations.create(legacySchedule)

        val place = workPlace(uuid(103), legacyObjective.id)
        val firstRule = rule(uuid(104), place, CONFIGURATION_DATE)
        val type = workType(uuid(105), "Guardia heredada")
        val template = template(
            id = uuid(106),
            place = place,
            type = type,
            startTime = legacySchedule.startTime,
            endTime = legacySchedule.endTime,
            legacyScheduleId = legacySchedule.id,
        )
        val adoption = WorkPlaceAdoption(
            workPlaceCandidate = place,
            firstRuleRevisionCandidate = firstRule,
            configurationContext = configuration,
            workTypeToCreate = type,
            workTemplateToCreate = template,
            expectedLegacyScheduleCombination = legacySchedule,
        )

        assertTrue(store.workCatalog.adoptWorkPlace(adoption) is WorkPlaceAdoptionResult.Created)
        assertTrue(store.workCatalog.adoptWorkPlace(adoption) is WorkPlaceAdoptionResult.Reused)
        assertEquals(place, store.workCatalog.getWorkPlace(place.id))
        assertEquals(template, store.workCatalog.getWorkTemplate(template.id))

        val duplicateType = type.copy(id = uuid(110))
        assertSuspendThrows<DuplicateWorkTypeNameException> {
            store.workCatalog.adoptWorkPlace(
                adoption.copy(
                    workTypeToCreate = duplicateType,
                    workTemplateToCreate = null,
                    expectedLegacyScheduleCombination = null,
                ),
            )
        }
        assertNull(store.workCatalog.getWorkType(duplicateType.id))

        val historicalTextWrite = buildV2ShiftWrite(
            id = uuid(109),
            date = LocalDate.of(2026, 1, 2),
            objective = legacyObjective,
            workPlace = place,
            workType = type,
            template = template,
            configurationContext = ResolvedWorkConfigurationRevision.resolve(
                requireNotNull(store.workConfiguration.get()),
                LocalDate.of(2026, 1, 2),
            ),
            position = null,
            timestamp = FIXED_INSTANT.plusSeconds(1),
            zoneId = ZONE,
        )
        store.v2Shifts.insert(historicalTextWrite)
        assertEquals("Lugar  legado", historicalTextWrite.shift.objectiveNameSnapshot)
        assertEquals("Calle\u00a0Ficticia  100", historicalTextWrite.shift.objectiveAddressSnapshot)

        val secondType = workType(uuid(107), "Consultorio heredado")
        val secondTemplate = template(
            id = uuid(108),
            place = place,
            type = secondType,
            startTime = legacySchedule.startTime,
            endTime = legacySchedule.endTime,
            legacyScheduleId = legacySchedule.id,
        )
        store.workCatalog.createWorkType(secondType)
        store.workCatalog.createWorkTemplate(secondTemplate)

        assertSuspendThrows<AdoptedObjectiveInUseException> {
            store.objectives.delete(legacyObjective.id)
        }
        assertNotNull(store.objectives.getById(legacyObjective.id))

        val renamed = requireNotNull(store.objectives.getById(legacyObjective.id)).copy(
            fullName = "Lugar legado actualizado",
            updatedAt = FIXED_INSTANT.plusSeconds(10),
        )
        store.objectives.update(renamed)
        assertEquals("AB", store.objectives.getById(legacyObjective.id)?.abbreviation)
        assertSuspendThrows<InvalidLocalDataException> {
            store.objectives.update(
                renamed.copy(
                    abbreviation = "XY",
                    updatedAt = FIXED_INSTANT.plusSeconds(20),
                ),
            )
        }
        assertEquals("AB", store.objectives.getById(legacyObjective.id)?.abbreviation)

        val unrelatedObjective = objective(uuid(111), "Lugar ajeno", "AJN")
        val adoptionTarget = objective(uuid(112), "Lugar por adoptar", "POR")
        val unrelatedSchedule = schedule(uuid(113), unrelatedObjective.id)
        store.objectives.create(unrelatedObjective)
        store.objectives.create(adoptionTarget)
        store.scheduleCombinations.create(unrelatedSchedule)
        val foreignPlace = workPlace(uuid(114), adoptionTarget.id)
        val foreignType = workType(uuid(115), "Servicio especial")
        val foreignTemplate = template(
            id = uuid(116),
            place = foreignPlace,
            type = foreignType,
            startTime = unrelatedSchedule.startTime,
            endTime = unrelatedSchedule.endTime,
            legacyScheduleId = unrelatedSchedule.id,
        )
        val forgedExpectedSchedule = unrelatedSchedule.copy(objectiveId = adoptionTarget.id)

        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.adoptWorkPlace(
                WorkPlaceAdoption(
                    workPlaceCandidate = foreignPlace,
                    firstRuleRevisionCandidate = rule(uuid(117), foreignPlace, CONFIGURATION_DATE),
                    configurationContext = configuration,
                    workTypeToCreate = foreignType,
                    workTemplateToCreate = foreignTemplate,
                    expectedLegacyScheduleCombination = forgedExpectedSchedule,
                ),
            )
        }
        assertNull(store.workCatalog.getWorkPlace(foreignPlace.id))
        assertNull(store.workCatalog.getWorkType(foreignType.id))
        assertNull(store.workCatalog.getWorkTemplate(foreignTemplate.id))

        val changedLegacySchedule = legacySchedule.copy(
            startTime = LocalTime.of(21, 0),
            endTime = LocalTime.of(9, 0),
            updatedAt = FIXED_INSTANT.plusSeconds(30),
        )
        store.scheduleCombinations.update(changedLegacySchedule)
        val recoloredTemplate = requireNotNull(store.workCatalog.getWorkTemplate(template.id)).copy(
            colorArgb = COLOR + 1,
            updatedAt = FIXED_INSTANT.plusSeconds(31),
        )
        store.workCatalog.updateWorkTemplate(
            WorkTemplateUpdate(
                previous = template,
                updated = recoloredTemplate,
            ),
        )
        val currentObjective = requireNotNull(store.objectives.getById(legacyObjective.id))
        val adoptedWrite = buildV2ShiftWrite(
            id = uuid(118),
            date = LocalDate.of(2026, 1, 10),
            objective = currentObjective,
            workPlace = place,
            workType = type,
            template = recoloredTemplate,
            configurationContext = ResolvedWorkConfigurationRevision.resolve(
                requireNotNull(store.workConfiguration.get()),
                LocalDate.of(2026, 1, 10),
            ),
            position = null,
            timestamp = FIXED_INSTANT.plusSeconds(32),
            zoneId = ZONE,
        )
        store.v2Shifts.insert(adoptedWrite)
        assertEquals(template.startTime, store.shifts.getById(adoptedWrite.shift.id)?.startTimeSnapshot)

        store.scheduleCombinations.delete(legacySchedule.id)
        assertNull(store.workCatalog.getWorkTemplate(template.id)?.legacyScheduleCombinationId)
        assertNull(store.workCatalog.getWorkTemplate(secondTemplate.id)?.legacyScheduleCombinationId)
    }

    @Test
    fun exactAdoptionRetryRemainsIdempotentAfterNewV2Backfill() = runBlocking {
        val configuration = configure()
        val legacyObjective = objective(uuid(141), "Lugar retrocargado", "RET")
        val legacySchedule = schedule(uuid(142), legacyObjective.id)
        store.objectives.create(legacyObjective)
        store.scheduleCombinations.create(legacySchedule)
        val place = workPlace(uuid(143), legacyObjective.id)
        val firstRule = rule(uuid(144), place, CONFIGURATION_DATE)
        val type = workType(uuid(145), "Guardia adoptada")
        val template = template(
            id = uuid(146),
            place = place,
            type = type,
            startTime = legacySchedule.startTime,
            endTime = legacySchedule.endTime,
            legacyScheduleId = legacySchedule.id,
        )
        val adoption = WorkPlaceAdoption(
            workPlaceCandidate = place,
            firstRuleRevisionCandidate = firstRule,
            configurationContext = configuration,
            workTypeToCreate = type,
            workTemplateToCreate = template,
            expectedLegacyScheduleCombination = legacySchedule,
        )
        assertTrue(store.workCatalog.adoptWorkPlace(adoption) is WorkPlaceAdoptionResult.Created)

        val currentHistory = requireNotNull(store.workConfiguration.get())
        val earlierDate = CONFIGURATION_DATE.minusDays(2)
        store.workCatalog.extendNewV2Backward(
            NewV2Backfill(
                currentHistory = currentHistory,
                configurationRevision = EffectiveRevision(
                    id = uuid(147),
                    effectiveFrom = earlierDate,
                    value = currentHistory.timeline.revisions.first().value,
                ),
                workplaceRuleBackfills = listOf(
                    WorkplaceRuleBackfill(
                        sourceRevision = firstRule,
                        earlierRevision = firstRule.copy(
                            id = uuid(148),
                            effectiveFrom = earlierDate,
                        ),
                    ),
                ),
            ),
        )

        assertTrue(store.workCatalog.adoptWorkPlace(adoption) is WorkPlaceAdoptionResult.Reused)
        assertEquals(place.id, store.workCatalog.getWorkPlace(place.id)?.id)
    }

    @Test
    fun sameObjectiveCanBeAdoptedAndArchivedIndependentlyInTwoSectors() = runBlocking {
        val medicineConfiguration = configure()
        val legacyObjective = objective(uuid(121), "Lugar compartido", "COM")
        val legacySchedule = schedule(uuid(122), legacyObjective.id)
        store.objectives.create(legacyObjective)
        store.scheduleCombinations.create(legacySchedule)

        val medicinePlace = workPlace(uuid(123), legacyObjective.id)
        val medicineType = workType(uuid(124), "Guardia médica")
        store.workCatalog.adoptWorkPlace(
            WorkPlaceAdoption(
                workPlaceCandidate = medicinePlace,
                firstRuleRevisionCandidate = rule(
                    uuid(125),
                    medicinePlace,
                    CONFIGURATION_DATE,
                ),
                configurationContext = medicineConfiguration,
                workTypeToCreate = medicineType,
                workTemplateToCreate = template(
                    uuid(126),
                    medicinePlace,
                    medicineType,
                    legacyScheduleId = legacySchedule.id,
                ),
                expectedLegacyScheduleCombination = legacySchedule,
            ),
        )

        val nursingDate = LocalDate.of(2026, 2, 1)
        store.workConfiguration.addRevision(
            TIMELINE_ID,
            EffectiveRevision(
                id = uuid(127),
                effectiveFrom = nursingDate,
                value = WorkConfiguration(
                    sector = WorkSector.NURSING,
                    hoursReference = HoursReference.PendingSetup,
                    availabilityLabel = null,
                ),
            ),
        )
        val nursingConfiguration = ResolvedWorkConfigurationRevision.resolve(
            requireNotNull(store.workConfiguration.get()),
            nursingDate,
        )
        val nursingPlace = medicinePlace.copy(
            id = uuid(128),
            sector = WorkSector.NURSING,
        )
        val nursingType = WorkType.create(
            id = uuid(129),
            timelineId = TIMELINE_ID,
            sector = WorkSector.NURSING,
            rawName = "Guardia de enfermería",
            timestamp = FIXED_INSTANT,
        )
        val nursingTemplate = template(
            uuid(130),
            nursingPlace,
            nursingType,
            legacyScheduleId = legacySchedule.id,
        ).copy(sector = WorkSector.NURSING)
        store.workCatalog.adoptWorkPlace(
            WorkPlaceAdoption(
                workPlaceCandidate = nursingPlace,
                firstRuleRevisionCandidate = rule(
                    uuid(131),
                    nursingPlace,
                    nursingDate,
                ).copy(sector = WorkSector.NURSING),
                configurationContext = nursingConfiguration,
                workTypeToCreate = nursingType,
                workTemplateToCreate = nursingTemplate,
                expectedLegacyScheduleCombination = legacySchedule,
            ),
        )

        store.workCatalog.setWorkPlaceActive(
            nursingPlace.id,
            isActive = false,
            updatedAt = FIXED_INSTANT.plusSeconds(1),
        )

        assertTrue(requireNotNull(store.workCatalog.getWorkPlace(medicinePlace.id)).isActive)
        assertFalse(requireNotNull(store.workCatalog.getWorkPlace(nursingPlace.id)).isActive)
        assertTrue(requireNotNull(store.objectives.getById(legacyObjective.id)).isActive)
        Unit
    }

    @Test
    fun normalizedTypesTemplatesAndArchiveStatesRespectIndependentIdentities() = runBlocking {
        val configuration = configure()
        val firstSet = firstWorkSet(configuration, rawTypeName = "Guardia \uFB01ja")
        store.workCatalog.createFirstWorkSet(firstSet)

        assertEquals("Guardia fija", store.workCatalog.getWorkType(firstSet.workType.id)?.name)
        assertSuspendThrows<DuplicateWorkTypeNameException> {
            store.workCatalog.createWorkType(workType(uuid(202), "Guardia fija"))
        }

        val secondType = workType(uuid(203), "Relevo")
        store.workCatalog.createWorkType(secondType)
        val sameHoursDifferentType = template(
            id = uuid(204),
            place = firstSet.workPlace,
            type = secondType,
            startTime = firstSet.workTemplate.startTime,
            endTime = firstSet.workTemplate.endTime,
        )
        store.workCatalog.createWorkTemplate(sameHoursDifferentType)
        assertNotNull(store.workCatalog.getWorkTemplate(sameHoursDifferentType.id))

        assertSuspendThrows<DuplicateWorkTemplateException> {
            store.workCatalog.createWorkTemplate(
                sameHoursDifferentType.copy(id = uuid(205)),
            )
        }

        store.workCatalog.setWorkPlaceActive(
            firstSet.workPlace.id,
            isActive = false,
            updatedAt = FIXED_INSTANT.plusSeconds(1),
        )
        assertFalse(requireNotNull(store.workCatalog.getWorkPlace(firstSet.workPlace.id)).isActive)
        assertTrue(requireNotNull(store.workCatalog.getWorkType(firstSet.workType.id)).isActive)
        assertTrue(requireNotNull(store.workCatalog.getWorkTemplate(firstSet.workTemplate.id)).isActive)

        store.workCatalog.setWorkTypeActive(
            firstSet.workType.id,
            isActive = false,
            updatedAt = FIXED_INSTANT.plusSeconds(2),
        )
        assertFalse(requireNotNull(store.workCatalog.getWorkType(firstSet.workType.id)).isActive)
        assertTrue(requireNotNull(store.workCatalog.getWorkTemplate(firstSet.workTemplate.id)).isActive)

        store.workCatalog.setWorkTemplateActive(
            firstSet.workTemplate.id,
            isActive = false,
            updatedAt = FIXED_INSTANT.plusSeconds(3),
        )
        assertFalse(requireNotNull(store.workCatalog.getWorkTemplate(firstSet.workTemplate.id)).isActive)
    }

    @Test
    fun ruleRevisionsAreInsertOnlyAndRetroactivityIsRevalidatedInsideTransaction() = runBlocking {
        val configuration = configure()
        val firstSet = firstWorkSet(configuration)
        store.workCatalog.createFirstWorkSet(firstSet)
        val retroactiveCandidate = rule(
            id = uuid(301),
            place = firstSet.workPlace,
            effectiveFrom = LocalDate.of(2026, 1, 31),
        )
        val februaryRule = rule(
            id = uuid(302),
            place = firstSet.workPlace,
            effectiveFrom = LocalDate.of(2026, 2, 1),
            rules = rules(holidayDifferentTreatment = true),
        )
        store.workCatalog.addWorkplaceRuleRevision(februaryRule, FIXED_INSTANT)

        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.addWorkplaceRuleRevision(
                februaryRule.copy(id = uuid(303)),
                FIXED_INSTANT,
            )
        }

        val shiftDate = LocalDate.of(2026, 1, 31)
        val shiftConfiguration = ResolvedWorkConfigurationRevision.resolve(
            requireNotNull(store.workConfiguration.get()),
            shiftDate,
        )
        val write = buildV2ShiftWrite(
            id = uuid(304),
            date = shiftDate,
            objective = firstSet.objective,
            workPlace = firstSet.workPlace,
            workType = firstSet.workType,
            template = firstSet.workTemplate,
            configurationContext = shiftConfiguration,
            position = "Puesto ficticio",
            timestamp = FIXED_INSTANT.plusSeconds(30),
            zoneId = ZONE,
        )
        store.v2Shifts.insert(write)

        assertSuspendThrows<RetroactiveWorkplaceRuleException> {
            store.workCatalog.addWorkplaceRuleRevision(
                retroactiveCandidate,
                confirmationNow = write.shift.startAt.plusSeconds(1),
            )
        }

        assertEquals(
            listOf(CONFIGURATION_DATE, LocalDate.of(2026, 2, 1)),
            store.workCatalog.getRuleRevisions(firstSet.workPlace.id).map { it.effectiveFrom },
        )
    }

    @Test
    fun futureRuleSurvivesAnInterveningSectorChangeAndARealReturn() = runBlocking {
        val configuration = configure()
        val firstSet = firstWorkSet(configuration)
        store.workCatalog.createFirstWorkSet(firstSet)
        val futureRule = rule(
            id = uuid(321),
            place = firstSet.workPlace,
            effectiveFrom = LocalDate.of(2026, 3, 1),
            rules = rules(holidayDifferentTreatment = true),
        )
        store.workCatalog.addWorkplaceRuleRevision(futureRule, FIXED_INSTANT)

        store.workConfiguration.addRevision(
            TIMELINE_ID,
            EffectiveRevision(
                id = uuid(322),
                effectiveFrom = LocalDate.of(2026, 2, 1),
                value = WorkConfiguration(
                    sector = WorkSector.NURSING,
                    hoursReference = HoursReference.PendingSetup,
                    availabilityLabel = null,
                ),
            ),
        )
        store.workConfiguration.addRevision(
            TIMELINE_ID,
            EffectiveRevision(
                id = uuid(323),
                effectiveFrom = LocalDate.of(2026, 4, 1),
                value = WorkConfiguration(
                    sector = SECTOR,
                    hoursReference = HoursReference.PendingSetup,
                    availabilityLabel = null,
                ),
            ),
        )

        val aprilDate = LocalDate.of(2026, 4, 2)
        val write = buildV2ShiftWrite(
            id = uuid(324),
            date = aprilDate,
            objective = firstSet.objective,
            workPlace = firstSet.workPlace,
            workType = firstSet.workType,
            template = firstSet.workTemplate,
            configurationContext = ResolvedWorkConfigurationRevision.resolve(
                requireNotNull(store.workConfiguration.get()),
                aprilDate,
            ),
            position = null,
            timestamp = FIXED_INSTANT.plusSeconds(1),
            zoneId = ZONE,
        )
        store.v2Shifts.insert(write)

        assertEquals(
            listOf(CONFIGURATION_DATE, futureRule.effectiveFrom),
            store.workCatalog.getRuleRevisions(firstSet.workPlace.id).map { it.effectiveFrom },
        )
        assertEquals(write.snapshot, store.v2Shifts.getWorkSnapshot(write.shift.id))
    }

    @Test
    fun newV2BackfillPersistsConfigurationAndFirstRuleTogether() = runBlocking {
        val configuration = configure()
        val firstSet = firstWorkSet(configuration)
        store.workCatalog.createFirstWorkSet(firstSet)
        val currentHistory = requireNotNull(store.workConfiguration.get())
        val earlierDate = CONFIGURATION_DATE.minusDays(3)
        val earlierConfiguration = EffectiveRevision(
            id = uuid(331),
            effectiveFrom = earlierDate,
            value = currentHistory.timeline.revisions.first().value,
        )
        val earlierRule = firstSet.firstRuleRevision.copy(
            id = uuid(332),
            effectiveFrom = earlierDate,
        )

        val extended = store.workCatalog.extendNewV2Backward(
            NewV2Backfill(
                currentHistory = currentHistory,
                configurationRevision = earlierConfiguration,
                workplaceRuleBackfills = listOf(
                    WorkplaceRuleBackfill(
                        sourceRevision = firstSet.firstRuleRevision,
                        earlierRevision = earlierRule,
                    ),
                ),
            ),
        )

        assertEquals(earlierDate, extended.timeline.revisions.first().effectiveFrom)
        store.close()
        openStore()
        assertEquals(
            listOf(earlierDate, CONFIGURATION_DATE),
            store.workCatalog.getRuleRevisions(firstSet.workPlace.id).map { it.effectiveFrom },
        )
        assertEquals(
            listOf(earlierDate, CONFIGURATION_DATE),
            requireNotNull(store.workConfiguration.get()).timeline.revisions.map { it.effectiveFrom },
        )
    }

    @Test
    fun externallyDeletingEveryRuleFailsReadsAndRollsBackWrites() = runBlocking {
        val configuration = configure()
        val firstSet = firstWorkSet(configuration)
        store.workCatalog.createFirstWorkSet(firstSet)
        store.close()

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(DATABASE_NAME).path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { sqlite ->
            sqlite.execSQL(
                "DELETE FROM workplace_rule_revisions WHERE workPlaceId = ?",
                arrayOf(firstSet.workPlace.id.toString()),
            )
        }
        openStore()

        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.observeCatalog(TIMELINE_ID, SECTOR).first()
            Unit
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.setWorkPlaceActive(
                firstSet.workPlace.id,
                isActive = false,
                updatedAt = FIXED_INSTANT.plusSeconds(1),
            )
        }
        database.openHelper.readableDatabase.query(
            "SELECT isActive FROM work_places WHERE id = ?",
            arrayOf(firstSet.workPlace.id.toString()),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        Unit
    }

    @Test
    fun externallyDeletingCoverageForOneCivilSegmentInvalidatesItsShift() = runBlocking {
        val configuration = configure()
        val firstSet = firstWorkSet(configuration)
        store.workCatalog.createFirstWorkSet(firstSet)
        store.workConfiguration.addRevision(
            TIMELINE_ID,
            EffectiveRevision(
                id = uuid(340),
                effectiveFrom = LocalDate.of(2026, 2, 1),
                value = WorkConfiguration(
                    sector = SECTOR,
                    hoursReference = HoursReference.PendingSetup,
                    availabilityLabel = AvailabilityLabel.AVAILABLE_FOR_CALL,
                ),
            ),
        )
        val februaryRule = rule(
            id = uuid(341),
            place = firstSet.workPlace,
            effectiveFrom = LocalDate.of(2026, 2, 1),
            rules = rules(holidayDifferentTreatment = true),
        )
        store.workCatalog.addWorkplaceRuleRevision(februaryRule, FIXED_INSTANT)
        val date = LocalDate.of(2026, 1, 31)
        val write = buildV2ShiftWrite(
            id = uuid(342),
            date = date,
            objective = firstSet.objective,
            workPlace = firstSet.workPlace,
            workType = firstSet.workType,
            template = firstSet.workTemplate,
            configurationContext = ResolvedWorkConfigurationRevision.resolve(
                requireNotNull(store.workConfiguration.get()),
                date,
            ),
            position = null,
            timestamp = FIXED_INSTANT.plusSeconds(1),
            zoneId = ZONE,
        )
        store.v2Shifts.insert(write)
        store.close()

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(DATABASE_NAME).path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { sqlite ->
            sqlite.execSQL(
                "DELETE FROM workplace_rule_revisions WHERE id = ?",
                arrayOf(firstSet.firstRuleRevision.id.toString()),
            )
        }
        openStore()

        assertSuspendThrows<InvalidLocalDataException> {
            store.v2Shifts.getWorkSnapshot(write.shift.id)
        }
        assertSuspendThrows<InvalidLocalDataException> {
            store.workCatalog.setWorkTypeActive(
                firstSet.workType.id,
                isActive = false,
                updatedAt = FIXED_INSTANT.plusSeconds(2),
            )
        }
        database.openHelper.readableDatabase.query(
            "SELECT isActive FROM work_types WHERE id = ?",
            arrayOf(firstSet.workType.id.toString()),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        Unit
    }

    private suspend fun configure(): ResolvedWorkConfigurationRevision {
        val revision = EffectiveRevision(
            id = CONFIGURATION_REVISION_ID,
            effectiveFrom = CONFIGURATION_DATE,
            value = WorkConfiguration(
                sector = SECTOR,
                hoursReference = HoursReference.PendingSetup,
                availabilityLabel = null,
            ),
        )
        store.workConfiguration.createInitial(TIMELINE_ID, revision)
        return ResolvedWorkConfigurationRevision.resolve(
            requireNotNull(store.workConfiguration.get()),
            CONFIGURATION_DATE,
        )
    }

    private suspend fun insertCatalogDirectly(
        sector: WorkSector,
        effectiveFrom: LocalDate,
        idOffset: Int,
    ): WorkPlace {
        val objective = objective(uuid(idOffset + 1), "Lugar externo ficticio", "EXT")
        val place = workPlace(uuid(idOffset + 2), objective.id).copy(sector = sector)
        val type = WorkType.create(
            id = uuid(idOffset + 3),
            timelineId = TIMELINE_ID,
            sector = sector,
            rawName = "Trabajo externo ficticio",
            timestamp = FIXED_INSTANT,
        )
        val template = template(uuid(idOffset + 4), place, type).copy(sector = sector)
        val rule = rule(uuid(idOffset + 5), place, effectiveFrom).copy(sector = sector)

        store.objectives.create(objective)
        database.workCatalogDao().insertWorkPlace(place.toEntity())
        database.workCatalogDao().insertWorkplaceRuleRevision(rule.toEntity())
        database.workCatalogDao().insertWorkType(type.toEntity())
        database.workCatalogDao().insertWorkTemplate(template.toEntity())
        return place
    }

    private fun firstWorkSet(
        configuration: ResolvedWorkConfigurationRevision,
        rawTypeName: String = "Guardia activa",
    ): FirstWorkSet {
        val objective = objective(uuid(11), "Clínica ficticia", "CLF")
        val place = workPlace(uuid(12), objective.id)
        val type = workType(uuid(13), rawTypeName)
        return FirstWorkSet(
            objective = objective,
            workPlace = place,
            firstRuleRevision = rule(uuid(14), place, CONFIGURATION_DATE),
            configurationContext = configuration,
            workType = type,
            workTemplate = template(uuid(15), place, type),
        )
    }

    private fun objective(
        id: UUID,
        name: String,
        abbreviation: String,
    ): Objective = Objective(
        id = id,
        fullName = name,
        abbreviation = abbreviation,
        address = "Calle Ficticia 100",
        note = null,
        isActive = true,
        createdAt = FIXED_INSTANT,
        updatedAt = FIXED_INSTANT,
    )

    private fun schedule(id: UUID, objectiveId: UUID): ScheduleCombination = ScheduleCombination(
        id = id,
        objectiveId = objectiveId,
        startTime = LocalTime.of(20, 0),
        endTime = LocalTime.of(8, 0),
        colorArgb = COLOR,
        isActive = true,
        createdAt = FIXED_INSTANT,
        updatedAt = FIXED_INSTANT,
    )

    private fun workPlace(id: UUID, objectiveId: UUID): WorkPlace = WorkPlace(
        id = id,
        timelineId = TIMELINE_ID,
        sector = SECTOR,
        objectiveId = objectiveId,
        isActive = true,
        createdAt = FIXED_INSTANT,
        updatedAt = FIXED_INSTANT,
    )

    private fun workType(id: UUID, rawName: String): WorkType = WorkType.create(
        id = id,
        timelineId = TIMELINE_ID,
        sector = SECTOR,
        rawName = rawName,
        timestamp = FIXED_INSTANT,
    )

    private fun template(
        id: UUID,
        place: WorkPlace,
        type: WorkType,
        startTime: LocalTime = LocalTime.of(20, 0),
        endTime: LocalTime = LocalTime.of(8, 0),
        legacyScheduleId: UUID? = null,
    ): WorkTemplate = WorkTemplate(
        id = id,
        timelineId = TIMELINE_ID,
        sector = SECTOR,
        workPlaceId = place.id,
        objectiveId = place.objectiveId,
        workTypeId = type.id,
        startTime = startTime,
        endTime = endTime,
        colorArgb = COLOR,
        isActive = true,
        legacyScheduleCombinationId = legacyScheduleId,
        createdAt = FIXED_INSTANT,
        updatedAt = FIXED_INSTANT,
    )

    private fun rule(
        id: UUID,
        place: WorkPlace,
        effectiveFrom: LocalDate,
        rules: WorkplaceRules = rules(),
    ): WorkplaceRuleRevision = WorkplaceRuleRevision(
        id = id,
        timelineId = TIMELINE_ID,
        sector = SECTOR,
        workPlaceId = place.id,
        objectiveId = place.objectiveId,
        effectiveFrom = effectiveFrom,
        rules = rules,
        createdAt = FIXED_INSTANT,
    )

    private fun rules(holidayDifferentTreatment: Boolean = false): WorkplaceRules = WorkplaceRules(
        nightHours = NightHoursRule.Disabled,
        weekend = WeekendRule.None,
        holiday = HolidayRule(
            differentTreatment = holidayDifferentTreatment,
            showDedicatedSummary = holidayDifferentTreatment,
        ),
    )

    private fun rowCount(table: String): Int = database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM $table")
        .use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun openStore() {
        database = MiGuardiaDatabase.build(context, DATABASE_NAME)
        store = LocalDataStore(database)
    }

    private suspend inline fun <reified T : Throwable> assertSuspendThrows(
        crossinline block: suspend () -> Unit,
    ): T = try {
        block()
        throw AssertionError("Se esperaba ${T::class.java.simpleName}")
    } catch (error: Throwable) {
        if (error !is T) throw error
        error
    }

    private companion object {
        const val DATABASE_NAME: String = "work-catalog-persistence-test.db"
        val COLOR: Int = 0xFF345678.toInt()
        val TIMELINE_ID: UUID = uuid(1)
        val CONFIGURATION_REVISION_ID: UUID = uuid(2)
        val CONFIGURATION_DATE: LocalDate = LocalDate.of(2026, 1, 1)
        val SECTOR: WorkSector = WorkSector.MEDICINE
        val FIXED_INSTANT: Instant = Instant.parse("2026-01-01T12:00:00Z")
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")

        fun uuid(suffix: Int): UUID = UUID.fromString(
            "00000000-0000-0000-0000-${suffix.toString().padStart(12, '0')}",
        )
    }
}
