package com.treetotal.android.engine

import com.treetotal.android.BeerEntry
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Shared substrate for all engines: per-day alcohol totals plus the
 * alcohol-free-day predicate, honouring the tracking start and the
 * end-of-day cutoff (callers pass the already-shifted [todayEffective]).
 *
 * Totals are kept for every entry supplied; AF-day logic only applies from
 * [trackingStart] onward. Today is provisional and reported separately.
 */
class DayLedger(
    entries: List<BeerEntry>,
    val trackingStart: LocalDate,
    val todayEffective: LocalDate,
    private val weekStartDay: DayOfWeek = DayOfWeek.MONDAY
) {
    /** Drink volume in ml per day (0% ABV entries are ignored). */
    val dailyTotals: Map<LocalDate, Double>

    /**
     * UK units per day - the same drinks measured by their alcohol rather than
     * their volume, so a glass of wine outweighs an equal measure of lager.
     * Volume drives goals and streaks; units are what health guidance is
     * written in, so both are kept.
     */
    val dailyUnits: Map<LocalDate, Double>

    init {
        val map = HashMap<LocalDate, Double>()
        val units = HashMap<LocalDate, Double>()
        for (entry in entries) {
            if (entry.alcoholPercentage <= 0) continue
            val date = try { LocalDate.parse(entry.date) } catch (_: Exception) { continue }
            map[date] = (map[date] ?: 0.0) + entry.volumeMl
            units[date] = (units[date] ?: 0.0) + UnitsEngine.unitsIn(entry.volumeMl, entry.alcoholPercentage)
        }
        dailyTotals = map
        dailyUnits = units
    }

    fun totalFor(date: LocalDate): Double = dailyTotals[date] ?: 0.0

    fun unitsFor(date: LocalDate): Double = dailyUnits[date] ?: 0.0

    /** Days that are fully over: trackingStart until (exclusive) todayEffective. */
    val completedDays: List<LocalDate> by lazy {
        val days = mutableListOf<LocalDate>()
        var d = trackingStart
        while (d < todayEffective) {
            days.add(d)
            d = d.plusDays(1)
        }
        days
    }

    fun isCompletedAfDay(date: LocalDate): Boolean =
        date >= trackingStart && date < todayEffective && totalFor(date) == 0.0

    /** No alcohol logged yet on the current (still open) day. */
    val isTodayAfSoFar: Boolean
        get() = totalFor(todayEffective) == 0.0

    fun weekStartOf(date: LocalDate): LocalDate {
        val diff = (date.dayOfWeek.value - weekStartDay.value + 7) % 7
        return date.minusDays(diff.toLong())
    }

    /** Inclusive total over a date range. */
    fun totalForRange(start: LocalDate, end: LocalDate): Double {
        var sum = 0.0
        var d = start
        while (d <= end) {
            sum += totalFor(d)
            d = d.plusDays(1)
        }
        return sum
    }

    /** Inclusive UK-unit total over a date range. */
    fun unitsForRange(start: LocalDate, end: LocalDate): Double {
        var sum = 0.0
        var d = start
        while (d <= end) {
            sum += unitsFor(d)
            d = d.plusDays(1)
        }
        return sum
    }
}
