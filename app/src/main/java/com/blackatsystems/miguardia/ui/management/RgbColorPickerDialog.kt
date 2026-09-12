package com.blackatsystems.miguardia.ui.management

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun RgbColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val initialHsv = FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) }
    var hue by rememberSaveable(initialColor) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable(initialColor) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by rememberSaveable(initialColor) { mutableFloatStateOf(initialHsv[2]) }
    val selectedColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val red = selectedColor ushr 16 and 0xFF
    val green = selectedColor ushr 8 and 0xFF
    val blue = selectedColor and 0xFF
    val selectedComposeColor = Color(selectedColor)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selector de color") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Saturación y luminosidad", fontWeight = FontWeight.SemiBold)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("color-saturation-brightness")
                        .semantics {
                            contentDescription = "Área de saturación y luminosidad"
                            stateDescription =
                                "Saturación ${(saturation * 100).toInt()} %, luminosidad ${(brightness * 100).toInt()} %"
                        }
                        .pointerInput(hue) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                fun updateColor(position: Offset) {
                                    saturation = (position.x / size.width).coerceIn(0f, 1f)
                                    brightness = (1f - position.y / size.height).coerceIn(0f, 1f)
                                }
                                updateColor(down.position)
                                down.consume()
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { change ->
                                        if (change.pressed) updateColor(change.position)
                                        change.consume()
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                ) {
                    drawRect(brush = Brush.horizontalGradient(listOf(Color.White, hueColor)))
                    drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val markerCenter = Offset(
                        x = (saturation * size.width).coerceIn(10.dp.toPx(), size.width - 10.dp.toPx()),
                        y = ((1f - brightness) * size.height).coerceIn(10.dp.toPx(), size.height - 10.dp.toPx()),
                    )
                    drawCircle(Color.Black, radius = 10.dp.toPx(), center = markerCenter)
                    drawCircle(Color.White, radius = 8.dp.toPx(), center = markerCenter)
                    drawCircle(selectedComposeColor, radius = 5.dp.toPx(), center = markerCenter)
                }

                Text("Tono", fontWeight = FontWeight.SemiBold)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("color-hue")
                        .semantics {
                            contentDescription = "Barra arcoíris de tono"
                            stateDescription = "Tono ${hue.toInt()} grados"
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                fun updateHue(position: Offset) {
                                    hue = ((position.x / size.width).coerceIn(0f, 1f) * 360f)
                                        .coerceAtMost(359.999f)
                                }
                                updateHue(down.position)
                                down.consume()
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { change ->
                                        if (change.pressed) updateHue(change.position)
                                        change.consume()
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                ) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red,
                            ),
                        ),
                    )
                    val markerRadius = 10.dp.toPx()
                    val markerCenter = Offset(
                        x = (hue / 360f * size.width).coerceIn(markerRadius, size.width - markerRadius),
                        y = size.height / 2f,
                    )
                    drawCircle(Color.Black, radius = markerRadius, center = markerCenter)
                    drawCircle(Color.White, radius = 8.dp.toPx(), center = markerCenter)
                    drawCircle(hueColor, radius = 5.dp.toPx(), center = markerCenter)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .background(selectedComposeColor, MaterialTheme.shapes.medium)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                            .semantics { contentDescription = "Vista previa del color" },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("RGB: $red, $green, $blue", fontWeight = FontWeight.SemiBold)
                        Text("HEX: #${selectedColor.toUInt().toString(16).takeLast(6).uppercase()}")
                    }
                }

                Text("Colores comunes", fontWeight = FontWeight.SemiBold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().testTag("common-colors"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 5,
                ) {
                    CommonColorPresets.forEach { preset ->
                        val selected = selectedColor == preset.argb
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        val hsv = FloatArray(3).also {
                                            android.graphics.Color.colorToHSV(preset.argb, it)
                                        }
                                        hue = hsv[0]
                                        saturation = hsv[1]
                                        brightness = hsv[2]
                                    },
                                )
                                .semantics {
                                    contentDescription = "Color común ${preset.name}"
                                    stateDescription = if (selected) "Elegido" else "Disponible"
                                }
                                .testTag("common-color-${preset.name.lowercase()}")
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .background(Color(preset.argb), MaterialTheme.shapes.small),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedColor) }) { Text("Usar color") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

internal data class CommonColorPreset(val name: String, val argb: Int)

internal val CommonColorPresets: List<CommonColorPreset> = listOf(
    CommonColorPreset("Rojo", 0xFFE53935.toInt()),
    CommonColorPreset("Naranja", 0xFFFB8C00.toInt()),
    CommonColorPreset("Amarillo", 0xFFFDD835.toInt()),
    CommonColorPreset("Verde", 0xFF43A047.toInt()),
    CommonColorPreset("Turquesa", 0xFF00897B.toInt()),
    CommonColorPreset("Celeste", 0xFF00ACC1.toInt()),
    CommonColorPreset("Azul", 0xFF1E88E5.toInt()),
    CommonColorPreset("Violeta", 0xFF5C4DFF.toInt()),
    CommonColorPreset("Morado", 0xFF8E24AA.toInt()),
    CommonColorPreset("Rosa", 0xFFD81B60.toInt()),
)
