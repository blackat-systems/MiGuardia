package com.blackatsystems.miguardia

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.HolidayConflictPolicy
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteAttachment
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteWithAttachments
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarInteractionMode
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarSelectionPurpose
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.calendar.enterEditing
import com.blackatsystems.miguardia.ui.calendar.finishEditing
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsActions
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsSurface
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsSurfaceHost
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsUiState
import com.blackatsystems.miguardia.ui.exceptions.HolidayDraft
import com.blackatsystems.miguardia.ui.exceptions.NoteDraft
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExceptionsComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun holidaysKeepMainCalendarSelectionAndLocalEditing() {
        val holiday = Holiday(UUID(0L, 1L), LocalDate.of(2026, 8, 17), "Feriado ficticio", NOW, NOW)
        var edited: Holiday? = null
        var saves: List<HolidayConflictPolicy?> = emptyList()
        var state by mutableStateOf(
            ExceptionsUiState(
                surface = ExceptionsSurface.HOLIDAYS,
                holidayMonth = YearMonth.of(2026, 8),
                holidays = listOf(holiday),
                holidayDraft = HolidayDraft(datesText = "2026-08-17,2026-08-18"),
            ),
        )
        compose.setContent {
            MaterialTheme {
                ExceptionsSurfaceHost(
                    state,
                    ExceptionsActions(
                        updateHolidayDraft = { transform ->
                            state = state.copy(holidayDraft = transform(state.holidayDraft))
                        },
                        editHoliday = { edited = it },
                        saveHolidays = { policy -> saves = saves + policy },
                    ),
                )
            }
        }

        compose.onNodeWithTag("holiday-date-selector").assertDoesNotExist()
        compose.onNodeWithText("17/08/2026, 18/08/2026").assertExists()
        compose.onNodeWithText("Nombre opcional").performTextInput("Feriado manual")
        compose.onNodeWithText("Guardar feriado(s)").performScrollTo().performClick()
        compose.onNodeWithText("Feriado ficticio").assertExists()
        compose.onNodeWithText("Editar").performScrollTo().performClick()

        compose.runOnIdle {
            assertEquals("2026-08-17,2026-08-18", state.holidayDraft.datesText)
            assertEquals("Feriado manual", state.holidayDraft.name)
            assertEquals(listOf(null), saves)
            assertEquals(holiday, edited)
        }
        compose.onNodeWithText("Novedades").assertDoesNotExist()
    }

    @Test
    fun holidayDateSelectionUsesTheOnlyMainMonthGrid() {
        val month = YearMonth.of(2026, 8)
        val first = LocalDate.of(2026, 8, 17)
        val second = first.plusDays(1)
        var calendar by mutableStateOf(calendarState(month).copy(lastOpenedDate = first))
        var exceptions by mutableStateOf(
            ExceptionsUiState(
                surface = ExceptionsSurface.HOLIDAYS,
                holidayMonth = month,
            ),
        )
        val actions = ExceptionsActions(
            beginHolidaySelection = { requestedMonth ->
                exceptions = exceptions.copy(
                    surface = ExceptionsSurface.NONE,
                    holidayMonth = requestedMonth,
                    holidaySelectionActive = true,
                )
            },
            updateHolidaySelection = { requestedMonth, dates ->
                exceptions = exceptions.copy(
                    holidayMonth = requestedMonth,
                    holidayDraft = exceptions.holidayDraft.copy(
                        datesText = dates.sorted().joinToString(","),
                    ),
                )
            },
            confirmHolidaySelection = { requestedMonth, dates ->
                exceptions = exceptions.copy(
                    surface = ExceptionsSurface.HOLIDAYS,
                    holidayMonth = requestedMonth,
                    holidaySelectionActive = false,
                    holidayDraft = exceptions.holidayDraft.copy(
                        datesText = dates.sorted().joinToString(","),
                    ),
                )
            },
            cancelHolidaySelection = {
                exceptions = exceptions.copy(
                    surface = ExceptionsSurface.HOLIDAYS,
                    holidaySelectionActive = false,
                )
            },
        )
        compose.setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarState = calendar,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    onEnterHolidayCalendarSelection = { date ->
                        calendar = calendar.enterEditing(date, CalendarSelectionPurpose.HOLIDAY)
                    },
                    onEditSelectionChange = { dates -> calendar = calendar.copy(editSelectedDates = dates) },
                    onFinishCalendarEditMode = {
                        calendar = calendar.copy(
                            interactionMode = CalendarInteractionMode.VIEW,
                            editSelectedDates = emptySet(),
                        )
                    },
                    exceptionsState = exceptions,
                    exceptionsActions = actions,
                )
            }
        }

        compose.onNodeWithTag("holiday-date-selector").assertDoesNotExist()
        compose.onNodeWithTag("holiday-open-calendar-selection").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("month-grid").assertCountEquals(1)
        compose.onNodeWithTag("holiday-calendar-selection").assertExists()
        compose.onNodeWithTag("calendar-v2-load-shifts").assertDoesNotExist()
        compose.onNodeWithTag("calendar-v2-repeat-shifts").assertDoesNotExist()
        compose.onNodeWithTag("calendar-work-setup-action").assertDoesNotExist()

        compose.onNodeWithTag("day-$first").performClick()
        compose.onNodeWithTag("day-$second").performClick()
        compose.onNodeWithText("2 fechas seleccionadas.").assertExists()
        compose.onAllNodesWithContentDescription("seleccionado como feriado", substring = true)
            .assertCountEquals(2)
        compose.onNodeWithTag("holiday-confirm-calendar-selection").performClick()

        compose.onNodeWithTag("holiday-date-selector").assertDoesNotExist()
        compose.onNodeWithText("17/08/2026, 18/08/2026").assertExists()
        compose.runOnIdle {
            assertEquals(setOf(first, second), exceptions.holidayDraft.selectedDates)
            assertEquals(CalendarInteractionMode.VIEW, calendar.interactionMode)
            assertEquals(emptySet<LocalDate>(), calendar.editSelectedDates)
        }
    }

    @Test
    fun backCancelsHolidaySelectionCompletelyWithoutRestorationOrWrites() {
        val month = YearMonth.of(2026, 8)
        val selectedDate = LocalDate.of(2026, 8, 17)
        var calendar by mutableStateOf(calendarState(month).copy(lastOpenedDate = selectedDate))
        var exceptions by mutableStateOf(
            ExceptionsUiState(
                surface = ExceptionsSurface.HOLIDAYS,
                holidayMonth = month,
            ),
        )
        var saves = 0
        val actions = ExceptionsActions(
            beginHolidaySelection = { requestedMonth ->
                exceptions = exceptions.copy(
                    surface = ExceptionsSurface.NONE,
                    holidayMonth = requestedMonth,
                    holidaySelectionActive = true,
                )
            },
            updateHolidaySelection = { requestedMonth, dates ->
                exceptions = exceptions.copy(
                    holidayMonth = requestedMonth,
                    holidayDraft = exceptions.holidayDraft.copy(
                        datesText = dates.sorted().joinToString(","),
                    ),
                )
            },
            cancelHolidaySelection = {
                exceptions = exceptions.copy(
                    surface = ExceptionsSurface.HOLIDAYS,
                    holidaySelectionActive = false,
                )
            },
            saveHolidays = { saves += 1 },
        )
        compose.setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarState = calendar,
                    onPreviousMonth = {}, onNextMonth = {}, onToday = {},
                    onSelectDate = {}, onDismissDate = {}, onRetry = {},
                    onEnterHolidayCalendarSelection = { date ->
                        calendar = calendar.enterEditing(date, CalendarSelectionPurpose.HOLIDAY)
                    },
                    onSetCalendarSelectionPurpose = { purpose ->
                        calendar = calendar.copy(selectionPurpose = purpose)
                    },
                    onEditSelectionChange = { dates -> calendar = calendar.copy(editSelectedDates = dates) },
                    onFinishCalendarEditMode = { calendar = calendar.finishEditing() },
                    exceptionsState = exceptions,
                    exceptionsActions = actions,
                )
            }
        }

        compose.onNodeWithTag("holiday-open-calendar-selection").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("day-$selectedDate").performClick()
        compose.onNodeWithTag("holiday-calendar-selection").assertExists()

        pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("holiday-calendar-selection").assertDoesNotExist()
        compose.onNodeWithTag("holiday-open-calendar-selection").assertExists()
        compose.runOnIdle {
            assertEquals(false, exceptions.holidaySelectionActive)
            assertEquals(ExceptionsSurface.HOLIDAYS, exceptions.surface)
            assertEquals(CalendarInteractionMode.VIEW, calendar.interactionMode)
            assertEquals(null, calendar.selectionPurpose)
            assertEquals(0, saves)
        }

        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(false, exceptions.holidaySelectionActive)
            assertEquals(CalendarInteractionMode.VIEW, calendar.interactionMode)
            assertEquals(0, saves)
        }
    }

    @Test
    fun holidayConflictsExposeReplaceKeepAndCancelPolicies() {
        val conflict = LocalDate.of(2026, 8, 17)
        val policies = mutableListOf<HolidayConflictPolicy?>()
        var cancelled = 0
        compose.setContent {
            MaterialTheme {
                ExceptionsSurfaceHost(
                    ExceptionsUiState(
                        surface = ExceptionsSurface.HOLIDAYS,
                        holidayMonth = YearMonth.of(2026, 8),
                        holidayDraft = HolidayDraft(
                            datesText = conflict.toString(),
                            conflictDates = setOf(conflict),
                        ),
                    ),
                    ExceptionsActions(
                        saveHolidays = policies::add,
                        cancelHolidayConflict = { cancelled++ },
                    ),
                )
            }
        }

        compose.onNodeWithText("Fechas con feriado").assertExists()
        compose.onNodeWithText("Conservar existentes").performClick()
        compose.runOnIdle { assertEquals(listOf(HolidayConflictPolicy.KEEP_EXISTING), policies) }

        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertEquals(1, cancelled) }
    }

    @Test
    fun closingWithAHolidayDraftRequiresExplicitDiscard() {
        var closes = 0
        var state by mutableStateOf(
            ExceptionsUiState(
                surface = ExceptionsSurface.HOLIDAYS,
                holidayMonth = YearMonth.of(2026, 8),
            ),
        )
        compose.setContent {
            MaterialTheme {
                ExceptionsSurfaceHost(
                    state,
                    ExceptionsActions(
                        close = { closes++ },
                        updateHolidayDraft = { transform ->
                            state = state.copy(holidayDraft = transform(state.holidayDraft))
                        },
                    ),
                )
            }
        }

        compose.onNodeWithText("Nombre opcional").performTextInput("Borrador ficticio")
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("Hay datos del feriado sin guardar.").assertExists()
        compose.onNodeWithText("Seguir editando").performClick()
        compose.runOnIdle { assertEquals(0, closes) }

        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("Descartar").performClick()
        compose.runOnIdle { assertEquals(1, closes) }
    }

    @Test
    fun notesRemainPrivateAndDeletionRequiresConfirmation() {
        compose.setContent {
            MaterialTheme {
                ExceptionsSurfaceHost(
                    ExceptionsUiState(
                        surface = ExceptionsSurface.NOTES,
                        holidayMonth = YearMonth.of(2026, 8),
                        selectedShift = SHIFT,
                        notes = listOf(ShiftNote(UUID(0L, 2L), SHIFT.id, "Texto privado ficticio", NOW, NOW)),
                    ),
                    ExceptionsActions(),
                )
            }
        }

        compose.onNodeWithText("Notas privadas").assertExists()
        compose.onNodeWithText("Texto privado ficticio").performScrollTo().assertExists()
        compose.onNodeWithText("Eliminar").performScrollTo().performClick()
        compose.onNodeWithText("Eliminar nota").assertExists()
        compose.onNodeWithText("La nota privada se eliminará.").assertExists()
        compose.onNodeWithText("Registrar ausencia").assertDoesNotExist()
        compose.onNodeWithText("Agregar segunda guardia").assertDoesNotExist()
    }

    @Test
    fun noteDraftUsesOnlyTheSelectedV2Shift() {
        var body = ""
        var saves = 0
        var state by mutableStateOf(
            ExceptionsUiState(
                surface = ExceptionsSurface.NOTES,
                selectedShift = SHIFT,
            ),
        )
        compose.setContent {
            MaterialTheme {
                ExceptionsSurfaceHost(
                    state,
                    ExceptionsActions(
                        updateNoteDraft = { transform ->
                            state = state.copy(noteDraft = transform(state.noteDraft))
                            body = state.noteDraft.body
                        },
                        saveNote = { saves++ },
                    ),
                )
            }
        }

        compose.onNodeWithText("Nota").performTextInput("Dato ficticio")
        compose.onNodeWithText("Guardar nota").performClick()
        compose.runOnIdle {
            assertEquals("Dato ficticio", body)
            assertEquals(1, saves)
        }
    }

    @Test
    fun noteAttachmentCanBeReplacedWithoutRecyclingTheBitmapStillBeingDrawn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val imageStore = SchedulePhotoFileStore(context)
        val noteId = UUID(0L, 40L)
        val first = noteAttachment(noteId, UUID(0L, 41L), "note_${noteId}_0000000a.png")
        val second = noteAttachment(noteId, UUID(0L, 42L), "note_${noteId}_0000000b.png")
        listOf(first, second).forEachIndexed { index, attachment ->
            val file = imageStore.file(attachment.storageKey)
            check(file.parentFile?.exists() == true || file.parentFile?.mkdirs() == true)
            Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(if (index == 0) 0xFF336699.toInt() else 0xFF993366.toInt())
                file.outputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
                bitmap.recycle()
            }
        }
        var state by mutableStateOf(
            ExceptionsUiState(
                surface = ExceptionsSurface.NOTES,
                selectedShift = SHIFT,
                noteDraft = NoteDraft(attachments = listOf(first)),
            ),
        )
        compose.setContent {
            MaterialTheme {
                ExceptionsSurfaceHost(state, ExceptionsActions(), imageStore)
            }
        }

        try {
            repeat(6) { index ->
                compose.waitUntil(5_000) {
                    compose.onAllNodesWithContentDescription("Imagen 1 de la nota")
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithContentDescription("Imagen 1 de la nota").captureToImage()
                compose.runOnIdle {
                    state = state.copy(
                        noteDraft = NoteDraft(attachments = listOf(if (index % 2 == 0) second else first)),
                    )
                }
            }
            compose.waitForIdle()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription("Imagen 1 de la nota")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Imagen 1 de la nota").captureToImage()
        } finally {
            imageStore.file(first.storageKey).delete()
            imageStore.file(second.storageKey).delete()
        }
    }

    @Test
    fun confirmedNoteThumbnailHasLabeledButtonRoleAndDisablesWhileSaving() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "stage4-note-thumbnail-${UUID.randomUUID()}")
        val context = object : ContextWrapper(base) { override fun getFilesDir() = root }
        val imageStore = SchedulePhotoFileStore(context)
        val noteId = UUID.randomUUID()
        val imageId = UUID.randomUUID()
        val attachment = noteAttachment(noteId, imageId, "note_$imageId.png")
        val file = imageStore.file(attachment.storageKey)
        check(file.parentFile!!.mkdirs())
        val bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        val item = ShiftNoteWithAttachments(ShiftNote(noteId, SHIFT.id, "Nota sintética guardada", NOW, NOW), listOf(attachment))
        var state by mutableStateOf(ExceptionsUiState(surface = ExceptionsSurface.NOTES,
            selectedShift = SHIFT, noteItems = listOf(item)))
        compose.setContent { MaterialTheme { ExceptionsSurfaceHost(state, ExceptionsActions(), imageStore) } }
        try {
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription("Imagen 1 de la nota").fetchSemanticsNodes().isNotEmpty()
            }
            val thumbnail = compose.onNodeWithContentDescription("Imagen 1 de la nota")
            thumbnail.performScrollTo().assertHasClickAction().assertIsEnabled()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assert(SemanticsMatcher("La acción anuncia Ver imagen 1") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == "Ver imagen 1"
                })
            compose.runOnIdle { state = state.copy(isSaving = true) }
            thumbnail.assertIsNotEnabled()
            compose.runOnIdle { state = state.copy(isSaving = false) }
            thumbnail.assertIsEnabled()
            compose.runOnIdle { assertEquals(listOf(item), state.noteItems) }
        } finally {
            check(root.canonicalFile.parentFile == base.cacheDir.canonicalFile)
            root.deleteRecursively()
        }
    }

    private companion object {
        fun noteAttachment(noteId: UUID, id: UUID, storageKey: String) = ShiftNoteAttachment(
            id = id,
            noteId = noteId,
            storageKey = storageKey,
            mimeType = "image/png",
            byteSize = 1,
            pixelWidth = 96,
            pixelHeight = 64,
            position = 0,
            createdAt = NOW,
            updatedAt = NOW,
        )

        val NOW: Instant = Instant.parse("2026-08-13T12:00:00Z")
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val SHIFT = Shift(
            id = UUID(0L, 10L),
            startAt = NOW,
            endAt = NOW.plusSeconds(8 * 3_600),
            zoneId = ZONE,
            localStartDate = LocalDate.of(2026, 8, 13),
            objectiveNameSnapshot = "Objetivo ficticio",
            objectiveAbbreviationSnapshot = "OBJ",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = LocalTime.of(9, 0),
            endTimeSnapshot = LocalTime.of(17, 0),
            colorArgbSnapshot = 0xFF123456.toInt(),
            position = null,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = UUID(0L, 11L),
            createdAt = NOW,
            updatedAt = NOW,
        )

        fun calendarState(month: YearMonth): CalendarUiState = CalendarUiState(
            visibleMonth = month,
            referenceInstant = NOW,
            days = projectCalendarMonth(
                month = month,
                shifts = emptyList(),
                explicitDayStatuses = emptyList(),
                medicalLeaves = emptyList(),
                now = NOW,
            ),
            loadState = CalendarLoadState.CONTENT,
        )
    }
}
