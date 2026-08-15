package com.blackatsystems.miguardia

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.blackatsystems.miguardia.ui.components.CONFIRMATION_DURATION_MILLIS
import com.blackatsystems.miguardia.ui.components.TransientConfirmation
import org.junit.Rule
import org.junit.Test

class TransientConfirmationComposeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun confirmationFloatsAndDisappearsAfterTwoAndAHalfSeconds() {
        var message by mutableStateOf<String?>("Acción completada.")
        composeRule.setContent {
            MaterialTheme {
                TransientConfirmation(message, onDismiss = { message = null }) { }
            }
        }

        composeRule.onNodeWithText("Acción completada.").assertExists()
        composeRule.waitUntil(CONFIRMATION_DURATION_MILLIS + 1_500L) { message == null }
        composeRule.onNodeWithText("Acción completada.").assertDoesNotExist()
    }
}
