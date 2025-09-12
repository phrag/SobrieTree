package com.brewlog.android

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class Baseline(
    val id: String,
    val averageDailyConsumption: Double,
    val averageWeeklyConsumption: Double,
    val calculatedDate: String,
    val periodStartDate: String,
    val periodEndDate: String
)

data class ProgressMetrics(
    val currentDailyAverage: Double,
    val currentWeeklyAverage: Double,
    val currentMonthlyAverage: Double,
    val reductionPercentageDaily: Double,
    val reductionPercentageWeekly: Double,
    val reductionPercentageMonthly: Double,
    val daysSinceBaseline: Int,
    val baselineDailyAverage: Double,
    val baselineWeeklyAverage: Double,
    val baselineMonthlyAverage: Double
)

class BrewLog {
    // For now, we'll use a simple in-memory storage
    // Later we can integrate with the Rust backend
    private val entries = mutableListOf<BeerEntry>()
    private var nextId = 1
    private var dailyGoal = 0.0
    private var weeklyGoal = 0.0
    private var baseline: Baseline? = null

    fun addBeerEntry(name: String, alcoholPercentage: Double, volumeMl: Double, notes: String) {
        val entry = BeerEntry(
            id = nextId++.toString(),
            name = name,
            alcoholPercentage = alcoholPercentage,
            volumeMl = volumeMl,
            date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
            notes = notes
        )
        entries.add(entry)
    }

    fun getDailyConsumption(date: LocalDate): Double {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return entries.filter { it.date == dateStr }.sumOf { it.volumeMl }
    }

    fun getWeeklyConsumption(weekStartDate: LocalDate): Double {
        val endDate = weekStartDate.plusDays(6)
        return entries.filter { 
            val entryDate = LocalDate.parse(it.date)
            entryDate >= weekStartDate && entryDate <= endDate
        }.sumOf { it.volumeMl }
    }

    fun setConsumptionGoal(dailyTarget: Double, weeklyTarget: Double, startDate: LocalDate, endDate: LocalDate) {
        dailyGoal = dailyTarget
        weeklyGoal = weeklyTarget
    }

    fun getBeerEntries(startDate: String, endDate: String): List<BeerEntry> {
        // For now, return all entries since we're using in-memory storage
        // In a real implementation, you'd filter by date
        return entries.toList().sortedByDescending { it.date }
    }

    fun updateBeerEntry(id: String, name: String, alcoholPercentage: Double, volumeMl: Double, notes: String) {
        val index = entries.indexOfFirst { it.id == id }
        if (index != -1) {
            entries[index] = BeerEntry(
                id = id,
                name = name,
                alcoholPercentage = alcoholPercentage,
                volumeMl = volumeMl,
                date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                notes = notes
            )
        } else {
            throw RuntimeException("Beer entry not found")
        }
    }

    fun deleteBeerEntry(id: String) {
        val index = entries.indexOfFirst { it.id == id }
        if (index != -1) {
            entries.removeAt(index)
        } else {
            throw RuntimeException("Beer entry not found")
        }
    }

    fun getDailyGoal(): Double = dailyGoal
    fun getWeeklyGoal(): Double = weeklyGoal

    // Baseline functionality
    fun calculateBaseline(startDate: LocalDate, endDate: LocalDate): Baseline {
        val periodEntries = entries.filter { 
            val entryDate = LocalDate.parse(it.date)
            entryDate >= startDate && entryDate <= endDate
        }
        
        if (periodEntries.isEmpty()) {
            throw RuntimeException("No entries found for baseline calculation")
        }

        val totalVolume = periodEntries.sumOf { it.volumeMl }
        val daysInPeriod = startDate.until(endDate).days + 1
        val averageDaily = totalVolume / daysInPeriod
        val averageWeekly = averageDaily * 7.0

        val baseline = Baseline(
            id = nextId++.toString(),
            averageDailyConsumption = averageDaily,
            averageWeeklyConsumption = averageWeekly,
            calculatedDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
            periodStartDate = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            periodEndDate = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        )
        
        this.baseline = baseline
        return baseline
    }

