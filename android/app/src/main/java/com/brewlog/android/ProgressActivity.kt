package com.brewlog.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.time.LocalDate
import java.time.DayOfWeek
import org.json.JSONArray

class ProgressActivity : AppCompatActivity() {
    private val prefsName = "brewlog_prefs"
    
    private fun getWeekStart(today: LocalDate): LocalDate {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val startOfWeek = prefs.getInt("start_of_week", 1) // Default to Monday (1)
        val targetDayOfWeek = DayOfWeek.of(startOfWeek)
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

		val brewLog = BrewLogProvider.instance
        val today = brewLog.nowEffectiveDate()
        val startOfWeek = getWeekStart(today)
		val prefs = getSharedPreferences("brewlog_prefs", MODE_PRIVATE)
		val sizeMl = prefs.getInt("default_beer_size", 500).toDouble().coerceAtLeast(1.0)
        fun drinksOf(ml: Double) = (ml / sizeMl)

        val todayMlNative = try { BrewLogNative.get_daily_consumption(today.toString()) } catch (_: Throwable) { -1.0 }
        val weekMlNative = try { BrewLogNative.get_weekly_consumption(startOfWeek.toString()) } catch (_: Throwable) { -1.0 }
        val todayMl = if (todayMlNative >= 0) todayMlNative else 0.0
        val weekMl = if (weekMlNative >= 0) weekMlNative else 0.0

        // Today and week consumption are now displayed in the Current vs Baseline comparison section

        // Baseline and reduction
        val baselineDaily = prefs.getFloat("baseline_daily_ml", 0f).toDouble()
        val baselineWeekly = baselineDaily * 7.0
        val baselineMonthly = baselineDaily * 30.0
        val reductionDaily = if (baselineDaily > 0) ((baselineDaily - todayMl) / baselineDaily) * 100 else 0.0
        val reductionWeekly = if (baselineWeekly > 0) ((baselineWeekly - weekMl) / baselineWeekly) * 100 else 0.0
        
        // Helper function to get daily totals from native
        fun getDailyTotals(start: LocalDate, end: LocalDate): Map<LocalDate, Double> {
            return try {
                val json = BrewLogNative.get_beer_entries_json(start.toString(), end.toString())
                android.util.Log.d("ProgressActivity", "Raw JSON from native: $json")
                val arr = JSONArray(json)
                val map = java.util.HashMap<LocalDate, Double>()
                var i = 0
                while (i < arr.length()) {
                    val o = arr.getJSONObject(i)
                    val date = LocalDate.parse(o.optString("date"))
                    val vol = o.optDouble("volume_ml", 0.0)
                    val alcoholPercentage = o.optDouble("alcohol_percentage", 0.0)
                    
                    android.util.Log.d("ProgressActivity", "Processing entry: date=$date, vol=$vol, alcohol=$alcoholPercentage")
                    
                    // Only count entries with alcohol percentage > 0 (same logic as main consumption)
                    if (alcoholPercentage > 0) {
                        val current = map[date] ?: 0.0
                        map[date] = current + vol
                        android.util.Log.d("ProgressActivity", "Added $vol to $date, new total: ${map[date]}")
                    } else {
                        android.util.Log.d("ProgressActivity", "Skipped entry with alcohol=$alcoholPercentage")
                    }
                    i++
                }
                android.util.Log.d("ProgressActivity", "Processed totals map: $map")
                map
            } catch (e: Throwable) {
                android.util.Log.e("ProgressActivity", "Error getting daily totals", e)
                emptyMap()
            }
        }
        
        // Get monthly consumption
        val monthStart = today.minusDays(29)
        val monthMlNative = try { 
            val totals = getDailyTotals(monthStart, today)
            totals.values.sum()
        } catch (_: Throwable) { 0.0 }
        val monthMl = if (monthMlNative >= 0) monthMlNative else 0.0
        val reductionMonthly = if (baselineMonthly > 0) ((baselineMonthly - monthMl) / baselineMonthly) * 100 else 0.0
        
        // Update all reduction displays
        findViewById<android.widget.TextView>(R.id.tv_daily_reduction).text = "${String.format("%.1f", reductionDaily)}%"
        findViewById<android.widget.TextView>(R.id.tv_weekly_reduction).text = "${String.format("%.1f", reductionWeekly)}%"
        findViewById<android.widget.TextView>(R.id.tv_monthly_reduction).text = "${String.format("%.1f", reductionMonthly)}%"

        // Buttons to set baseline/goals
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_set_baseline_progress).setOnClickListener {
            startActivity(android.content.Intent(this, MainActivity::class.java).apply {
                putExtra("open_setup_dialog", true)
            })
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_set_goals_progress).setOnClickListener {
            startActivity(android.content.Intent(this, MainActivity::class.java).apply {
                putExtra("open_setup_dialog", true)
            })
        }
        
