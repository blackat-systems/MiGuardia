package com.blackatsystems.miguardia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.em
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
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

/** Style v2 (8/11/2026): buttons share the asymmetric corner of cards, smaller. */
val MiGuardiaButtonShape: Shape = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 6.dp,
    bottomEnd = 18.dp,
    bottomStart = 6.dp,
)

/** Hairline that frames cards in both themes (style v2). */
@Composable
internal fun cardBorder(): BorderStroke? =
    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

/**
 * Style v2 card surface: a soft vertical gradient in the dark theme and a white card with a
 * gentle, tinted shadow in the light theme. The Card itself is transparent on top of this.
 */
@Composable
internal fun Modifier.vigiliaCardSurface(shape: Shape, base: Color): Modifier {
    val colors = MaterialTheme.vigiliaColors
    return if (colors.isDark) {
        background(Brush.verticalGradient(listOf(colors.cardTop, base)), shape)
    } else {
        shadow(6.dp, shape, ambientColor = LightShadow, spotColor = LightShadow).background(base, shape)
    }
}

private val LightShadow = Color(0x331A1E33)

/**
 * Style v2 screen background: the page color with a soft radial glow at the top end, like the
 * approved mockup. Purely decorative; nothing is drawn over content.
 */
@Composable
fun Modifier.vigiliaScreenBackground(): Modifier {
    val colors = MaterialTheme.vigiliaColors
    val page = MaterialTheme.colorScheme.background
    return background(page).drawBehind {
        val radius = 320.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.glow, colors.glowSecondary, Color.Transparent),
                center = Offset(size.width, 0f),
                radius = radius,
            ),
            radius = radius,
            center = Offset(size.width, 0f),
        )
    }
}

/** Colors for a tonal destructive button (style v2): calm surface, clearly red text. */
@Composable
fun destructiveButtonColors() = ButtonDefaults.filledTonalButtonColors(
    containerColor = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.error,
)

