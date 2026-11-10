package com.blackatsystems.miguardia.stage12

import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.work.FirstWorkSet
import java.io.File
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.time.Instant
import java.time.LocalTime
import java.util.Base64
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Immutable seed and real empty-preference serialization controls. No Room or Android execution. */
class Stage12TemporalFixtureRecipeTest {
    private val run = "b119c026-0000-4000-8000-000000000006"
    private val stamp = Instant.parse("2026-11-02T10:00:00.123Z")
    private fun scopes() = Stage12TemporalVariant.entries.map { Stage12TemporalScope(it) } +
        Stage12TemporalScope(Stage12TemporalVariant.TWENTY_FIVE_HOURS, Stage12TemporalFixtureRole.ONE_HOUR_COMPARATOR)
    private fun text(v: Any) = "T:" + Base64.getEncoder().encodeToString(v.toString().toByteArray(Charsets.UTF_8))

    @Test fun allElevenSeedsHaveExactCountsDisjointIdentitiesAndAbsentTargets() {
        val recipes = scopes().map { Stage12TemporalFixtureRecipe(run, it, stamp) }
        assertEquals(11, recipes.size)
        assertEquals(listOf(16,16,16,16,16,15,15,15,15,18,15), recipes.map { it.businessRowCount })
        assertEquals(173, recipes.sumOf { it.businessRowCount })
        assertEquals(189, recipes.sumOf { it.businessRowCount + if (it.isShift) 2 else 1 })
        assertEquals(11, recipes.map { it.business.timelineId }.toSet().size)
        assertEquals(29, Stage12TemporalFixtureRecipe.businessTables.size)
        recipes.forEach { recipe ->
            recipe.requireSafeBeforeDelegates()
            val rows = recipe.expectedRows()
            assertEquals(recipe.businessRowCount, rows.size)
            val shiftCount = if (recipe.scope.variant == Stage12TemporalVariant.RESUMPTION) 2 else 1
            assertEquals(mapOf("work_configuration_roots" to 1, "work_configuration_revisions" to 1, "objectives" to 1,
                "work_places" to 1, "work_types" to 1, "workplace_rule_revisions" to 1, "work_templates" to if (recipe.isShift || shiftCount == 2) 2 else 1,
                "shifts" to shiftCount, "shift_work_snapshots" to shiftCount, "availability_windows" to 1,
                "shift_notes" to 1, "shift_note_attachments" to 1, "shift_notification_configs" to 1, "shift_notification_reminders" to 2),
                rows.groupingBy { it.table }.eachCount())
            val seedIds = setOf(recipe.business.id("neighbour-shift")) +
                if (recipe.scope.variant == Stage12TemporalVariant.RESUMPTION) setOf(recipe.business.id("active-shift")) else emptySet()
            assertEquals(seedIds, recipe.shifts.map { it.shift.id }.toSet())
            assertEquals(seedIds.map(::text).toSet(), rows.filter { it.table == "shifts" }.map { it.values.getValue("id") }.toSet())
            assertEquals(seedIds.map(::text).toSet(), rows.filter { it.table == "shift_work_snapshots" }.map { it.values.getValue("shiftId") }.toSet())
            assertEquals(setOf(text(recipe.business.id("neighbour-shift"))), rows.filter { it.table == "shift_notes" || it.table == "shift_notification_configs" }
                .map { it.values.getValue("shiftId") }.toSet())
            assertEquals(text(recipe.business.id("neighbour-note")), rows.single { it.table == "shift_notes" }.values.getValue("id"))
            assertEquals(text(recipe.business.id("neighbour-note")), rows.single { it.table == "shift_note_attachments" }.values.getValue("noteId"))
            assertEquals(listOf(recipe.neighbourWindow.id), recipe.expectedRows().filter { it.table == "availability_windows" }
                .map { row -> recipe.neighbourWindow.id.also { assertEquals(text(it), row.values.getValue("id")) } })
            assertEquals(LocalTime.of(5, 0), recipe.neighbourShift.shift.startTimeSnapshot)
            assertEquals(LocalTime.of(5, 30), recipe.neighbourShift.shift.endTimeSnapshot)
            assertEquals(recipe.ownerDate, recipe.neighbourWindow.ownerLocalDate)
            assertTrue(recipe.neighbourWindow.end <= recipe.literal.start)
            assertTrue(recipe.neighbourShift.shift.endAt <= recipe.literal.start)
            assertEquals(if (recipe.scope.variant == Stage12TemporalVariant.RESUMPTION) 2 else 1, recipe.shifts.size)
        }
    }

    @Test fun seedCannotBorrowTheTargetTemplateDifferentHoursOrAnotherIdentityBeforeDelegate() {
        val recipe = Stage12TemporalFixtureRecipe(run, Stage12TemporalScope(Stage12TemporalVariant.SAME_DAY), stamp)
        assertNotEquals(recipe.initialTemplate.id, recipe.neighbourTemplate.id)
        val original = recipe.neighbourShift
        val wrongTemplate = V2ShiftWrite(original.shift, original.snapshot.copy(templateId = recipe.initialTemplate.id))
        val wrongHours = original.copy(shift = original.shift.copy(endAt = original.shift.endAt.plusSeconds(60)))
        val wrongOwner = original.copy(shift = original.shift.copy(localStartDate = original.shift.localStartDate.plusDays(1)))
        for (mutant in listOf(wrongTemplate, wrongHours, wrongOwner)) {
            var delegates = 0
            assertThrows(IllegalStateException::class.java) { recipe.requireSeedArgumentBeforeDelegate(mutant); delegates++ }
            assertEquals(0, delegates)
        }
        recipe.requireSeedArgumentBeforeDelegate(original)
    }

