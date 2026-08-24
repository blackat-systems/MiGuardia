package com.blackatsystems.miguardia.core.domain.work

data class WorkConfigurationHistory(
    val timeline: EffectiveDateTimeline<WorkConfiguration>,
    val perPeriodHoursValues: PerPeriodHoursValues,
) {
    init {
        require(timeline.revisions.isNotEmpty()) {
            "Una configuración laboral debe tener al menos una revisión"
        }

        val periodsByDefinition = timeline.revisions
            .mapNotNull { revision ->
                (revision.value.hoursReference as? HoursReference.PerPeriod)
                    ?.let { reference -> reference.definitionId to reference.period }
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })

        require(periodsByDefinition.values.all { periods -> periods.distinct().size == 1 }) {
            "Una definición por período no puede cambiar su patrón"
        }

        val canonicalPeriods = periodsByDefinition.mapValues { (_, periods) -> periods.first() }
        perPeriodHoursValues.entries.forEach { entry ->
            val canonicalPeriod = canonicalPeriods[entry.key.definitionId]
            require(canonicalPeriod != null) {
                "Un valor por período debe pertenecer a una definición del historial"
            }
            require(entry.key.period == canonicalPeriod) {
                "Un valor por período debe usar el patrón de su definición"
            }
        }
    }
}
