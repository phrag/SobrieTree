package com.brewlog.android

import android.app.DatePickerDialog
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
    private var selectedStartDate: LocalDate? = null
    private var selectedEndDate: LocalDate? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

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
        
        // Add baseline button click listener
        findViewById<View>(R.id.btn_baseline).setOnClickListener { showBaselineDialog() }
        findViewById<View>(R.id.btn_progress).setOnClickListener { showProgressDialog() }
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
                
                findViewById<View>(R.id.empty_state).visibility = if (entries.isEmpty()) View.GONE else View.GONE
                
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddBeerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_beer, null)
        val prefs = getSharedPreferences("brewlog_prefs", MODE_PRIVATE)
        val defaultSize = prefs.getInt("default_beer_size", 500)
        val defaultStrength = prefs.getFloat("default_beer_strength", 5.0f)

        val nameEdit = dialogView.findViewById<android.widget.EditText>(R.id.et_beer_name)
        val strengthEdit = dialogView.findViewById<android.widget.EditText>(R.id.et_alcohol_percentage)
        val volumeEdit = dialogView.findViewById<android.widget.EditText>(R.id.et_volume_ml)
        val notesEdit = dialogView.findViewById<android.widget.EditText>(R.id.et_notes)
        val setDefaultBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_set_default_beer)
        val makeDefaultCheckbox = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.checkbox_make_default_beer)

        // Pre-fill with defaults
        volumeEdit.setText(defaultSize.toString())
        strengthEdit.setText(defaultStrength.toString())

        setDefaultBtn.setOnClickListener {
            val size = volumeEdit.text.toString().toIntOrNull()
            val strength = strengthEdit.text.toString().toFloatOrNull()
            if (size != null && size > 0 && strength != null && strength > 0f) {
                prefs.edit().putInt("default_beer_size", size).putFloat("default_beer_strength", strength).apply()
                Toast.makeText(this, "Set as default beer", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Enter valid size and strength first", Toast.LENGTH_SHORT).show()
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btn_save).setOnClickListener {
            val name = nameEdit.text.toString()
            val alcoholPercentage = strengthEdit.text.toString().toDoubleOrNull() ?: 0.0
            val volumeMl = volumeEdit.text.toString().toDoubleOrNull() ?: 0.0
            val notes = notesEdit.text.toString()

            if (name.isNotEmpty() && volumeMl > 0) {
                addBeerEntry(name, alcoholPercentage, volumeMl, notes)
                if (makeDefaultCheckbox.isChecked) {
                    prefs.edit().putInt("default_beer_size", volumeMl.toInt()).putFloat("default_beer_strength", alcoholPercentage.toFloat()).apply()
                    Toast.makeText(this, "Set as default beer", Toast.LENGTH_SHORT).show()
                }
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

    private fun showBaselineDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_baseline, null)
        
        val prefs = getSharedPreferences("brewlog_prefs", MODE_PRIVATE)
        val defaultBeerSize = prefs.getInt("default_beer_size", 500)
        val startDateView = dialogView.findViewById<android.widget.TextView>(R.id.tv_start_date)
        val endDateView = dialogView.findViewById<android.widget.TextView>(R.id.tv_end_date)
        val totalConsumptionEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_total_consumption)
        val dailyAverageEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_daily_average)
        val beerCountEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_beer_count)
        val btnPreset500ml = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_preset_500ml)
        val btnPreset330ml = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_preset_330ml)

        // Update preset button text for default beer size
        btnPreset500ml.text = "${defaultBeerSize}ml"

        btnPreset500ml.setOnClickListener {
            val current = totalConsumptionEdit.text.toString().toDoubleOrNull() ?: 0.0
            val newTotal = current + defaultBeerSize
            totalConsumptionEdit.setText(newTotal.toInt().toString())
            // Update beer count
            beerCountEdit.setText((newTotal / defaultBeerSize).toInt().toString())
        }
        btnPreset330ml.setOnClickListener {
            val current = totalConsumptionEdit.text.toString().toDoubleOrNull() ?: 0.0
            val newTotal = current + 330.0
            totalConsumptionEdit.setText(newTotal.toInt().toString())
            // Update beer count (rounded down)
            beerCountEdit.setText((newTotal / defaultBeerSize).toInt().toString())
        }

        // Sync beer count <-> total consumption
        beerCountEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val count = s?.toString()?.toIntOrNull() ?: 0
                val ml = count * defaultBeerSize
                totalConsumptionEdit.setText(if (ml > 0) ml.toString() else "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        totalConsumptionEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val ml = s?.toString()?.toIntOrNull() ?: 0
                if (ml % defaultBeerSize == 0 && ml > 0) {
                    beerCountEdit.setText((ml / defaultBeerSize).toString())
                } else if (ml == 0) {
                    beerCountEdit.setText("")
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        startDateView.setOnClickListener {
            showDatePicker { date ->
                selectedStartDate = date
                startDateView.text = date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
            }
        }
        
        endDateView.setOnClickListener {
            showDatePicker { date ->
                selectedEndDate = date
                endDateView.text = date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
            }
        }
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btn_calculate).setOnClickListener {
            if (selectedStartDate != null && selectedEndDate != null) {
                if (selectedStartDate!! <= selectedEndDate!!) {
                    val totalConsumption = totalConsumptionEdit.text.toString().toDoubleOrNull()
                    val dailyAverage = dailyAverageEdit.text.toString().toDoubleOrNull()
                    
                    if (totalConsumption == null && dailyAverage == null) {
                        Toast.makeText(this, "Please enter either total consumption or daily average", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    
                    if (totalConsumption != null && totalConsumption < 0) {
                        Toast.makeText(this, "Total consumption cannot be negative", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    
                    if (dailyAverage != null && dailyAverage < 0) {
                        Toast.makeText(this, "Daily average cannot be negative", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    
                    try {
                        val baseline = brewLog?.setBaseline(selectedStartDate!!, selectedEndDate!!, totalConsumption, dailyAverage)
                        Toast.makeText(
                            this, 
                            "Baseline set: ${baseline?.averageDailyConsumption?.toInt()} ml/day average", 
                            Toast.LENGTH_LONG
                        ).show()
                        dialog.dismiss()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Failed to set baseline: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Start date must be before end date", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please select both start and end dates", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun showProgressDialog() {
        val progressMetrics = brewLog?.getProgressMetrics()
        
        if (progressMetrics == null) {
            Toast.makeText(this, "No baseline set. Please set a baseline first.", Toast.LENGTH_LONG).show()
            return
        }
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_progress_metrics, null)
        
        // Update progress metrics
        dialogView.findViewById<android.widget.TextView>(R.id.tv_reduction_percentage).text = 
            "${String.format("%.1f", progressMetrics.reductionPercentage)}%"
        
        dialogView.findViewById<android.widget.TextView>(R.id.tv_days_since_baseline).text = 
            "${progressMetrics.daysSinceBaseline} days since baseline"
        
        dialogView.findViewById<android.widget.TextView>(R.id.tv_baseline_daily).text = 
            "${progressMetrics.baselineDailyAverage.toInt()} ml/day"
        
        dialogView.findViewById<android.widget.TextView>(R.id.tv_baseline_weekly).text = 
            "${progressMetrics.baselineWeeklyAverage.toInt()} ml/week"
        
        dialogView.findViewById<android.widget.TextView>(R.id.tv_current_daily).text = 
            "${progressMetrics.currentDailyAverage.toInt()} ml/day"
        
        dialogView.findViewById<android.widget.TextView>(R.id.tv_current_weekly).text = 
            "${progressMetrics.currentWeeklyAverage.toInt()} ml/week"
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btn_reset_baseline).setOnClickListener {
            resetBaseline()
            dialog.dismiss()
        }
        
        dialogView.findViewById<View>(R.id.btn_close).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showDatePicker(onDateSelected: (LocalDate) -> Unit) {
        val today = LocalDate.now()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                onDateSelected(selectedDate)
            },
            today.year,
            today.monthValue - 1,
            today.dayOfMonth
        ).show()
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

    private fun resetBaseline() {
        brewLog?.let { log ->
            try {
                log.clearBaseline()
                Toast.makeText(this, "Baseline reset successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to reset baseline", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val prefs = getSharedPreferences("brewlog_prefs", MODE_PRIVATE)
        val defaultSize = prefs.getInt("default_beer_size", 500)
        val defaultStrength = prefs.getFloat("default_beer_strength", 5.0f)

        val beerSizeEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_beer_size)
        val beerStrengthEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_beer_strength)
        val beerSizeLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.beer_size_layout)
        val beerStrengthLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.beer_strength_layout)

        beerSizeEdit.setText(defaultSize.toString())
        beerStrengthEdit.setText(defaultStrength.toString())

        val dialog = AlertDialog.Builder(this)
            .setTitle(null)
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val size = beerSizeEdit.text.toString().toIntOrNull()
                val strength = beerStrengthEdit.text.toString().toFloatOrNull()
                var valid = true
                if (size == null || size <= 0) {
                    beerSizeLayout.error = "Enter a valid size (ml)"
                    valid = false
                } else {
                    beerSizeLayout.error = null
                }
                if (strength == null || strength <= 0f) {
                    beerStrengthLayout.error = "Enter a valid % ABV"
                    valid = false
                } else {
                    beerStrengthLayout.error = null
                }
                if (valid) {
                    prefs.edit().putInt("default_beer_size", size!!).putFloat("default_beer_strength", strength!!).apply()
                    Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
} 