    @Test fun firstSetUsesItsFirstRuleDateAndCompleteReferenceMetadata() {
        val recipe = Stage12TemporalFixtureRecipe(run, Stage12TemporalScope(Stage12TemporalVariant.RESUMPTION), stamp)
        assertEquals("2026-01-01", recipe.firstSet.configurationContext.referenceDate.toString())
        assertThrows(IllegalArgumentException::class.java) {
            FirstWorkSet(recipe.business.objective, recipe.business.place, recipe.rule,
                recipe.business.configuration(recipe.ownerDate), recipe.business.type, recipe.initialTemplate)
        }
        val rows = recipe.expectedRows()
        val root = rows.single { it.table == "work_configuration_roots" }
        assertEquals(mapOf("timelineId" to text(recipe.business.timelineId), "singletonSlot" to "I:1"), root.values)
        val revision = rows.single { it.table == "work_configuration_revisions" }
        assertEquals(13, revision.values.size)
        assertEquals(text("PENDING_SETUP"), revision.values.getValue("hoursReferenceKind"))
        assertEquals(text("PASSIVE_GUARD"), revision.values.getValue("availabilityLabel"))
        for (name in listOf("periodKind", "weeklyFirstDayIso", "cycleAnchorDate", "cycleLengthDays", "requiredMinutes", "perPeriodDefinitionId", "hoursReferenceStartedOn"))
            assertEquals(name, "N", revision.values.getValue(name))
        val rule = rows.single { it.table == "workplace_rule_revisions" }
        assertEquals(17, rule.values.size)
        assertEquals(text("DISABLED"), rule.values.getValue("nightRuleCode"))
        assertEquals(text("NONE"), rule.values.getValue("weekendRuleCode"))
        for (name in listOf("nightStartTime", "nightEndTime", "nightDifferentTreatment", "nightShowDedicatedSummary", "weekendDifferentTreatment", "weekendShowDedicatedSummary"))
            assertEquals(name, "N", rule.values.getValue(name))
        assertEquals("I:0", rule.values.getValue("holidayDifferentTreatment"))
        assertEquals("I:0", rule.values.getValue("holidayShowDedicatedSummary"))
        assertEquals(listOf("I:11", "I:23"), rows.filter { it.table == "shift_notification_reminders" }.map { it.values.getValue("leadMinutes") })
        assertEquals(10, rows.single { it.table == "shift_note_attachments" }.values.size)
    }

    @Test fun privateFileInputsContainOnlyDeclaredDirectoriesFourEmptyPreferencesAndThePng() {
        scopes().forEach { scope ->
            val recipe = Stage12TemporalFixtureRecipe(run, scope, stamp)
            val before = recipe.expectedPrivateFiles(false)
            val after = recipe.expectedPrivateFiles(true)
            assertEquals(9, before.size); assertEquals(10, after.size)
            assertEquals(5, before.values.count { it.kind == Stage12PrivateNodeKind.DIRECTORY })
            assertEquals(4, recipe.expectedPrivatePreferences().size)
            assertEquals(before, after - "files/schedule_photos/${recipe.pngKey}")
            assertTrue(recipe.expectedPrivatePreferences().values.all { it == "BYTES:0:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" })
        }
        val recipe = Stage12TemporalFixtureRecipe(run, scopes().first(), stamp)
        ImageIO.createImageInputStream(ByteArrayInputStream(recipe.png)).use { input ->
            val readers = ImageIO.getImageReaders(input)
            assertTrue("The declared fixture must be a decodable image", readers.hasNext())
            val reader = readers.next()
            try {
                assertEquals("png", reader.formatName.lowercase(java.util.Locale.ROOT))
                reader.input = input
                val decoded = reader.read(0)
                assertEquals(1, decoded.width); assertEquals(1, decoded.height)
                decoded.flush()
            } finally { reader.dispose() }
        }
        ImageIO.createImageInputStream(ByteArrayInputStream(byteArrayOf(0))).use { invalid ->
            assertFalse(ImageIO.getImageReaders(invalid).hasNext())
        }
    }

    @Test fun literalEmptyPreferenceProtobufIsReadByTheRealDataStoreWithoutChangingItsBytes() = runBlocking {
        val directory = Files.createTempDirectory("stage12-temporal-preference-").toFile()
        val file = File(directory, "own.preferences_pb")
        Files.write(file.toPath(), byteArrayOf(), CREATE_NEW)
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
            assertTrue(store.data.first().asMap().isEmpty())
            assertArrayEquals(byteArrayOf(), file.readBytes())
        } finally {
            job.cancelAndJoin()
            assertTrue(job.isCompleted)
            Files.deleteIfExists(file.toPath()); Files.deleteIfExists(directory.toPath())
        }
    }

    @Test fun truncatedPreferenceProtobufIsRejectedAndItsOriginalBytesAreRetained() = runBlocking {
        val directory = Files.createTempDirectory("stage12-temporal-preference-invalid-").toFile()
        val file = File(directory, "own.preferences_pb")
        val invalid = byteArrayOf(0x0a)
        Files.write(file.toPath(), invalid, CREATE_NEW)
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
            try { store.data.first(); fail("Invalid input must not become an empty preference value") }
            catch (_: CorruptionException) { }
            assertArrayEquals(invalid, file.readBytes())
        } finally {
            job.cancelAndJoin()
            assertTrue(job.isCompleted)
            Files.deleteIfExists(file.toPath()); Files.deleteIfExists(directory.toPath())
        }
    }
}
