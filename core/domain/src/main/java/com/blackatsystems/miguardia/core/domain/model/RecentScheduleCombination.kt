package com.blackatsystems.miguardia.core.domain.model

import java.time.Instant

data class RecentScheduleCombination(
    val objective: Objective,
    val combination: ScheduleCombination,
    val lastUsedAt: Instant,
)
