package com.treetotal.android.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DailyScheduleTest {

    private fun hours(ms: Long) = ms / 3_600_000.0

    @Test
    fun `an evening reminder set in the morning fires the same evening`() {
        // The bug this replaces: an 8pm reminder arriving at breakfast.
        val morning = LocalDateTime.of(2026, 8, 20, 9, 0)
        val delay = DailySchedule.delayMillisUntilNext(morning, 20, 0)
        assertEquals(11.0, hours(delay), 0.001)
    }

    @Test
    fun `a time already gone today rolls to tomorrow, not to now`() {
        val lateEvening = LocalDateTime.of(2026, 8, 20, 22, 30)
        val delay = DailySchedule.delayMillisUntilNext(lateEvening, 20, 0)
        assertEquals(21.5, hours(delay), 0.001)
        assertTrue(delay > 0)
    }

    @Test
    fun `firing exactly on the hour queues the next day, never zero`() {
        // Recomputed from inside the worker at its own scheduled moment, so
        // "now == the target" is the normal case, not an edge case. Returning 0
        // would spin: fire, reschedule for now, fire again.
        val onTheHour = LocalDateTime.of(2026, 8, 20, 20, 0)
        val delay = DailySchedule.delayMillisUntilNext(onTheHour, 20, 0)
        assertEquals(24.0, hours(delay), 0.001)
    }

    @Test
    fun `a minute past the target still waits nearly a full day`() {
        val justAfter = LocalDateTime.of(2026, 8, 20, 20, 1)
        val delay = DailySchedule.delayMillisUntilNext(justAfter, 20, 0)
        assertEquals(23.983, hours(delay), 0.01)
    }

    @Test
    fun `the craving nudge lands ahead of the stated time`() {
        val afternoon = LocalDateTime.of(2026, 8, 20, 12, 0)
        val delay = DailySchedule.delayMillisUntilNext(afternoon, 18, 0, leadMinutes = 30)
        assertEquals(5.5, hours(delay), 0.001)   // 17:30, not 18:00
    }

    @Test
    fun `the lead time can push the nudge to the previous day`() {
        // 00:15 with a 30-minute lead means 23:45 the night before.
        val evening = LocalDateTime.of(2026, 8, 20, 20, 0)
        val delay = DailySchedule.delayMillisUntilNext(evening, 0, 15, leadMinutes = 30)
        assertEquals(3.75, hours(delay), 0.001)  // 23:45 tonight
    }

    @Test
    fun `it always lands on the requested wall-clock time`() {
        // Walk a fortnight an hour at a time; every result must land exactly on
        // 20:00. Drift is the failure this whole change exists to prevent.
        var now = LocalDateTime.of(2026, 8, 20, 0, 0)
        repeat(24 * 14) {
            val fire = now.plusNanos(DailySchedule.delayMillisUntilNext(now, 20, 0) * 1_000_000)
            assertEquals(20, fire.hour)
            assertEquals(0, fire.minute)
            now = now.plusHours(1)
        }
    }

    @Test
    fun `out of range values are clamped rather than throwing`() {
        val now = LocalDateTime.of(2026, 8, 20, 12, 0)
        assertTrue(DailySchedule.delayMillisUntilNext(now, 99, 99) > 0)
        assertTrue(DailySchedule.delayMillisUntilNext(now, -5, -5) > 0)
    }

    @Test
    fun `midnight is a valid reminder time`() {
        val beforeMidnight = LocalDateTime.of(2026, 8, 20, 23, 0)
        assertEquals(1.0, hours(DailySchedule.delayMillisUntilNext(beforeMidnight, 0, 0)), 0.001)
    }
}
