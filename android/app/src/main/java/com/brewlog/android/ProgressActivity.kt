package com.brewlog.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ProgressActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_progress)

		val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
		setSupportActionBar(toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		toolbar.setNavigationOnClickListener { finish() }

		val brewLog = BrewLogProvider.instance
		val metrics = brewLog.getProgressMetrics()
		val today = java.time.LocalDate.now()
		val weekStart = today.minusDays(6)
		val todayMl = brewLog.getDailyConsumption(today)
		val weekMl = brewLog.getWeeklyConsumption(weekStart)
		val prefs = getSharedPreferences("brewlog_prefs", MODE_PRIVATE)
		val sizeMl = prefs.getInt("default_beer_size", 500).toDouble().coerceAtLeast(1.0)
		fun drinksOf(ml: Double) = (ml / sizeMl).toInt()
		findViewById<android.widget.TextView>(R.id.today_consumption).text = "${drinksOf(todayMl)} drinks"
		findViewById<android.widget.TextView>(R.id.week_consumption).text = "${drinksOf(weekMl)} drinks"

		if (metrics != null) {
			// Convert ml to drinks using default size
			val prefs = getSharedPreferences("brewlog_prefs", MODE_PRIVATE)
			val defaultSizeMl = prefs.getInt("default_beer_size", 500).toDouble().coerceAtLeast(1.0)
			fun drinksOf(ml: Double) = (ml / defaultSizeMl).toInt()
			findViewById<android.widget.TextView>(R.id.tv_reduction_percentage).text = "${String.format("%.1f", metrics.reductionPercentageDaily)}%"
			findViewById<android.widget.TextView>(R.id.tv_baseline_daily).text = "${drinksOf(metrics.baselineDailyAverage)} drinks/day"
			findViewById<android.widget.TextView>(R.id.tv_current_daily).text = "${drinksOf(metrics.currentDailyAverage)} drinks/day"
			findViewById<android.widget.TextView>(R.id.tv_baseline_weekly).text = "${drinksOf(metrics.baselineWeeklyAverage)} drinks/week"
			findViewById<android.widget.TextView>(R.id.tv_current_weekly).text = "${drinksOf(metrics.currentWeeklyAverage)} drinks/week"
		}

		// Bottom nav
		findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav).apply {
			selectedItemId = R.id.nav_progress
			setOnItemSelectedListener { item ->
				when (item.itemId) {
					R.id.nav_home -> {
						startActivity(android.content.Intent(this@ProgressActivity, MainActivity::class.java))
						true
					}
					R.id.nav_progress -> true
					else -> false
				}
			}
		}
	}
}


