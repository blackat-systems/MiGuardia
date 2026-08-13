package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Embedded
import com.blackatsystems.miguardia.core.database.entity.ObjectiveEntity
import com.blackatsystems.miguardia.core.database.entity.ScheduleCombinationEntity

internal data class RecentScheduleCombinationRow(
    @Embedded(prefix = "objective_") val objective: ObjectiveEntity,
    @Embedded(prefix = "combination_") val combination: ScheduleCombinationEntity,
    val lastUsedAtEpochMillis: Long,
)
