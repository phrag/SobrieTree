package com.sobrietree.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import java.time.LocalDate
import android.widget.CalendarView
import org.json.JSONArray

class CalendarActivity : AppCompatActivity() {
    private lateinit var adapter: BeerEntryAdapter
    private val prefsName = "sobrietree_prefs"
    private val repo by lazy { EntryRepository() }
    private lateinit var trend: TrendChart

    /** The day the calendar is pointed at; edits refresh this, not the entry's own date. */
    private var currentDate: LocalDate = LocalDate.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)

        SecureWindow.apply(this)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        adapter = BeerEntryAdapter(
            onEditClick = { entry -> showInlineEdit(entry) },
            onDeleteClick = { entry -> deleteInline(entry) }
        )
        val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_day_entries)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        trend = TrendChart(this, findViewById(R.id.line_chart), findViewById(R.id.tv_chart_empty), repo)
        trend.showRange(7)
        findViewById<android.view.View>(R.id.chip_7d).setOnClickListener { trend.showRange(7) }
        findViewById<android.view.View>(R.id.chip_4w).setOnClickListener { trend.showRange(28) }
        findViewById<android.view.View>(R.id.chip_3m).setOnClickListener { trend.showRange(90) }

        val startDate = intent.getStringExtra(EXTRA_DATE)
            ?.let { try { LocalDate.parse(it) } catch (_: Exception) { null } }
            ?: LocalDate.now()
        setDate(startDate)
        syncCalendarView(startDate)

        findViewById<android.view.View>(R.id.btn_yesterday).setOnClickListener {
            val yesterday = LocalDate.now().minusDays(1)
            setDate(yesterday)
            syncCalendarView(yesterday)
        }
        findViewById<android.view.View>(R.id.btn_add_entry_for_day).setOnClickListener { showQuickAddForDate(currentDate) }
        findViewById<android.view.View>(R.id.btn_set_total_for_day).setOnClickListener { showSetTotalDialog(currentDate) }

        findViewById<CalendarView>(R.id.calendar_view).setOnDateChangeListener { _, year, month, dayOfMonth ->
            setDate(LocalDate.of(year, month + 1, dayOfMonth))
        }

        BottomNavHelper.wire(this, findViewById(R.id.bottom_nav), R.id.nav_calendar)
    }

    override fun onResume() {
        super.onResume()
        SecureWindow.apply(this)
        // Edits made on the calendar change the trend, so keep the two in step.
        if (::trend.isInitialized) trend.showRange(trend.currentRangeDays)
    }

    private fun syncCalendarView(date: LocalDate) {
        val millis = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        findViewById<CalendarView>(R.id.calendar_view).setDate(millis, false, true)
    }

    /** Shows the entries logged on the chosen day, with an empty state when there are none. */
    private fun setDate(date: LocalDate) {
        currentDate = date
        val heading = findViewById<android.widget.TextView>(R.id.tv_selected_date)
        try {
            val forDay = repo.getEntries(date, date)
            heading.text = if (forDay.isEmpty()) {
                getString(R.string.calendar_nothing_on_day, date.toString())
            } else {
                date.toString()
            }
            adapter.submitList(forDay)
            if (::trend.isInitialized) trend.showRange(trend.currentRangeDays)
        } catch (_: Exception) {}
    }

    private fun showQuickAddForDate(date: LocalDate) {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val presets = DrinkPresetStore.getPresets(prefs)
        if (presets.isEmpty()) {
            android.widget.Toast.makeText(this, "Add a drink preset first", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val names = presets.map { "${it.volume}ml ${it.name}" }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Add to ${date}")
            .setItems(names) { d, which ->
                val p = presets[which]
                val id = java.util.UUID.randomUUID().toString()
                val res = SobrieTreeNative.add_beer_entry_full_jni(id, p.name, p.abv, p.volume.toDouble(), date.toString(), "")
                if (res.startsWith("OK")) { setDate(date) }
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showInlineEdit(entry: BeerEntry) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_beer, null)
        dialogView.findViewById<android.widget.TextView>(R.id.tv_dialog_title).setText(R.string.edit_drink)
        dialogView.findViewById<android.widget.EditText>(R.id.et_beer_name).setText(entry.name)
        dialogView.findViewById<android.widget.EditText>(R.id.et_alcohol_percentage).setText(entry.alcoholPercentage.toString())
        dialogView.findViewById<android.widget.EditText>(R.id.et_volume_ml).setText(entry.volumeMl.toString())
        dialogView.findViewById<android.widget.EditText>(R.id.et_notes).setText(entry.notes)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialogView.findViewById<android.view.View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<android.view.View>(R.id.btn_save).setOnClickListener {
            val name = dialogView.findViewById<android.widget.EditText>(R.id.et_beer_name).text.toString()
            val strength = dialogView.findViewById<android.widget.EditText>(R.id.et_alcohol_percentage).text.toString().toDoubleOrNull() ?: entry.alcoholPercentage
            val vol = dialogView.findViewById<android.widget.EditText>(R.id.et_volume_ml).text.toString().toDoubleOrNull() ?: entry.volumeMl
            val notes = dialogView.findViewById<android.widget.EditText>(R.id.et_notes).text.toString()
            val r = SobrieTreeNative.update_beer_entry_jni(entry.id, name, strength, vol, notes)
            if (r.startsWith("OK")) setDate(currentDate)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun deleteInline(entry: BeerEntry) {
        val r = SobrieTreeNative.delete_beer_entry_jni(entry.id)
        if (r.startsWith("OK")) setDate(currentDate)
    }

    private fun showSetTotalDialog(date: LocalDate) {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val defaultSizeMl = prefs.getInt("default_beer_size", 500)
        val defaultAlcoholPercentage = prefs.getFloat("default_beer_strength", 5.0f)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "Total drinks for ${date}"
        dialog.setTitle("Set total for day")
            .setView(input)
            .setPositiveButton("Save") { d, _ ->
                val numDrinks = input.text.toString().toIntOrNull() ?: 0
                setTotalForDay(date, numDrinks * defaultSizeMl, defaultAlcoholPercentage.toDouble())
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setTotalForDay(date: LocalDate, targetMl: Int, alcoholPercentage: Double = 0.0) {
        try {
            val json = SobrieTreeNative.get_beer_entries_json(date.toString(), date.toString())
            val arr = JSONArray(json)
            var currentMl = 0.0
            val entries = mutableListOf<BeerEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val e = BeerEntry(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    alcoholPercentage = o.optDouble("alcohol_percentage", o.optDouble("alcoholPercentage", 0.0)),
                    volumeMl = o.optDouble("volume_ml", o.optDouble("volumeMl", 0.0)),
                    date = o.optString("date"),
                    notes = o.optString("notes", "")
                )
                entries.add(e)
                currentMl += e.volumeMl
            }
            val diff = targetMl - currentMl
            if (diff <= 0) {
                setDate(date)
                return
            }
            // Add one synthetic entry to reach total using the provided alcohol percentage
            val id = java.util.UUID.randomUUID().toString()
            val res = SobrieTreeNative.add_beer_entry_full_jni(id, "Adjustment", alcoholPercentage, diff, date.toString(), "auto")
            if (res.startsWith("OK")) setDate(date)
        } catch (_: Exception) {}
    }

    companion object {
        const val EXTRA_DATE = "date"
    }
}
