package com.blackatsystems.miguardia.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.core.domain.AppDefaults
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val SpanishArgentina = Locale.forLanguageTag("es-AR")

private val YearMonthSaver = Saver<YearMonth, String>(
    save = { month -> month.toString() },
    restore = { value -> YearMonth.parse(value) },
)

private enum class MainDestination(
    @param:StringRes val labelRes: Int,
    val glyph: String,
) {
    CALENDAR(R.string.calendar, "C"),
    SUMMARY(R.string.summary, "R"),
    SETTINGS(R.string.settings, "A"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiGuardiaApp(
    modifier: Modifier = Modifier,
    initialMonth: YearMonth = YearMonth.now(AppDefaults.zoneId()),
) {
    var destination by rememberSaveable { mutableStateOf(MainDestination.CALENDAR) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
        bottomBar = {
            NavigationBar {
                MainDestination.entries.forEach { item ->
                    val label = stringResource(item.labelRes)
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = {
                            Text(
                                text = item.glyph,
                                modifier = Modifier.clearAndSetSemantics {},
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (destination) {
            MainDestination.CALENDAR -> CalendarScreen(
                initialMonth = initialMonth,
                contentPadding = innerPadding,
            )

            MainDestination.SUMMARY -> PlaceholderScreen(
                title = stringResource(R.string.summary),
                body = stringResource(R.string.summary_empty),
                contentPadding = innerPadding,
            )

            MainDestination.SETTINGS -> PlaceholderScreen(
                title = stringResource(R.string.settings),
                body = stringResource(R.string.settings_intro),
                contentPadding = innerPadding,
            )
        }
    }
}

@Composable
private fun CalendarScreen(
    initialMonth: YearMonth,
    contentPadding: PaddingValues,
) {
    var visibleMonth by rememberSaveable(stateSaver = YearMonthSaver) {
        mutableStateOf(initialMonth)
    }
    val currentDate = LocalDate.now(AppDefaults.zoneId())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NextGuardCard()

        MonthControls(
            visibleMonth = visibleMonth,
            onPrevious = { visibleMonth = visibleMonth.minusMonths(1) },
            onNext = { visibleMonth = visibleMonth.plusMonths(1) },
            onToday = { visibleMonth = YearMonth.from(currentDate) },
        )

        WeekdayHeader()
        MonthGrid(
            month = visibleMonth,
            today = currentDate,
        )

        Button(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.add))
        }
    }
}

@Composable
private fun NextGuardCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.next_guard),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.no_guard_loaded),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MonthControls(
    visibleMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val previousDescription = stringResource(R.string.previous_month)
    val nextDescription = stringResource(R.string.next_month)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier.semantics { contentDescription = previousDescription },
        ) {
            Text(
                text = "‹",
                modifier = Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = visibleMonth.displayName(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onToday) {
                Text(stringResource(R.string.today))
            }
        }

        IconButton(
            onClick = onNext,
            modifier = Modifier.semantics { contentDescription = nextDescription },
        ) {
            Text(
                text = "›",
                modifier = Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val labels = listOf(
        R.string.monday_short to R.string.monday,
        R.string.tuesday_short to R.string.tuesday,
        R.string.wednesday_short to R.string.wednesday,
        R.string.thursday_short to R.string.thursday,
        R.string.friday_short to R.string.friday,
        R.string.saturday_short to R.string.saturday,
        R.string.sunday_short to R.string.sunday,
    )

    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEach { (shortLabel, fullLabel) ->
            val fullDayName = stringResource(fullLabel)
            Text(
                text = stringResource(shortLabel),
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = fullDayName },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
) {
    val firstDay = month.atDay(1)
    val offset = firstDay.dayOfWeek.value - 1
    val cells = List(42) { index ->
        val dayNumber = index - offset + 1
        dayNumber.takeIf { it in 1..month.lengthOfMonth() }
    }
    val monthName = month.month.getDisplayName(TextStyle.FULL, SpanishArgentina)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { dayNumber ->
                    if (dayNumber == null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                        )
                    } else {
                        val date = month.atDay(dayNumber)
                        val dayDescription = stringResource(
                            if (date == today) {
                                R.string.today_undefined_day_description
                            } else {
                                R.string.undefined_day_description
                            },
                            dayNumber,
                            monthName,
                        )
                        val background = if (date == today) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .heightIn(min = 52.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(background)
                                .clearAndSetSemantics { contentDescription = dayDescription }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = dayNumber.toString(),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = "?",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    body: String,
    contentPadding: PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun YearMonth.displayName(): String {
    val monthName = month.getDisplayName(TextStyle.FULL, SpanishArgentina)
        .replaceFirstChar { it.titlecase(SpanishArgentina) }
    return "$monthName de $year"
}
