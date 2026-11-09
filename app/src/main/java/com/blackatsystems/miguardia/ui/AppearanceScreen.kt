package com.blackatsystems.miguardia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.ui.components.MiGuardiaSpacing
import com.blackatsystems.miguardia.ui.components.PrimaryAction
import com.blackatsystems.miguardia.ui.components.ScreenHeading
import com.blackatsystems.miguardia.ui.components.SecondaryAction
import com.blackatsystems.miguardia.ui.components.SectionCard
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.ui.theme.vigiliaColors

@Composable
internal fun AppearanceScreen(
    contentPadding: PaddingValues,
    appThemeMode: AppThemeMode,
    onAppThemeModeChange: (AppThemeMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(MiGuardiaSpacing.large),
        verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.large),
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
            Column(verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small)) {
                PrimaryAction(
                    label = if (alternateMode == AppThemeMode.LIGHT) "Usar modo claro" else "Usar modo oscuro",
                    onClick = { onAppThemeModeChange(alternateMode) },
                    modifier = Modifier.testTag("theme-mode-toggle"),
                )
                SecondaryAction(
                    label = "Seguir el sistema",
                    onClick = { onAppThemeModeChange(AppThemeMode.SYSTEM) },
                    enabled = appThemeMode != AppThemeMode.SYSTEM,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("theme-mode-system")
                        .semantics { selected = appThemeMode == AppThemeMode.SYSTEM },
                )
            }
        }
    }
}
