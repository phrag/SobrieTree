package com.brewlog.android

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: BeerEntryAdapter
    private var brewLog: BrewLog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupRecyclerView()
        setupClickListeners()
        initializeBrewLog()
        loadData()
    }

    private fun setupRecyclerView() {
        adapter = BeerEntryAdapter(
            onEditClick = { entry -> showEditBeerDialog(entry) },
            onDeleteClick = { entry -> deleteBeerEntry(entry) }
        )
        
        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_view).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btn_add_beer).setOnClickListener { showAddBeerDialog() }
        findViewById<View>(R.id.fab_add).setOnClickListener { showAddBeerDialog() }
        findViewById<View>(R.id.btn_set_goals).setOnClickListener { showSetGoalsDialog() }
    }

    private fun initializeBrewLog() {
        try {
            brewLog = BrewLog()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to initialize brew log", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadData() {
        brewLog?.let { log ->
            try {
                val today = LocalDate.now()
                val weekStart = LocalDate.now().minusDays(6)
                
                val todayConsumption = log.getDailyConsumption(today)
                val weekConsumption = log.getWeeklyConsumption(weekStart)
                
                findViewById<android.widget.TextView>(R.id.today_consumption).text = "${todayConsumption.toInt()} ml"
                findViewById<android.widget.TextView>(R.id.week_consumption).text = "${weekConsumption.toInt()} ml"
                
                val entries = log.getBeerEntries(weekStart.toString(), today.toString())
                adapter.submitList(entries)
                
                findViewById<View>(R.id.empty_state).visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
                
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddBeerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_beer, null)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btn_save).setOnClickListener {
            val name = dialogView.findViewById<android.widget.EditText>(R.id.et_beer_name).text.toString()
            val alcoholPercentage = dialogView.findViewById<android.widget.EditText>(R.id.et_alcohol_percentage).text.toString().toDoubleOrNull() ?: 0.0
            val volumeMl = dialogView.findViewById<android.widget.EditText>(R.id.et_volume_ml).text.toString().toDoubleOrNull() ?: 0.0
            val notes = dialogView.findViewById<android.widget.EditText>(R.id.et_notes).text.toString()

            if (name.isNotEmpty() && volumeMl > 0) {
                addBeerEntry(name, alcoholPercentage, volumeMl, notes)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun showEditBeerDialog(entry: BeerEntry) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_beer, null)
        
        // Pre-fill the fields
        dialogView.findViewById<android.widget.EditText>(R.id.et_beer_name).setText(entry.name)
        dialogView.findViewById<android.widget.EditText>(R.id.et_alcohol_percentage).setText(entry.alcoholPercentage.toString())
        dialogView.findViewById<android.widget.EditText>(R.id.et_volume_ml).setText(entry.volumeMl.toString())
        dialogView.findViewById<android.widget.EditText>(R.id.et_notes).setText(entry.notes)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btn_save).setOnClickListener {
            val name = dialogView.findViewById<android.widget.EditText>(R.id.et_beer_name).text.toString()
            val alcoholPercentage = dialogView.findViewById<android.widget.EditText>(R.id.et_alcohol_percentage).text.toString().toDoubleOrNull() ?: 0.0
            val volumeMl = dialogView.findViewById<android.widget.EditText>(R.id.et_volume_ml).text.toString().toDoubleOrNull() ?: 0.0
            val notes = dialogView.findViewById<android.widget.EditText>(R.id.et_notes).text.toString()

            if (name.isNotEmpty() && volumeMl > 0) {
                updateBeerEntry(entry.id, name, alcoholPercentage, volumeMl, notes)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun showSetGoalsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_beer, null)
        
        // Reuse the dialog layout but change the field hints
        dialogView.findViewById<android.widget.EditText>(R.id.et_beer_name).hint = "Daily Target (ml)"
        dialogView.findViewById<android.widget.EditText>(R.id.et_alcohol_percentage).hint = "Weekly Target (ml)"
        dialogView.findViewById<View>(R.id.et_volume_ml).visibility = View.GONE
        dialogView.findViewById<View>(R.id.et_notes).visibility = View.GONE
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Set Goals")
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btn_save).setOnClickListener {
            val dailyTarget = dialogView.findViewById<android.widget.EditText>(R.id.et_beer_name).text.toString().toDoubleOrNull() ?: 0.0
            val weeklyTarget = dialogView.findViewById<android.widget.EditText>(R.id.et_alcohol_percentage).text.toString().toDoubleOrNull() ?: 0.0

            if (dailyTarget >= 0 && weeklyTarget >= 0) {
                setConsumptionGoals(dailyTarget, weeklyTarget)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter valid targets", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun addBeerEntry(name: String, alcoholPercentage: Double, volumeMl: Double, notes: String) {
        brewLog?.let { log ->
            try {
                log.addBeerEntry(name, alcoholPercentage, volumeMl, notes)
                Toast.makeText(this, "Beer entry added successfully", Toast.LENGTH_SHORT).show()
                loadData()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to add beer entry", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateBeerEntry(id: String, name: String, alcoholPercentage: Double, volumeMl: Double, notes: String) {
        brewLog?.let { log ->
            try {
                log.updateBeerEntry(id, name, alcoholPercentage, volumeMl, notes)
                Toast.makeText(this, "Beer entry updated successfully", Toast.LENGTH_SHORT).show()
                loadData()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to update beer entry", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteBeerEntry(entry: BeerEntry) {
        AlertDialog.Builder(this)
            .setTitle("Delete Entry")
            .setMessage("Are you sure you want to delete this beer entry?")
            .setPositiveButton("Delete") { _, _ ->
                brewLog?.let { log ->
                    try {
                        log.deleteBeerEntry(entry.id)
                        Toast.makeText(this, "Beer entry deleted successfully", Toast.LENGTH_SHORT).show()
                        loadData()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Failed to delete beer entry", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setConsumptionGoals(dailyTarget: Double, weeklyTarget: Double) {
        brewLog?.let { log ->
            try {
                val today = LocalDate.now()
                val endDate = LocalDate.now().plusWeeks(4)
                
                log.setConsumptionGoal(dailyTarget, weeklyTarget, today, endDate)
                Toast.makeText(this, "Goals set successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to set goals", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 