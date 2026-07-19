package com.brewlog.android.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeEngineTest {

    private fun inputs(
        hasAnyEntry: Boolean = true,
        totalAfDays: Int = 0,
        bestStreak: Int = 0,
        totalWeeksUnderGoal: Int = 0,
        bestConsecutiveWeeksUnderGoal: Int = 0,
        moneySaved: Double = 0.0
    ) = BadgeEngine.Inputs(hasAnyEntry, totalAfDays, bestStreak, totalWeeksUnderGoal, bestConsecutiveWeeksUnderGoal, moneySaved)

    @Test
    fun `first log earned by any entry`() {
        val earned = BadgeEngine.evaluate(inputs(hasAnyEntry = true), emptySet())
        assertTrue(earned.any { it.id == "first_log" })
        val none = BadgeEngine.evaluate(inputs(hasAnyEntry = false), emptySet())
        assertTrue(none.none { it.id == "first_log" })
    }

    @Test
    fun `af badges accumulate with thresholds`() {
        val earned = BadgeEngine.evaluate(inputs(totalAfDays = 14), emptySet()).map { it.id }
        assertTrue("first_af" in earned)
        assertTrue("af_3" in earned)
        assertTrue("af_7" in earned)
        assertTrue("af_14" in earned)
        assertTrue("af_30" !in earned)
    }

    @Test
    fun `already earned badges are not re-issued`() {
        val earned = BadgeEngine.evaluate(inputs(totalAfDays = 3), setOf("first_af", "first_log"))
        assertEquals(listOf("af_3"), earned.map { it.id })
    }

    @Test
    fun `week and month under goal use different counters`() {
        // 5 weeks under goal in total but never 4 in a row
        val i = inputs(totalWeeksUnderGoal = 5, bestConsecutiveWeeksUnderGoal = 2)
        val earned = BadgeEngine.evaluate(i, emptySet()).map { it.id }
        assertTrue("week_under_goal" in earned)
        assertTrue("month_under_goal" !in earned)
    }

    @Test
    fun `money badges at rising thresholds`() {
        val earned = BadgeEngine.evaluate(inputs(moneySaved = 120.0), emptySet()).map { it.id }
        assertTrue("saver_50" in earned)
        assertTrue("saver_100" in earned)
        assertTrue("saver_500" !in earned)
    }

    @Test
    fun `progress hint caps at threshold`() {
        val badge = BadgeCatalog.byId("af_7")!!
        assertEquals("5 of 7 AF days", BadgeEngine.progressHint(badge, inputs(totalAfDays = 5)))
        assertEquals("7 of 7 AF days", BadgeEngine.progressHint(badge, inputs(totalAfDays = 12)))
    }

    @Test
    fun `catalog has sixteen unique badges`() {
        assertEquals(16, BadgeCatalog.all.size)
        assertEquals(16, BadgeCatalog.all.map { it.id }.toSet().size)
    }
}
