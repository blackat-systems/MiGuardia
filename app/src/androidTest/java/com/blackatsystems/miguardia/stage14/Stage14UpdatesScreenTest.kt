package com.blackatsystems.miguardia.stage14

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.updates.InstalledAppIdentity
import com.blackatsystems.miguardia.updates.InstalledUpdateApp
import com.blackatsystems.miguardia.updates.PUBLIC_CERTIFICATE
import com.blackatsystems.miguardia.updates.PUBLIC_PACKAGE
import com.blackatsystems.miguardia.updates.UpdateRelease
import com.blackatsystems.miguardia.updates.UpdateSaveVerification
import com.blackatsystems.miguardia.updates.UpdateStage
import com.blackatsystems.miguardia.updates.UpdatesScreen
import com.blackatsystems.miguardia.updates.UpdatesState
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Etapa 14 · Actualizaciones (A273, A275, A276, A279, A280, A281, A283): textos y controles de la
 * pantalla con estado ficticio en memoria. No usa red, selector, archivos ni capturas.
 */
class Stage14UpdatesScreenTest {
    @get:Rule val compose = createComposeRule()

    private val installed = InstalledUpdateApp(InstalledAppIdentity("com.blackatsystems.miguardia.qa", "b".repeat(64), 1), "1.0.0", 26)
    private val mib = 1024L * 1024L
    private val release = UpdateRelease("release_stage14_01", "2.0.0-beta.2", 3, PUBLIC_PACKAGE, 26,
        5 * mib / 2, "a".repeat(64), PUBLIC_CERTIFICATE, Instant.parse("2026-09-06T12:00:00.000Z"))

    private var state by mutableStateOf(UpdatesState(open = true, installed = installed))
    private var searches = 0
    private var downloads = 0
    private var saves = 0
    private var cancels = 0
    private var cancelAction: () -> Unit = {}

