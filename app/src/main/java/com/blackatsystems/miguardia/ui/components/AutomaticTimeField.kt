package com.blackatsystems.miguardia.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import java.time.LocalTime

@Composable
fun AutomaticTimeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val completeInvalidTime = isCompleteInvalidAutomaticTime(value)
    var isFocused by remember { mutableStateOf(false) }
    var editingValue by remember {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }
    LaunchedEffect(value) {
        if (!isFocused && value != editingValue.text) {
            editingValue = TextFieldValue(value, selection = TextRange(value.length))
        }
    }
    OutlinedTextField(
        value = editingValue,
        onValueChange = { candidate ->
            val next = applyAutomaticTimeEdit(editingValue, candidate) ?: return@OutlinedTextField
            editingValue = next
            onValueChange(canonicalAutomaticTimeOrDraft(next.text))
        },
        modifier = modifier.onFocusChanged { focusState ->
            val lostFocus = isFocused && !focusState.isFocused
            isFocused = focusState.isFocused
            if (lostFocus) {
                val normalized = canonicalAutomaticTimeOrDraft(editingValue.text)
                if (normalized != editingValue.text) {
                    editingValue = TextFieldValue(normalized, selection = TextRange(normalized.length))
                }
                onValueChange(normalized)
            }
        },
        enabled = enabled,
        isError = completeInvalidTime,
        label = { Text(label) },
        placeholder = { Text("Ejemplo: 0830") },
        supportingText = {
            Text(
                if (completeInvalidTime) {
                    "Ingresá una hora válida entre 00:00 y 23:59."
                } else {
                    "Escribí 4 números. MiGuardia agrega los dos puntos."
                },
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

internal fun isCompleteInvalidAutomaticTime(value: String): Boolean =
    value.length == 5 && runCatching { LocalTime.parse(value) }.isFailure

internal fun canonicalAutomaticTimeOrDraft(value: String): String {
    val formatted = formatAutomaticTimeInput(value) ?: return value
    return formatted.takeIf { runCatching { LocalTime.parse(it) }.isSuccess } ?: value
}

internal fun formatAutomaticTimeInput(rawValue: String): String? {
    if (rawValue.any { character -> character !in '0'..'9' && character != ':' }) return null
    if (rawValue.count { it == ':' } > 1) return null
    if (':' in rawValue) {
        val hour = rawValue.substringBefore(':')
        val minute = rawValue.substringAfter(':')
        if (hour.length > 2 || minute.length > 2) return null
        if (hour.isEmpty()) return if (minute.isEmpty()) "" else null
        if (minute.isEmpty()) return hour
        return "${hour.padStart(2, '0')}:$minute"
    }
    val digits = rawValue.filter { it in '0'..'9' }
    if (digits.length > 4) return null
    return if (digits.length <= 2) digits else "${digits.take(2)}:${digits.drop(2)}"
}

internal fun applyAutomaticTimeEdit(
    previous: TextFieldValue,
    candidate: TextFieldValue,
): TextFieldValue? {
    val formatted = formatAutomaticTimeInput(candidate.text) ?: return null
    val selectedWholePreviousValue = previous.selection.start != previous.selection.end &&
        minOf(previous.selection.start, previous.selection.end) == 0 &&
        maxOf(previous.selection.start, previous.selection.end) == previous.text.length
    val preserveDeletion = candidate.text.length < previous.text.length &&
        !selectedWholePreviousValue &&
        !removedOnlyAutomaticSeparator(previous.text, candidate.text, ':')
    if (preserveDeletion) {
        return candidate.copy(
            selection = TextRange(
                candidate.selection.start.coerceIn(0, candidate.text.length),
                candidate.selection.end.coerceIn(0, candidate.text.length),
            ),
        )
    }
    return TextFieldValue(
        text = formatted,
        selection = TextRange(
            start = automaticTimeCursorOffset(candidate.text, candidate.selection.start, formatted.length),
            end = automaticTimeCursorOffset(candidate.text, candidate.selection.end, formatted.length),
        ),
    )
}

internal fun removedOnlyAutomaticSeparator(
    previousText: String,
    candidateText: String,
    separator: Char,
): Boolean {
    if (candidateText.length >= previousText.length) return false
    var candidateIndex = 0
    var removedSeparator = false
    previousText.forEach { previousCharacter ->
        if (
            candidateIndex < candidateText.length &&
            previousCharacter == candidateText[candidateIndex]
        ) {
            candidateIndex += 1
        } else {
            if (previousCharacter != separator) return false
            removedSeparator = true
        }
    }
    return removedSeparator && candidateIndex == candidateText.length
}

private fun automaticTimeCursorOffset(rawValue: String, rawOffset: Int, formattedLength: Int): Int {
    val prefix = rawValue.take(rawOffset.coerceIn(0, rawValue.length))
    val digitOffset = prefix.count { it in '0'..'9' }
    val pastSeparator = prefix.contains(':') || digitOffset > 2
    val paddedSingleDigitHour = ':' in rawValue &&
        rawValue.substringBefore(':').length == 1 &&
        rawValue.substringAfter(':').isNotEmpty()
    val separatorOffset = if (formattedLength > 2 && pastSeparator) 1 else 0
    val leadingZeroOffset = if (paddedSingleDigitHour) 1 else 0
    val formattedOffset = digitOffset + separatorOffset + leadingZeroOffset
    return formattedOffset.coerceIn(0, formattedLength)
}
