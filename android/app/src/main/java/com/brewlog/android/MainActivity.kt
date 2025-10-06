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
import java.time.DayOfWeek
import android.widget.ArrayAdapter
import org.json.JSONArray
import org.json.JSONObject
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.brewlog.android.DrinkPreset
import com.brewlog.android.DrinkType
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatDelegate
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: BeerEntryAdapter
    private var brewLog: BrewLog? = null
    private var selectedStartDate: LocalDate? = null
    private var selectedEndDate: LocalDate? = null
    private val prefsName = "brewlog_prefs"

    private fun getWeekStart(today: LocalDate): LocalDate {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val startOfWeek = prefs.getInt("start_of_week", 1) // Default to Monday (1)
        val targetDayOfWeek = DayOfWeek.of(startOfWeek)
        val daysToSubtract = (today.dayOfWeek.value - targetDayOfWeek.value + 7) % 7
        return today.minusDays(daysToSubtract.toLong())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Apply FLAG_SECURE at startup (default ON)
        if (getSharedPreferences(prefsName, MODE_PRIVATE).getBoolean("flag_secure", true)) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }

        setupRecyclerView()
        setupClickListeners()
        initializeBrewLog()
        loadData()

        // If navigated from Progress to open setup
        if (intent?.getBooleanExtra("open_setup_dialog", false) == true) {
            showSetGoalsDialog()
            intent.removeExtra("open_setup_dialog")
        }
        if (intent?.getBooleanExtra("open_settings", false) == true) {
            startActivity(Intent(this, SettingsActivity::class.java))
            intent.removeExtra("open_settings")
        }

        // Initial Setup CTA visibility
        val onboardingDone = getSharedPreferences(prefsName, MODE_PRIVATE).getBoolean("onboarding_complete", false)
        findViewById<View>(R.id.btn_initial_setup).apply {
            visibility = if (onboardingDone) View.GONE else View.VISIBLE
            setOnClickListener { showSetGoalsDialog() }
        }
    }

    override fun onResume() {
        super.onResume()
        try { loadData() } catch (_: Exception) {}
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
        // Quick add sheet can also be opened by tapping header Quick Add chips

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
            menu.clear()
            inflateMenu(R.menu.menu_bottom)
            selectedItemId = R.id.nav_home
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> true
                    R.id.nav_progress -> {
                        startActivity(android.content.Intent(this@MainActivity, ProgressActivity::class.java))
                        true
                    }
                    R.id.nav_calendar -> {
                        startActivity(android.content.Intent(this@MainActivity, CalendarActivity::class.java))
                        true
                    }
                    R.id.nav_settings -> {
                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
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
        val eod = prefs.getInt("end_of_day_hour", 3)
        brewLog?.setEndOfDayHour(eod)
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
        } else {
            // Default baseline: 14 drinks/week => 2 drinks/day
            val drinks = getDrinkPresets(prefs)
            val defaultDrink = drinks.firstOrNull { it.favorite } ?: drinks.firstOrNull()
            val defaultSizeMl = defaultDrink?.volume ?: prefs.getInt("default_beer_size", 500)
            val defaultDailyBaselineMl = 2.0 * defaultSizeMl
            val today = LocalDate.now()
            brewLog?.setBaseline(startDate = today, endDate = today.plusWeeks(4), totalConsumption = null, dailyAverage = defaultDailyBaselineMl)
            prefs.edit().putFloat("baseline_daily_ml", defaultDailyBaselineMl.toFloat()).apply()
        }
    }

    private fun loadData() {
        brewLog?.let { log ->
            try {
                val today = log.nowEffectiveDate()
                val weekStart = getWeekStart(today)
                val monthStart = today.minusDays(29)

                val todayConsumption = try {
                    val v = BrewLogNative.get_daily_consumption(today.toString())
                    if (v >= 0) v else log.getDailyConsumption(today)
                } catch (_: Throwable) {
                    log.getDailyConsumption(today)
                }
                // Weekly consumption used on Progress screen
                val weekConsumption = try {
                    val v = BrewLogNative.get_weekly_consumption(weekStart.toString())
                    if (v >= 0) v else log.getWeeklyConsumption(weekStart)
                } catch (_: Throwable) {
                    log.getWeeklyConsumption(weekStart)
                }
                val monthConsumption = log.getMonthlyConsumption(monthStart)

                val json = try { BrewLogNative.get_beer_entries_json(weekStart.toString(), today.toString()) } catch (_: Throwable) { "" }
                val entries = if (json.startsWith("[")) {
                    val arr = JSONArray(json)
                    List(arr.length()) { i ->
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
                } else {
                    log.getBeerEntries(weekStart.toString(), today.toString())
                }
                adapter.submitList(entries)

                findViewById<View>(R.id.empty_state).visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

                // Progress cards moved to dedicated screen

                // Update beer glass progress (daily consumption vs daily goal)
                val beerGlass = findViewById<BeerGlassView>(R.id.beer_glass)
                val beerGlassText = findViewById<android.widget.TextView>(R.id.beer_glass_progress)
                val dailyGoalMlRaw = log.getDailyGoal().takeIf { it >= 0 } ?: 0.0

                // Show in drinks instead of ml
                val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
                val drinks = getDrinkPresets(prefs)
                val defaultDrink = drinks.firstOrNull { it.favorite } ?: drinks.firstOrNull()
                val drinkVolume = defaultDrink?.volume?.toDouble() ?: 500.0
                // Use baseline as fallback target when no explicit goal set
                val baselineDailyMl = getSharedPreferences(prefsName, MODE_PRIVATE).getFloat("baseline_daily_ml", 0f).toDouble()
                val baselineWeeklyMl = baselineDailyMl * 7.0
                val effectiveDailyGoalMl = if (dailyGoalMlRaw > 0.0) dailyGoalMlRaw else baselineDailyMl
                val ratio = if (effectiveDailyGoalMl > 0) (todayConsumption / effectiveDailyGoalMl).coerceIn(0.0, 1.0) else 0.0
                beerGlass.setProgress(ratio)

                val todayDrinks = if (drinkVolume > 0) (todayConsumption / drinkVolume) else 0.0
                val goalDrinks = if (drinkVolume > 0) (effectiveDailyGoalMl / drinkVolume) else 0.0
                beerGlassText.text = "${todayDrinks.toInt()} / ${goalDrinks.toInt()} drinks"
                findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.daily_goal_progress).apply {
                    progress = (ratio * 100).toInt()
                }

                // Weekly progress bar and label
                val weeklyGoalMlRaw = log.getWeeklyGoal().takeIf { it >= 0 } ?: 0.0
                val effectiveWeeklyGoalMl = if (weeklyGoalMlRaw > 0.0) weeklyGoalMlRaw else baselineWeeklyMl
                val weeklyRatio = if (effectiveWeeklyGoalMl > 0) (weekConsumption / effectiveWeeklyGoalMl).coerceIn(0.0, 1.0) else 0.0
                findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.weekly_goal_progress)?.apply {
                    progress = (weeklyRatio * 100).toInt()
                }
                findViewById<android.widget.TextView>(R.id.weekly_glass_progress)?.apply {
                    val weekDrinks = if (drinkVolume > 0) (weekConsumption / drinkVolume) else 0.0
                    val weekGoalDrinks = if (drinkVolume > 0) (effectiveWeeklyGoalMl / drinkVolume) else 0.0
                    text = "${weekDrinks.toInt()} / ${weekGoalDrinks.toInt()} drinks"
                }

                // Monthly metrics are shown on the Progress screen only

                // Color progress bars by desirable (goal) vs baseline thresholds
                fun pickColor(current: Double, desirable: Double, baseline: Double): Int {
                    val green = android.graphics.Color.parseColor("#2E7D32")
                    val amber = android.graphics.Color.parseColor("#FF8F00")
                    val red = android.graphics.Color.parseColor("#C62828")
                    val effectiveBaseline = if (baseline <= 0.0) desirable else baseline
                    return when {
                        desirable > 0 && current <= desirable -> green
                        effectiveBaseline > 0 && current <= effectiveBaseline -> amber
                        else -> red
                    }
                }
                val baselineMonthlyMl = baselineDailyMl * 30.0 // used on Progress screen

                findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.daily_goal_progress)?.apply {
                    val color = pickColor(todayConsumption, effectiveDailyGoalMl, baselineDailyMl)
                    setIndicatorColor(color)
                }
                findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.weekly_goal_progress)?.apply {
                    val color = pickColor(weekConsumption, effectiveWeeklyGoalMl, baselineWeeklyMl)
                    setIndicatorColor(color)
                }
                // No monthly progress bar on home tile

                // Warn when near weekly max (>= 80%) and celebrate when within goals at end of day
                val weeklyPct = if (effectiveWeeklyGoalMl > 0) weekConsumption / effectiveWeeklyGoalMl else 0.0
                if (weeklyPct >= 0.8 && weeklyPct < 1.0) {
                    Toast.makeText(this, "Warning: close to weekly goal", Toast.LENGTH_SHORT).show()
                }
                if (ratio in 0.99..1.0 || (effectiveDailyGoalMl > 0 && todayConsumption <= effectiveDailyGoalMl && todayConsumption > 0)) {
                    try { findViewById<BeerGlassView>(R.id.beer_glass)?.celebrate() } catch (_: Exception) {}
                }

                // Calculate reduction percentages for home screen
                val reductionDaily = if (baselineDailyMl > 0) ((baselineDailyMl - todayConsumption) / baselineDailyMl) * 100 else 0.0
                val reductionWeekly = if (baselineWeeklyMl > 0) ((baselineWeeklyMl - weekConsumption) / baselineWeeklyMl) * 100 else 0.0
                val reductionMonthly = if (baselineMonthlyMl > 0) ((baselineMonthlyMl - monthConsumption) / baselineMonthlyMl) * 100 else 0.0
                
                // Update reduction displays on home screen
                findViewById<android.widget.TextView>(R.id.tv_daily_reduction_home)?.text = "${String.format("%.1f", reductionDaily)}%"
                findViewById<android.widget.TextView>(R.id.tv_weekly_reduction_home)?.text = "${String.format("%.1f", reductionWeekly)}%"
                findViewById<android.widget.TextView>(R.id.tv_monthly_reduction_home)?.text = "${String.format("%.1f", reductionMonthly)}%"

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
                        setOnLongClickListener {
                            showPresetOptions(preset) { loadData() }
                            true
                        }
                    }
                    chipGroup.addView(chip)
                }

                // Drinks manager tile actions
                findViewById<View>(R.id.btn_manage_drinks_tile)?.setOnClickListener {
                    showDrinkManagerDialog { selected ->
                        addBeerEntry(selected.name, selected.strength.toDouble(), selected.volume.toDouble(), "")
                    }
                }
                findViewById<View>(R.id.btn_add_drink_tile)?.setOnClickListener {
                    showAddBeerDialog()
                }

                // Drinks left indicators (day/week)
                val remainingTodayDrinks = if (drinkVolume > 0) ((effectiveDailyGoalMl - todayConsumption) / drinkVolume).coerceAtLeast(0.0) else 0.0
                val remainingWeekDrinks = if (drinkVolume > 0) ((effectiveWeeklyGoalMl - weekConsumption) / drinkVolume).coerceAtLeast(0.0) else 0.0
                findViewById<android.widget.TextView>(R.id.tv_daily_drinks_left)?.text =
                    "${remainingTodayDrinks.toInt()} left today"
                findViewById<android.widget.TextView>(R.id.tv_weekly_drinks_left)?.text =
                    "${remainingWeekDrinks.toInt()} left this week"

                // If user has no presets yet, nudge a favorite-setup sheet
                if (presets.isEmpty()) {
                    showFavoriteSetupSheet()
                }

                // Removed initial tip dialog to avoid obscuring buttons on small screens

            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFavoriteSetupSheet() {
        val sheet = layoutInflater.inflate(R.layout.bottom_sheet_favorite_setup, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(sheet)
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val defaultSize = prefs.getInt("default_beer_size", 500)
        val defaultStrength = prefs.getFloat("default_beer_strength", 5.0f)
        val etName = sheet.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_name)
        val etVol = sheet.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_volume)
        val etStr = sheet.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_strength)
        etName.setText("Beer")
        etVol.setText(defaultSize.toString())
        etStr.setText(defaultStrength.toString())

        fun saveFavorite(addOne: Boolean) {
            val name = etName.text?.toString()?.trim().orEmpty()
            val vol = etVol.text?.toString()?.toIntOrNull() ?: defaultSize
            val str = etStr.text?.toString()?.toFloatOrNull() ?: defaultStrength
            if (name.isEmpty() || vol <= 0 || str <= 0f) {
                Toast.makeText(this, "Enter a valid favorite drink", Toast.LENGTH_SHORT).show()
                return
            }
            addDrinkPreset(prefs, DrinkPreset(name, DrinkType.BEER, vol, str, favorite = true))
            if (addOne) {
                addBeerEntry(name, str.toDouble(), vol.toDouble(), "")
            } else {
                loadData()
            }
            dialog.dismiss()
        }

        sheet.findViewById<View>(R.id.btn_save_and_add).setOnClickListener { saveFavorite(true) }
        sheet.findViewById<View>(R.id.btn_save_only).setOnClickListener { saveFavorite(false) }
        dialog.show()
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
        val savePresetSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_save_preset)
        val favoriteSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_favorite)
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
                if (savePresetSwitch?.isChecked == true) {
                    addDrinkPreset(
                        prefs,
                        DrinkPreset(
                            name = name,
                            type = type,
                            volume = volumeMl.toInt(),
                            strength = alcoholPercentage.toFloat(),
                            favorite = favoriteSwitch?.isChecked == true
                        )
                    )
                }
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
        
        // Add Change Date button at top
        val changeDateBtn = MaterialButton(this).apply {
            text = "Change Date"
            setIconResource(android.R.drawable.ic_menu_my_calendar)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setOnClickListener {
                showDatePicker { selected ->
                    try {
                        val dateStr = selected.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val r = try { BrewLogNative.update_beer_entry_date_jni(entry.id, dateStr) } catch (_: Throwable) { "" }
                        if (!r.startsWith("OK")) {
                            brewLog?.updateBeerEntryDate(entry.id, selected)
                        }
                        Toast.makeText(this@MainActivity, "Date updated", Toast.LENGTH_SHORT).show()
                        loadData()
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "Failed to update date", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        (dialogView as LinearLayout).addView(changeDateBtn, 0)

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
        // German low-risk guideline anchor: ~12 g pure alcohol/day for women, ~24 g for men (approx.)
        // Convert grams alcohol to drinks by: grams = vol_ml * abv * 0.8 / 100; here we approximate with user's default drink size and typical 5% beer.
        val assumedAbv = prefs.getFloat("default_beer_strength", 5.0f).toDouble().coerceAtLeast(1.0)
        val gramsPerDrink = vol * (assumedAbv / 100.0) * 0.8
        val guidelineDailyDrinks = if (gramsPerDrink > 0) (24.0 / gramsPerDrink) else 2.0 // default to ~2 drinks/day if unknown
        val defaultDailyDrinks = guidelineDailyDrinks.coerceIn(1.0, 5.0)
        val defaultWeeklyDrinks = (defaultDailyDrinks * 7).toInt()
        dailyGoalDrinks.setText(
            if (currentDailyMl > 0) (currentDailyMl / vol).toInt().toString() else defaultDailyDrinks.toInt().toString()
        )
        weeklyGoalDrinks.setText(
            if (currentWeeklyMl > 0) (currentWeeklyMl / vol).toInt().toString() else defaultWeeklyDrinks.toString()
        )

        // Prefill baseline in drinks
        val baseline = brewLog?.getCurrentBaseline()
        dailyBaselineDrinks.setText(if ((baseline?.averageDailyConsumption ?: 0.0) > 0) ((baseline!!.averageDailyConsumption) / vol).toInt().toString() else "0")
        weeklyBaselineDrinks.setText(if ((baseline?.averageWeeklyConsumption ?: 0.0) > 0) ((baseline!!.averageWeeklyConsumption) / vol).toInt().toString() else "0")

        // Show guideline note
        dialogView.findViewById<android.widget.TextView>(R.id.tv_guideline_note)?.text =
            "Guideline: set goals around low-risk intake. Defaults use your drink size (" +
                    "${vol.toInt()}ml @ ${assumedAbv}% )."

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
                .putString("baseline_set_date", today.toString())
                .apply()

            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            loadData()

            // Mark onboarding as complete after first successful setup
            val prefsDone = getSharedPreferences(prefsName, MODE_PRIVATE)
            if (!prefsDone.getBoolean("onboarding_complete", false)) {
                prefsDone.edit().putBoolean("onboarding_complete", true).apply()
                findViewById<View>(R.id.btn_initial_setup)?.visibility = View.GONE
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

    private fun showFilterMenu() {
        val choices = arrayOf("Today", "Yesterday", "Last 7 days", "Last 30 days", "Custom date", "Custom range")
        AlertDialog.Builder(this)
            .setTitle("Filter entries")
            .setItems(choices) { d, which ->
                val today = LocalDate.now()
                when (which) {
                    0 -> { selectedStartDate = today; selectedEndDate = today; loadDataWithRange() }
                    1 -> { val y = today.minusDays(1); selectedStartDate = y; selectedEndDate = y; loadDataWithRange() }
                    2 -> { selectedStartDate = today.minusDays(6); selectedEndDate = today; loadDataWithRange() }
                    3 -> { selectedStartDate = today.minusDays(29); selectedEndDate = today; loadDataWithRange() }
                    4 -> showDatePicker { date -> selectedStartDate = date; selectedEndDate = date; loadDataWithRange() }
                    5 -> showDatePicker { start -> showDatePicker { end -> selectedStartDate = start; selectedEndDate = end; loadDataWithRange() } }
                }
                d.dismiss()
            }
            .show()
    }

    private fun loadDataWithRange() {
        brewLog?.let {
            try {
                val start = selectedStartDate ?: LocalDate.now().minusDays(6)
                val end = selectedEndDate ?: LocalDate.now()
                val json = try { BrewLogNative.get_beer_entries_json(start.toString(), end.toString()) } catch (_: Throwable) { "[]" }
                val entries = try {
                    val arr = JSONArray(json)
                    List(arr.length()) { i ->
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
                } catch (_: Throwable) { emptyList() }
                adapter.submitList(entries)
                findViewById<View>(R.id.empty_state).visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            } catch (_: Exception) {
                Toast.makeText(this, "Failed to apply filter", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addBeerEntry(name: String, alcoholPercentage: Double, volumeMl: Double, notes: String) {
        brewLog?.let { log ->
            try {
                val r = try { BrewLogNative.add_beer_entry(name, alcoholPercentage, volumeMl, notes) } catch (_: Throwable) { "" }
                if (!r.startsWith("OK")) {
                    log.addBeerEntry(name, alcoholPercentage, volumeMl, notes)
                }
                Toast.makeText(this, "Beer entry added successfully", Toast.LENGTH_SHORT).show()
                // Celebrate with animation and sound
                try {
                    findViewById<BeerGlassView>(R.id.beer_glass)?.celebrate()
                    playClink()
                } catch (_: Exception) {}
                loadData()
                maybePromptSavePreset(name, volumeMl, alcoholPercentage)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to add beer entry", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun maybePromptSavePreset(name: String, volumeMl: Double, strength: Double) {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val hasAny = getDrinkPresets(prefs).isNotEmpty()
        if (hasAny) return
        AlertDialog.Builder(this)
            .setTitle("Save as preset?")
            .setMessage("You just added your first drink. Save it for one‑tap adding next time?")
            .setPositiveButton("Save") { d, _ ->
                addDrinkPreset(
                    prefs,
                    DrinkPreset(
                        name = name,
                        type = DrinkType.BEER,
                        volume = volumeMl.toInt(),
                        strength = strength.toFloat(),
                        favorite = true
                    )
                )
                d.dismiss()
                loadData()
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun updateBeerEntry(id: String, name: String, alcoholPercentage: Double, volumeMl: Double, notes: String) {
        brewLog?.let { log ->
            try {
                val r = try { BrewLogNative.update_beer_entry_jni(id, name, alcoholPercentage, volumeMl, notes) } catch (_: Throwable) { "" }
                if (!r.startsWith("OK")) {
                    log.updateBeerEntry(id, name, alcoholPercentage, volumeMl, notes)
                }
                Toast.makeText(this, "Beer entry updated successfully", Toast.LENGTH_SHORT).show()
                loadData()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to update beer entry", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteBeerEntry(entry: BeerEntry) {
        brewLog?.let { log ->
            try {
                val r = try { BrewLogNative.delete_beer_entry_jni(entry.id) } catch (_: Throwable) { "" }
                if (!r.startsWith("OK")) {
                    log.deleteBeerEntry(entry.id)
                }
                Toast.makeText(this, "Beer entry deleted successfully", Toast.LENGTH_SHORT).show()
                loadData()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to delete beer entry", Toast.LENGTH_SHORT).show()
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
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_set_goals -> {
                showSetGoalsDialog()
                true
            }
            R.id.action_filter -> {
                showFilterMenu()
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

    // Settings now handled by SettingsActivity
    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val defaultSize = prefs.getInt("default_beer_size", 500)
        val defaultStrength = prefs.getFloat("default_beer_strength", 5.0f)
        val endOfDay = prefs.getInt("end_of_day_hour", 3)

        val beerSizeEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_beer_size)
        val beerStrengthEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_beer_strength)
        val beerSizeLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.beer_size_layout)
        val beerStrengthLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.beer_strength_layout)
        val themeSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_theme)
        val secureSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_secure)
        val eodEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_end_of_day)
        val exportBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_export)
        val importBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_import)
        val deleteAllBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_delete_all)
        val infoGuidelines = dialogView.findViewById<android.widget.TextView>(R.id.tv_info_guidelines)
        val redoSetupBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_redo_initial_setup)
        val versionText = dialogView.findViewById<android.widget.TextView>(R.id.tv_version)

        beerSizeEdit.setText(defaultSize.toString())
        beerStrengthEdit.setText(defaultStrength.toString())
        themeSwitch.isChecked = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        // Restore secure toggle
        secureSwitch.isChecked = getSharedPreferences(prefsName, MODE_PRIVATE).getBoolean("flag_secure", true)
        eodEdit.setText(endOfDay.toString())
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            versionText?.text = "Version: ${pInfo.versionName}"
        } catch (_: Exception) { }

        // Show low-risk guideline info based on current defaults
        try {
            val gramsPerDrink = (defaultSize.toDouble() * (defaultStrength.toDouble() / 100.0) * 0.8)
            if (gramsPerDrink > 0) {
                val approxDailyFemale = (12.0 / gramsPerDrink).coerceAtLeast(0.0)
                val approxDailyMale = (24.0 / gramsPerDrink).coerceAtLeast(0.0)
                infoGuidelines?.text =
                    "Guideline (approx.): ${approxDailyFemale.toInt()} drink/day (lower) to ${approxDailyMale.toInt()} drinks/day (upper). Consider 2 alcohol‑free days/week.\nSource: national low‑risk guidance."
            } else {
                infoGuidelines?.text = "Guideline: keep daily goals modest and include alcohol‑free days each week."
            }
        } catch (_: Exception) { }

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
                val eod = eodEdit.text.toString().toIntOrNull()
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
                if (eod == null || eod !in 0..23) {
                    dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.eod_layout).error = "0-23"
                    valid = false
                } else {
                    dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.eod_layout).error = null
                }
                if (valid) {
                    prefs.edit()
                        .putInt("default_beer_size", size!!)
                        .putFloat("default_beer_strength", strength!!)
                        .putInt("end_of_day_hour", eod!!)
                        .apply()
                    brewLog?.setEndOfDayHour(eod!!)

                    if (themeSwitch.isChecked) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    }

                    // Apply FLAG_SECURE preference
                    val enableSecure = secureSwitch.isChecked
                    prefs.edit().putBoolean("flag_secure", enableSecure).apply()
                    if (enableSecure) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    }

                    Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()

        // Export/Import handlers
        exportBtn.setOnClickListener {
            try {
                val data = brewLog?.toJson() ?: "[]"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_TEXT, data)
                    putExtra(Intent.EXTRA_SUBJECT, "BrewLog Export")
                }
                startActivity(Intent.createChooser(intent, "Export BrewLog data"))
            } catch (_: Exception) {
                Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
        importBtn.setOnClickListener {
            try {
                val openDoc = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                // Fallback simple prompt: paste JSON
                val input = android.widget.EditText(this)
                input.hint = "Paste exported JSON"
                AlertDialog.Builder(this)
                    .setTitle("Import Data")
                    .setView(input)
                    .setPositiveButton("Import") { d, _ ->
                        val text = input.text.toString()
                        if (text.isNotBlank()) {
                            brewLog?.loadFromJson(text)
                            loadData()
                            Toast.makeText(this, "Imported", Toast.LENGTH_SHORT).show()
                        }
                        d.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } catch (_: Exception) {
                Toast.makeText(this, "Import failed", Toast.LENGTH_SHORT).show()
            }
        }

        deleteAllBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete All Data")
                .setMessage("This will permanently remove all entries and goals on this device. This cannot be undone.")
                .setPositiveButton("Delete") { d, _ ->
                    try {
                        val res = BrewLogNative.delete_all_data()
                        if (!res.startsWith("OK")) {
                            Toast.makeText(this, "Failed to delete data", Toast.LENGTH_SHORT).show()
                        } else {
                            // Clear prefs storing goals/baseline and presets
                            getSharedPreferences(prefsName, MODE_PRIVATE).edit()
                                .remove("goal_daily_ml")
                                .remove("goal_weekly_ml")
                                .remove("baseline_daily_ml")
                                .remove("drink_presets")
                                .apply()
                            Toast.makeText(this, "All data deleted", Toast.LENGTH_SHORT).show()
                            loadData()
                        }
                    } catch (_: Exception) {
                        Toast.makeText(this, "Failed to delete data", Toast.LENGTH_SHORT).show()
                    }
                    d.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Redo Initial Setup -> open the setup dialog
        redoSetupBtn?.setOnClickListener {
            dialog.dismiss()
            showSetupDialog()
        }
    }

    private fun showQuickAddSheet() {
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_quick_add, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(sheetView)

        val group = sheetView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.group_presets)
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val presets = getDrinkPresets(prefs).sortedByDescending { it.favorite }.take(12)
        group.removeAllViews()
        presets.forEach { preset ->
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
                    dialog.dismiss()
                }
                setOnLongClickListener {
                    showPresetOptions(preset) {
                        // Rebuild chips after change
                        dialog.dismiss()
                        showQuickAddSheet()
                    }
                    true
                }
            }
            group.addView(chip)
        }

        // Add New chip for quick access
        val addNewChip = com.google.android.material.chip.Chip(this).apply {
            text = "+ Add New"
            isCheckable = false
            isClickable = true
            setChipIconResource(android.R.drawable.ic_input_add)
            isChipIconVisible = true
            setOnClickListener {
                dialog.dismiss()
                showEditDrinkDialog(null) { newDrink ->
                    val all = getDrinkPresets(prefs).toMutableList()
                    all.add(newDrink)
                    saveDrinkPresets(prefs, all)
                    loadData()
                }
            }
        }
        group.addView(addNewChip, 0)

        sheetView.findViewById<View>(R.id.btn_manage_drinks).setOnClickListener {
            dialog.dismiss()
            showDrinkManagerDialog { selected ->
                addBeerEntry(selected.name, selected.strength.toDouble(), selected.volume.toDouble(), "")
            }
        }
        sheetView.findViewById<View>(R.id.btn_close).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showPresetOptions(preset: DrinkPreset, onChanged: () -> Unit) {
        val items = arrayOf("Edit", if (preset.favorite) "Unfavorite" else "Favorite", "Delete")
        AlertDialog.Builder(this)
            .setTitle(preset.name)
            .setItems(items) { d, which ->
                val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
                var list = getDrinkPresets(prefs).toMutableList()
                when (which) {
                    0 -> { // Edit
                        showEditDrinkDialog(preset) { updated ->
                            val idx = list.indexOfFirst { it.name == preset.name && it.type == preset.type && it.volume == preset.volume && it.strength == preset.strength }
                            if (idx != -1) list[idx] = updated else list.add(updated)
                            saveDrinkPresets(prefs, list)
                            onChanged()
                        }
                    }
                    1 -> { // Favorite toggle
                        list = list.map { it.copy(favorite = (it.name == preset.name && it.type == preset.type && it.volume == preset.volume && it.strength == preset.strength)) }.toMutableList()
                        // If already favorite, unfavorite all
                        if (preset.favorite) {
                            list = list.map { it.copy(favorite = false) }.toMutableList()
                        }
                        saveDrinkPresets(prefs, list)
                        onChanged()
                    }
                    2 -> { // Delete
                        list.removeAll { it.name == preset.name && it.type == preset.type && it.volume == preset.volume && it.strength == preset.strength }
                        saveDrinkPresets(prefs, list)
                        onChanged()
                    }
                }
                d.dismiss()
            }
            .show()
    }
} 