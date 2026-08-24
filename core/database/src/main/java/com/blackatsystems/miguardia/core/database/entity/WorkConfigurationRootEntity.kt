package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "work_configuration_roots",
    indices = [Index(value = ["singletonSlot"], unique = true)],
)
internal data class WorkConfigurationRootEntity(
    @PrimaryKey val timelineId: String,
    val singletonSlot: Int,
)
