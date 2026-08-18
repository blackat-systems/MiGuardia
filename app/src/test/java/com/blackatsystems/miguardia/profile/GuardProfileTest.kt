package com.blackatsystems.miguardia.profile

import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import java.time.Instant
import java.time.LocalTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class GuardProfileTest {
    @Test
    fun defaultsAndNormalizationKeepOnlyCurrentProfileValues() {
        assertNull(GuardProfile().displayName)
        assertEquals("Inforce", GuardProfile().company)
        assertEquals("Vigilancia y seguridad", GUARD_PROFESSION)

        val normalized = normalizeGuardProfile("  Usuario ficticio  ", "  Empresa ficticia  ")
        assertEquals("Usuario ficticio", normalized.displayName)
        assertEquals("Empresa ficticia", normalized.company)
        assertNull(normalizeGuardProfile("   ", "Inforce").displayName)
    }

    @Test
    fun blankCompanyIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeGuardProfile("Persona ficticia", "   ")
        }
    }

    @Test
    fun projectionIncludesActiveObjectivesAndActiveSchedulesWithoutDuplicates() {
        val active = objective(ACTIVE_OBJECTIVE_ID, "Objetivo B", true)
        val activeFirst = objective(SECOND_OBJECTIVE_ID, "Objetivo A", true)
        val hidden = objective(HIDDEN_OBJECTIVE_ID, "Objetivo oculto", false)
        val schedule = schedule(ACTIVE_SCHEDULE_ID, active.id, true)
        val hiddenSchedule = schedule(HIDDEN_SCHEDULE_ID, active.id, false)
        val hiddenObjectiveSchedule = schedule(OTHER_SCHEDULE_ID, hidden.id, true)

        val projection = activeProfileObjectives(
            objectives = listOf(active, hidden, activeFirst, active),
            schedules = listOf(schedule, hiddenSchedule, hiddenObjectiveSchedule, schedule),
        )

        assertEquals(listOf(activeFirst.id, active.id), projection.map { it.objective.id })
        assertEquals(emptyList<ScheduleCombination>(), projection[0].schedules)
        assertEquals(listOf(schedule.id), projection[1].schedules.map(ScheduleCombination::id))
    }

    private fun objective(id: UUID, name: String, active: Boolean) = Objective(
        id = id,
        fullName = name,
        abbreviation = name.takeLast(1).uppercase().repeat(3),
        address = null,
        note = null,
        isActive = active,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun schedule(id: UUID, objectiveId: UUID, active: Boolean) = ScheduleCombination(
        id = id,
        objectiveId = objectiveId,
        startTime = LocalTime.of(19, 0),
        endTime = LocalTime.of(7, 0),
        colorArgb = 0xff336699.toInt(),
        isActive = active,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private companion object {
        val ACTIVE_OBJECTIVE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000001301")
        val SECOND_OBJECTIVE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000001302")
        val HIDDEN_OBJECTIVE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000001303")
        val ACTIVE_SCHEDULE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000001311")
        val HIDDEN_SCHEDULE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000001312")
        val OTHER_SCHEDULE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000001313")
    }
}