    private fun show(initial: UpdatesState = state) {
        state = initial
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = false) {
                UpdatesScreen(state, { searches++ }, { downloads++ }, { saves++ }, { cancels++; cancelAction() }, {})
            }
        }
    }
    private fun set(next: UpdatesState) { compose.runOnIdle { state = next } }
    private fun stage(stage: UpdateStage) = UpdatesState(open = true, installed = installed, stage = stage)
    private fun status(expected: String) {
        compose.onNodeWithTag("updates-status").performScrollTo().assertIsDisplayed().assertTextEquals(expected)
    }
    /** Same es-AR number format the screen uses; the surrounding words are asserted literally. */
    private fun size(bytes: Long) = String.format(Locale.forLanguageTag("es-AR"), "%.1f MiB", bytes / (1024.0 * 1024.0))

    // A273
    @Test fun installedVersionIsShownAndItsAbsenceIsSaidInWords() {
        show()
        compose.onNodeWithText("Versión instalada: 1.0.0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Versión instalada: No disponible").assertDoesNotExist()
        set(UpdatesState(open = true, installed = null, stage = UpdateStage.UNAVAILABLE))
        compose.onNodeWithText("Versión instalada: No disponible").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Versión instalada: 1.0.0").assertDoesNotExist()
        status("Actualizaciones no disponibles por el momento.")
    }

    // A275
    @Test fun eachSearchResultHasItsOwnExactTextAndOnlyAvailableOffersTheDownload() {
        show()
        status("Buscá cuando quieras comprobar si hay otra versión.")
        val results = listOf(
            stage(UpdateStage.NO_RELEASE) to "Todavía no hay una versión publicada para descargar.",
            stage(UpdateStage.CURRENT).copy(release = release) to "No hay una versión más nueva que la instalada.",
            stage(UpdateStage.INCOMPATIBLE).copy(release = release) to "Esta versión no es compatible con esta instalación.",
        )
        for ((next, text) in results) {
            set(next)
            status(text)
            compose.onNodeWithTag("updates-download").assertDoesNotExist()
            compose.onNodeWithTag("updates-save").assertDoesNotExist()
            compose.onNodeWithTag("updates-search").performScrollTo().assertIsDisplayed().assertIsEnabled()
                .assertTextEquals("Buscar actualización")
        }
        set(stage(UpdateStage.AVAILABLE).copy(release = release))
        status("Hay una actualización disponible.")
        compose.onNodeWithText("Versión: 2.0.0-beta.2\nTamaño: ${size(release.byteSize)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("updates-search").assertDoesNotExist()
        compose.onNodeWithTag("updates-download").performScrollTo().assertIsDisplayed().assertIsEnabled()
            .assertTextEquals("Descargar actualización").performClick()
        compose.runOnIdle { assertEquals(1, downloads); assertEquals(0, searches); assertEquals(0, saves) }
    }

    // A276
    @Test fun downloadShowsBytesReceivedOutOfTheTotalAndFollowsTheState() {
        val downloading = stage(UpdateStage.DOWNLOADING).copy(release = release)
        show(downloading)
        status("Descargando: ${size(0)} de ${size(release.byteSize)}")
        set(downloading.copy(bytes = mib))
        status("Descargando: ${size(mib)} de ${size(release.byteSize)}")
        set(downloading.copy(bytes = release.byteSize))
        status("Descargando: ${size(release.byteSize)} de ${size(release.byteSize)}")
        compose.onNodeWithTag("updates-cancel").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("updates-search").assertDoesNotExist()
        compose.onNodeWithTag("updates-download").assertDoesNotExist()
        // The three figures are really different texts, not one constant.
        assertEquals(3, setOf(size(0), size(mib), size(release.byteSize)).size)
        set(stage(UpdateStage.VERIFYING).copy(release = release, bytes = release.byteSize))
        status("Comprobando el APK…")
    }

    // A279
    @Test fun savedApkSaysWhetherTheDestinationWasRereadOrOnlyTheWrittenBytesWereChecked() {
        val reread = "Comprobamos el archivo guardado volviéndolo a leer."
        val written = "Comprobamos los bytes escritos. El destino no permitió volver a leer el archivo."
        show(stage(UpdateStage.SAVED).copy(saveVerification = UpdateSaveVerification.READ_BACK))
        status("APK guardado. La actualización todavía no está instalada.")
        compose.onNodeWithText(reread).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(written).assertDoesNotExist()
        set(stage(UpdateStage.SAVED).copy(saveVerification = UpdateSaveVerification.WRITE_ONLY))
        status("APK guardado. La actualización todavía no está instalada.")
        compose.onNodeWithText(written).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(reread).assertDoesNotExist()
        // Neither claim appears before something was actually saved.
        set(stage(UpdateStage.READY).copy(release = release, saveVerification = UpdateSaveVerification.READ_BACK))
        compose.onNodeWithText(reread).assertDoesNotExist()
        compose.onNodeWithText(written).assertDoesNotExist()
    }

    // A280
    @Test fun cancelIsVisibleWhileSearchingAndReturnsToRest() {
        cancelAction = { state = stage(UpdateStage.CANCELLED) }
        show(stage(UpdateStage.CHECKING))
        status("Buscando actualización…")
        compose.onNodeWithTag("updates-search").assertDoesNotExist()
        compose.onNodeWithTag("updates-cancel").performScrollTo().assertIsDisplayed().assertHasClickAction()
            .assertTextEquals("Cancelar").performClick()
        compose.runOnIdle { assertEquals(1, cancels); assertEquals(0, searches) }
        status("Operación cancelada.")
        compose.onNodeWithTag("updates-cancel").assertDoesNotExist()
        compose.onNodeWithTag("updates-search").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, searches); assertEquals(1, cancels) }
    }

    // A281
    @Test fun retryWaitShowsTheCountdownAndKeepsSearchDisabledUntilItEnds() {
        val message = "Hay demasiados intentos. Esperá a que termine la cuenta regresiva antes de buscar de nuevo."
        val waiting = stage(UpdateStage.UNAVAILABLE).copy(message = message, retryRemainingMillis = 30_000)
        show(waiting)
        status("Actualizaciones no disponibles por el momento.")
        compose.onNodeWithText(message).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Podés reintentar en 30 segundos.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("updates-search").performScrollTo().assertIsDisplayed().assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, searches) }
        // Partial seconds round up, so the text never promises less than what is left.
        for ((millis, seconds) in listOf(29_001L to 30, 29_000L to 29, 1_001L to 2)) {
            set(waiting.copy(retryRemainingMillis = millis))
            compose.onNodeWithText("Podés reintentar en $seconds segundos.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("updates-search").assertIsNotEnabled()
        }
        // The last second is written in singular.
        for (millis in listOf(1_000L, 1L)) {
            set(waiting.copy(retryRemainingMillis = millis))
            compose.onNodeWithText("Podés reintentar en 1 segundo.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("updates-search").assertIsNotEnabled()
        }
        set(waiting.copy(retryRemainingMillis = 0))
        compose.onNodeWithText("Podés reintentar en", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("updates-search").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, searches) }
    }

    // A281
    @Test fun connectionErrorIsExplainedAndSearchStaysAvailable() {
        // Message the ViewModel sets for a failed transfer (asserted there in Stage14UpdatesViewModelTest).
        val message = "No pudimos completar o verificar la operación. Revisá la conexión y el espacio disponible y volvé a buscar. Tus datos de MiGuardia siguen en el teléfono."
        show(stage(UpdateStage.ERROR).copy(message = message))
        status("No se completó la operación.")
        compose.onNodeWithText(message).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Podés reintentar en", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("updates-cancel").assertDoesNotExist()
        compose.onNodeWithTag("updates-search").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, searches) }
    }

    // A283
    @Test fun manualInstallationIsExplainedAndSavingIsNeverPresentedAsUpdating() {
        show()
        val installOrUpdateControl = hasClickAction() and
            (hasText("Instalar", substring = true, ignoreCase = true) or hasText("Actualizar", substring = true, ignoreCase = true))
        val stages = listOf(
            stage(UpdateStage.IDLE),
            stage(UpdateStage.AVAILABLE).copy(release = release),
            stage(UpdateStage.READY).copy(release = release),
            stage(UpdateStage.SAVED).copy(saveVerification = UpdateSaveVerification.READ_BACK),
        )
        for (next in stages) {
            set(next)
            compose.onNodeWithText("Instalación manual").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Para actualizar, abrí el APK guardado desde Archivos y elegí Actualizar. No desinstales MiGuardia. Si Android rechaza el archivo por firma o compatibilidad, cancelá: no borres datos ni desactives Play Protect.")
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Si querés, antes podés crear una copia desde Copias de seguridad en el menú.")
                .performScrollTo().assertIsDisplayed()
            compose.onAllNodes(installOrUpdateControl).assertCountEquals(0)
        }
        set(stage(UpdateStage.READY).copy(release = release))
        status("El APK está verificado y listo para guardar.")
        compose.onNodeWithTag("updates-save").performScrollTo().assertIsDisplayed().assertTextEquals("Guardar APK").performClick()
        compose.runOnIdle { assertEquals(1, saves); assertEquals(0, downloads); assertEquals(0, searches) }
        set(stage(UpdateStage.SAVED).copy(saveVerification = UpdateSaveVerification.READ_BACK))
        status("APK guardado. La actualización todavía no está instalada.")
        compose.onNodeWithTag("updates-save").assertDoesNotExist()
    }
}
