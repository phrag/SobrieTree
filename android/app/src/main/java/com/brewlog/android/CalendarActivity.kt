package com.brewlog.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import java.time.LocalDate
import android.widget.CalendarView
import org.json.JSONArray

class CalendarActivity : AppCompatActivity() {
    private lateinit var adapter: BeerEntryAdapter
    private val prefsName = "brewlog_prefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)

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

        val today = LocalDate.now()
        setDate(today)

        findViewById<android.widget.TextView>(R.id.tv_selected_date).text = today.toString()
        findViewById<android.view.View>(R.id.btn_add_entry_for_day).setOnClickListener {
            showQuickAddForDate(today)
        }

        findViewById<CalendarView>(R.id.calendar_view).setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selected = LocalDate.of(year, month + 1, dayOfMonth)
            findViewById<android.widget.TextView>(R.id.tv_selected_date).text = selected.toString()
            setDate(selected)
        }

        findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav).apply {
            selectedItemId = R.id.nav_calendar
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> { startActivity(android.content.Intent(this@CalendarActivity, MainActivity::class.java)); true }
                    R.id.nav_progress -> { startActivity(android.content.Intent(this@CalendarActivity, ProgressActivity::class.java)); true }
                    R.id.nav_calendar -> true
                    else -> false
                }
            }
        }
    }

    private fun setDate(date: LocalDate) {
        try {
            val json = BrewLogNative.get_beer_entries_json(date.toString(), date.toString())
            val arr = JSONArray(json)
            val list = List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                BeerEntry(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    alcoholPercentage = o.optDouble("alcohol_percentage", o.optDouble("alcoholPercentage", 0.0)),
                    volumeMl = o.optDouble("volume_ml", o.optDouble("volumeMl", 0.0)),
                    date = o.optString("date"),
                    notes = o.optString("notes", "")
                )
            }
            adapter.submitList(list)
        } catch (_: Exception) {}
    }

    private fun showQuickAddForDate(date: LocalDate) {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val presets = (MainActivity()).getDrinkPresets(prefs)
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
                val res = BrewLogNative.add_beer_entry_full_jni(id, p.name, p.strength.toDouble(), p.volume.toDouble(), date.toString(), "")
                if (res.startsWith("OK")) { setDate(date) }
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showInlineEdit(entry: BeerEntry) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_beer, null)
        dialogView.findViewById<android.widget.EditText>(R.id.et_beer_name).setText(entry.name)
        dialogView.findViewById<android.widget.EditText>(R.id.et_alcohol_percentage).setText(entry.alcoholPercentage.toString())
        dialogView.findViewById<android.widget.EditText>(R.id.et_volume_ml).setText(entry.volumeMl.toString())
        dialogView.findViewById<android.widget.EditText>(R.id.et_notes).setText(entry.notes)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save") { d, _ ->
                val name = dialogView.findViewById<android.widget.EditText>(R.id.et_beer_name).text.toString()
                val strength = dialogView.findViewById<android.widget.EditText>(R.id.et_alcohol_percentage).text.toString().toDoubleOrNull() ?: entry.alcoholPercentage
                val vol = dialogView.findViewById<android.widget.EditText>(R.id.et_volume_ml).text.toString().toDoubleOrNull() ?: entry.volumeMl
                val notes = dialogView.findViewById<android.widget.EditText>(R.id.et_notes).text.toString()
                val r = BrewLogNative.update_beer_entry_jni(entry.id, name, strength, vol, notes)
                if (r.startsWith("OK")) setDate(LocalDate.parse(entry.date))
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteInline(entry: BeerEntry) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Entry")
            .setMessage("Delete this entry?")
            .setPositiveButton("Delete") { d, _ ->
                val r = BrewLogNative.delete_beer_entry_jni(entry.id)
                if (r.startsWith("OK")) setDate(LocalDate.parse(entry.date))
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}


