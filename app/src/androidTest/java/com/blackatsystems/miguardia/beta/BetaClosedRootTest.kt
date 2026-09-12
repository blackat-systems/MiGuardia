package com.blackatsystems.miguardia.beta
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.MiGuardiaApplication
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
/** Real public root: recovery and lock remain; there is no beta gate or authority injection. */
class BetaClosedRootTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun assertUsableLocalRoot() {
        val tags = listOf("access-lock-gate", "work-sector-selection", "work-setup-calendar-guide", "work-setup-overview", "help-introduction-1", "month-grid")
        compose.waitUntil(15000) { tags.any { compose.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() } }
        assertTrue(tags.any { compose.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() })
    }
    @Test fun publicRootAndRecreationDoNotCreateOrChangeResidualBetaFiles() {
        val app = ApplicationProvider.getApplicationContext<MiGuardiaApplication>()
        fun residual() = app.noBackupFilesDir.walkTopDown().filter { it.isFile && it.name.startsWith("beta") }
            .associate { it.relativeTo(app.noBackupFilesDir).path to it.readBytes().toList() }
        val before = residual()
        ActivityScenario.launch<MainActivity>(Intent(app,MainActivity::class.java)).use { scenario ->
            compose.waitUntil(15000) { app.startupRecoveryGate.isReady }
            assertUsableLocalRoot()
            compose.onNodeWithTag("beta-screen").assertDoesNotExist()
            assertEquals(before,residual())
            scenario.recreate()
            compose.waitUntil(15000) { app.startupRecoveryGate.isReady }
            assertUsableLocalRoot()
            compose.onNodeWithTag("beta-screen").assertDoesNotExist()
            assertEquals(before,residual())
        }
    }
}
