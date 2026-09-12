package com.blackatsystems.miguardia.ui.summary

import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryEssentials
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryProjection
import com.blackatsystems.miguardia.core.domain.summary.SummaryContribution
import com.blackatsystems.miguardia.core.domain.summary.SummaryContributionKind
import com.blackatsystems.miguardia.core.domain.summary.SummaryMetric
import com.blackatsystems.miguardia.core.domain.summary.SummaryOptionalFamily
import com.blackatsystems.miguardia.core.domain.summary.SummaryOptionalSection
import com.blackatsystems.miguardia.core.domain.summary.SummaryValueUnit
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryStateTest {
    @Test
    fun recoverableFailureKeepsOnlyACacheFromTheRequestedMonth() {
        val cached = projection(MONTH, hasContent = true)
        val sameMonth = reduceSummaryFailure(
            SummaryUiState(MONTH, SummaryLoadState.CONTENT, cached),
            MONTH,
        )

        assertSame(cached, sameMonth.projection)
        assertEquals(SummaryLoadState.CONTENT, sameMonth.loadState)
        assertTrue(sameMonth.errorMessage.orEmpty().contains("Reintentá"))

        val nextMonth = reduceSummaryFailure(
            sameMonth.copy(visibleMonth = MONTH.plusMonths(1)),
            MONTH.plusMonths(1),
        )
        assertNull(nextMonth.projection)
        assertEquals(SummaryLoadState.ERROR, nextMonth.loadState)
        assertTrue(nextMonth.errorMessage.orEmpty().contains("Reintentá"))
    }

    @Test
    fun aChangedProjectionClosesOnlyADetailWhoseMetricDisappeared() {
        val current = SummaryUiState(
            visibleMonth = MONTH,
            loadState = SummaryLoadState.CONTENT,
            projection = projection(MONTH, hasContent = true),
            surface = SummarySurface.DETAIL,
            selectedMetricId = METRIC_ID,
        )
        val withoutMetric = reduceSummaryProjection(
            current,
            MONTH,
            projection(MONTH, hasContent = false),
            SummaryPreferences(introSeen = true),
            introDismissedThisSession = false,
        )

        assertEquals(SummarySurface.OVERVIEW, withoutMetric.surface)
        assertNull(withoutMetric.selectedMetricId)
        assertEquals(SummaryLoadState.EMPTY, withoutMetric.loadState)
    }

    @Test
    fun visibleFamiliesFollowPersistedOrderAndDropHiddenOrEmptyFamilies() {
        val order = listOf(SummaryOptionalFamily.HOLIDAYS, SummaryOptionalFamily.NIGHTS) +
            SummaryOptionalFamily.entries.filterNot {
                it == SummaryOptionalFamily.HOLIDAYS || it == SummaryOptionalFamily.NIGHTS
            }
        val state = SummaryUiState(
            visibleMonth = MONTH,
            loadState = SummaryLoadState.CONTENT,
            projection = projection(MONTH, hasContent = true),
            preferences = SummaryPreferences(
                orderedFamilies = order,
                hiddenFamilies = setOf(SummaryOptionalFamily.NIGHTS),
                introSeen = true,
            ),
        )

        assertEquals(listOf(SummaryOptionalFamily.HOLIDAYS), state.visibleOptionalFamilies())
    }

    private fun projection(month: YearMonth, hasContent: Boolean): MonthlySummaryProjection {
        val contribution = SummaryContribution(
            id = "row",
            sourceId = "source",
            ownerLocalDate = month.atDay(1),
            start = Instant.parse("2026-08-01T08:00:00Z"),
            end = Instant.parse("2026-08-01T09:00:00Z"),
            zoneId = ZoneOffset.UTC,
            value = 60L,
            unit = SummaryValueUnit.MINUTES,
            kind = SummaryContributionKind.REGULAR_WORK,
            sourceLabel = "Fuente ficticia",
        )
        val total = SummaryMetric(METRIC_ID, "Total trabajado", 60L, SummaryValueUnit.MINUTES, listOf(contribution))
        val holiday = SummaryMetric(
            "optional:holidays",
            "Feriados",
            60L,
            SummaryValueUnit.MINUTES,
            listOf(contribution.copy(id = "holiday", kind = SummaryContributionKind.HOLIDAY)),
        )
        return MonthlySummaryProjection(
            month = month,
            essentials = if (hasContent) {
                MonthlySummaryEssentials(total, total.copy(id = "essential:regular"), null, null)
            } else {
                MonthlySummaryEssentials(null, null, null, null)
            },
            compliance = emptyList(),
            availability = null,
            optionalSections = if (hasContent) {
                listOf(SummaryOptionalSection(SummaryOptionalFamily.HOLIDAYS, listOf(holiday)))
            } else {
                emptyList()
            },
            hasContent = hasContent,
        )
    }

    private companion object {
        val MONTH: YearMonth = YearMonth.of(2026, 8)
        const val METRIC_ID = "essential:total"
    }
}
