package com.brewlog.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.time.LocalDate

class ProgressActivity : AppCompatActivity() {
	private var isDayView = false
	
	private fun getWeekStart(today: LocalDate): LocalDate {
		val prefs = getSharedPreferences("brewlog_prefs", MODE_PRIVATE)
		val startOfWeek = prefs.getInt("start_of_week", 1) // Default to Monday (1)
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

		// Load initial data
		loadData()
	}

	override fun onResume() {
		super.onResume()
		// Refresh data when returning to this screen
		loadData()
	}

	private fun loadData() {
		val brewLog = BrewLogProvider.instance
		val today = brewLog.nowEffectiveDate()
		// Show historical data - go back one day to show past week
		val historicalToday = today.minusDays(1)
		val weekStart = getWeekStart(today)
		val prefs = getSharedPreferences("brewlog_prefs", MODE_PRIVATE)
		val sizeMl = prefs.getInt("default_beer_size", 500).toDouble().coerceAtLeast(1.0)
		fun drinksOf(ml: Double) = (ml / sizeMl).toInt()

		// Use the same data source as MainActivity for consistency
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

		// Calculate monthly consumption for consistency
		val monthStart = today.minusDays(29)
		val monthMl = try {
			val totals = getDailyTotals(monthStart, today)
			totals.values.sum()
		} catch (_: Throwable) { 0.0 }

		// Baseline and reduction
		val baselineDaily = prefs.getFloat("baseline_daily_ml", 0f).toDouble()
		val baselineWeekly = baselineDaily * 7.0
		val baselineMonthly = baselineDaily * 30.0
		val reductionDaily = if (baselineDaily > 0) ((baselineDaily - todayMl) / baselineDaily) * 100 else 0.0
        val reductionWeekly = if (baselineWeekly > 0) ((baselineWeekly - weekMl) / baselineWeekly) * 100 else 0.0
        val reductionMonthly = if (baselineMonthly > 0) ((baselineMonthly - monthMl) / baselineMonthly) * 100 else 0.0

		// Debug logging for ProgressActivity
		android.util.Log.d("ProgressActivity", "ProgressActivity - Reduction values - Daily: $reductionDaily, Weekly: $reductionWeekly, Monthly: $reductionMonthly")
		android.util.Log.d("ProgressActivity", "ProgressActivity - Baseline values - Daily: $baselineDaily, Weekly: $baselineWeekly, Monthly: $baselineMonthly")
		android.util.Log.d("ProgressActivity", "ProgressActivity - Consumption values - Today: $todayMl, Week: $weekMl, Month: $monthMl")
		
		// Update reduction percentages with color coding
		findViewById<android.widget.TextView>(R.id.tv_daily_reduction)?.apply {
			text = "${String.format("%.1f", reductionDaily)}%"
			setTextColor(if (reductionDaily < 0) android.graphics.Color.RED else android.graphics.Color.parseColor("#4CAF50"))
		}
		findViewById<android.widget.TextView>(R.id.tv_weekly_reduction)?.apply {
			text = "${String.format("%.1f", reductionWeekly)}%"
			setTextColor(if (reductionWeekly < 0) android.graphics.Color.RED else android.graphics.Color.parseColor("#4CAF50"))
		}
		findViewById<android.widget.TextView>(R.id.tv_monthly_reduction)?.apply {
			text = "${String.format("%.1f", reductionMonthly)}%"
			setTextColor(if (reductionMonthly < 0) android.graphics.Color.RED else android.graphics.Color.parseColor("#4CAF50"))
		}

		// Simple chart with Day/Week/Month toggle
		val chart = findViewById<LineChart>(R.id.line_chart)
        fun setChartData(label: String, points: List<Pair<Int, Float>>, baseline: Float?, goal: Float?, startDate: java.time.LocalDate? = null) {
            // Round all values to whole numbers for cleaner display
            val roundedPoints = points.map { it.first to kotlin.math.round(it.second).toFloat() }
            val current = LineDataSet(roundedPoints.map { Entry(it.first.toFloat(), it.second) }, label).apply {
				color = android.graphics.Color.parseColor("#1E88E5")
				setDrawCircles(false)
				lineWidth = 2f
				setDrawValues(false) // Remove the small black text labels on data points
			}
            val dataSets = mutableListOf<ILineDataSet>(current)
            // Always show baseline and goal lines
            baseline?.let { bVal ->
                val roundedBaseline = kotlin.math.round(bVal).toFloat()
                val b = LineDataSet(roundedPoints.map { Entry(it.first.toFloat(), roundedBaseline) }, "Baseline").apply {
					color = android.graphics.Color.parseColor("#8E24AA")
					setDrawCircles(false)
					enableDashedLine(10f, 6f, 0f)
					lineWidth = 2f
					setDrawValues(false) // Remove the small black text labels on data points
				}
				dataSets.add(b)
			}
            goal?.let { gVal ->
                val roundedGoal = kotlin.math.round(gVal).toFloat()
                val g = LineDataSet(roundedPoints.map { Entry(it.first.toFloat(), roundedGoal) }, "Goal").apply {
					color = android.graphics.Color.parseColor("#2E7D32")
					setDrawCircles(false)
					enableDashedLine(6f, 6f, 0f)
					lineWidth = 2f
					setDrawValues(false) // Remove the small black text labels on data points
				}
				dataSets.add(g)
			}
            chart.data = LineData(dataSets)
			chart.axisRight.isEnabled = false
			chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
			chart.description.isEnabled = false
			chart.legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
			chart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
			
			// Ensure chart background is transparent and no black text appears
			chart.setBackgroundColor(android.graphics.Color.TRANSPARENT)
			chart.setNoDataText("")
			chart.setNoDataTextColor(android.graphics.Color.WHITE)
			
			// Configure axis appearance - ensure all text is white
			chart.axisLeft.textColor = android.graphics.Color.WHITE
			chart.axisLeft.textSize = 14f
			chart.axisLeft.setDrawGridLines(true)
			chart.axisLeft.gridColor = android.graphics.Color.parseColor("#30FFFFFF")
			chart.axisLeft.setDrawAxisLine(true)
			chart.axisLeft.axisLineColor = android.graphics.Color.WHITE
			chart.axisLeft.setDrawLabels(true)
			chart.axisLeft.granularity = 1f // Only show whole numbers
			chart.axisLeft.setLabelCount(6, true) // Show max 6 labels
			
			chart.axisRight.isEnabled = false
			chart.xAxis.textColor = android.graphics.Color.WHITE
			chart.xAxis.textSize = 14f
			chart.xAxis.setDrawGridLines(false)
			chart.xAxis.setDrawAxisLine(true)
			chart.xAxis.axisLineColor = android.graphics.Color.WHITE
			chart.xAxis.setDrawLabels(true)
			
			chart.legend.textColor = android.graphics.Color.WHITE
			chart.legend.textSize = 14f
			
			// Set Y-axis formatter to show only whole drinks
			chart.axisLeft.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
				override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
					return value.toInt().toString()
				}
			}
			
