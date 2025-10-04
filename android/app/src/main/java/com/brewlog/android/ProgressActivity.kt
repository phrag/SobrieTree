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
		val today = brewLog.nowEffectiveDate()
		val weekStart = today.minusDays(6)
		val prefs = getSharedPreferences("brewlog_prefs", MODE_PRIVATE)
		val sizeMl = prefs.getInt("default_beer_size", 500).toDouble().coerceAtLeast(1.0)
		fun drinksOf(ml: Double) = (ml / sizeMl).toInt()

		val todayMlNative = try { BrewLogNative.get_daily_consumption(today.toString()) } catch (_: Throwable) { -1.0 }
		val weekMlNative = try { BrewLogNative.get_weekly_consumption(weekStart.toString()) } catch (_: Throwable) { -1.0 }
		val todayMl = if (todayMlNative >= 0) todayMlNative else 0.0
		val weekMl = if (weekMlNative >= 0) weekMlNative else 0.0

		findViewById<android.widget.TextView>(R.id.today_consumption).text = "${drinksOf(todayMl)} drinks"
		findViewById<android.widget.TextView>(R.id.week_consumption).text = "${drinksOf(weekMl)} drinks"

		// Baseline and reduction
		val baselineDaily = prefs.getFloat("baseline_daily_ml", 0f).toDouble()
		val baselineWeekly = baselineDaily * 7.0
		val reductionDaily = if (baselineDaily > 0) ((baselineDaily - todayMl) / baselineDaily) * 100 else 0.0
		val reductionWeekly = if (baselineWeekly > 0) ((baselineWeekly - weekMl) / baselineWeekly) * 100 else 0.0
		findViewById<android.widget.TextView>(R.id.tv_reduction_percentage).text = "${String.format("%.1f", reductionDaily)}%"
		findViewById<android.widget.TextView>(R.id.tv_baseline_daily).text = "${drinksOf(baselineDaily)} drinks/day"
		findViewById<android.widget.TextView>(R.id.tv_current_daily).text = "${drinksOf(todayMl)} drinks/day"
		findViewById<android.widget.TextView>(R.id.tv_baseline_weekly).text = "${drinksOf(baselineWeekly)} drinks/week"
		findViewById<android.widget.TextView>(R.id.tv_current_weekly).text = "${drinksOf(weekMl)} drinks/week"
		// Days since baseline
		val baselineDateStr = prefs.getString("baseline_set_date", null)
		if (baselineDateStr != null) {
			val days = java.time.LocalDate.parse(baselineDateStr).until(today).days
			findViewById<android.widget.TextView>(R.id.tv_days_since_baseline).text = "$days days since baseline"
		}

		// Buttons to set baseline/goals
		findViewById<android.view.View>(R.id.btn_set_baseline_progress).setOnClickListener {
			startActivity(android.content.Intent(this, MainActivity::class.java).apply {
				putExtra("open_setup_dialog", true)
			})
		}
		findViewById<android.view.View>(R.id.btn_set_goals_progress).setOnClickListener {
			startActivity(android.content.Intent(this, MainActivity::class.java).apply {
				putExtra("open_setup_dialog", true)
			})
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
					R.id.nav_calendar -> {
						startActivity(android.content.Intent(this@ProgressActivity, CalendarActivity::class.java))
						true
					}
					else -> false
				}
			}
		}
	}
}


