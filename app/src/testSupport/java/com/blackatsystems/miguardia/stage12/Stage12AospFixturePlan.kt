package com.blackatsystems.miguardia.stage12

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** Explicit synthetic examples, never defaults or a substitute for the cases' oracles. */
internal data class Stage12AospFixturePlan(val anchor: LocalDate) {
    val pastSource: LocalDate = anchor.minusDays(3)
    val futureSource: LocalDate = anchor.plusDays(2)
    val c090EmptyDate: LocalDate = anchor.minusDays(2)
    val sourceDates: Set<LocalDate> = setOf(pastSource, futureSource)
    // Normal manual loading accepts one month per batch; preserve that contract.
    val batches: List<Set<LocalDate>> = sourceDates.sorted().groupBy(YearMonth::from).values.map { it.toSet() }
    val start: LocalTime = LocalTime.of(9, 0)
    val end: LocalTime = LocalTime.of(17, 0)

    fun requireSameDay(today: LocalDate) = check(today == anchor) { "AOSP_FIXTURE_DAY_CHANGED" }

    fun requireExactSources(dates: List<LocalDate>) {
        check(dates.size == 2 && dates.toSet() == sourceDates) { "AOSP_FIXTURE_SOURCE_DATES_DIFFER" }
    }

    companion object {
        /** The fixture's actual finalization path, shared with failure-injection JVM tests. */
        fun finishPreparation(admission: Stage12ActivityAdmission, initial: Throwable?,
            close: () -> Unit, readback: () -> Unit, publish: (Throwable?) -> Unit): Throwable? {
            var primary = initial
            admission.phase(Stage12ActivityAdmission.Phase.CLOSE)
            try { close() } catch (error: Throwable) {
                admission.failed(error);primary = Stage12W010Oracle.retain(primary, error)
            }
            if (primary == null) {
                admission.phase(Stage12ActivityAdmission.Phase.POSTCONDITION)
                try { readback() } catch (error: Throwable) {
                    admission.failed(error);primary = Stage12W010Oracle.retain(primary, error)
                }
            }
            admission.phase(Stage12ActivityAdmission.Phase.PUBLISH)
            try { publish(primary) } catch (error: Throwable) {
                admission.failed(error);primary = Stage12W010Oracle.retain(primary, error)
            }
            admission.finish(primary == null)
            return primary
        }

        const val PLACE_NAME = "Stage12 QA Laboral"
        const val ABBREVIATION = "QAA"
        const val POSITION = "PRIVATE_SYNTHETIC_AOSP_SOURCE"
        fun requireEmptyWork(historyPresent: Boolean, rowsByTable: Map<String, Int>) {
            check(!historyPresent && "shifts" in rowsByTable && "shift_work_snapshots" in rowsByTable) {
                "AOSP_FIXTURE_REQUIRES_UNCONFIGURED_WORK"
            }
            val metadata = setOf("sqlite_master", "sqlite_sequence", "android_metadata", "room_master_table")
            check(rowsByTable.filterKeys { it !in metadata }.values.all { it == 0 }) {
                "AOSP_FIXTURE_RECEIVED_BUSINESS_ROWS_PRESENT"
            }
        }
    }
}