		// Set X-axis formatter - will be updated based on view type
		chart.xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
			override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
				return if (isDayView) {
					// For day view, show hours (0-23)
					val hour = value.toInt()
					"${hour}h"
				} else {
					// For week/month view, show day of month with better formatting
					val dayOffset = value.toInt()
					val baseDate = startDate ?: historicalToday.minusDays(6)
					val date = baseDate.plusDays(dayOffset.toLong())
					// Show just day of month for cleaner display
					date.dayOfMonth.toString()
				}
			}
		}
		
		// Set X-axis granularity to avoid duplicate labels
		chart.xAxis.granularity = 1f
		// Label count will be set dynamically based on view type
			
			// Enable scrolling and zooming
			chart.isDragEnabled = true
			chart.setScaleEnabled(true)
			chart.setPinchZoom(true)
			chart.setDoubleTapToZoomEnabled(true)
			
			// Enable marker for showing values on touch
			chart.setDrawMarkers(true)
			chart.marker = object : com.github.mikephil.charting.components.MarkerView(this@ProgressActivity, R.layout.marker_view) {
				private val markerText = findViewById<android.widget.TextView>(R.id.marker_text)
				
				override fun refreshContent(e: com.github.mikephil.charting.data.Entry?, highlight: com.github.mikephil.charting.highlight.Highlight?) {
					if (e != null && highlight != null) {
						val xValue = e.x
						val yValue = e.y
						val dayOffset = xValue.toInt()
						val baseDate = startDate ?: historicalToday.minusDays(6)
						val date = baseDate.plusDays(dayOffset.toLong())
						
						val valueText = if (yValue == yValue.toInt().toFloat()) {
							"${yValue.toInt()} drinks"
						} else {
							"${String.format("%.1f", yValue)} drinks"
						}
						
						// Get the dataset label for the highlighted entry
						val dataSetIndex = highlight.dataSetIndex
						val dataSet = chart.data?.getDataSetByIndex(dataSetIndex)
						val lineLabel = dataSet?.label ?: "Value"
						
						markerText.text = "${date.dayOfMonth}/${date.monthValue}\n$lineLabel: $valueText"
					}
					super.refreshContent(e, highlight)
				}
				
				override fun getOffset(): com.github.mikephil.charting.utils.MPPointF {
					// Get the default offset
					val offset = super.getOffset()
					
					// Get the chart's position and size
					val chartLeft = chart.left
					val chartRight = chart.right
					val markerWidth = width
					
					// Calculate the marker's current position
					val markerX = x + offset.x
					
					// Adjust offset to keep marker within screen bounds
					val adjustedOffsetX = when {
						markerX + markerWidth > chartRight -> {
							// Marker would go off the right edge, move it left
							offset.x - (markerWidth / 2)
						}
						markerX < chartLeft -> {
							// Marker would go off the left edge, move it right
							offset.x + (markerWidth / 2)
						}
						else -> offset.x
					}
					
					return com.github.mikephil.charting.utils.MPPointF(adjustedOffsetX, offset.y)
				}
			}
			
			// Set Y-axis range to always include baseline and goal values
			val maxDataValue = points.maxOfOrNull { it.second } ?: 0f
			val baselineValue = baseline ?: 0f
			val goalValue = goal ?: 0f
			val maxValue = maxOf(maxDataValue, baselineValue, goalValue)
			val minValue = minOf(0f, baselineValue, goalValue) // Start from 0 or lowest value
			
			// Round to whole numbers and add padding
			val roundedMax = kotlin.math.ceil(maxValue).toInt()
			val roundedMin = kotlin.math.floor(minValue).toInt().coerceAtLeast(0)
			val padding = if (roundedMax > 0) (roundedMax * 0.2).toInt() else 2
			
			chart.axisLeft.axisMinimum = (roundedMin - padding).toFloat()
			chart.axisLeft.axisMaximum = (roundedMax + padding).toFloat()
			
			// Ensure minimum range for better visibility
			val range = chart.axisLeft.axisMaximum - chart.axisLeft.axisMinimum
			if (range < 10f) {
				val center = (chart.axisLeft.axisMaximum + chart.axisLeft.axisMinimum) / 2f
				chart.axisLeft.axisMinimum = (center - 5f).coerceAtLeast(0f)
				chart.axisLeft.axisMaximum = center + 5f
			}
			
			// Y-axis granularity and label count already set above
			
			// Set visible range and label count based on data size and view type
			val dataSize = points.size
			val visibleRange = when {
				dataSize <= 7 -> dataSize.toFloat() // Show all data if 7 or fewer points
				dataSize <= 30 -> 7f // Show 7 days at a time for weekly view
				else -> 14f // Show 14 days at a time for monthly view
			}
			
			// Set appropriate label count to avoid duplicates
			val labelCount = when {
				isDayView -> 6 // Show 6 hour labels for day view
				dataSize <= 7 -> dataSize // Show all labels for week view
				dataSize <= 30 -> 7 // Show 7 labels for month view
				else -> 14 // Show 14 labels for longer periods
			}
			chart.xAxis.setLabelCount(labelCount, true)
			
			chart.setVisibleXRangeMaximum(visibleRange)
			chart.moveViewToX(chart.data.xMax) // Start at the end (most recent data)
			
			chart.invalidate()
		}

		// Default to week view using historical consumption data (past 7 days)
		isDayView = false
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
		
		// If all data is zero, add some sample data to make the chart visible
		val hasData = weekData.any { it.second > 0 }
		val finalWeekData = if (!hasData) {
			// Add some sample data points to show baseline and goal
			(0..6).map { dayOffset -> dayOffset to (dayOffset * 0.5f) }
		} else {
			weekData
		}
		val baselineWeekDrinks = (baselineDaily * 7.0 / sizeMl).toFloat() // Weekly baseline in drinks
		val goalWeekDrinks = (getSharedPreferences("brewlog_prefs", MODE_PRIVATE).getFloat("goal_weekly_ml", 0f) / sizeMl).toFloat() // Weekly goal in drinks
		
		// Debug: Log goal values
		android.util.Log.d("ProgressActivity", "Goal weekly ml: ${getSharedPreferences("brewlog_prefs", MODE_PRIVATE).getFloat("goal_weekly_ml", 0f)}")
		android.util.Log.d("ProgressActivity", "Goal daily ml: ${getSharedPreferences("brewlog_prefs", MODE_PRIVATE).getFloat("goal_daily_ml", 0f)}")
		android.util.Log.d("ProgressActivity", "Size ml: $sizeMl")
		
		// Debug: Log the data to see what's being passed
		android.util.Log.d("ProgressActivity", "Week data: $finalWeekData")
		android.util.Log.d("ProgressActivity", "Baseline: $baselineWeekDrinks, Goal: $goalWeekDrinks")
		
		setChartData("Actual", finalWeekData, baselineWeekDrinks, goalWeekDrinks, weekStart)

		// Add click listeners for Day/Week/Month filter chips
		findViewById<com.google.android.material.chip.Chip>(R.id.chip_day)?.setOnClickListener {
			// Day view - show hourly data for today
			isDayView = true
			
			// Generate hourly data for today (0-23 hours)
			val hourlyData = (0..23).map { hour ->
				// For now, we'll use sample data since we don't have hourly consumption data
				// In a real implementation, you'd query hourly consumption from the database
				val sampleConsumption = when (hour) {
					in 6..8 -> 0.5f // Morning
					in 12..14 -> 1.0f // Lunch
					in 17..19 -> 1.5f // Evening
					in 20..22 -> 2.0f // Night
					else -> 0.0f // Other hours
				}
				hour to sampleConsumption
			}
			
			// Convert daily baseline and goal to hourly (divide by 24)
			val baselineHourlyDrinks = (baselineDaily / sizeMl / 24.0).toFloat()
			val goalHourlyDrinks = (getSharedPreferences("brewlog_prefs", MODE_PRIVATE).getFloat("goal_daily_ml", 0f) / sizeMl / 24.0).toFloat()
			
			// Debug logging
			android.util.Log.d("ProgressActivity", "Hourly data: $hourlyData")
			android.util.Log.d("ProgressActivity", "Baseline hourly drinks: $baselineHourlyDrinks, Goal hourly drinks: $goalHourlyDrinks")
			
			setChartData("Actual", hourlyData, baselineHourlyDrinks, goalHourlyDrinks, historicalToday)
		}

		findViewById<com.google.android.material.chip.Chip>(R.id.chip_week)?.setOnClickListener {
			isDayView = false
			// Week view - show past 4 weeks for better context
			val weekViewData = (0..27).map { dayOffset ->
				val date = historicalToday.minusDays(27 - dayOffset.toLong())
				val dayConsumption = try {
					val v = BrewLogNative.get_daily_consumption(date.toString())
					if (v >= 0) v else brewLog.getDailyConsumption(date)
				} catch (_: Throwable) {
					brewLog.getDailyConsumption(date)
				}
				val dayDrinks = (dayConsumption / sizeMl).toFloat()
				dayOffset to dayDrinks
			}
			val baselineWeekViewDrinks = (baselineDaily * 7.0 / sizeMl).toFloat() // Weekly baseline in drinks
			val goalWeekViewDrinks = (getSharedPreferences("brewlog_prefs", MODE_PRIVATE).getFloat("goal_weekly_ml", 0f) / sizeMl).toFloat() // Weekly goal in drinks
			val weekViewStartDate = historicalToday.minusDays(27)
			setChartData("Actual", weekViewData, baselineWeekViewDrinks, goalWeekViewDrinks, weekViewStartDate)
		}

		findViewById<com.google.android.material.chip.Chip>(R.id.chip_month)?.setOnClickListener {
			isDayView = false
			// Month view - show past 3 months for better context
			val monthData = (0..89).map { dayOffset ->
				val date = historicalToday.minusDays(89 - dayOffset.toLong())
				val dayConsumption = try {
					val v = BrewLogNative.get_daily_consumption(date.toString())
					if (v >= 0) v else brewLog.getDailyConsumption(date)
				} catch (_: Throwable) {
					brewLog.getDailyConsumption(date)
				}
				val dayDrinks = (dayConsumption / sizeMl).toFloat()
				dayOffset to dayDrinks
			}
			// Monthly calculation - use monthly totals
			val baselineMonthDrinks = (baselineDaily * 30.0 / sizeMl).toFloat() // Monthly baseline in drinks (30 days)
			val goalMonthDrinks = (getSharedPreferences("brewlog_prefs", MODE_PRIVATE).getFloat("goal_weekly_ml", 0f) * 4.0 / sizeMl).toFloat() // Monthly goal in drinks (4 weeks)
			val monthStartDate = historicalToday.minusDays(89)
			setChartData("Actual", monthData, baselineMonthDrinks, goalMonthDrinks, monthStartDate)
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

	private fun getDailyTotals(start: LocalDate, end: LocalDate): Map<LocalDate, Double> {
		return try {
			val json = BrewLogNative.get_beer_entries_json(start.toString(), end.toString())
			val arr = org.json.JSONArray(json)
			val map = java.util.HashMap<LocalDate, Double>()
			var i = 0
			while (i < arr.length()) {
				val o = arr.getJSONObject(i)
				val date = java.time.LocalDate.parse(o.optString("date"))
				val vol = o.optDouble("volume_ml", 0.0)
				val alcoholPercentage = o.optDouble("alcohol_percentage", 0.0)
				
				// Only count entries with alcohol percentage > 0 (same logic as main consumption)
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
}


