package com.brewlog.android

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import androidx.core.content.ContextCompat

class ProgressActivity : AppCompatActivity() {
    
    private fun getWeekStart(today: LocalDate): LocalDate {
        val prefs = getSharedPreferences("brewlog_prefs", MODE_PRIVATE)
        val startOfWeek = prefs.getInt("start_of_week", 1)
        val targetDayOfWeek = java.time.DayOfWeek.of(startOfWeek)
        val daysToSubtract = (today.dayOfWeek.value - targetDayOfWeek.value + 7) % 7
        return today.minusDays(daysToSubtract.toLong())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_progress)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        val brewLog = BrewLogProvider.instance
        val today = brewLog.nowEffectiveDate()
        val weekStart = getWeekStart(today)
        val prefs = getSharedPreferences("brewlog_prefs", MODE_PRIVATE)
        val sizeMl = prefs.getInt("default_beer_size", 500).toDouble().coerceAtLeast(1.0)

        val todayMl = try {
            val v = BrewLogNative.get_daily_consumption(today.toString())
            if (v >= 0) v else brewLog.getDailyConsumption(today)
        } catch (_: Throwable) {
            brewLog.getDailyConsumption(today)
        }
        
        val weekMl = try {
            val v = BrewLogNative.get_weekly_consumption(weekStart.toString())
            if (v >= 0) v else brewLog.getWeeklyConsumption(weekStart)
        } catch (_: Throwable) {
            brewLog.getWeeklyConsumption(weekStart)
        }

        val monthStart = today.minusDays(29)
        val monthMl = try {
            val totals = getDailyTotals(monthStart, today)
            totals.values.sum()
        } catch (_: Throwable) { 0.0 }

        val baselineDaily = prefs.getFloat("baseline_daily_ml", 0f).toDouble()
        val baselineWeekly = baselineDaily * 7.0
        val baselineMonthly = baselineDaily * 30.0
        
        val reductionDaily = if (baselineDaily > 0) ((baselineDaily - todayMl) / baselineDaily) * 100 else 0.0
        val reductionWeekly = if (baselineWeekly > 0) ((baselineWeekly - weekMl) / baselineWeekly) * 100 else 0.0
        val reductionMonthly = if (baselineMonthly > 0) ((baselineMonthly - monthMl) / baselineMonthly) * 100 else 0.0

        // Use accessible colors - blue for positive progress, muted orange for over baseline
        val positiveColor = android.graphics.Color.parseColor("#1976D2") // Blue
        val cautionColor = android.graphics.Color.parseColor("#F57C00") // Orange (not harsh red)
        
        findViewById<android.widget.TextView>(R.id.tv_daily_reduction)?.apply {
            text = "${String.format("%.0f", reductionDaily)}%"
            setTextColor(if (reductionDaily < 0) cautionColor else positiveColor)
        }
        findViewById<android.widget.TextView>(R.id.tv_weekly_reduction)?.apply {
            text = "${String.format("%.0f", reductionWeekly)}%"
            setTextColor(if (reductionWeekly < 0) cautionColor else positiveColor)
        }
        findViewById<android.widget.TextView>(R.id.tv_monthly_reduction)?.apply {
            text = "${String.format("%.0f", reductionMonthly)}%"
            setTextColor(if (reductionMonthly < 0) cautionColor else positiveColor)
        }

        val chart = findViewById<BarChart>(R.id.bar_chart)
        val emptyState = findViewById<android.widget.TextView>(R.id.tv_empty_state)
        val goalDailyMl = prefs.getFloat("goal_daily_ml", 0f).toDouble()
        
