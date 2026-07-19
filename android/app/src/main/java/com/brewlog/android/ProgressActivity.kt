package com.brewlog.android

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.time.LocalDate

/**
 * Consumption trend over real logged data. All series are per-day drinks, so
 * the baseline and goal reference lines are per-day values too - the old
 * screen plotted daily points against weekly reference lines.
 */
class ProgressActivity : AppCompatActivity() {

    private val repo by lazy { EntryRepository() }
    private val gamification by lazy { GamificationManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_progress)

        SecureWindow.apply(this)

        setSupportActionBar(findViewById(R.id.toolbar))

        findViewById<View>(R.id.chip_7d).setOnClickListener { showRange(7) }
        findViewById<View>(R.id.chip_4w).setOnClickListener { showRange(28) }
        findViewById<View>(R.id.chip_3m).setOnClickListener { showRange(90) }

        BottomNavHelper.wire(this, findViewById(R.id.bottom_nav), R.id.nav_progress)
    }

    override fun onResume() {
        super.onResume()
        SecureWindow.apply(this)
        loadData()
    }

    private fun loadData() {
        bindStatTiles()
        showRange(currentRangeDays)
    }

    private var currentRangeDays = 7

    private fun bindStatTiles() {
        val state = gamification.homeState()

        val afThisWeek = state.streaks.afDaysThisWeek + if (state.isTodayAf) 1 else 0
        findViewById<TextView>(R.id.tv_af_week).text = getString(R.string.n_of_7, afThisWeek)

        val reduction = state.metrics.reductionWeeklyPct
        val reductionView = findViewById<TextView>(R.id.tv_reduction_week)
        val reductionLabel = findViewById<TextView>(R.id.tv_reduction_week_label)
        if (reduction >= 0) {
            reductionView.text = String.format("%.0f%%", reduction)
            reductionView.setTextColor(ContextCompat.getColor(this, R.color.state_positive))
            reductionLabel.text = getString(R.string.less_than_baseline)
        } else {
            // Above baseline this week: neutral tone, never red
            reductionView.text = String.format("%.0f%%", -reduction)
            reductionView.setTextColor(ContextCompat.getColor(this, R.color.state_neutral))
            reductionLabel.text = getString(R.string.more_than_baseline)
        }

        val moneyTile = findViewById<View>(R.id.tile_money_progress)
        if (state.moneyAvailable) {
            moneyTile.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tv_money_progress).text = String.format("%.0f", state.moneySaved)
        } else {
            moneyTile.visibility = View.GONE
        }
    }

    private fun showRange(days: Int) {
        currentRangeDays = days
        val chart = findViewById<LineChart>(R.id.line_chart)
        val emptyView = findViewById<TextView>(R.id.tv_chart_empty)

        val prefs = AppPrefs(this)
        val sizeMl = prefs.defaultDrinkSizeMl.toDouble().coerceAtLeast(1.0)
        val today = gamification.todayEffective()
        val startDate = today.minusDays((days - 1).toLong())

        val totals = repo.getDailyTotals(startDate, today)
        val points = (0 until days).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            offset to ((totals[date] ?: 0.0) / sizeMl).toFloat()
        }

        val hasData = totals.values.any { it > 0.0 }
        if (!hasData) {
            chart.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            return
        }
        chart.visibility = View.VISIBLE
        emptyView.visibility = View.GONE

        // Per-day reference values in drinks
        val baselinePerDay = (prefs.baselineDailyMl / sizeMl).toFloat()
        val goalDailyMl = if (prefs.goalDailyMl > 0) prefs.goalDailyMl else prefs.baselineDailyMl
        val goalPerDay = (goalDailyMl / sizeMl).toFloat()

        renderChart(chart, points, baselinePerDay, goalPerDay, startDate)
    }

    private fun renderChart(
        chart: LineChart,
        points: List<Pair<Int, Float>>,
        baseline: Float,
        goal: Float,
        startDate: LocalDate
    ) {
        val actualColor = ContextCompat.getColor(this, R.color.chart_actual)
        val baselineColor = ContextCompat.getColor(this, R.color.chart_baseline)
        val goalColor = ContextCompat.getColor(this, R.color.chart_goal)
        val axisTextColor = ContextCompat.getColor(this, R.color.chart_axis_text)
        val gridColor = ContextCompat.getColor(this, R.color.chart_grid)

        val current = LineDataSet(points.map { Entry(it.first.toFloat(), it.second) }, getString(R.string.chart_actual_label)).apply {
            color = actualColor
            setDrawCircles(points.size <= 7)
            setCircleColor(actualColor)
            circleRadius = 3f
            setDrawCircleHole(false)
            lineWidth = 2.5f
            setDrawValues(false)
        }
        val dataSets = mutableListOf<ILineDataSet>(current)
        if (baseline > 0) {
            dataSets.add(LineDataSet(points.map { Entry(it.first.toFloat(), baseline) }, getString(R.string.chart_baseline_label)).apply {
                color = baselineColor
                setDrawCircles(false)
                enableDashedLine(10f, 6f, 0f)
                lineWidth = 1.5f
                setDrawValues(false)
            })
        }
        if (goal > 0) {
            dataSets.add(LineDataSet(points.map { Entry(it.first.toFloat(), goal) }, getString(R.string.chart_goal_label)).apply {
                color = goalColor
                setDrawCircles(false)
                enableDashedLine(6f, 6f, 0f)
                lineWidth = 1.5f
                setDrawValues(false)
            })
        }

        chart.data = LineData(dataSets)
        chart.description.isEnabled = false
        chart.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        chart.legend.apply {
            verticalAlignment = Legend.LegendVerticalAlignment.TOP
            horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
            textColor = axisTextColor
            textSize = 12f
        }

        chart.axisLeft.apply {
            textColor = axisTextColor
            textSize = 12f
            setDrawGridLines(true)
            this.gridColor = gridColor
            setDrawAxisLine(false)
            granularity = 1f
            axisMinimum = 0f
            val maxVal = maxOf(points.maxOfOrNull { it.second } ?: 0f, baseline, goal)
            axisMaximum = kotlin.math.ceil(maxVal).coerceAtLeast(2f) + 1f
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String =
                    value.toInt().toString()
            }
        }
        chart.axisRight.isEnabled = false

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textColor = axisTextColor
            textSize = 12f
            setDrawGridLines(false)
            setDrawAxisLine(true)
            axisLineColor = gridColor
            granularity = 1f
            setLabelCount(minOf(points.size, 7), false)
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                    val date = startDate.plusDays(value.toInt().toLong())
                    return "${date.dayOfMonth}/${date.monthValue}"
                }
            }
        }

        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)

        chart.setDrawMarkers(true)
        chart.marker = object : com.github.mikephil.charting.components.MarkerView(this, R.layout.marker_view) {
            private val markerText = findViewById<TextView>(R.id.marker_text)
            override fun refreshContent(e: Entry?, highlight: com.github.mikephil.charting.highlight.Highlight?) {
                if (e != null && highlight != null) {
                    val date = startDate.plusDays(e.x.toInt().toLong())
                    val label = chart.data?.getDataSetByIndex(highlight.dataSetIndex)?.label ?: ""
                    val drinks = if (e.y == e.y.toInt().toFloat()) "${e.y.toInt()}" else String.format("%.1f", e.y)
                    markerText.text = "${date.dayOfMonth}/${date.monthValue}\n$label: $drinks drinks"
                }
                super.refreshContent(e, highlight)
            }
        }

        chart.invalidate()
    }
}
