package com.blackatsystems.miguardia.profile

import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import java.util.Locale

data class ActiveProfileObjective(
    val objective: Objective,
    val schedules: List<ScheduleCombination>,
)

internal fun activeProfileObjectives(
    objectives: List<Objective>,
    schedules: List<ScheduleCombination>,
): List<ActiveProfileObjective> = objectives
    .asSequence()
    .filter(Objective::isActive)
    .distinctBy(Objective::id)
    .sortedWith(
        compareBy<Objective> { it.fullName.lowercase(Locale.ROOT) }
            .thenBy { it.id.toString() },
    )
    .map { objective ->
        ActiveProfileObjective(
            objective = objective,
            schedules = schedules
                .asSequence()
                .filter { it.objectiveId == objective.id && it.isActive }
                .distinctBy(ScheduleCombination::id)
                .sortedWith(
                    compareBy<ScheduleCombination> { it.startTime }
                        .thenBy { it.endTime }
                        .thenBy { it.id.toString() },
                )
                .toList(),
        )
    }
    .toList()
