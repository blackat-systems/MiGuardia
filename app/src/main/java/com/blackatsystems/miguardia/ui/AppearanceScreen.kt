package com.blackatsystems.miguardia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.ui.components.ScreenHeading
import com.blackatsystems.miguardia.ui.components.SectionCard
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.ui.theme.vigiliaColors

@Composable
internal fun AppearanceScreen(
    contentPadding: PaddingValues,
    appZoom: AppZoom,
    onAppZoomChange: (AppZoom) -> Unit,
    appThemeMode: AppThemeMode,
    onAppThemeModeChange: (AppThemeMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeading("Apariencia", supportingText = stringResource(R.string.appearance_intro))
        SectionCard(
            title = "Tema de MiGuardia",
            supportingText = "Alterná entre claro y oscuro, o dejá que Android elija.",
        ) {
            val alternateMode = if (appThemeMode == AppThemeMode.LIGHT) {
                AppThemeMode.DARK
            } else {
                AppThemeMode.LIGHT
            }
            Text(
                "Tema actual: ${appThemeMode.label}",
                color = MaterialTheme.vigiliaColors.onSurfaceMuted,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAppThemeModeChange(alternateMode) },
                    modifier = Modifier.testTag("theme-mode-toggle"),
                ) {
                    Text(if (alternateMode == AppThemeMode.LIGHT) "Usar modo claro" else "Usar modo oscuro")
                }
                OutlinedButton(
                    onClick = { onAppThemeModeChange(AppThemeMode.SYSTEM) },
                    enabled = appThemeMode != AppThemeMode.SYSTEM,
                    modifier = Modifier
                        .testTag("theme-mode-system")
                        .semantics { selected = appThemeMode == AppThemeMode.SYSTEM },
                ) {
                    Text("Seguir el sistema")
                }
            }
        }
        SectionCard(
            title = "Zoom de MiGuardia",
            supportingText = "Aumenta toda la aplicación sin modificar ajustes de Android.",
        ) {
            AppZoom.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onAppZoomChange(option) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = appZoom == option,
                        onClick = { onAppZoomChange(option) },
                    )
                    Text(option.label, fontWeight = if (appZoom == option) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
