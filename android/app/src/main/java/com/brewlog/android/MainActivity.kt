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
    private val prefsName = "brewlog_prefs"

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
        
        // Add baseline and set goals button click listeners
        findViewById<View>(R.id.btn_baseline).setOnClickListener { showBaselineDialog() }
        findViewById<View>(R.id.btn_set_goals).setOnClickListener { showSetGoalsDialog() }

        // Quick-add by tapping the beer glass: adds last drink preset
        findViewById<BeerGlassView>(R.id.beer_glass).setOnClickListener {
            val prefs = getSharedPreferences("brewlog_prefs", MODE_PRIVATE)
            val presets = getDrinkPresets(prefs)
            val last = presets.firstOrNull { it.favorite } ?: presets.firstOrNull()
            if (last != null) {
                addBeerEntry(last.name, last.strength.toDouble(), last.volume.toDouble(), "")
            } else {
                Toast.makeText(this, "No saved drinks yet. Add one first.", Toast.LENGTH_SHORT).show()
            }
        }

        // Bottom nav
        findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav).apply {
            selectedItemId = R.id.nav_home
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> true
                    R.id.nav_progress -> {
                        startActivity(android.content.Intent(this@MainActivity, ProgressActivity::class.java))
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun initializeBrewLog() {
        try {
            brewLog = BrewLogProvider.instance
            restoreGoalsAndBaseline()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to initialize brew log", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreGoalsAndBaseline() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val goalDaily = prefs.getFloat("goal_daily_ml", 0f).toDouble()
        val goalWeekly = prefs.getFloat("goal_weekly_ml", 0f).toDouble()
        if (goalDaily > 0.0 || goalWeekly > 0.0) {
            val today = LocalDate.now()
            brewLog?.setConsumptionGoal(goalDaily, goalWeekly, today, today.plusWeeks(4))
        }
        val baselineDaily = prefs.getFloat("baseline_daily_ml", 0f).toDouble()
        if (baselineDaily > 0.0) {
            val today = LocalDate.now()
            brewLog?.setBaseline(startDate = today, endDate = today.plusWeeks(4), totalConsumption = null, dailyAverage = baselineDaily)
        }
    }

    private fun loadData() {
        brewLog?.let { log ->
            try {
                val today = LocalDate.now()
                val weekStart = LocalDate.now().minusDays(6)

                val todayConsumption = log.getDailyConsumption(today)
                // Weekly consumption used on Progress screen
                /* val weekConsumption = */ log.getWeeklyConsumption(weekStart)

                val entries = log.getBeerEntries(weekStart.toString(), today.toString())
                adapter.submitList(entries)

                findViewById<View>(R.id.empty_state).visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

                // Progress cards moved to dedicated screen

                // Update beer glass progress (daily consumption vs daily goal)
                val beerGlass = findViewById<BeerGlassView>(R.id.beer_glass)
                val beerGlassText = findViewById<android.widget.TextView>(R.id.beer_glass_progress)
                val dailyGoalMl = log.getDailyGoal().takeIf { it >= 0 } ?: 0.0
                val ratio = if (dailyGoalMl > 0) (todayConsumption / dailyGoalMl).coerceIn(0.0, 1.0) else 0.0
                beerGlass.setProgress(ratio)

                // Show in drinks instead of ml
                val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
                val drinks = getDrinkPresets(prefs)
                val defaultDrink = drinks.firstOrNull { it.favorite } ?: drinks.firstOrNull()
                val drinkVolume = defaultDrink?.volume?.toDouble() ?: 500.0
                val todayDrinks = if (drinkVolume > 0) (todayConsumption / drinkVolume) else 0.0
                val goalDrinks = if (drinkVolume > 0) (dailyGoalMl / drinkVolume) else 0.0
                beerGlassText.text = "${todayDrinks.toInt()} / ${goalDrinks.toInt()} drinks"
                findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.daily_goal_progress).apply {
                    progress = (ratio * 100).toInt()
                }

                // Populate Quick Add chips from presets (prioritize favorite, last-added)
                val chipGroup = findViewById<com.google.android.material.chip.ChipGroup>(R.id.quick_add_group)
                chipGroup.removeAllViews()
                val presets = getDrinkPresets(getSharedPreferences(prefsName, MODE_PRIVATE))
                val topPresets = presets.sortedByDescending { it.favorite }.take(6)
                topPresets.forEach { preset ->
                    val chip = com.google.android.material.chip.Chip(this).apply {
                        text = "${preset.volume}ml ${preset.name}"
                        isCheckable = false
                        isClickable = true
                        setOnClickListener {
                            addBeerEntry(
                                name = preset.name,
                                alcoholPercentage = preset.strength.toDouble(),
                                volumeMl = preset.volume.toDouble(),
                                notes = ""
                            )
                        }
                    }
                    chipGroup.addView(chip)
                }

                // If goals/baseline are zero, gently prompt once
                if ((log.getDailyGoal() <= 0.0) || (log.getCurrentBaseline() == null)) {
                    android.app.AlertDialog.Builder(this)
                        .setMessage("Set your daily goal and baseline to track progress.")
                        .setPositiveButton("Set Now") { d, _ ->
                            showSetGoalsDialog(); d.dismiss()
                        }
                        .setNegativeButton("Later", null)
                        .show()
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
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
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
            // Type is currently informational; not used in save
            if (name.isNotEmpty() && volumeMl > 0) {
                addBeerEntry(name, alcoholPercentage, volumeMl, notes)
                dialog.dismiss()
                // Refresh home to update goal/drinks
                loadData()
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
        showSetupDialog()
    }

    private fun showBaselineDialog() {
        showSetupDialog()
    }

    private fun showSetupDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_setup, null)
        val defaultDrinkEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_default_drink_ml)
        val dailyGoalDrinks = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_goal_daily_drinks)
        val weeklyGoalDrinks = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_goal_weekly_drinks)
        val dailyBaselineDrinks = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_baseline_daily_drinks)
        val weeklyBaselineDrinks = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_baseline_weekly_drinks)

        val layoutDailyGoal = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layout_daily_goal_drinks)
        val layoutWeeklyGoal = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layout_weekly_goal_drinks)
        val layoutDailyBaseline = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layout_daily_baseline_drinks)
        val layoutWeeklyBaseline = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layout_weekly_baseline_drinks)

        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val drinks = getDrinkPresets(prefs)
        val defaultDrink = drinks.firstOrNull { it.favorite } ?: drinks.firstOrNull()
        val defaultSizeMl = defaultDrink?.volume ?: prefs.getInt("default_beer_size", 500)
        defaultDrinkEdit.setText(defaultSizeMl.toString())

        // Prefill goals in drinks
        val currentDailyMl = brewLog?.getDailyGoal() ?: 0.0
        val currentWeeklyMl = brewLog?.getWeeklyGoal() ?: 0.0
        val vol = if (defaultSizeMl > 0) defaultSizeMl.toDouble() else 500.0
        dailyGoalDrinks.setText(if (currentDailyMl > 0) (currentDailyMl / vol).toInt().toString() else "0")
        weeklyGoalDrinks.setText(if (currentWeeklyMl > 0) (currentWeeklyMl / vol).toInt().toString() else "0")

        // Prefill baseline in drinks
        val baseline = brewLog?.getCurrentBaseline()
        dailyBaselineDrinks.setText(if ((baseline?.averageDailyConsumption ?: 0.0) > 0) ((baseline!!.averageDailyConsumption) / vol).toInt().toString() else "0")
        weeklyBaselineDrinks.setText(if ((baseline?.averageWeeklyConsumption ?: 0.0) > 0) ((baseline!!.averageWeeklyConsumption) / vol).toInt().toString() else "0")

        fun recalcWeeklyFromDaily(source: com.google.android.material.textfield.TextInputEditText, target: com.google.android.material.textfield.TextInputEditText) {
            val d = source.text.toString().toDoubleOrNull() ?: 0.0
            target.setText((d * 7).toInt().toString())
        }
        dailyGoalDrinks.addTextChangedListener(object: android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { recalcWeeklyFromDaily(dailyGoalDrinks, weeklyGoalDrinks) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        dailyBaselineDrinks.addTextChangedListener(object: android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { recalcWeeklyFromDaily(dailyBaselineDrinks, weeklyBaselineDrinks) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btn_save).setOnClickListener {
            val sizeInput = defaultDrinkEdit.text.toString().toIntOrNull() ?: 0
            val dailyGoal = dailyGoalDrinks.text.toString().toDoubleOrNull() ?: 0.0
            val weeklyGoal = weeklyGoalDrinks.text.toString().toDoubleOrNull() ?: 0.0
            val dailyBase = dailyBaselineDrinks.text.toString().toDoubleOrNull() ?: 0.0
            val weeklyBase = weeklyBaselineDrinks.text.toString().toDoubleOrNull() ?: 0.0

            var valid = true
            if (dailyGoal <= 0) { layoutDailyGoal.error = "Enter daily goal"; valid = false } else layoutDailyGoal.error = null
            if (weeklyGoal <= 0) { layoutWeeklyGoal.error = "Enter weekly goal"; valid = false } else layoutWeeklyGoal.error = null
            if (dailyBase <= 0) { layoutDailyBaseline.error = "Enter daily baseline"; valid = false } else layoutDailyBaseline.error = null
            if (weeklyBase <= 0) { layoutWeeklyBaseline.error = "Enter weekly baseline"; valid = false } else layoutWeeklyBaseline.error = null

            if (!valid) return@setOnClickListener

            val effectiveSize = if (sizeInput > 0) sizeInput else defaultSizeMl
            if (effectiveSize > 0) {
                prefs.edit().putInt("default_beer_size", effectiveSize).apply()
            }

            val mlDailyGoal = dailyGoal * effectiveSize
            val mlWeeklyGoal = weeklyGoal * effectiveSize
            val mlDailyBaseline = dailyBase * effectiveSize

            val today = LocalDate.now()
            brewLog?.setConsumptionGoal(mlDailyGoal, mlWeeklyGoal, today, today.plusWeeks(4))
            brewLog?.setBaseline(startDate = today, endDate = today.plusWeeks(4), totalConsumption = null, dailyAverage = mlDailyBaseline)

            // Persist selections for next launch
            prefs.edit()
                .putFloat("goal_daily_ml", mlDailyGoal.toFloat())
                .putFloat("goal_weekly_ml", mlWeeklyGoal.toFloat())
                .putFloat("baseline_daily_ml", mlDailyBaseline.toFloat())
                .apply()

            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            loadData()
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
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val drinks = getDrinkPresets(prefs)
        val defaultDrink = drinks.firstOrNull { it.favorite } ?: drinks.firstOrNull()
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
                // Celebrate with animation and sound
                try {
                    findViewById<BeerGlassView>(R.id.beer_glass)?.celebrate()
                    playClink()
                } catch (_: Exception) {}
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
            R.id.action_set_goals -> {
                showSetGoalsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun playClink() {
        try {
            val candidates = listOf(
                "clink_beer", "open_beer", "clink_crystal", "clink_bar", "clink_soft", "clink_heavy"
            )
            val available = candidates.mapNotNull { name ->
                val id = resources.getIdentifier(name, "raw", packageName)
                if (id != 0) id else null
            }
            if (available.isEmpty()) {
                try {
                    val mas = android.media.MediaActionSound()
                    mas.play(android.media.MediaActionSound.SHUTTER_CLICK)
                } catch (_: Exception) {
                    val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
                    tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 150)
                    tone.release()
                }
                return
            }

            val resId = available.random()

            // Skip zero-length placeholders and fall back to a system tone
            val afd = resources.openRawResourceFd(resId)
            val isEmpty = (afd.length <= 0)
            afd.close()
            if (isEmpty) {
                try {
                    val mas = android.media.MediaActionSound()
                    mas.play(android.media.MediaActionSound.SHUTTER_CLICK)
                } catch (_: Exception) {
                    val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
                    tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 150)
                    tone.release()
                }
                return
            }

            val mp = android.media.MediaPlayer.create(this, resId)
            mp.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setOnCompletionListener { it.release() }
            val vol = 0.7f
            mp.setVolume(vol, vol)
            mp.start()
        } catch (_: Exception) {
            try {
                val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
                tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 150)
                tone.release()
            } catch (_: Exception) { }
        }
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
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