        fun setBarChartData(
            data: List<Pair<Int, Float>>,
            labels: List<String>,
            goalLine: Float?,
            baselineLine: Float?,
            title: String
        ) {
            val hasData = data.any { it.second > 0 }
            
            if (!hasData) {
                chart.visibility = View.GONE
                emptyState?.visibility = View.VISIBLE
                return
            }
            
            chart.visibility = View.VISIBLE
            emptyState?.visibility = View.GONE
            
            val entries = data.map { BarEntry(it.first.toFloat(), it.second) }
            
            val dataSet = BarDataSet(entries, title).apply {
                color = ContextCompat.getColor(this@ProgressActivity, R.color.chart_bar)
                setDrawValues(true)
                valueTextColor = ContextCompat.getColor(this@ProgressActivity, R.color.chart_bar_text)
                valueTextSize = 10f
                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return if (value > 0) value.toInt().toString() else ""
                    }
                }
            }
            
            chart.data = BarData(dataSet).apply {
                barWidth = 0.6f
            }
            
            // Clear existing limit lines
            chart.axisLeft.removeAllLimitLines()
            
            // Add goal line (green, dashed)
            val goalLineColor = ContextCompat.getColor(this@ProgressActivity, R.color.chart_goal_line)
            goalLine?.let { goal ->
                if (goal > 0) {
                    val limitLine = LimitLine(goal, "Goal").apply {
                        lineColor = goalLineColor
                        lineWidth = 2f
                        enableDashedLine(10f, 5f, 0f)
                        textColor = goalLineColor
                        textSize = 10f
                    }
                    chart.axisLeft.addLimitLine(limitLine)
                }
            }
            
            // Add baseline line (purple, dashed)
            val baselineLineColor = ContextCompat.getColor(this@ProgressActivity, R.color.chart_baseline_line)
            baselineLine?.let { baseline ->
                if (baseline > 0) {
                    val limitLine = LimitLine(baseline, "Starting point").apply {
                        lineColor = baselineLineColor
                        lineWidth = 2f
                        enableDashedLine(6f, 4f, 0f)
                        textColor = baselineLineColor
                        textSize = 10f
                    }
                    chart.axisLeft.addLimitLine(limitLine)
                }
            }
            
            // Configure chart appearance
            chart.description.isEnabled = false
            chart.setDrawGridBackground(false)
            chart.setDrawBarShadow(false)
            chart.setFitBars(true)
            
            // X-axis with weekday labels
            val axisTextColor = ContextCompat.getColor(this@ProgressActivity, R.color.chart_axis_text)
            val gridColor = ContextCompat.getColor(this@ProgressActivity, R.color.chart_grid)
            chart.xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = axisTextColor
                textSize = 11f
                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return if (index >= 0 && index < labels.size) labels[index] else ""
                    }
                }
            }
            
            // Y-axis
            chart.axisLeft.apply {
                setDrawGridLines(true)
                this.gridColor = gridColor
                textColor = axisTextColor
                textSize = 11f
                axisMinimum = 0f
                granularity = 1f
                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                    override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                        return value.toInt().toString()
                    }
                }
            }
            chart.axisRight.isEnabled = false
            
            // Legend
            chart.legend.apply {
                isEnabled = true
                verticalAlignment = Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                textColor = axisTextColor
            }
            
            chart.setExtraOffsets(8f, 8f, 8f, 8f)
            chart.animateY(300)
            chart.invalidate()
        }

        // Default: Week view (past 7 days with real data)
        val weekData = (0..6).map { dayOffset ->
            val date = weekStart.plusDays(dayOffset.toLong())
            val dayConsumption = try {
                val v = BrewLogNative.get_daily_consumption(date.toString())
                if (v >= 0) v else brewLog.getDailyConsumption(date)
            } catch (_: Throwable) {
                brewLog.getDailyConsumption(date)
            }
            val dayDrinks = (dayConsumption / sizeMl).toFloat()
            dayOffset to dayDrinks
        }
        
        val weekLabels = (0..6).map { dayOffset ->
            val date = weekStart.plusDays(dayOffset.toLong())
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
        
        val goalDailyDrinks = if (goalDailyMl > 0) (goalDailyMl / sizeMl).toFloat() else null
        val baselineDailyDrinks = if (baselineDaily > 0) (baselineDaily / sizeMl).toFloat() else null
        
        setBarChartData(weekData, weekLabels, goalDailyDrinks, baselineDailyDrinks, "Drinks")

        // Weekly Insights
        updateWeeklyInsights(weekStart, today, sizeMl)

        // Day chip - show today's total only (no fake hourly data)
        findViewById<com.google.android.material.chip.Chip>(R.id.chip_day)?.setOnClickListener {
            val todayDrinks = (todayMl / sizeMl).toFloat()
            val todayData = listOf(0 to todayDrinks)
            val todayLabels = listOf("Today")
            setBarChartData(todayData, todayLabels, goalDailyDrinks, baselineDailyDrinks, "Drinks")
        }

        // Week chip
        findViewById<com.google.android.material.chip.Chip>(R.id.chip_week)?.setOnClickListener {
            setBarChartData(weekData, weekLabels, goalDailyDrinks, baselineDailyDrinks, "Drinks")
        }

        // Month chip - show past 4 weeks aggregated
        findViewById<com.google.android.material.chip.Chip>(R.id.chip_month)?.setOnClickListener {
            val monthData = (0..3).map { weekOffset ->
                val weekStartDate = today.minusWeeks((3 - weekOffset).toLong())
                val weekEndDate = weekStartDate.plusDays(6)
                val weekTotal = try {
                    val totals = getDailyTotals(weekStartDate, weekEndDate)
                    totals.values.sum()
                } catch (_: Throwable) { 0.0 }
                val weekDrinks = (weekTotal / sizeMl).toFloat()
                weekOffset to weekDrinks
            }
            
            val monthLabels = (0..3).map { weekOffset ->
                val weekStartDate = today.minusWeeks((3 - weekOffset).toLong())
                "W${weekStartDate.dayOfMonth}/${weekStartDate.monthValue}"
            }
            
            val goalWeeklyDrinks = if (goalDailyMl > 0) (goalDailyMl * 7 / sizeMl).toFloat() else null
            val baselineWeeklyDrinks = if (baselineDaily > 0) (baselineDaily * 7 / sizeMl).toFloat() else null
            
            setBarChartData(monthData, monthLabels, goalWeeklyDrinks, baselineWeeklyDrinks, "Weekly total")
        }

        // Bottom nav
        findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav).apply {
            menu.clear()
            inflateMenu(R.menu.menu_bottom)
            selectedItemId = R.id.nav_progress
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> {
                        startActivity(android.content.Intent(this@ProgressActivity, MainActivity::class.java))
                        true
                    }
                    R.id.nav_progress -> true
                    R.id.nav_calendar -> {
                        startActivity(android.content.Intent(this@ProgressActivity, CalendarActivity::class.java))
                        true
                    }
                    R.id.nav_settings -> {
                        startActivity(android.content.Intent(this@ProgressActivity, SettingsActivity::class.java))
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun getDailyTotals(start: LocalDate, end: LocalDate): Map<LocalDate, Double> {
        return try {
            val json = BrewLogNative.get_beer_entries_json(start.toString(), end.toString())
            val arr = org.json.JSONArray(json)
            val map = java.util.HashMap<LocalDate, Double>()
            var i = 0
            while (i < arr.length()) {
                val o = arr.getJSONObject(i)
                val date = LocalDate.parse(o.optString("date"))
                val vol = o.optDouble("volume_ml", 0.0)
                val alcoholPercentage = o.optDouble("alcohol_percentage", 0.0)
                
                if (alcoholPercentage > 0) {
                    val current = map[date] ?: 0.0
                    map[date] = current + vol
                }
                i++
            }
            map
        } catch (e: Throwable) {
            emptyMap()
        }
    }

    private fun updateWeeklyInsights(weekStart: LocalDate, today: LocalDate, sizeMl: Double) {
        val lastWeekStart = weekStart.minusWeeks(1)
        val lastWeekEnd = weekStart.minusDays(1)

        val thisWeekTotals = getDailyTotals(weekStart, today)
        val lastWeekTotals = getDailyTotals(lastWeekStart, lastWeekEnd)

        val thisWeekMl = thisWeekTotals.values.sum()
        val lastWeekMl = lastWeekTotals.values.sum()

        val thisWeekDrinks = (thisWeekMl / sizeMl).toInt()
        val lastWeekDrinks = (lastWeekMl / sizeMl).toInt()

        // Count alcohol-free days this week (days with no consumption)
        val daysInWeekSoFar = ChronoUnit.DAYS.between(weekStart, today).toInt() + 1
        val daysWithDrinks = thisWeekTotals.keys.count { thisWeekTotals[it]!! > 0 }
        val alcoholFreeDays = daysInWeekSoFar - daysWithDrinks

        // Find best day (lowest consumption, including zero days)
        val dailyConsumptions = (0 until daysInWeekSoFar).map { dayOffset ->
            val date = weekStart.plusDays(dayOffset.toLong())
            val consumption = thisWeekTotals[date] ?: 0.0
            date to consumption
        }
        val bestDay = dailyConsumptions.minByOrNull { it.second }
        val bestDayName = bestDay?.first?.dayOfWeek?.getDisplayName(TextStyle.SHORT, Locale.getDefault()) ?: "--"

        // Calculate trend
        val improvementColor = getColor(R.color.insight_improvement)
        val cautionColor = getColor(R.color.insight_caution)
        val neutralColor = getColor(R.color.insight_neutral)

        val trendText: String
        val trendColor: Int

        if (lastWeekMl <= 0 && thisWeekMl <= 0) {
            trendText = "No data from last week to compare"
            trendColor = neutralColor
        } else if (lastWeekMl <= 0) {
            trendText = "First week of tracking"
            trendColor = neutralColor
        } else {
            val changePercent = ((thisWeekMl - lastWeekMl) / lastWeekMl * 100).toInt()
            when {
                changePercent <= -10 -> {
                    trendText = "Down ${-changePercent}% from last week"
                    trendColor = improvementColor
                }
                changePercent >= 10 -> {
                    trendText = "Up ${changePercent}% - tomorrow is a new day"
                    trendColor = cautionColor
                }
                else -> {
                    trendText = "Steady compared to last week"
                    trendColor = neutralColor
                }
            }
        }

        // Generate supportive insight message
        val insightMessage = generateInsightMessage(
            thisWeekDrinks, lastWeekDrinks, alcoholFreeDays, daysInWeekSoFar
        )

        // Update UI
        findViewById<android.widget.TextView>(R.id.tv_this_week_drinks)?.text =
            "$thisWeekDrinks drink${if (thisWeekDrinks != 1) "s" else ""}"

        findViewById<android.widget.TextView>(R.id.tv_trend_indicator)?.apply {
            text = trendText
            setTextColor(trendColor)
        }

        findViewById<android.widget.TextView>(R.id.tv_alcohol_free_days)?.text = alcoholFreeDays.toString()
        findViewById<android.widget.TextView>(R.id.tv_best_day)?.text = bestDayName
        findViewById<android.widget.TextView>(R.id.tv_insight_message)?.text = insightMessage
    }

    private fun generateInsightMessage(
        thisWeekDrinks: Int,
        lastWeekDrinks: Int,
        alcoholFreeDays: Int,
        daysTracked: Int
    ): String {
        return when {
            daysTracked <= 1 -> "Keep tracking to see your patterns."
            
            alcoholFreeDays >= daysTracked -> 
                "An alcohol-free week so far. Your body thanks you."
            
            alcoholFreeDays >= 4 -> 
                "Great balance this week with $alcoholFreeDays alcohol-free days."
            
            thisWeekDrinks < lastWeekDrinks && lastWeekDrinks > 0 -> 
                "You're drinking less than last week. Small changes add up."
            
            thisWeekDrinks == 0 -> 
                "Starting fresh this week. Every day is a choice."
            
            alcoholFreeDays >= 2 -> 
                "You've had $alcoholFreeDays alcohol-free days. Rest days matter."
            
            alcoholFreeDays == 1 -> 
                "One alcohol-free day this week. Consider adding another."
            
            else -> 
                "Awareness is the first step. Keep tracking your patterns."
        }
    }
}