@Composable
fun DateBadge(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.vigiliaColors.dateContainer,
        contentColor = MaterialTheme.vigiliaColors.onDateContainer,
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun StatusBadge(label: String, containerColor: Color, contentColor: Color,
    modifier: Modifier = Modifier, symbol: String? = null) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val labelWidth = measurer.measure(label, style = style, softWrap = false).size.width
        val padding = if (labelWidth + with(density) { 16.dp.roundToPx() } <= constraints.maxWidth) 8.dp else 0.dp
        val symbolWidth = symbol?.let { measurer.measure(it, style = style).size.width } ?: 0
        val showSymbol = symbol != null && labelWidth + symbolWidth +
            with(density) { (padding * 2 + 6.dp).roundToPx() } <= constraints.maxWidth
        Surface(shape = MaterialTheme.shapes.extraSmall, color = containerColor, contentColor = contentColor) {
            Row(Modifier.padding(horizontal = padding, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showSymbol) Text(requireNotNull(symbol), Modifier.clearAndSetSemantics {}, style = style)
                // The full text path keeps painted and accessibility layout results identical.
                Text(AnnotatedString(label), style = style, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
fun ScreenHeading(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    eyebrow: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.extraSmall),
    ) {
        eyebrow?.let { Eyebrow(it) }
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
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
    navigationEnabled: Boolean = true,
    navigationTestTag: String? = null,
) {
    val navigationModifier = if (navigationTestTag != null) Modifier.testTag(navigationTestTag) else Modifier
    val titleStyle = MaterialTheme.typography.titleLarge
    val actionStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().padding(
            horizontal = MiGuardiaSpacing.large,
            vertical = MiGuardiaSpacing.small,
        ),
    ) {
        // Large Android text with the wider style v2 font can leave no room for a whole word
        // next to the action (seen: "Disponibilid/ad" at 200%). Then the action goes on top
        // and the title takes the full width, breaking only between words.
        val longestWord = title.split(' ').maxByOrNull { it.length }.orEmpty()
        val wordWidth = measurer.measure(longestWord, titleStyle, softWrap = false).size.width
        val actionWidth = measurer.measure(navigationLabel, actionStyle, softWrap = false).size.width +
            with(density) { (24.dp + MiGuardiaSpacing.small).roundToPx() }
        val fitsBeside = wordWidth + actionWidth <= constraints.maxWidth
        if (fitsBeside) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = titleStyle,
                )
                TextButton(onClick = onNavigation, enabled = navigationEnabled, modifier = navigationModifier) {
                    Text(navigationLabel)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onNavigation,
                    enabled = navigationEnabled,
                    modifier = navigationModifier.align(Alignment.End),
                ) {
                    Text(navigationLabel)
                }
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                    style = titleStyle,
                )
            }
        }
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
    previousTestTag: String? = null,
    nextTestTag: String? = null,
) {
    val shape = MaterialTheme.shapes.large
    Card(
        modifier = modifier.fillMaxWidth().vigiliaCardSurface(shape, MaterialTheme.colorScheme.surfaceContainer),
        shape = shape,
        border = cardBorder(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
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
                modifier = Modifier.semantics { contentDescription = previousDescription }
                    .let { if (previousTestTag != null) it.testTag(previousTestTag) else it },
            ) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
            Text(
                monthLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            TextButton(
                onClick = onNext,
                modifier = Modifier.semantics { contentDescription = nextDescription }
                    .let { if (nextTestTag != null) it.testTag(nextTestTag) else it },
            ) { Text("›", style = MaterialTheme.typography.headlineSmall) }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    eyebrow: String? = null,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Card(
        modifier = modifier.fillMaxWidth().vigiliaCardSurface(shape, MaterialTheme.colorScheme.surfaceContainer),
        shape = shape,
        border = cardBorder(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            modifier = Modifier.padding(MiGuardiaSpacing.large),
            verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
        ) {
            eyebrow?.let { Eyebrow(it) }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            supportingText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(MiGuardiaSpacing.extraSmall))
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
    val shape = MaterialTheme.shapes.extraLarge
    val background = Brush.linearGradient(
        listOf(colors.surfaceHero, colors.heroEnd, MaterialTheme.colorScheme.surfaceContainer),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(
                horizontal = if (compact) MiGuardiaSpacing.large else MiGuardiaSpacing.extraLarge,
                vertical = if (compact) MiGuardiaSpacing.small else MiGuardiaSpacing.extraLarge,
            ),
        verticalArrangement = Arrangement.spacedBy(
            if (compact) MiGuardiaSpacing.extraSmall else MiGuardiaSpacing.small,
        ),
    ) {
        Eyebrow(title)
        // The hero draws its own background, so it also sets its own text color instead of
        // inheriting whatever the parent provides.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
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
    val shape = MaterialTheme.shapes.large
    Card(
        modifier = modifier.fillMaxWidth().vigiliaCardSurface(shape, MaterialTheme.colorScheme.surfaceContainer),
        shape = shape,
        border = cardBorder(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MiGuardiaSpacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                PrimaryAction(label = actionLabel, onClick = onAction)
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
                    OutlinedButton(onClick = it, shape = MiGuardiaButtonShape) { Text(stringResource(R.string.retry)) }
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
    val active = enabled && !working
    val brush = Brush.horizontalGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.vigiliaColors.actionEnd),
    )
    Button(
        onClick = onClick,
        enabled = active,
        shape = MiGuardiaButtonShape,
        // Enabled: the gradient sits behind a transparent container. Disabled keeps Material's
        // neutral disabled colors so the state stays obvious.
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (active) Modifier.background(brush, MiGuardiaButtonShape) else Modifier),
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
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = MiGuardiaButtonShape,
        colors = destructiveButtonColors(),
    ) {
        Text(label)
    }
}

/** Secondary action (style v2): quiet card-colored button with a hairline. */
@Composable
fun SecondaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = MiGuardiaButtonShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(label)
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
            .vigiliaCardSurface(MaterialTheme.shapes.large, MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        border = cardBorder(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
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
                Text(title, style = MaterialTheme.typography.titleMedium)
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
            Text(title, style = MaterialTheme.typography.titleMedium)
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

/** Small violet label above a title (style v2). Same text for TalkBack; sentence case. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.06.em,
        color = MaterialTheme.vigiliaColors.active,
    )
}
