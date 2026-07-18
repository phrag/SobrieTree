package com.brewlog.android.engine

import com.brewlog.android.BeerEntry
import java.time.LocalDate

object TestFixtures {

    var nextId = 1

    fun entry(date: LocalDate, volumeMl: Double = 500.0, abv: Double = 5.0): BeerEntry =
        BeerEntry(
            id = (nextId++).toString(),
            name = "Beer",
            alcoholPercentage = abv,
            volumeMl = volumeMl,
            date = date.toString(),
            notes = ""
        )

    /** Ledger for a fixed window; drinkDays get one 500ml/5% entry each. */
    fun ledger(
        trackingStart: LocalDate,
        today: LocalDate,
        drinkDays: List<LocalDate> = emptyList(),
        entries: List<BeerEntry> = emptyList()
    ): DayLedger = DayLedger(
        entries = entries + drinkDays.map { entry(it) },
        trackingStart = trackingStart,
        todayEffective = today
    )
}
