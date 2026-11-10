package com.blackatsystems.miguardia.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.ui.theme.vigiliaColors
import kotlinx.coroutines.delay

const val CONFIRMATION_DURATION_MILLIS = 2_500L

enum class TransientConfirmationLayout { FillAvailable, WrapContent }

@Composable
fun TransientConfirmation(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    reserveSpace: Boolean = false,
    showNotice: Boolean = true,
    layout: TransientConfirmationLayout = TransientConfirmationLayout.FillAvailable,
    content: @Composable () -> Unit,
) {
    val currentDismiss = rememberUpdatedState(onDismiss)
    LaunchedEffect(message) {
        if (message != null) {
            delay(CONFIRMATION_DURATION_MILLIS)
            currentDismiss.value()
        }
    }

    // Full-screen destinations contain their own navigation header. A floating
    // Surface intercepts taps on that header, even with no Snackbar action.
    // Reserve space there; constrained surfaces need an explicit layout policy.
    if (layout == TransientConfirmationLayout.WrapContent) {
        // The caller owns the safe viewport and any fixed navigation header.
        // Reserve only a visible notice; the body can shrink or scroll within
        // the remaining finite height without making a short dialog fill it.
        Column(modifier) {
            if (message != null && showNotice) {
                ConfirmationNotice(message, Modifier.padding(vertical = 8.dp))
            }
            Box(Modifier.weight(1f, fill = false)) { content() }
        }
    } else if (reserveSpace) {
        Column(modifier.fillMaxSize().safeDrawingPadding()) {
            if (message != null && showNotice) {
                ConfirmationNotice(message, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            Box(Modifier.weight(1f)) { content() }
        }
    } else Box(modifier.fillMaxSize()) {
        content()
        if (message != null && showNotice) {
            ConfirmationNotice(
                message,
                Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ConfirmationNotice(message: String, modifier: Modifier) {
    Snackbar(
        modifier = modifier.testTag("transient-confirmation")
            .semantics { liveRegion = LiveRegionMode.Polite },
        containerColor = MaterialTheme.vigiliaColors.completedContainer,
        contentColor = MaterialTheme.vigiliaColors.onCompletedContainer,
    ) { Text(message) }
}
