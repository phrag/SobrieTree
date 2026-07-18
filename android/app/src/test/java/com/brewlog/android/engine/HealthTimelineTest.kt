package com.brewlog.android.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HealthTimelineTest {

    private val start = LocalDate.of(2026, 7, 1)

    @Test
    fun `date reached counts af days with lapses in between`() {
        val today = start.plusDays(10)
        // Drink on days 2 and 3 -> AF days are 1,4,5,6,7,8,9,10 (completed up to day 9)
        val drinkDays = listOf(start.plusDays(1), start.plusDays(2))
        val ledger = TestFixtures.ledger(start, today, drinkDays)
        assertEquals(start, HealthTimeline.dateReached(ledger, 1))
        assertEquals(start.plusDays(5), HealthTimeline.dateReached(ledger, 4))
        assertNull(HealthTimeline.dateReached(ledger, 30))
    }

    @Test
    fun `next milestone after current total`() {
        assertEquals(1, HealthTimeline.next(0)?.afDays)
        assertEquals(30, HealthTimeline.next(14)?.afDays)
        assertEquals(180, HealthTimeline.next(90)?.afDays)
        assertNull(HealthTimeline.next(180))
    }

    @Test
    fun `milestones are ordered and unique`() {
        val days = HealthTimeline.milestones.map { it.afDays }
        assertEquals(days.sorted(), days)
        assertEquals(days.toSet().size, days.size)
    }
}
