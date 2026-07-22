package com.brewlog.android

import android.content.Context
import com.brewlog.android.engine.Badge
import com.brewlog.android.engine.BadgeCatalog
import com.brewlog.android.engine.BadgeEngine
import com.brewlog.android.engine.DayLedger
import com.brewlog.android.engine.EducationCard
import com.brewlog.android.engine.EducationLibrary
import com.brewlog.android.engine.EncouragementEngine
import com.brewlog.android.engine.HealthMilestone
import com.brewlog.android.engine.HealthTimeline
import com.brewlog.android.engine.HighRiskSupport
import com.brewlog.android.engine.MetricsEngine
import com.brewlog.android.engine.SavingsEngine
import com.brewlog.android.engine.StreakEngine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Android-side orchestrator: loads prefs and entries, runs the engines,
 * persists newly earned badges / consumed shields, and hands each screen a
 * single immutable snapshot to bind.
 */
class GamificationManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = AppPrefs(appContext)
    private val repo = EntryRepository()

    enum class DayDotState { AF, UNDER_GOAL, OVER_GOAL, FUTURE }

    data class DayDot(val date: LocalDate, val state: DayDotState, val isToday: Boolean)

    data class HomeState(
        val metrics: MetricsEngine.Result,
        val streaks: StreakEngine.Result,
        val isTodayAf: Boolean,
        /** Growth of the tree currently in the ring, 0..1 over 30 AF days (today counts). */
        val treeProgress: Float,
        /** Fully grown trees banked in the forest. */
        val treesCollected: Int,
        val encouragement: String,
        val moneySaved: Double,
        val moneyAvailable: Boolean,
        val caloriesSaved: Double,
        val nextBadge: Badge?,
        val nextBadgeHint: String?,
        /** Most recent health-recovery stage reached (today's AF day counts), or null on day zero. */
        val currentHealthStage: HealthMilestone?,
        val weekDots: List<DayDot>,
        val drinkSizeMl: Double,
        /** Craving-time support message, non-null when now is in the high-risk window. */
        val cravingSupport: String?,
        /** Badges earned but not yet celebrated with the bottom sheet. */
        val uncelebrated: List<Badge>
    )

    data class TimelineEntry(
        val milestone: HealthMilestone,
        val reachedDate: LocalDate?,
        val isNext: Boolean
    )

    data class BadgeState(
        val badge: Badge,
        val earnedDate: String?,
        val progressHint: String
    )

    data class JourneyState(
        val totalAfDays: Int,
        val displayStreak: Int,
        val bestStreak: Int,
        val shieldsHeld: Int,
        val treeProgress: Float,
        val treesCollected: Int,
        val treeDaysGrown: Int,
        val treeDaysNeeded: Int,
        val timeline: List<TimelineEntry>,
        val badges: List<BadgeState>,
        val moneySaved: Double,
        val moneySpent: Double,
        val moneyAvailable: Boolean,
        val caloriesSaved: Double,
        val burgersEquivalent: Int,
        val educationCards: List<EducationCard>
    )

    fun todayEffective(): LocalDate {
        val now = LocalDateTime.now()
        return if (now.hour < prefs.endOfDayHour) now.toLocalDate().minusDays(1) else now.toLocalDate()
    }

    private fun weekStartDay(): DayOfWeek = DayOfWeek.of(prefs.startOfWeek.coerceIn(1, 7))

    private data class Computed(
        val ledger: DayLedger,
        val entries: List<BeerEntry>,
        val metrics: MetricsEngine.Result,
        val streaks: StreakEngine.Result,
        val savings: SavingsEngine.Result
    )

    private fun compute(): Computed {
        val today = todayEffective()
        val fetchStart = minOf(prefs.baselineSetDate ?: today, today.minusDays(365))
        val entries = repo.getEntries(fetchStart, today)
        val earliestEntry = entries.mapNotNull {
            try { LocalDate.parse(it.date) } catch (_: Exception) { null }
        }.minOrNull()
        val trackingStart = prefs.baselineSetDate ?: earliestEntry ?: today

        val ledger = DayLedger(entries, trackingStart, today, weekStartDay())
        val metrics = MetricsEngine.compute(ledger, prefs.goalDailyMl, prefs.goalWeeklyMl, prefs.baselineDailyMl)
        val alreadyBridged = prefs.shieldBridgedDates.mapNotNull {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        }.toSet()
        val streaks = StreakEngine.compute(ledger, metrics.effectiveWeeklyGoalMl, alreadyBridged)
        if (streaks.newlyBridgedDates.isNotEmpty()) {
            prefs.shieldBridgedDates = prefs.shieldBridgedDates + streaks.newlyBridgedDates.map { it.toString() }
        }
        val presets = DrinkPresetStore.getPresets(prefs.prefs)
        val favorite = presets.firstOrNull { it.favorite } ?: presets.firstOrNull()
        val drinkSize = (favorite?.volume ?: prefs.defaultDrinkSizeMl).toDouble()
        val savings = SavingsEngine.compute(
            entries = entries,
            ledger = ledger,
            baselineDailyMl = prefs.baselineDailyMl,
            defaultAbv = prefs.defaultDrinkStrength.toDouble(),
            drinkSizeMl = drinkSize,
            pricePerDrink = prefs.pricePerDrink.toDouble(),
            presetCosts = presets.map {
                SavingsEngine.DrinkCost(it.name, it.volume.toDouble(), it.cost.toDouble())
            },
            // The baseline is denominated in the favorite drink, so use its cost when set
            baselineCostPerDrink = favorite?.cost?.takeIf { it > 0 }?.toDouble()
                ?: prefs.pricePerDrink.toDouble(),
            // Preferred when set: what the user says they used to spend per week
            baselineWeeklySpend = prefs.baselineWeeklySpend.toDouble()
        )
        return Computed(ledger, entries, metrics, streaks, savings)
    }

    private fun badgeInputs(c: Computed): BadgeEngine.Inputs = BadgeEngine.Inputs(
        hasAnyEntry = c.entries.isNotEmpty(),
        totalAfDays = c.streaks.totalAfDays,
        bestStreak = c.streaks.bestStreak,
        totalWeeksUnderGoal = c.streaks.totalWeeksUnderGoal,
        bestConsecutiveWeeksUnderGoal = c.streaks.bestConsecutiveWeeksUnderGoal,
        moneySaved = c.savings.moneySaved
    )

    /** Evaluates badges, persists new ones, and returns those not yet celebrated. */
    private fun refreshBadges(c: Computed): List<Badge> {
        val inputs = badgeInputs(c)
        val earned = prefs.badgesEarned
        val newly = BadgeEngine.evaluate(inputs, earned.keys)
        if (newly.isNotEmpty()) {
            prefs.badgesEarned = earned + newly.associate { it.id to c.ledger.todayEffective.toString() }
        }
        val celebrated = prefs.celebratedMilestones
        return (earned.keys + newly.map { it.id })
            .filter { it !in celebrated }
            .mapNotNull { BadgeCatalog.byId(it) }
    }

    fun markCelebrated(badgeId: String) {
        prefs.celebratedMilestones = prefs.celebratedMilestones + badgeId
    }

    fun homeState(): HomeState {
        val c = compute()
        val ledger = c.ledger
        val streaks = c.streaks
        val isTodayAf = ledger.isTodayAfSoFar

        val inputs = badgeInputs(c)
        val earnedIds = prefs.badgesEarned.keys
        // Nearest unearned cumulative-AF badge drives the "next milestone" teaser.
        val nextBadge = BadgeCatalog.all
            .filter { it.id !in earnedIds && !BadgeEngine.isEarned(it, inputs) }
            .minByOrNull { badge ->
                val remaining = badge.threshold - BadgeEngine.currentValue(badge, inputs)
                remaining.coerceAtLeast(0)
            }
        val daysToNextBadge = nextBadge
            ?.takeIf { it.kind == com.brewlog.android.engine.BadgeKind.AF_TOTAL || it.kind == com.brewlog.android.engine.BadgeKind.STREAK }
            ?.let { it.threshold - BadgeEngine.currentValue(it, inputs) }

        val yesterdayOverGoal = c.metrics.effectiveDailyGoalMl > 0 &&
            ledger.totalFor(ledger.todayEffective.minusDays(1)) > c.metrics.effectiveDailyGoalMl
        val state = EncouragementEngine.state(
            isTodayAfSoFar = isTodayAf,
            todayMl = c.metrics.todayMl,
            effectiveDailyGoalMl = c.metrics.effectiveDailyGoalMl,
            yesterdayOverGoal = yesterdayOverGoal,
            daysToNextBadge = daysToNextBadge
        )
        val encouragement = EncouragementEngine.message(state, ledger.todayEffective)

        val weekStart = ledger.weekStartOf(ledger.todayEffective)
        val weekDots = (0..6).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val dotState = when {
                date > ledger.todayEffective -> DayDotState.FUTURE
                ledger.totalFor(date) == 0.0 -> DayDotState.AF
                c.metrics.effectiveDailyGoalMl > 0 && ledger.totalFor(date) > c.metrics.effectiveDailyGoalMl -> DayDotState.OVER_GOAL
                else -> DayDotState.UNDER_GOAL
            }
            DayDot(date, dotState, date == ledger.todayEffective)
        }

        val drinkSize = (DrinkPresetStore.defaultPreset(prefs.prefs)?.volume ?: prefs.defaultDrinkSizeMl).toDouble()

        // Craving-time support: only while inside the user's high-risk window
        val cravingSupport = if (prefs.highRiskEnabled) {
            val nowT = java.time.LocalTime.now()
            val nowMin = nowT.hour * 60 + nowT.minute
            val startMin = prefs.highRiskHour * 60 + prefs.highRiskMinute
            if (HighRiskSupport.isInWindow(nowMin, startMin)) {
                val daysSinceStart = java.time.temporal.ChronoUnit.DAYS
                    .between(ledger.trackingStart, ledger.todayEffective).toInt().coerceAtLeast(0)
                HighRiskSupport.message(HighRiskSupport.intensity(daysSinceStart), ledger.todayEffective)
            } else null
        } else null

        // Today's provisional AF day counts toward growth so the plant moves the same day
        val displayAfDays = streaks.totalAfDays + if (isTodayAf) 1 else 0

        return HomeState(
            metrics = c.metrics,
            streaks = streaks,
            isTodayAf = isTodayAf,
            treeProgress = StreakEngine.treeProgress(displayAfDays),
            treesCollected = StreakEngine.treesCollected(displayAfDays),
            encouragement = encouragement,
            moneySaved = c.savings.moneySaved,
            moneyAvailable = c.savings.moneyAvailable,
            caloriesSaved = c.savings.caloriesSaved,
            nextBadge = nextBadge,
            nextBadgeHint = nextBadge?.let { BadgeEngine.progressHint(it, inputs) },
            currentHealthStage = HealthTimeline.current(displayAfDays),
            weekDots = weekDots,
            drinkSizeMl = drinkSize,
            cravingSupport = cravingSupport,
            uncelebrated = treeCelebrations(displayAfDays) + refreshBadges(c)
        )
    }

    /**
     * A completed tree is a milestone of its own: synthesise a one-off badge
     * per tree so the celebration sheet fires once, deduped through the same
     * milestones_celebrated set as catalog badges.
     */
    private fun treeCelebrations(displayAfDays: Int): List<Badge> {
        val trees = StreakEngine.treesCollected(displayAfDays)
        if (trees <= 0) return emptyList()
        val celebrated = prefs.celebratedMilestones
        return (1..trees).filter { "tree_$it" !in celebrated }.map { n ->
            val cost = StreakEngine.treeCost(n - 1)
            val big = StreakEngine.isBigTree(n - 1)
            Badge(
                id = "tree_$n",
                title = when {
                    n == 1 -> "Your first tree!"
                    big && !StreakEngine.isBigTree(n - 2) -> "Your first month tree!"
                    big -> "A mighty month tree joins your forest"
                    else -> "Tree #$n joins your forest"
                },
                description = if (big) {
                    "A full month of alcohol-free days grew a big tree - the tall kind your forest is built around. A fresh seed is already in the ring."
                } else {
                    "$cost alcohol-free days grew a tree for your forest. Keep going - a new seed is already planted."
                },
                kind = com.brewlog.android.engine.BadgeKind.AF_TOTAL,
                threshold = StreakEngine.treeCompletionDay(n)
            )
        }
    }

    fun journeyState(): JourneyState {
        val c = compute()
        refreshBadges(c)
        val inputs = badgeInputs(c)
        val earned = prefs.badgesEarned
        val totalAf = c.streaks.totalAfDays

        val nextMilestone = HealthTimeline.next(totalAf)
        val timeline = HealthTimeline.milestones.map { m ->
            TimelineEntry(
                milestone = m,
                reachedDate = if (totalAf >= m.afDays) HealthTimeline.dateReached(c.ledger, m.afDays) else null,
                isNext = m.id == nextMilestone?.id
            )
        }

        val badges = BadgeCatalog.all.map { badge ->
            BadgeState(
                badge = badge,
                earnedDate = earned[badge.id],
                progressHint = BadgeEngine.progressHint(badge, inputs)
            )
        }

        val displayAfDays = totalAf + if (c.ledger.isTodayAfSoFar) 1 else 0
        return JourneyState(
            totalAfDays = totalAf,
            displayStreak = c.streaks.displayStreak,
            bestStreak = c.streaks.bestStreak,
            shieldsHeld = c.streaks.shieldsHeld,
            treeProgress = StreakEngine.treeProgress(displayAfDays),
            treesCollected = StreakEngine.treesCollected(displayAfDays),
            treeDaysGrown = StreakEngine.treeDaysGrown(displayAfDays),
            treeDaysNeeded = StreakEngine.treeDaysNeeded(displayAfDays),
            timeline = timeline,
            badges = badges,
            moneySaved = c.savings.moneySaved,
            moneySpent = c.savings.moneySpent,
            moneyAvailable = c.savings.moneyAvailable,
            caloriesSaved = c.savings.caloriesSaved,
            burgersEquivalent = c.savings.burgersEquivalent,
            educationCards = EducationLibrary.orderedFor(prefs.motivations)
        )
    }
}
