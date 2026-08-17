package com.blackatsystems.miguardia.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.ui.theme.vigiliaColors
import kotlinx.coroutines.delay

const val CONFIRMATION_DURATION_MILLIS = 2_500L

@Composable
fun TransientConfirmation(
    message: String?,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(CONFIRMATION_DURATION_MILLIS)
            onDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        content()
        if (message != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                containerColor = MaterialTheme.vigiliaColors.success,
                contentColor = MaterialTheme.vigiliaColors.onSuccess,
            ) {
                Text(message)
            }
        }
    }
}
