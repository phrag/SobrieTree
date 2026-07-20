package com.brewlog.android.engine

import java.time.LocalDate

/**
 * Streak and alcohol-free-day arithmetic.
 *
 * Design notes (supportive gamification):
 * - [Result.totalAfDays] is cumulative and never resets; it is celebrated as
 *   loudly as any streak so a lapse never wipes out visible progress.
 * - Streak shields: one is earned per 7 total AF days (max 3 held). A single
 *   lapse day sandwiched between AF days is bridged by consuming a shield, so
 *   one off night does not end a streak. Two consecutive lapse days do.
 * - Bridged lapse dates are persisted by the caller so re-computation never
 *   consumes a second shield for the same date.
 */
object StreakEngine {

    const val AF_DAYS_PER_SHIELD = 7
    const val MAX_SHIELDS_HELD = 3

    data class Result(
        val totalAfDays: Int,
        /** Consecutive AF completed days ending yesterday (with bridges applied). */
        val currentStreak: Int,
        /** currentStreak + 1 when today has no alcohol logged yet. */
        val displayStreak: Int,
        val bestStreak: Int,
        /** Completed AF days in the week containing todayEffective. */
        val afDaysThisWeek: Int,
        /** Complete weeks at or under the weekly goal, counting back from last week. */
        val consecutiveWeeksUnderGoal: Int,
        /** All complete weeks at or under the weekly goal since tracking start. */
        val totalWeeksUnderGoal: Int,
        val bestConsecutiveWeeksUnderGoal: Int,
        val shieldsHeld: Int,
        /** Lapse dates bridged for the current streak (already persisted + new). */
        val bridgedDates: List<LocalDate>,
        /** Bridges consumed by this computation; caller must persist them. */
        val newlyBridgedDates: List<LocalDate>
    )

    fun compute(
        ledger: DayLedger,
        weeklyGoalMl: Double,
        alreadyBridged: Set<LocalDate>
    ): Result {
        val totalAfDays = ledger.completedDays.count { ledger.isCompletedAfDay(it) }
        val shieldsEarned = totalAfDays / AF_DAYS_PER_SHIELD
        var shieldsHeld = (shieldsEarned - alreadyBridged.size).coerceIn(0, MAX_SHIELDS_HELD)

        // Walk backward from yesterday, bridging single lapse days with shields.
        var streak = 0
        val bridged = mutableListOf<LocalDate>()
        val newlyBridged = mutableListOf<LocalDate>()
        var day = ledger.todayEffective.minusDays(1)
        while (day >= ledger.trackingStart) {
            if (ledger.isCompletedAfDay(day)) {
                streak++
                day = day.minusDays(1)
                continue
            }
            val prev = day.minusDays(1)
            val prevIsAf = prev >= ledger.trackingStart && ledger.isCompletedAfDay(prev)
            if (!prevIsAf) break
            when {
                day in alreadyBridged -> bridged.add(day)
                shieldsHeld > 0 -> {
                    shieldsHeld--
                    bridged.add(day)
                    newlyBridged.add(day)
                }
                else -> break
            }
            day = prev
        }

        // Raw best run of AF days (no bridging; history of shield use is unknown).
        var best = 0
        var run = 0
        for (d in ledger.completedDays) {
            if (ledger.isCompletedAfDay(d)) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
        }
        best = maxOf(best, streak)

        val weekStart = ledger.weekStartOf(ledger.todayEffective)
        var afThisWeek = 0
        var d = weekStart
        while (d < ledger.todayEffective) {
            if (ledger.isCompletedAfDay(d)) afThisWeek++
            d = d.plusDays(1)
        }

        // Complete weeks, oldest to newest, that fall entirely inside the tracking window.
        val weekTotals = mutableListOf<Double>()
        var ws = ledger.weekStartOf(ledger.trackingStart)
        if (ws < ledger.trackingStart) ws = ws.plusDays(7)
        while (ws.plusDays(6) < ledger.todayEffective) {
            weekTotals.add(ledger.totalForRange(ws, ws.plusDays(6)))
            ws = ws.plusDays(7)
        }
        var totalUnder = 0
        var bestConsecutive = 0
        var runningConsecutive = 0
        if (weeklyGoalMl > 0) {
            for (total in weekTotals) {
                if (total <= weeklyGoalMl) {
                    totalUnder++
                    runningConsecutive++
                    if (runningConsecutive > bestConsecutive) bestConsecutive = runningConsecutive
                } else {
                    runningConsecutive = 0
                }
            }
        }
        val consecutiveFromLastWeek = if (weeklyGoalMl > 0) {
            var count = 0
            for (total in weekTotals.reversed()) {
                if (total <= weeklyGoalMl) count++ else break
            }
            count
        } else 0

        val displayStreak = if (ledger.isTodayAfSoFar) streak + 1 else streak

        return Result(
            totalAfDays = totalAfDays,
            currentStreak = streak,
            displayStreak = displayStreak,
            bestStreak = best,
            afDaysThisWeek = afThisWeek,
            consecutiveWeeksUnderGoal = consecutiveFromLastWeek,
            totalWeeksUnderGoal = totalUnder,
            bestConsecutiveWeeksUnderGoal = bestConsecutive,
            shieldsHeld = shieldsHeld,
            bridgedDates = bridged,
            newlyBridgedDates = newlyBridged
        )
    }

    /** Growth stage for static illustrations: 0 seed, 1 sprout, 2 seedling, 3 sapling, 4 tree. */
    fun growthStage(totalAfDays: Int): Int = when {
        totalAfDays >= 90 -> 4
        totalAfDays >= 30 -> 3
        totalAfDays >= 7 -> 2
        totalAfDays >= 3 -> 1
        else -> 0
    }

    /** Steady-state days per (big) tree once the first-month ramp is done. */
    const val TREE_DAYS = 30

    /** During the first month a tree grows every week: 4 quick weekly trees. */
    const val WEEKLY_TREE_DAYS = 7
    const val WEEKLY_TREE_COUNT = 4

    /** AF days needed to grow the tree at [treeIndex] (0-based). */
    fun treeCost(treeIndex: Int): Int =
        if (treeIndex < WEEKLY_TREE_COUNT) WEEKLY_TREE_DAYS else TREE_DAYS

    /** Month trees (post-ramp) are the big ones in the forest. */
    fun isBigTree(treeIndex: Int): Boolean = treeIndex >= WEEKLY_TREE_COUNT

    /** (trees fully grown, AF days into the current tree). */
    private fun decompose(totalAfDays: Int): Pair<Int, Int> {
        var days = totalAfDays.coerceAtLeast(0)
        var trees = 0
        while (days >= treeCost(trees)) {
            days -= treeCost(trees)
            trees++
        }
        return trees to days
    }

    /** Fully grown trees banked in the forest. */
    fun treesCollected(totalAfDays: Int): Int = decompose(totalAfDays).first

    /** Growth of the tree currently in the ring, 0..1 (a fresh cycle starts at 0). */
    fun treeProgress(totalAfDays: Int): Float {
        val (trees, days) = decompose(totalAfDays)
        return days / treeCost(trees).toFloat()
    }

    /** AF days grown into the current tree. */
    fun treeDaysGrown(totalAfDays: Int): Int = decompose(totalAfDays).second

    /** AF days the current tree needs in total. */
    fun treeDaysNeeded(totalAfDays: Int): Int = treeCost(decompose(totalAfDays).first)

    /** Cumulative AF days at which tree number [n] (1-based) completes. */
    fun treeCompletionDay(n: Int): Int = (0 until n).sumOf { treeCost(it) }
}
