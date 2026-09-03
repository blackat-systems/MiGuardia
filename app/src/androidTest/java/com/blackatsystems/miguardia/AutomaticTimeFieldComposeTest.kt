package com.blackatsystems.miguardia

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.blackatsystems.miguardia.ui.components.AutomaticTimeField
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AutomaticTimeFieldComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun shortHourRemainsEditableAndNormalizesVisiblyWhenFocusLeaves() {
        var value by mutableStateOf("08:30")
        var changeCount = 0
        compose.setContent {
            val focusManager = LocalFocusManager.current
            MiGuardiaTheme {
                Column {
                    AutomaticTimeField(
                        value = value,
                        onValueChange = {
                            changeCount += 1
                            value = it
                        },
                        label = "Hora ficticia",
                        modifier = Modifier.testTag("automatic-time"),
                    )
                    Button(
                        onClick = focusManager::clearFocus,
                        modifier = Modifier.testTag("leave-time-field"),
                    ) {
                        Text("Continuar")
                    }
                }
            }
        }

        compose.onNodeWithTag("automatic-time").performClick()
        compose.onNodeWithTag("automatic-time").performTextReplacement("8:30")
        compose.onNodeWithTag("automatic-time").assertTextContains("8:30")
        compose.runOnIdle {
            assertEquals("08:30", value)
            assertEquals(1, changeCount)
        }

        compose.onNodeWithTag("leave-time-field").performClick()
        compose.onNodeWithTag("automatic-time").assertTextContains("08:30")
        compose.runOnIdle { assertEquals(1, changeCount) }
    }

    @Test
    fun focusLossDoesNotRepeatAValueAlreadyDispatchedBeforeParentRecomposes() {
        var changeCount = 0
        compose.setContent {
            val focusManager = LocalFocusManager.current
            MiGuardiaTheme {
                Column {
                    AutomaticTimeField(
                        value = "08:00",
                        onValueChange = { changeCount += 1 },
                        label = "Hora ficticia",
                        modifier = Modifier.testTag("automatic-time"),
                    )
                    Button(
                        onClick = focusManager::clearFocus,
                        modifier = Modifier.testTag("leave-time-field"),
                    ) {
                        Text("Continuar")
                    }
                }
            }
        }

        compose.onNodeWithTag("automatic-time").performClick()
        compose.onNodeWithTag("automatic-time").performTextReplacement("09:00")
        compose.onNodeWithTag("leave-time-field").performClick()

        compose.runOnIdle { assertEquals(1, changeCount) }
    }
}
