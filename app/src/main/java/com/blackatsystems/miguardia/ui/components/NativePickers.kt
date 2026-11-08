package com.blackatsystems.miguardia.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.ui.theme.vigiliaColors
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.blackatsystems.miguardia.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

private val PickerLocale: Locale = Locale.forLanguageTag("es-AR")

/*
 * Native Material 3 pickers that complement typed entry. A picker never writes on its own:
 * only "Aceptar" calls back, with the same canonical text a person could type, so the
 * screen's draft, review and save rules stay exactly the same. Cancel or dismiss changes nothing.
 */

/** Small violet tag inside a field (style v2). */
@Composable
fun FieldBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.vigiliaColors.selection,
        contentColor = MaterialTheme.vigiliaColors.active,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Test tag suffixes shared by the pickers. The field tag is the prefix. */
const val PICKER_BUTTON_SUFFIX = "-picker"
const val PICKER_CONFIRM_TAG = "native-picker-confirm"
const val PICKER_CANCEL_TAG = "native-picker-cancel"

/** Canonical "HH:mm" text for a picked time, identical to typed entry. */
internal fun formatPickedTime(hour: Int, minute: Int): String =
    LocalTime.of(hour, minute).toString()

/** Starting point for the time picker: the field's valid value, otherwise [fallback]. */
internal fun timePickerInitialValue(value: String, fallback: LocalTime): LocalTime =
    value.takeIf { it.length == 5 }?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: fallback

/**
 * Material's DatePicker works with UTC-midnight millis. Converting through UTC keeps the
 * picked calendar date exact whatever the device time zone is.
 */
internal fun localDateFromPickerMillis(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

internal fun pickerMillisFromLocalDate(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Text formats already accepted by typed date fields. The picker writes the same text. */
enum class DateFieldFormat(private val pattern: String, val hint: String) {
    /** 2026-11-08 */
    ISO("uuuu-MM-dd", "AAAA-MM-DD"),

    /** 08/11/2026 */
    DAY_MONTH_YEAR("dd/MM/uuuu", "DD/MM/AAAA"),
    ;

    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT)

    fun format(date: LocalDate): String = date.format(formatter)

    fun parseOrNull(text: String): LocalDate? =
        runCatching { LocalDate.parse(text.trim(), formatter) }.getOrNull()
}

@Composable
internal fun PickerTrailingButton(
    iconRes: Int,
    description: String,
    enabled: Boolean,
    testTag: String?,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .semantics { contentDescription = description }
            .let { if (testTag != null) it.testTag(testTag) else it },
    ) {
        Icon(painterResource(iconRes), contentDescription = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiGuardiaTimePickerDialog(
    initial: LocalTime,
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    // A Boolean survives recreation; TimePickerDisplayMode itself cannot be stored in a Bundle.
    var keyboardInput by rememberSaveable { mutableStateOf(false) }
    val displayMode = if (keyboardInput) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = { keyboardInput = !keyboardInput },
                displayMode = displayMode,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(PICKER_CANCEL_TAG)) {
                Text("Cancelar")
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(formatPickedTime(state.hour, state.minute)) },
                modifier = Modifier.testTag(PICKER_CONFIRM_TAG),
            ) { Text("Aceptar") }
        },
    ) {
        if (displayMode == TimePickerDisplayMode.Picker) TimePicker(state) else TimeInput(state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiGuardiaDatePickerDialog(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // Argentine Spanish like the rest of the app: weeks start on Monday, as in the Calendar,
    // whatever language the phone uses (a phone in es-US would start on Sunday).
    val state = remember(initial) {
        DatePickerState(
            locale = PickerLocale,
            initialSelectedDateMillis = pickerMillisFromLocalDate(initial),
        )
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(PICKER_CANCEL_TAG)) {
                Text("Cancelar")
            }
        },
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(localDateFromPickerMillis(it)) } },
                enabled = state.selectedDateMillis != null,
                modifier = Modifier.testTag(PICKER_CONFIRM_TAG),
            ) { Text("Aceptar") }
        },
    ) {
        DatePicker(state)
    }
}

/**
 * Typed date field with a calendar button. Typing keeps working exactly as before; the
 * calendar writes the same text format into the draft and nothing else.
 */
@Composable
fun PickableDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    format: DateFieldFormat,
    modifier: Modifier = Modifier,
    supportingText: String? = format.hint,
    pickerFallback: () -> LocalDate = { LocalDate.now() },
    pickerTestTag: String? = null,
    enabled: Boolean = true,
    // Style v2: short tag shown inside the field, e.g. "+1 día" when the date crosses midnight.
    badge: String? = null,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                badge?.let { FieldBadge(it) }
                PickerTrailingButton(
                    iconRes = R.drawable.ic_pick_date,
                    description = "Elegir en el calendario: $label",
                    enabled = enabled,
                    testTag = pickerTestTag,
                ) { showPicker = true }
            }
        },
        singleLine = true,
        enabled = enabled,
        modifier = modifier,
    )
    if (showPicker) {
        MiGuardiaDatePickerDialog(
            initial = format.parseOrNull(value) ?: pickerFallback(),
            onConfirm = { picked ->
                showPicker = false
                onValueChange(format.format(picked))
            },
            onDismiss = { showPicker = false },
        )
    }
}
