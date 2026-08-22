package com.sobrietree.android.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EncouragementEngineTest {

    @Test
    fun `over goal wins over everything`() {
        val s = EncouragementEngine.state(
            isTodayAfSoFar = false, todayMl = 2000.0, effectiveDailyGoalMl = 1000.0,
            yesterdayOverGoal = true, daysToNextBadge = 1
        )
        assertEquals(EncourageState.OVER_GOAL, s)
    }

    @Test
    fun `clean day after heavy day is lapse recovery`() {
        val s = EncouragementEngine.state(
            isTodayAfSoFar = true, todayMl = 0.0, effectiveDailyGoalMl = 1000.0,
            yesterdayOverGoal = true, daysToNextBadge = null
        )
        assertEquals(EncourageState.LAPSE_RECOVERY, s)
    }

    @Test
    fun `near milestone on a clean day`() {
        val s = EncouragementEngine.state(
            isTodayAfSoFar = true, todayMl = 0.0, effectiveDailyGoalMl = 1000.0,
            yesterdayOverGoal = false, daysToNextBadge = 2
        )
        assertEquals(EncourageState.MILESTONE_NEAR, s)
    }

    @Test
    fun `plain af day`() {
        val s = EncouragementEngine.state(
            isTodayAfSoFar = true, todayMl = 0.0, effectiveDailyGoalMl = 1000.0,
            yesterdayOverGoal = false, daysToNextBadge = 5
        )
        assertEquals(EncourageState.AF_TODAY, s)
    }

    @Test
    fun `at goal near the line, under goal otherwise`() {
        val at = EncouragementEngine.state(false, 950.0, 1000.0, false, null)
        assertEquals(EncourageState.AT_GOAL, at)
        val under = EncouragementEngine.state(false, 400.0, 1000.0, false, null)
        assertEquals(EncourageState.UNDER_GOAL, under)
    }

    @Test
    fun `every state has a pool and rotation is deterministic`() {
        for (state in EncourageState.values()) {
            val pool = EncouragementEngine.pools.getValue(state)
            assertTrue(pool.size >= 6)
            val d = LocalDate.of(2026, 7, 18)
            assertEquals(
                EncouragementEngine.message(state, d),
                EncouragementEngine.message(state, d)
            )
        }
    }

    @Test
    fun `over goal pool never shames`() {
        val banned = listOf("fail", "shame", "bad", "weak", "guilt")
        for (msg in EncouragementEngine.pools.getValue(EncourageState.OVER_GOAL)) {
            for (word in banned) {
                assertTrue("'$msg' contains '$word'", !msg.lowercase().contains(word))
            }
        }
    }
}
