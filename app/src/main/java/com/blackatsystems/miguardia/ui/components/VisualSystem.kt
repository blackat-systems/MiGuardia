package com.blackatsystems.miguardia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.ui.theme.vigiliaColors

object MiGuardiaSpacing {
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 24.dp
    val huge = 32.dp
}

@Composable
fun ScreenHeading(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.extraSmall),
    ) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        supportingText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SurfaceHeader(
    title: String,
    navigationLabel: String,
    onNavigation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(
            horizontal = MiGuardiaSpacing.large,
            vertical = MiGuardiaSpacing.small,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        TextButton(onClick = onNavigation) { Text(navigationLabel) }
    }
}

@Composable
fun MonthNavigator(
    monthLabel: String,
    previousDescription: String,
    nextDescription: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = MiGuardiaSpacing.small,
                vertical = MiGuardiaSpacing.extraSmall,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                onClick = onPrevious,
                modifier = Modifier.semantics { contentDescription = previousDescription },
            ) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
            Text(
                monthLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            TextButton(
                onClick = onNext,
                modifier = Modifier.semantics { contentDescription = nextDescription },
            ) { Text("›", style = MaterialTheme.typography.headlineSmall) }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(MiGuardiaSpacing.large),
            verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            supportingText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
fun HeroCard(
    title: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.vigiliaColors
    val shape = MaterialTheme.shapes.large
    val background = if (colors.isDark) {
        Brush.linearGradient(
            listOf(
                colors.surfaceHero,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
            ),
        )
    } else {
        Brush.linearGradient(listOf(colors.surfaceRaised, colors.surfaceRaised))
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(
                width = 1.dp,
                color = if (colors.isDark) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = shape,
            )
            .padding(
                horizontal = if (compact) MiGuardiaSpacing.large else MiGuardiaSpacing.extraLarge,
                vertical = if (compact) MiGuardiaSpacing.small else MiGuardiaSpacing.extraLarge,
            ),
        verticalArrangement = Arrangement.spacedBy(
            if (compact) MiGuardiaSpacing.extraSmall else MiGuardiaSpacing.small,
        ),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (colors.isDark) colors.active else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        content()
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MiGuardiaSpacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun PersistentMessage(
    message: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().semantics {
            liveRegion = LiveRegionMode.Assertive
        },
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(MiGuardiaSpacing.large),
            verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small)) {
                onRetry?.let {
                    OutlinedButton(onClick = it) { Text(stringResource(R.string.retry)) }
                }
                onDismiss?.let {
                    TextButton(onClick = it) { Text("Cerrar") }
                }
            }
        }
    }
}

@Composable
fun PrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    working: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !working,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (working) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.size(MiGuardiaSpacing.small))
        }
        Text(label)
    }
}

@Composable
fun DestructiveAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(label, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun NavigationCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accessibility = "$title. $description"
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = accessibility
                role = Role.Button
            }
            .clickable(onClick = onClick),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(MiGuardiaSpacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.medium),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.extraSmall),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
fun NavigationRow(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accessibility = "$title. $description"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = accessibility
                role = Role.Button
            }
            .clickable(onClick = onClick)
            .padding(vertical = MiGuardiaSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.medium),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.extraSmall),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.vigiliaColors.onSurfaceMuted,
            )
        }
        Text(
            "›",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
