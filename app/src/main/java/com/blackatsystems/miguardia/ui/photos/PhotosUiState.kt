package com.blackatsystems.miguardia.ui.photos

import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.SchedulePhoto
import java.time.YearMonth
import java.util.UUID

enum class PhotosSurface { NONE, LIST, VIEWER }

data class PhotosUiState(
    val surface: PhotosSurface = PhotosSurface.NONE,
    val month: YearMonth,
    val photos: List<SchedulePhoto> = emptyList(),
    val objectives: List<Objective> = emptyList(),
    val selectedId: UUID? = null,
    val pendingDeleteId: UUID? = null,
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)
