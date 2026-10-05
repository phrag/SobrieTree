package com.sobrietree.android

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.sobrietree.android.engine.GoalEngine
import com.sobrietree.android.engine.GoalMode
import com.sobrietree.android.engine.StatsEngine
import com.sobrietree.android.engine.UnitsEngine

/**
 * Consumption trend over real logged data. All series are per-day drinks, so
 * the baseline and goal reference lines are per-day values too - the old
 * screen plotted daily points against weekly reference lines.
 */
class ProgressActivity : AppCompatActivity() {

    private val repo by lazy { EntryRepository() }
    private val gamification by lazy { GamificationManager(this) }
    private val trend by lazy {
        TrendChart(this, findViewById(R.id.line_chart), findViewById(R.id.tv_chart_empty), repo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_progress)

        SecureWindow.apply(this)

        setSupportActionBar(findViewById(R.id.toolbar))
        trend.wireChips(window.decorView)

        BottomNavHelper.wire(this, findViewById(R.id.bottom_nav), R.id.nav_progress)
    }

    override fun onResume() {
        super.onResume()
        SecureWindow.apply(this)
        loadData()
    }

    private fun loadData() {
        bindGoal()
        bindStats()
        bindUnits()
        trend.showRange(trend.currentRangeDays)
    }

    /**
     * The active goal, stated in the terms the user chose: a limit for today,
     * or an allowance for the week with the days left to spend it in. This is
     * the same figure the Home ring is drawing, in words.
     */
    private fun bindGoal() {
        val goal = gamification.goalState()
        val ring = goal.ring
        val card = findViewById<View>(R.id.card_goal)
        if (ring.allowanceMl <= 0) {
            // Neither a goal nor a baseline: there is no allowance to report.
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE

        val weekly = ring.mode == GoalMode.WEEKLY
        val drinkSize = goal.drinkSizeMl.takeIf { it > 0 } ?: 500.0
        val consumed = ring.consumedMl / drinkSize
        val allowance = ring.allowanceMl / drinkSize
        val remaining = ring.remainingMl / drinkSize

        findViewById<TextView>(R.id.tv_goal_title).setText(
            if (weekly) R.string.goal_card_title_week else R.string.goal_card_title_day
        )
        findViewById<TextView>(R.id.tv_goal_value).text =
            getString(R.string.goal_card_value, format(consumed), format(allowance))

        val bar = findViewById<LinearProgressIndicator>(R.id.progress_goal)
        bar.progress = (ring.consumedRatio * 100).toInt().coerceIn(0, 100)
        // Over the allowance is information, not a telling-off - amber, never red.
        bar.setIndicatorColor(
            ContextCompat.getColor(
                this,
                if (ring.overGoal) R.color.state_caution else R.color.state_positive
            )
        )

        findViewById<TextView>(R.id.tv_goal_status).text = when {
            ring.overGoal && weekly -> getString(R.string.goal_over_week, format(consumed - allowance))
            ring.overGoal -> getString(R.string.goal_over_day, format(consumed - allowance))
            weekly -> getString(
                R.string.goal_left_week,
                format(remaining),
                ring.daysLeftInWindow,
                dayWord(ring.daysLeftInWindow)
            )
            else -> getString(R.string.goal_left_day, format(remaining))
        }
    }

    /**
     * Every window carries the span it covers. "12 drinks" means nothing until
     * you know whether that is three days or thirty, and a part-finished week
     * next to a whole one flatters the shorter of the two.
     */
    private fun bindStats() {
        val container = findViewById<android.widget.LinearLayout>(R.id.stats_container)
        container.removeAllViews()
        val stats = gamification.statsState()
        val dayFormat = java.time.format.DateTimeFormatter.ofPattern("d MMM")

        for (stat in stats) {
            val row = layoutInflater.inflate(R.layout.item_stat_period, container, false)
            row.findViewById<TextView>(R.id.tv_stat_label).setText(
                when (stat.period) {
                    StatsEngine.Period.THIS_WEEK -> R.string.stats_this_week
                    StatsEngine.Period.LAST_WEEK -> R.string.stats_last_week
                    StatsEngine.Period.THIS_MONTH -> R.string.stats_this_month
                    StatsEngine.Period.LAST_30_DAYS -> R.string.stats_last_30
                    StatsEngine.Period.ALL_TIME -> R.string.stats_all_time
                }
            )
            val window = row.findViewById<TextView>(R.id.tv_stat_window)
            val line = row.findViewById<TextView>(R.id.tv_stat_line)
            val detail = row.findViewById<TextView>(R.id.tv_stat_detail)

            if (stat.days == 0) {
                window.setText(R.string.stats_not_started)
                line.visibility = View.GONE
                detail.visibility = View.GONE
                container.addView(row)
                continue
            }

            window.text = if (stat.days == 1) {
                getString(R.string.stats_window_day, stat.start.format(dayFormat))
            } else {
                getString(
                    R.string.stats_window_days,
                    stat.start.format(dayFormat),
                    stat.end.format(dayFormat),
                    stat.days
                )
            }

            if (!stat.hasData) {
                line.setText(R.string.stats_no_data)
                detail.visibility = View.GONE
                container.addView(row)
                continue
            }

            line.visibility = View.VISIBLE
            line.text = getString(
                R.string.stats_line,
                format(stat.drinks),
                format(stat.units),
                stat.alcoholFreeDays,
                getString(if (stat.alcoholFreeDays == 1) R.string.day_singular else R.string.day_plural)
            )

            detail.visibility = View.VISIBLE
            detail.text = buildString {
                append(getString(R.string.stats_avg, format(stat.avgUnitsPerDay)))
                stat.vsBaselinePct?.let { pct ->
                    append(" · ")
                    append(
                        if (pct >= 0) getString(R.string.stats_vs_baseline_below, format(pct))
                        else getString(R.string.stats_vs_baseline_above, format(-pct))
                    )
                }
            }
            container.addView(row)
        }
    }

    /**
     * The week in UK units next to the 14-unit low-risk guideline. Volume drives
     * the rest of the app; this is the one place the numbers are expressed the
     * way the guidance the app cites is written.
     */
    private fun bindUnits() {
        val u = gamification.unitsState()
        val value = findViewById<TextView>(R.id.tv_units_value)
        val bar = findViewById<LinearProgressIndicator>(R.id.progress_units)
        val status = findViewById<TextView>(R.id.tv_units_status)
        val average = findViewById<TextView>(R.id.tv_units_average)

        if (!u.hasUnitData) {
            value.text = getString(R.string.units_value, format(0.0))
            bar.progress = 0
            status.setText(R.string.units_empty)
            average.visibility = View.GONE
            return
        }

        value.text = getString(R.string.units_value, format(u.unitsThisWeek))
        bar.progress = (u.ratioOfGuideline * 100).toInt().coerceIn(0, 100)

        // Caution, not alarm: over the guideline is information, not a telling-off.
        val overColor = ContextCompat.getColor(this, R.color.state_caution)
        val okColor = ContextCompat.getColor(this, R.color.state_positive)
        bar.setIndicatorColor(if (u.withinGuideline) okColor else overColor)

        status.text = when {
            u.concentratedDrinking -> getString(
                R.string.units_concentrated,
                u.drinkingDaysThisWeek,
                dayWord(u.drinkingDaysThisWeek)
            )
            !u.withinGuideline -> getString(
                R.string.units_over,
                format(u.unitsThisWeek - UnitsEngine.WEEKLY_GUIDELINE_UNITS)
            )
            u.drinkFreeDaysThisWeek > 0 -> getString(
                R.string.units_within_with_dry,
                u.drinkFreeDaysThisWeek,
                dayWord(u.drinkFreeDaysThisWeek)
            )
            else -> getString(R.string.units_within)
        }

        average.visibility = View.VISIBLE
        average.text = getString(R.string.units_average, format(u.avgUnitsPerWeek))
    }

    private fun dayWord(n: Int): String =
        getString(if (n == 1) R.string.day_singular else R.string.day_plural)

    /** Units read better to one decimal, but without a trailing ".0". */
    private fun format(units: Double): String =
        if (units == units.toInt().toDouble()) units.toInt().toString()
        else String.format("%.1f", units)
}