        // Reset baseline button
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_reset_baseline).setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Reset Baseline")
                .setMessage("Are you sure you want to reset your baseline? This will clear your current baseline data.")
                .setPositiveButton("Reset") { _, _ ->
			val prefs = getSharedPreferences("brewlog_prefs", MODE_PRIVATE)
                    prefs.edit().remove("baseline_daily_ml").remove("baseline_set_date").apply()
                    // Reload data to update display
                    val today = brewLog.nowEffectiveDate()
                    val startOfWeek = getWeekStart(today)
                    val todayMlNative = try { BrewLogNative.get_daily_consumption(today.toString()) } catch (_: Throwable) { -1.0 }
                    val weekMlNative = try { BrewLogNative.get_weekly_consumption(startOfWeek.toString()) } catch (_: Throwable) { -1.0 }
                    val todayMl = if (todayMlNative >= 0) todayMlNative else 0.0
                    val weekMl = if (weekMlNative >= 0) weekMlNative else 0.0
                    
                    // Update display
                    val baselineDaily = prefs.getFloat("baseline_daily_ml", 0f).toDouble()
                    val baselineWeekly = baselineDaily * 7.0
                    
                    // Update reduction percentage
                    val reductionDaily = if (baselineDaily > 0) ((baselineDaily - todayMl) / baselineDaily) * 100 else 0.0
                    val reductionWeekly = if (baselineWeekly > 0) ((baselineWeekly - weekMl) / baselineWeekly) * 100 else 0.0
                    
                    // Update reduction display
                    android.widget.Toast.makeText(this, "Baseline reset", android.widget.Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Chart setup and data
        val chart = findViewById<LineChart>(R.id.line_chart)
        chart.description.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.setNoDataText("No data available")
        chart.setNoDataTextColor(android.graphics.Color.parseColor("#B0B0B0"))
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.xAxis.textColor = android.graphics.Color.parseColor("#B0B0B0")
        chart.xAxis.textSize = 12f
        chart.xAxis.setDrawGridLines(false)
        chart.xAxis.setDrawAxisLine(true)
        chart.xAxis.axisLineColor = android.graphics.Color.parseColor("#404040")
        
        // Enable scrolling and zooming
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.setDoubleTapToZoomEnabled(false) // Disable double-tap zoom
        chart.setHorizontalScrollBarEnabled(true)
        chart.setScrollContainer(true)
        
        chart.axisLeft.textColor = android.graphics.Color.parseColor("#B0B0B0")
        chart.axisLeft.textSize = 12f
        chart.axisLeft.setDrawGridLines(true)
        chart.axisLeft.gridColor = android.graphics.Color.parseColor("#404040")
        chart.axisLeft.granularity = 1f // Force whole number increments
        chart.axisLeft.setGranularityEnabled(true)
        chart.axisLeft.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return value.toInt().toString() // Always show whole numbers
            }
        }
        chart.axisLeft.axisLineColor = android.graphics.Color.parseColor("#404040")
        chart.axisLeft.setDrawAxisLine(true)
        
        chart.legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
        chart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
        chart.legend.textColor = android.graphics.Color.parseColor("#B0B0B0")
        chart.legend.textSize = 12f
        
        chart.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        chart.setDrawGridBackground(false)
        chart.setTouchEnabled(true)
        chart.setDragEnabled(true)
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.setHighlightPerDragEnabled(false)
        chart.setHighlightPerTapEnabled(false)

        fun applyLimitLines(baselineDrinks: Float?, goalDrinks: Float?) {
            val left = chart.axisLeft
            left.removeAllLimitLines()
            left.axisMinimum = 0f
            baselineDrinks?.let {
                val ll = LimitLine(it, "Baseline")
                ll.lineColor = android.graphics.Color.parseColor("#8E24AA")
                ll.enableDashedLine(12f, 6f, 0f)
                ll.textColor = android.graphics.Color.parseColor("#B0B0B0")
                chart.axisLeft.addLimitLine(ll)
            }
            goalDrinks?.let {
                val ll = LimitLine(it, "Goal")
                ll.lineColor = android.graphics.Color.parseColor("#2E7D32")
                ll.enableDashedLine(8f, 6f, 0f)
                ll.textColor = android.graphics.Color.parseColor("#B0B0B0")
                chart.axisLeft.addLimitLine(ll)
            }
        }

        fun setChartData(label: String, yValues: List<Float>, xLabels: List<String>, baseline: Float?, goal: Float?) {
            val entries = yValues.mapIndexed { index, value -> Entry(index.toFloat(), value) }
            val current = LineDataSet(entries, label).apply {
                color = android.graphics.Color.parseColor("#4CAF50")
                setDrawCircles(true)
                circleRadius = 4f
                circleHoleRadius = 2f
                circleHoleColor = android.graphics.Color.parseColor("#4CAF50")
                setCircleColor(android.graphics.Color.parseColor("#4CAF50"))
                setDrawValues(false)
                lineWidth = 3f
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawFilled(true)
                fillColor = android.graphics.Color.parseColor("#4CAF50")
                fillAlpha = 30
                setDrawHorizontalHighlightIndicator(false)
                setDrawVerticalHighlightIndicator(true)
                highLightColor = android.graphics.Color.parseColor("#FFC107")
                setHighlightLineWidth(2f)
            }
            val dataSets = mutableListOf<ILineDataSet>(current)
            chart.data = LineData(dataSets)

            chart.xAxis.labelRotationAngle = 0f
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val i = value.toInt()
                    return if (i in xLabels.indices) xLabels[i] else ""
                }
            }

            // Set visible range based on data size for scrolling
            when {
                yValues.size <= 1 -> {
                    // Day view - no scrolling needed
                    chart.setVisibleXRangeMaximum(1f)
                    chart.setVisibleXRangeMinimum(1f)
                }
                yValues.size <= 7 -> {
                    // Week view - show 5 days at a time, enable scrolling
                    chart.setVisibleXRangeMaximum(5f)
                    chart.setVisibleXRangeMinimum(3f)
                }
                yValues.size <= 30 -> {
                    // Month view - show 10 days at a time, enable scrolling
                    chart.setVisibleXRangeMaximum(10f)
                    chart.setVisibleXRangeMinimum(5f)
                }
                else -> {
                    // Longer periods
                    chart.setVisibleXRangeMaximum(15f)
                    chart.setVisibleXRangeMinimum(7f)
                }
            }

            applyLimitLines(baseline, goal)
            chart.animateY(500)
            chart.animateX(500)
            chart.invalidate()
            
            // Move to the end of the data (most recent)
            chart.moveViewToX((yValues.size - 1).toFloat())
        }

        fun loadWeek() {
            val start = getWeekStart(today)
            val end = start.plusDays(6) // Full week from start to start+6 days
            android.util.Log.d("ProgressActivity", "Loading week data from $start to $end")
            val totals = getDailyTotals(start, end)
            
            // Create data for the full week (7 days)
            val weekData = (0..6).map { offset -> 
                val date = start.plusDays(offset.toLong())
                android.util.Log.d("ProgressActivity", "Week day $offset: $date, total: ${totals[date]}")
                Pair(
                    "${date.dayOfWeek.name.substring(0, 3)} ${date.dayOfMonth}/${date.monthValue}",
                    drinksOf(totals[date] ?: 0.0).toFloat()
                )
            }
            
            val labels = weekData.map { it.first }
            val values = weekData.map { it.second }
            
            val baselineWeekDrinks = (baselineDaily * 7.0 / sizeMl).toFloat().takeIf { it > 0f }
            val goalWeekDrinks = (prefs.getFloat("goal_weekly_ml", 0f).toDouble() / sizeMl).toFloat().takeIf { it > 0f }
            
            // Debug logging
            android.util.Log.d("ProgressActivity", "Week data - start: $start, end: $end, totals: $totals, values: $values, sizeMl: $sizeMl")
            android.util.Log.d("ProgressActivity", "Week labels: $labels")
            
            setChartData("Drinks", values, labels, baselineWeekDrinks, goalWeekDrinks)
        }

        fun loadMonth() {
            val start = today.minusDays(29)
            val end = today
            android.util.Log.d("ProgressActivity", "Loading month data from $start to $end")
            val totals = getDailyTotals(start, end)
            
            // Create data for past 30 days (including today), filtering out future dates
            val monthData = (0..29).mapNotNull { offset -> 
                val date = start.plusDays(offset.toLong())
                if (date.isAfter(today)) null else Pair(
                    "${date.dayOfWeek.name.substring(0, 3)} ${date.dayOfMonth}",
                    drinksOf(totals[date] ?: 0.0).toFloat()
                )
            }
            
            val labels = monthData.map { it.first }
            val values = monthData.map { it.second }
            
            val baselineMonthDrinks = (baselineDaily * 30.0 / sizeMl).toFloat().takeIf { it > 0f }
            val weeklyGoalMl = prefs.getFloat("goal_weekly_ml", 0f).toDouble()
            val monthGoalMl = if (weeklyGoalMl > 0) weeklyGoalMl * 4.0 else 0.0
            val goalMonthDrinks = (monthGoalMl / sizeMl).toFloat().takeIf { it > 0f }
            
            // Debug logging
            android.util.Log.d("ProgressActivity", "Month data - start: $start, end: $end, totals: $totals, values: $values, sizeMl: $sizeMl")
            android.util.Log.d("ProgressActivity", "Month labels: $labels")
            
            setChartData("Drinks", values, labels, baselineMonthDrinks, goalMonthDrinks)
        }

        fun loadDay() {
            // Show last 7 days for context
            val start = today.minusDays(6)
            val end = today
            val totals = getDailyTotals(start, end)
            
            val dayData = (0..6).map { offset ->
                val date = start.plusDays(offset.toLong())
                val isToday = date == today
                Pair(
                    if (isToday) "Today" else "${date.dayOfWeek.name.substring(0, 3)} ${date.dayOfMonth}",
                    drinksOf(totals[date] ?: 0.0).toFloat()
                )
            }
            
            val labels = dayData.map { it.first }
            val values = dayData.map { it.second }
            
            val baselineDayDrinks = (baselineDaily / sizeMl).toFloat().takeIf { it > 0f }
            val goalDayDrinks = (prefs.getFloat("goal_daily_ml", 0f).toDouble() / sizeMl).toFloat().takeIf { it > 0f }
            setChartData("Drinks", values, labels, baselineDayDrinks, goalDayDrinks)
        }

        // Default to week
        loadWeek()

        // Hook chips
        findViewById<com.google.android.material.chip.Chip>(R.id.chip_week).setOnClickListener { 
            loadWeek()
        }
        findViewById<com.google.android.material.chip.Chip>(R.id.chip_month).setOnClickListener { 
            loadMonth()
        }
        findViewById<com.google.android.material.chip.Chip>(R.id.chip_day).setOnClickListener { 
            loadDay()
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
                        startActivity(android.content.Intent(this@ProgressActivity, MainActivity::class.java).apply {
                            putExtra("open_settings", true)
                        })
                        true
                    }
					else -> false
				}
			}
		}
	}
}


