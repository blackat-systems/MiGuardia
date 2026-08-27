package com.blackatsystems.miguardia.ui.nextevent

import com.blackatsystems.miguardia.core.domain.nextevent.TodayCardProjection

enum class NextEventLoadState {
    LOADING,
    CONTENT,
    ERROR,
}

data class NextEventUiState(
    val loadState: NextEventLoadState = NextEventLoadState.LOADING,
    val result: TodayCardProjection? = null,
    val errorMessage: String? = null,
)
