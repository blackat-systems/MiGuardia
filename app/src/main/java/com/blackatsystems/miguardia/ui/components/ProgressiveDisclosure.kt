package com.blackatsystems.miguardia.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class ContextHelp(
    val title: String,
    val whatItDoes: String,
    val howToUseIt: String,
    val example: String,
)

@Composable
fun AdvancedOptionsSection(
    modifier: Modifier = Modifier,
    help: ContextHelp? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    var helpVisible by rememberSaveable(help?.title) { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val toggle: @Composable (Modifier) -> Unit = { buttonModifier ->
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    modifier = buttonModifier
                        .heightIn(min = 48.dp)
                        .testTag("advanced-options-toggle"),
                ) {
                    Text(
                        if (expanded) "Ocultar opciones avanzadas" else "Opciones avanzadas",
                        textAlign = TextAlign.Center,
                    )
                }
            }
            // Large Android text needs more room. Keep the help action from
            // squeezing the label into fragments; do not shrink the user's text size.
            if (maxWidth < 240.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                    toggle(Modifier.fillMaxWidth())
                    help?.let { ContextHelpAction(it) { helpVisible = true } }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    toggle(Modifier.weight(1f))
                    help?.let { ContextHelpAction(it) { helpVisible = true } }
                }
            }
        }
        if (expanded) content()
    }
    // Keep the open dialog and its saved state outside the changing Row/Column branch.
    if (helpVisible && help != null) ContextHelpDialog(help) { helpVisible = false }
}

@Composable
fun ContextHelpButton(
    help: ContextHelp,
    modifier: Modifier = Modifier,
) {
    var visible by rememberSaveable(help.title) { mutableStateOf(false) }
    ContextHelpAction(help, modifier) { visible = true }
    if (visible) ContextHelpDialog(help) { visible = false }
}

@Composable
private fun ContextHelpAction(help: ContextHelp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = "Ayuda sobre ${help.title}" }
            .testTag("context-help-${help.title.toSafeTag()}"),
    ) {
        Text("(?)", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ContextHelpDialog(help: ContextHelp, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.testTag("context-help-dialog"),
            title = { Text(help.title) },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .testTag("context-help-content"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HelpParagraph("Qué hace", help.whatItDoes)
                    HelpParagraph("Cómo usarlo", help.howToUseIt)
                    HelpParagraph("Ejemplo", help.example)
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Entendido") }
            },
        )
}

@Composable
private fun HelpParagraph(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(body)
    }
}

private fun String.toSafeTag(): String = lowercase()
    .map { character -> if (character.isLetterOrDigit()) character else '-' }
    .joinToString("")
    .replace(Regex("-+"), "-")
    .trim('-')
