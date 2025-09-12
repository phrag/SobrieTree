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

		val brewLog = BrewLog()
		val metrics = brewLog.getProgressMetrics()
		if (metrics != null) {
			findViewById<android.widget.TextView>(R.id.tv_reduction_percentage).text = "${String.format("%.1f", metrics.reductionPercentageDaily)}%"
			findViewById<android.widget.TextView>(R.id.tv_baseline_daily).text = "${metrics.baselineDailyAverage.toInt()} ml/day"
			findViewById<android.widget.TextView>(R.id.tv_current_daily).text = "${metrics.currentDailyAverage.toInt()} ml/day"
			findViewById<android.widget.TextView>(R.id.tv_baseline_weekly).text = "${metrics.baselineWeeklyAverage.toInt()} ml/week"
			findViewById<android.widget.TextView>(R.id.tv_current_weekly).text = "${metrics.currentWeeklyAverage.toInt()} ml/week"
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


