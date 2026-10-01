package com.sobrietree.android

import android.content.Context
import android.view.View
import android.widget.TextView
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
 * The per-day drinks trend chart, shared by Progress and Calendar. All series
 * are per-day drinks, so the baseline and goal reference lines are per-day too.
 */
class TrendChart(
    private val context: Context,
    private val chart: LineChart,
    private val emptyView: TextView,
    private val repo: EntryRepository = EntryRepository()
) {

    /** The range last asked for - may be [RANGE_ALL], which is resolved on each draw. */
    var currentRangeDays = 7
        private set

    /** Hooks the range chips (present in both layouts that host this chart) up to the chart. */
    fun wireChips(root: View) {
        val ranges = mapOf(
            R.id.chip_7d to 7, R.id.chip_4w to 28, R.id.chip_3m to 90,
            R.id.chip_6m to 180, R.id.chip_1y to 365, R.id.chip_all to RANGE_ALL
        )
        ranges.forEach { (id, days) -> root.findViewById<View>(id).setOnClickListener { showRange(days) } }
    }

    fun showRange(requested: Int) {
        currentRangeDays = requested

        val prefs = AppPrefs(context)
        val sizeMl = prefs.defaultDrinkSizeMl.toDouble().coerceAtLeast(1.0)
        val today = GamificationManager(context).todayEffective()
        val days = if (requested == RANGE_ALL) {
            // From the first logged day, but never a window too short to read as a trend.
            val first = repo.getEntries(today.minusDays(MAX_ALL_DAYS), today)
                .mapNotNull { try { LocalDate.parse(it.date) } catch (_: Exception) { null } }
                .minOrNull()
            val span = if (first == null) 0 else java.time.temporal.ChronoUnit.DAYS.between(first, today).toInt() + 1
            span.coerceAtLeast(7)
        } else requested
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
        val actualColor = ContextCompat.getColor(context, R.color.chart_actual)
        val baselineColor = ContextCompat.getColor(context, R.color.chart_baseline)
        val goalColor = ContextCompat.getColor(context, R.color.chart_goal)
        val axisTextColor = ContextCompat.getColor(context, R.color.chart_axis_text)
        val gridColor = ContextCompat.getColor(context, R.color.chart_grid)

        val current = LineDataSet(points.map { Entry(it.first.toFloat(), it.second) }, context.getString(R.string.chart_actual_label)).apply {
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
            dataSets.add(LineDataSet(points.map { Entry(it.first.toFloat(), baseline) }, context.getString(R.string.chart_baseline_label)).apply {
                color = baselineColor
                setDrawCircles(false)
                enableDashedLine(10f, 6f, 0f)
                lineWidth = 1.5f
                setDrawValues(false)
            })
        }
        if (goal > 0) {
            dataSets.add(LineDataSet(points.map { Entry(it.first.toFloat(), goal) }, context.getString(R.string.chart_goal_label)).apply {
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
        chart.marker = object : com.github.mikephil.charting.components.MarkerView(context, R.layout.marker_view) {
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

    companion object {
        const val RANGE_ALL = -1
        private const val MAX_ALL_DAYS = 3650L
    }
}
