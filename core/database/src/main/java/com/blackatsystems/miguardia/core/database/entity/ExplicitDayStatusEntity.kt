package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "explicit_day_statuses")
internal data class ExplicitDayStatusEntity(
    @PrimaryKey val localDate: String,
    val type: String,
)
