package com.brewlog.android

data class BeerEntry(
    val id: String,
    val name: String,
    val alcoholPercentage: Double,
    val volumeMl: Double,
    val date: String,
    val notes: String
) 