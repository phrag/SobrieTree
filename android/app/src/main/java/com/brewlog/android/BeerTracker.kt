package com.brewlog.android

import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BeerTracker {
    // For now, we'll use a simple in-memory storage
    // Later we can integrate with the Rust backend
    private val entries = mutableListOf<BeerEntry>()
    private var nextId = 1
    private var dailyGoal = 500.0
    private var weeklyGoal = 3500.0

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
} 