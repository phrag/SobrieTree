package com.sobrietree.android.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DayBoundaryTest {

    private val d27 = LocalDate.of(2026, 8, 27)
    private val d28 = LocalDate.of(2026, 8, 28)
    private val d29 = LocalDate.of(2026, 8, 29)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))

    @Test
    fun `a late boundary does not brand the whole day as yesterday`() {
        // The reported bug: boundary at 23, 09:59 on the 28th, and the app
        // showed the 27th's drinks under "Today".
        assertEquals(d28, DayBoundary.effectiveDate(at(d28, 9, 59), 23))
    }

    @Test
    fun `a late boundary pushes the evening into tomorrow`() {
        // What someone setting 23 means: past 11pm is tomorrow's problem.
        assertEquals(d28, DayBoundary.effectiveDate(at(d28, 22, 59), 23))
        assertEquals(d29, DayBoundary.effectiveDate(at(d28, 23, 0), 23))
        assertEquals(d29, DayBoundary.effectiveDate(at(d28, 23, 59), 23))
    }

    @Test
    fun `an early boundary keeps last night attached to last night`() {
        // The original purpose: a drink at 1am belongs to the night before.
        assertEquals(d27, DayBoundary.effectiveDate(at(d28, 0, 30), 3))
        assertEquals(d27, DayBoundary.effectiveDate(at(d28, 2, 59), 3))
        assertEquals(d28, DayBoundary.effectiveDate(at(d28, 3, 0), 3))
        assertEquals(d28, DayBoundary.effectiveDate(at(d28, 9, 59), 3))
    }

    @Test
    fun `midnight means no shift at all`() {
        assertEquals(d28, DayBoundary.effectiveDate(at(d28, 0, 0), 0))
        assertEquals(d28, DayBoundary.effectiveDate(at(d28, 23, 59), 0))
    }

    @Test
    fun `every hour of the day maps somewhere sensible`() {
        // Whatever the boundary, the logging day must cover a contiguous 24
        // hours and never leave the calendar date it mostly occupies.
        for (boundary in 0..23) {
            val dates = (0..23).map { DayBoundary.effectiveDate(at(d28, it), boundary) }
            // Only ever the day before, the day itself, or the day after.
            assert(dates.all { it in setOf(d27, d28, d29) }) { "boundary $boundary: $dates" }
            // The 28th is always the majority label for a 24-hour window.
            val onDay = dates.count { it == d28 }
            assert(onDay >= 12) { "boundary $boundary labelled only $onDay hours as the 28th" }
        }
    }

    @Test
    fun `each boundary shifts in exactly one direction`() {
        for (boundary in 0..23) {
            val dates = (0..23).map { DayBoundary.effectiveDate(at(d28, it), boundary) }
            val shiftsBack = dates.any { it == d27 }
            val shiftsForward = dates.any { it == d29 }
            assert(!(shiftsBack && shiftsForward)) { "boundary $boundary shifts both ways" }
        }
    }

    @Test
    fun `out of range values are clamped rather than throwing`() {
        assertEquals(d28, DayBoundary.effectiveDate(at(d28, 9), -5))
        assertEquals(d28, DayBoundary.effectiveDate(at(d28, 9), 99))
    }

    @Test
    fun `the boundary hour itself is the moment the day turns over`() {
        // Early: the new day starts AT the boundary.
        assertEquals(d27, DayBoundary.effectiveDate(at(d28, 4, 59), 5))
        assertEquals(d28, DayBoundary.effectiveDate(at(d28, 5, 0), 5))
        // Late: the next day starts AT the boundary.
        assertEquals(d28, DayBoundary.effectiveDate(at(d28, 19, 59), 20))
        assertEquals(d29, DayBoundary.effectiveDate(at(d28, 20, 0), 20))
    }
}