    fun setBaseline(startDate: LocalDate, endDate: LocalDate, totalConsumption: Double? = null, dailyAverage: Double? = null): Baseline {
        val daysInPeriod = startDate.until(endDate).days + 1
        
        val averageDaily = when {
            totalConsumption != null -> totalConsumption / daysInPeriod
            dailyAverage != null -> dailyAverage
            else -> throw RuntimeException("Either total consumption or daily average must be provided")
        }
        
        val averageWeekly = averageDaily * 7.0

        val baseline = Baseline(
            id = nextId++.toString(),
            averageDailyConsumption = averageDaily,
            averageWeeklyConsumption = averageWeekly,
            calculatedDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
            periodStartDate = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            periodEndDate = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        )
        
        this.baseline = baseline
        return baseline
    }

    fun getCurrentBaseline(): Baseline? = baseline

    fun getProgressMetrics(): ProgressMetrics? {
        val currentBaseline = baseline ?: return null
        
        val baselineDate = LocalDate.parse(currentBaseline.calculatedDate)
        val today = LocalDate.now()
        val daysSinceBaseline = baselineDate.until(today).days

        // Calculate current averages
        // Daily (last 1 day)
        val currentDayEntries = entries.filter { 
            val entryDate = LocalDate.parse(it.date)
            entryDate == today
        }
        val currentDayVolume = currentDayEntries.sumOf { it.volumeMl }
        val currentDailyAverage = currentDayVolume.toDouble()

        // Weekly (last 7 days)
        val weekStart = today.minusDays(6)
        val currentWeekEntries = entries.filter { 
            val entryDate = LocalDate.parse(it.date)
            entryDate >= weekStart && entryDate <= today
        }
        val currentWeekVolume = currentWeekEntries.sumOf { it.volumeMl }
        val currentWeeklyAverage = currentWeekVolume.toDouble()

        // Monthly (last 30 days)
        val monthStart = today.minusDays(29)
        val currentMonthEntries = entries.filter { 
            val entryDate = LocalDate.parse(it.date)
            entryDate >= monthStart && entryDate <= today
        }
        val currentMonthVolume = currentMonthEntries.sumOf { it.volumeMl }
        val currentMonthlyAverage = currentMonthVolume.toDouble()

        // Baseline monthly average
        val baselineMonthlyAverage = currentBaseline.averageDailyConsumption * 30.0

        // Calculate reduction percentages
        val reductionPercentageDaily = if (currentBaseline.averageDailyConsumption > 0) {
            ((currentBaseline.averageDailyConsumption - currentDailyAverage) / currentBaseline.averageDailyConsumption) * 100
        } else 0.0
        val reductionPercentageWeekly = if (currentBaseline.averageWeeklyConsumption > 0) {
            ((currentBaseline.averageWeeklyConsumption - currentWeeklyAverage) / currentBaseline.averageWeeklyConsumption) * 100
        } else 0.0
        val reductionPercentageMonthly = if (baselineMonthlyAverage > 0) {
            ((baselineMonthlyAverage - currentMonthlyAverage) / baselineMonthlyAverage) * 100
        } else 0.0

        return ProgressMetrics(
            currentDailyAverage = currentDailyAverage,
            currentWeeklyAverage = currentWeeklyAverage,
            currentMonthlyAverage = currentMonthlyAverage,
            reductionPercentageDaily = reductionPercentageDaily,
            reductionPercentageWeekly = reductionPercentageWeekly,
            reductionPercentageMonthly = reductionPercentageMonthly,
            daysSinceBaseline = daysSinceBaseline,
            baselineDailyAverage = currentBaseline.averageDailyConsumption,
            baselineWeeklyAverage = currentBaseline.averageWeeklyConsumption,
            baselineMonthlyAverage = baselineMonthlyAverage
        )
    }

    fun clearBaseline() {
        baseline = null
    }
} 