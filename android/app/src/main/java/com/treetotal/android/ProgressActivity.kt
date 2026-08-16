package com.treetotal.android

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
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.treetotal.android.engine.UnitsEngine
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
        bindUnits()
        showRange(currentRangeDays)
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

    private var currentRangeDays = 7

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
