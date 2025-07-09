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
import android.widget.ArrayAdapter
import org.json.JSONArray
import org.json.JSONObject
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.brewlog.android.DrinkPreset
import com.brewlog.android.DrinkType
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatDelegate

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

                // Get default or most recent drink size
                val prefs = getSharedPreferences("brewlog_prefs", MODE_PRIVATE)
                val drinks = getDrinkPresets(prefs)
                val defaultDrink = drinks.firstOrNull { it.favorite } ?: drinks.firstOrNull()
                val drinkVolume = defaultDrink?.volume?.toDouble() ?: 500.0
                val drinkName = defaultDrink?.name ?: "beer"
                fun formatMlAndDrinks(ml: Double): String {
                    val drinksCount = if (drinkVolume > 0) (ml / drinkVolume) else 0.0
                    return "${ml.toInt()} ml (${drinksCount.toInt()} × ${drinkVolume.toInt()}ml $drinkName${if (drinksCount.toInt() == 1) "" else "s"})"
                }

                findViewById<android.widget.TextView>(R.id.today_consumption).text = formatMlAndDrinks(todayConsumption)
                findViewById<android.widget.TextView>(R.id.week_consumption).text = formatMlAndDrinks(weekConsumption)

                val entries = log.getBeerEntries(weekStart.toString(), today.toString())
                adapter.submitList(entries)

                findViewById<View>(R.id.empty_state).visibility = if (entries.isEmpty()) View.GONE else View.GONE

                // Update live progress cards
                val progressMetrics = log.getProgressMetrics()
                val dailyView = findViewById<android.widget.TextView>(R.id.progress_daily)
                val weeklyView = findViewById<android.widget.TextView>(R.id.progress_weekly)
                val monthlyView = findViewById<android.widget.TextView>(R.id.progress_monthly)
                if (progressMetrics != null) {
                    dailyView.text = "${String.format("%.1f", progressMetrics.reductionPercentageDaily)}%"
                    weeklyView.text = "${String.format("%.1f", progressMetrics.reductionPercentageWeekly)}%"
                } else {
                    dailyView.text = "--"
                    weeklyView.text = "--"
                }

            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun getDrinkPresets(prefs: android.content.SharedPreferences): List<DrinkPreset> {
        val json = prefs.getString("drink_presets", "[]") ?: "[]"
        val arr = JSONArray(json)
        return List(arr.length()) { i -> DrinkPreset.fromJson(arr.getJSONObject(i)) }
    }

    fun saveDrinkPresets(prefs: android.content.SharedPreferences, presets: List<DrinkPreset>) {
        val arr = JSONArray()
        presets.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("drink_presets", arr.toString()).apply()
    }

    fun addDrinkPreset(prefs: android.content.SharedPreferences, preset: DrinkPreset) {
        val presets = getDrinkPresets(prefs).toMutableList()
        if (presets.none { it.name == preset.name && it.type == preset.type && it.volume == preset.volume && it.strength == preset.strength }) {
            presets.add(preset)
            saveDrinkPresets(prefs, presets)
        }
    }

    private fun showDrinkManagerDialog(
        onDrinkSelected: (DrinkPreset) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_drink_manager, null)
        val prefs = getSharedPreferences("brewlog_prefs", MODE_PRIVATE)
        val rv = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_drinks)
        val addBtn = dialogView.findViewById<MaterialButton>(R.id.btn_add_new_drink)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        var drinks = getDrinkPresets(prefs).toMutableList()
        lateinit var adapter: DrinkManagerAdapter
        adapter = DrinkManagerAdapter(
            drinks,
            onSelect = {
                onDrinkSelected(it)
                dialog.dismiss()
            },
            onEdit = { drink ->
                showEditDrinkDialog(drink) { updated ->
                    val idx = drinks.indexOfFirst { it.name == drink.name && it.type == drink.type }
                    if (idx != -1) {
                        drinks[idx] = updated
                        saveDrinkPresets(prefs, drinks)
                        adapter.updateDrinks(drinks)
                    }
                }
            },
            onDelete = { drink ->
                drinks.remove(drink)
                saveDrinkPresets(prefs, drinks)
                adapter.updateDrinks(drinks)
            },
            onFavorite = { drink ->
                drinks = drinks.map { it.copy(favorite = it == drink) }.toMutableList()
                saveDrinkPresets(prefs, drinks)
                adapter.updateDrinks(drinks)
            }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        addBtn.setOnClickListener {
            showEditDrinkDialog(null) { newDrink ->
                drinks.add(newDrink)
                saveDrinkPresets(prefs, drinks)
                adapter.updateDrinks(drinks)
            }
        }

        dialog.show()
    }

    private fun showEditDrinkDialog(
        drink: DrinkPreset?,
        onSave: (DrinkPreset) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_beer, null)
        val nameEdit = dialogView.findViewById<TextInputEditText>(R.id.et_beer_name)
        val strengthEdit = dialogView.findViewById<TextInputEditText>(R.id.et_alcohol_percentage)
        val volumeEdit = dialogView.findViewById<TextInputEditText>(R.id.et_volume_ml)
        val typeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.spinner_drink_type)
        val typeNames = DrinkType.values().map { it.displayName }
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeNames)
        typeSpinner.adapter = typeAdapter
        if (drink != null) {
            nameEdit.setText(drink.name)
            strengthEdit.setText(drink.strength.toString())
            volumeEdit.setText(drink.volume.toString())
            typeSpinner.setSelection(DrinkType.values().indexOf(drink.type))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (drink == null) "Add Drink" else "Edit Drink")
            .setView(dialogView)
            .setPositiveButton("Save") { d, _ ->
                val name = nameEdit.text.toString()
                val strength = strengthEdit.text.toString().toFloatOrNull() ?: 0f
                val volume = volumeEdit.text.toString().toIntOrNull() ?: 0
                val type = DrinkType.values()[typeSpinner.selectedItemPosition]
                if (name.isNotEmpty() && strength > 0 && volume > 0) {
                    onSave(DrinkPreset(name, type, volume, strength, drink?.favorite ?: false))
                    d.dismiss()
                }
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()
        dialog.show()
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
        val typeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.spinner_drink_type)
        val typeNames = DrinkType.values().map { it.displayName }
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeNames)
        typeSpinner.adapter = typeAdapter
        typeSpinner.setSelection(0)

        val chooseDrinkBtn = MaterialButton(this).apply {
            text = "Choose Drink"
            setOnClickListener {
                showDrinkManagerDialog { drink ->
                    nameEdit.setText(drink.name)
                    volumeEdit.setText(drink.volume.toString())
                    strengthEdit.setText(drink.strength.toString())
                    typeSpinner.setSelection(DrinkType.values().indexOf(drink.type))
                }
            }
        }
        (dialogView as LinearLayout).addView(chooseDrinkBtn, 0)

        // Pre-fill with defaults
        volumeEdit.setText(defaultSize.toString())
        strengthEdit.setText(defaultStrength.toString())

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btn_save).setOnClickListener {
            val name = nameEdit.text.toString()
            val alcoholPercentage = strengthEdit.text.toString().toDoubleOrNull() ?: 0.0
            val volumeMl = volumeEdit.text.toString().toDoubleOrNull() ?: 0.0
            val notes = notesEdit.text.toString()
            val type = DrinkType.values()[typeSpinner.selectedItemPosition]

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
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_goals, null)
        val dailyGoalEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_daily_goal)
        val weeklyGoalEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_weekly_goal)
        val layoutDaily = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layout_daily_goal)
        val layoutWeekly = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layout_weekly_goal)
        // Add summary TextView
        val summaryView = android.widget.TextView(this)
        summaryView.setTextColor(resources.getColor(R.color.text_secondary, null))
        summaryView.textSize = 14f
        val parent = dialogView as android.widget.LinearLayout
        parent.addView(summaryView, parent.indexOfChild(dialogView.findViewById(R.id.layout_weekly_goal)) + 1)

        // Get default or most recent drink size
        val prefs = getSharedPreferences("brewlog_prefs", MODE_PRIVATE)
        val drinks = getDrinkPresets(prefs)
        val defaultDrink = drinks.firstOrNull { it.favorite } ?: drinks.firstOrNull()
        val drinkVolume = defaultDrink?.volume?.toDouble() ?: 500.0
        val drinkName = defaultDrink?.name ?: "beer"

        fun updateSummary() {
            val dailyDrinks = dailyGoalEdit.text.toString().toDoubleOrNull() ?: 0.0
            val weeklyDrinks = dailyDrinks * 7
            weeklyGoalEdit.setText(weeklyDrinks.toInt().toString())
            summaryView.text =
                "Daily: ${dailyDrinks.toInt()} drinks\n" +
                "Weekly: ${weeklyDrinks.toInt()} drinks"
        }
        dailyGoalEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { updateSummary() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        // Optionally, pre-fill with current goals
        val currentDaily = brewLog?.getDailyGoal() ?: 0.0
        val currentWeekly = brewLog?.getWeeklyGoal() ?: 0.0
        if (currentDaily > 0) dailyGoalEdit.setText(currentDaily.toInt().toString())
        if (currentWeekly > 0) weeklyGoalEdit.setText((currentWeekly / 500).toInt().toString())
        updateSummary()
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btn_save).setOnClickListener {
            val dailyTarget = (dailyGoalEdit.text.toString().toDoubleOrNull() ?: 0.0) * 500
            val weeklyTarget = (weeklyGoalEdit.text.toString().toDoubleOrNull() ?: 0.0) * 500
            var valid = true
            if (dailyTarget <= 0) {
                layoutDaily.error = "Enter a valid daily target"
                valid = false
            } else {
                layoutDaily.error = null
            }
            if (weeklyTarget <= 0) {
                layoutWeekly.error = "Enter a valid weekly target"
                valid = false
            } else {
                layoutWeekly.error = null
            }
            if (valid) {
                val today = java.time.LocalDate.now()
                val endDate = today.plusWeeks(4)
                brewLog?.setConsumptionGoal(dailyTarget, weeklyTarget, today, endDate)
                Toast.makeText(this, "Goals set successfully", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showBaselineDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_baseline, null)
        val prefs = getSharedPreferences("brewlog_prefs", MODE_PRIVATE)
        val totalEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_total_consumption)
        val startDateView = dialogView.findViewById<android.widget.TextView>(R.id.tv_start_date)
        val endDateView = dialogView.findViewById<android.widget.TextView>(R.id.tv_end_date)

        var daysInPeriod = 1
        var weeksInPeriod = 1.0

        fun updatePeriod() {
            if (selectedStartDate != null && selectedEndDate != null) {
                daysInPeriod = java.time.temporal.ChronoUnit.DAYS.between(selectedStartDate, selectedEndDate).toInt() + 1
                weeksInPeriod = Math.ceil(daysInPeriod / 7.0)
            } else {
                daysInPeriod = 1
                weeksInPeriod = 1.0
            }
        }


        startDateView.setOnClickListener {
            showDatePicker { date ->
                selectedStartDate = date
                startDateView.text = date.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy"))
            }
        }
        endDateView.setOnClickListener {
            showDatePicker { date ->
                selectedEndDate = date
                endDateView.text = date.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy"))
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btn_save_baseline).setOnClickListener {
            if (selectedStartDate != null && selectedEndDate != null) {
                if (selectedStartDate!! <= selectedEndDate!!) {
                    val totalConsumption = (totalEdit.text.toString().toDoubleOrNull() ?: 0.0) * 500
                    if (totalConsumption < 0) {
                        Toast.makeText(this, "Total consumption cannot be negative", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    try {
                        val baseline = brewLog?.setBaseline(selectedStartDate!!, selectedEndDate!!, totalConsumption, null)
                        Toast.makeText(this, "Baseline set: ${baseline?.averageDailyConsumption?.toInt()} ml/day average", Toast.LENGTH_LONG).show()
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

        // Get default or most recent drink size
        val prefs = getSharedPreferences("brewlog_prefs", MODE_PRIVATE)
        val drinks = getDrinkPresets(prefs)
        val defaultDrink = drinks.firstOrNull { it.favorite } ?: drinks.firstOrNull()
        val drinkLabel = if (defaultDrink != null) " (${(defaultDrink.volume).toInt()}ml ${defaultDrink.name}${if (defaultDrink.name.endsWith("s")) "" else "s"})" else ""
        val drinkVolume = defaultDrink?.volume?.toDouble() ?: 500.0

        // Helper to format ml + drink equivalent
        fun formatMlAndDrinks(ml: Double): String {
            val drinksCount = if (drinkVolume > 0) (ml / drinkVolume) else 0.0
            return "${ml.toInt()} ml (${drinksCount.toInt()} × ${drinkVolume.toInt()}ml${if (defaultDrink != null) " ${defaultDrink.name}${if (drinksCount.toInt() == 1) "" else "s"}" else " drink(s)"})"
        }

        dialogView.findViewById<android.widget.TextView>(R.id.tv_reduction_percentage).text =
            "${String.format("%.1f", progressMetrics.reductionPercentageDaily)}%"
        dialogView.findViewById<android.widget.TextView>(R.id.tv_baseline_daily).text =
            formatMlAndDrinks(progressMetrics.baselineDailyAverage) + "/day"
        dialogView.findViewById<android.widget.TextView>(R.id.tv_current_daily).text =
            formatMlAndDrinks(progressMetrics.currentDailyAverage) + "/day"
        dialogView.findViewById<android.widget.TextView>(R.id.tv_baseline_weekly).text =
            formatMlAndDrinks(progressMetrics.baselineWeeklyAverage) + "/week"
        dialogView.findViewById<android.widget.TextView>(R.id.tv_current_weekly).text =
            formatMlAndDrinks(progressMetrics.currentWeeklyAverage) + "/week"
        // Monthly fields

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
        val themeSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_theme)

        beerSizeEdit.setText(defaultSize.toString())
        beerStrengthEdit.setText(defaultStrength.toString())
        themeSwitch.isChecked = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES

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
                    prefs.edit()
                        .putInt("default_beer_size", size!!)
                        .putFloat("default_beer_strength", strength!!)
                        .apply()

                    if (themeSwitch.isChecked) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    }

                    Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
} 