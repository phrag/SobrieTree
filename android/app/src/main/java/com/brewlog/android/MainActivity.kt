package com.brewlog.android

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: BeerEntryAdapter
    private var brewLog: BrewLog? = null
    private var hasShownFavoriteSetup = false
    private var celebrationShowing = false
    private val prefsName = AppPrefs.NAME

    private val repo by lazy { EntryRepository() }
    private val gamification by lazy { GamificationManager(this) }

    private val onboardingNotifPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        val prefs = AppPrefs(this)
        if (granted) {
            prefs.reminderEnabled = true
            ReminderScheduler.schedule(this, prefs.reminderHour, prefs.reminderMinute)
        } else {
            prefs.reminderEnabled = false
        }
        completeOnboarding()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        SecureWindow.apply(this)

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

        // Initial Setup CTA visibility and flow
        val onboardingDone = getSharedPreferences(prefsName, MODE_PRIVATE).getBoolean("onboarding_complete", false)
        val hasDrinkPresets = getDrinkPresets(getSharedPreferences(prefsName, MODE_PRIVATE)).isNotEmpty()
        val hasData = brewLog?.getAllEntries()?.isNotEmpty() ?: false

        findViewById<View>(R.id.btn_initial_setup).apply {
            visibility = if (onboardingDone) View.GONE else View.VISIBLE
            setOnClickListener {
                if (!hasDrinkPresets) {
                    showInitialSetupFlow()
                } else {
                    showSetGoalsDialog()
                }
            }
        }

        // Auto-trigger initial setup flow for fresh installs
        if (!onboardingDone && !hasDrinkPresets && !hasData) {
            showInitialSetupFlow()
        }
    }

    override fun onResume() {
        super.onResume()
        SecureWindow.apply(this)
        // Also re-checks for badges earned by days passing since the last visit
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
        // Quick-add by tapping the ring: logs the favorite preset (subtle haptic, no fanfare)
        findViewById<GrowthRingView>(R.id.growth_ring).setOnClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val preset = DrinkPresetStore.defaultPreset(getSharedPreferences(prefsName, MODE_PRIVATE))
            if (preset != null) {
                addBeerEntry(preset.name, preset.strength.toDouble(), preset.volume.toDouble(), "")
            } else {
                Toast.makeText(this, "No saved drinks yet. Add one first.", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.btn_add_drink_tile).setOnClickListener { showAddBeerDialog() }
        findViewById<View>(R.id.btn_manage_drinks_tile).setOnClickListener {
            showDrinkManagerDialog { selected ->
                addBeerEntry(selected.name, selected.strength.toDouble(), selected.volume.toDouble(), "")
            }
        }
        findViewById<View>(R.id.btn_manage_goals_baseline).setOnClickListener {
            showManageGoalsBaselineDialog()
        }

        // Stat tiles open the Journey tab
        val openJourney = View.OnClickListener {
            startActivity(Intent(this, JourneyActivity::class.java))
        }
        findViewById<View>(R.id.tile_money).setOnClickListener(openJourney)
        findViewById<View>(R.id.tile_calories).setOnClickListener(openJourney)
        findViewById<View>(R.id.tile_milestone).setOnClickListener(openJourney)

        BottomNavHelper.wire(this, findViewById(R.id.bottom_nav), R.id.nav_home)
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
            // Default baseline: 2 drinks/day at the favorite drink's size
            val defaultDrink = DrinkPresetStore.defaultPreset(prefs)
            val defaultSizeMl = defaultDrink?.volume ?: prefs.getInt("default_beer_size", 500)
            val defaultDailyBaselineMl = 2.0 * defaultSizeMl
            val today = LocalDate.now()
            brewLog?.setBaseline(startDate = today, endDate = today.plusWeeks(4), totalConsumption = null, dailyAverage = defaultDailyBaselineMl)
            prefs.edit().putFloat("baseline_daily_ml", defaultDailyBaselineMl.toFloat()).apply()
        }
    }

    private fun loadData() {
        try {
            val state = gamification.homeState()
            val today = gamification.todayEffective()
            val weekStart = state.weekDots.firstOrNull()?.date ?: today.minusDays(6)

            val entries = repo.getEntries(weekStart, today)
            adapter.submitList(entries)
            findViewById<View>(R.id.empty_state).visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

            bindHomeState(state)
            populateQuickAdd()
            showMilestoneCelebrations(state.uncelebrated)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindHomeState(state: GamificationManager.HomeState) {
        findViewById<GrowthRingView>(R.id.growth_ring).setState(
            consumedRatio = state.metrics.dailyRatio.toFloat(),
            isAfToday = state.isTodayAf,
            growthStage = state.growthStage,
            overGoal = state.metrics.overDailyGoal
        )

        val drinkVolume = state.drinkSizeMl
        val todayDrinks = if (drinkVolume > 0) state.metrics.todayMl / drinkVolume else 0.0
        val goalDrinks = if (drinkVolume > 0) state.metrics.effectiveDailyGoalMl / drinkVolume else 0.0
        findViewById<TextView>(R.id.tv_ring_progress).text =
            if (state.isTodayAf) "Alcohol-free so far"
            else "${todayDrinks.toInt()} of ${goalDrinks.toInt()} drinks"

        val bestPart = if (state.streaks.bestStreak > 0) " · best ${state.streaks.bestStreak}" else ""
        findViewById<TextView>(R.id.tv_streak).text =
            "🌱 ${state.streaks.displayStreak}-day streak$bestPart"
        val shieldSuffix = if (state.streaks.shieldsHeld > 0) " · 🛡 ${state.streaks.shieldsHeld}" else ""
        findViewById<TextView>(R.id.tv_total_af).text =
            "${state.streaks.totalAfDays} alcohol-free days$shieldSuffix"
        findViewById<TextView>(R.id.tv_encouragement).text = state.encouragement

        val cravingCard = findViewById<View>(R.id.card_craving_support)
        if (state.cravingSupport != null) {
            cravingCard.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tv_craving_support).text = state.cravingSupport
        } else {
            cravingCard.visibility = View.GONE
        }

        val moneyTile = findViewById<View>(R.id.tile_money)
        if (state.moneyAvailable) {
            moneyTile.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tv_money_saved).text = Money.format(state.moneySaved)
        } else {
            moneyTile.visibility = View.GONE
        }
        findViewById<TextView>(R.id.tv_calories_saved).text = String.format("%,d", state.caloriesSaved.toInt())
        findViewById<TextView>(R.id.tv_next_milestone).text =
            state.nextBadge?.let { badge -> "${badge.title} · ${state.nextBadgeHint.orEmpty()}" }
                ?: "All badges earned!"

        renderWeekDots(state)
    }

    private fun renderWeekDots(state: GamificationManager.HomeState) {
        val container = findViewById<LinearLayout>(R.id.week_dots)
        container.removeAllViews()
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        state.weekDots.forEach { dot ->
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val label = TextView(this).apply {
                text = dot.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault())
                textSize = 11f
                gravity = android.view.Gravity.CENTER
                setTextColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        if (dot.isToday) R.color.text_primary else R.color.text_hint
                    )
                )
            }
            // Over-goal days render neutral gray, never red
            val dotView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply { topMargin = dp(4) }
                val (bg, tint) = when (dot.state) {
                    GamificationManager.DayDotState.AF -> R.drawable.dot_circle to R.color.state_positive
                    GamificationManager.DayDotState.UNDER_GOAL -> R.drawable.dot_circle_outline to R.color.state_positive
                    GamificationManager.DayDotState.OVER_GOAL -> R.drawable.dot_circle to R.color.state_neutral
                    GamificationManager.DayDotState.FUTURE -> R.drawable.dot_circle_outline to R.color.text_hint
                }
                background = ContextCompat.getDrawable(this@MainActivity, bg)
                backgroundTintList = ContextCompat.getColorStateList(this@MainActivity, tint)
            }
            column.addView(label)
            column.addView(dotView)
            container.addView(column)
        }
    }

    private fun populateQuickAdd() {
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
    }

    private fun showMilestoneCelebrations(badges: List<com.brewlog.android.engine.Badge>) {
        if (celebrationShowing || badges.isEmpty()) return
        val badge = badges.first()
        celebrationShowing = true
        val sheet = layoutInflater.inflate(R.layout.bottom_sheet_milestone, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(sheet)
        sheet.findViewById<TextView>(R.id.tv_badge_title).text = badge.title
        sheet.findViewById<TextView>(R.id.tv_badge_description).text = badge.description
        val icon = sheet.findViewById<android.widget.ImageView>(R.id.iv_badge_icon)
        icon.scaleX = 0f
        icon.scaleY = 0f
        icon.animate().scaleX(1f).scaleY(1f).setDuration(400)
            .setInterpolator(android.view.animation.OvershootInterpolator()).start()
        sheet.findViewById<View>(R.id.btn_keep_going).setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            gamification.markCelebrated(badge.id)
            celebrationShowing = false
            val remaining = badges.drop(1)
            if (remaining.isNotEmpty()) showMilestoneCelebrations(remaining)
        }
        dialog.show()
    }

    private fun showInitialSetupFlow() {
        showWelcomeDialog()
    }

    private fun showWelcomeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_welcome_setup, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<View>(R.id.btn_get_started).setOnClickListener {
            dialog.dismiss()
            showMotivationDialog()
        }

        dialog.show()
    }

    private fun showMotivationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_onboarding_motivation, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val chipToKey = mapOf(
            R.id.chip_sleep to "sleep",
            R.id.chip_health to "health",
            R.id.chip_money to "money",
            R.id.chip_weight to "weight",
            R.id.chip_mind to "mind",
            R.id.chip_curious to "curious"
        )
        dialogView.findViewById<View>(R.id.btn_motivation_continue).setOnClickListener {
            val selected = chipToKey.filter { (id, _) ->
                dialogView.findViewById<com.google.android.material.chip.Chip>(id).isChecked
            }.values.toSet()
            AppPrefs(this).motivations = selected
            dialog.dismiss()
            showSetGoalsDialog(true)
        }

        dialog.show()
    }

    private fun showReminderOptInDialog() {
        val prefs = AppPrefs(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_onboarding_reminder, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val timeText = dialogView.findViewById<TextView>(R.id.tv_onboarding_reminder_time)
        timeText.text = String.format("%02d:%02d", prefs.reminderHour, prefs.reminderMinute)
        dialogView.findViewById<View>(R.id.row_onboarding_reminder_time).setOnClickListener {
            android.app.TimePickerDialog(
                this,
                { _, hour, minute ->
                    prefs.reminderHour = hour
                    prefs.reminderMinute = minute
                    timeText.text = String.format("%02d:%02d", hour, minute)
                },
                prefs.reminderHour, prefs.reminderMinute, true
            ).show()
        }

        dialogView.findViewById<View>(R.id.btn_reminder_skip).setOnClickListener {
            prefs.reminderEnabled = false
            dialog.dismiss()
            completeOnboarding()
        }
        dialogView.findViewById<View>(R.id.btn_reminder_enable).setOnClickListener {
            dialog.dismiss()
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                onboardingNotifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                prefs.reminderEnabled = true
                ReminderScheduler.schedule(this, prefs.reminderHour, prefs.reminderMinute)
                completeOnboarding()
            }
        }

        dialog.show()
    }

    private fun showFavoriteSetupSheet(isInitialSetup: Boolean = false) {
        if (hasShownFavoriteSetup && !isInitialSetup) {
            return
        }
        hasShownFavoriteSetup = true

        val sheet = layoutInflater.inflate(R.layout.bottom_sheet_favorite_setup, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(sheet)
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val defaultSize = prefs.getInt("default_beer_size", 500)
        val defaultStrength = prefs.getFloat("default_beer_strength", 5.0f)
        val etName = sheet.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_name)
        val etVol = sheet.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_volume)
        val etStr = sheet.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_strength)
        val etCost = sheet.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_fav_cost)
        etName.setText("Beer")
        etVol.setText(defaultSize.toString())
        etStr.setText(defaultStrength.toString())
        if (AppPrefs(this).pricePerDrink > 0) {
            etCost.setText(String.format("%.2f", AppPrefs(this).pricePerDrink))
        }

        fun saveFavorite(addOne: Boolean) {
            val name = etName.text?.toString()?.trim().orEmpty()
            val vol = etVol.text?.toString()?.toIntOrNull() ?: defaultSize
            val str = etStr.text?.toString()?.toFloatOrNull() ?: defaultStrength
            val cost = etCost.text?.toString()?.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
            if (name.isEmpty() || vol <= 0 || str <= 0f) {
                Toast.makeText(this, "Enter a valid favorite drink", Toast.LENGTH_SHORT).show()
                return
            }
            addDrinkPreset(prefs, DrinkPreset(name, DrinkType.BEER, vol, str, favorite = true, cost = cost))
            if (addOne) {
                addBeerEntry(name, str.toDouble(), vol.toDouble(), "")
            } else {
                loadData()
            }
            hasShownFavoriteSetup = false
            dialog.dismiss()

            // Last data step of initial setup; the reminder opt-in closes the flow
            if (isInitialSetup) {
                showReminderOptInDialog()
            }
        }

        sheet.findViewById<View>(R.id.btn_save_and_add).setOnClickListener { saveFavorite(true) }
        sheet.findViewById<View>(R.id.btn_save_only).setOnClickListener { saveFavorite(false) }

        dialog.setOnDismissListener {
            hasShownFavoriteSetup = false
        }

        dialog.show()
    }

    private fun completeOnboarding() {
        getSharedPreferences(prefsName, MODE_PRIVATE).edit().putBoolean("onboarding_complete", true).apply()
        findViewById<View>(R.id.btn_initial_setup)?.visibility = View.GONE
        loadData()
    }

    fun getDrinkPresets(prefs: android.content.SharedPreferences): List<DrinkPreset> =
        DrinkPresetStore.getPresets(prefs)

    fun saveDrinkPresets(prefs: android.content.SharedPreferences, presets: List<DrinkPreset>) =
        DrinkPresetStore.savePresets(prefs, presets)

    fun addDrinkPreset(prefs: android.content.SharedPreferences, preset: DrinkPreset) =
        DrinkPresetStore.addPreset(prefs, preset)

    private fun showDrinkManagerDialog(
        onDrinkSelected: (DrinkPreset) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_drink_manager, null)
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
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
        val costEdit = dialogView.findViewById<TextInputEditText>(R.id.et_cost)
        val typeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.spinner_drink_type)
        val typeNames = DrinkType.values().map { it.displayName }
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeNames)
        typeSpinner.adapter = typeAdapter
        if (drink != null) {
            nameEdit.setText(drink.name)
            strengthEdit.setText(drink.strength.toString())
            volumeEdit.setText(drink.volume.toString())
            if (drink.cost > 0) costEdit.setText(String.format("%.2f", drink.cost))
            typeSpinner.setSelection(DrinkType.values().indexOf(drink.type))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (drink == null) "Add Drink" else "Edit Drink")
            .setView(dialogView)
            .setPositiveButton("Save") { d, _ ->
                val name = nameEdit.text.toString()
                val strength = strengthEdit.text.toString().toFloatOrNull() ?: 0f
                val volume = volumeEdit.text.toString().toIntOrNull() ?: 0
                val cost = costEdit.text?.toString()?.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
                val type = DrinkType.values()[typeSpinner.selectedItemPosition]
                if (name.isNotEmpty() && strength > 0 && volume > 0) {
                    onSave(DrinkPreset(name, type, volume, strength, drink?.favorite ?: false, cost))
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
            val cost = dialogView.findViewById<android.widget.EditText>(R.id.et_cost)
                ?.text?.toString()?.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
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
                            favorite = favoriteSwitch?.isChecked == true,
                            cost = cost
                        )
                    )
                }
                dialog.dismiss()
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
            setIconResource(R.drawable.ic_nav_calendar)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setOnClickListener {
                showDatePicker { selected ->
                    try {
                        repo.updateEntryDate(entry.id, selected)
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

    private fun showSetGoalsDialog(isInitialSetup: Boolean = false) {
        showSetupDialog(isInitialSetup)
    }

    private fun showManageGoalsBaselineDialog() {
        val options = arrayOf("Set Goals & Baseline", "Reset Baseline")
        AlertDialog.Builder(this)
            .setTitle("Manage Goals & Baseline")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSetGoalsDialog()
                    1 -> {
                        AlertDialog.Builder(this)
                            .setTitle("Reset Baseline")
                            .setMessage("This will clear your current baseline. You can set a new one afterwards.")
                            .setPositiveButton("Reset") { _, _ -> resetBaseline() }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun showSetupDialog(isInitialSetup: Boolean = false) {
        GoalsSetupDialog.show(this) {
            loadData()
            if (isInitialSetup) {
                showFavoriteSetupSheet(true)
            } else {
                val prefsDone = getSharedPreferences(prefsName, MODE_PRIVATE)
                if (!prefsDone.getBoolean("onboarding_complete", false)) {
                    prefsDone.edit().putBoolean("onboarding_complete", true).apply()
                    findViewById<View>(R.id.btn_initial_setup)?.visibility = View.GONE
                }
            }
        }
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
        try {
            repo.addEntry(name, alcoholPercentage, volumeMl, notes)
            // Neutral confirmation - logging is data, not a celebration
            Toast.makeText(this, getString(R.string.logged_toast), Toast.LENGTH_SHORT).show()
            loadData()
            maybePromptSavePreset(name, volumeMl, alcoholPercentage)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't log that. Try again.", Toast.LENGTH_SHORT).show()
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
        try {
            repo.updateEntry(id, name, alcoholPercentage, volumeMl, notes)
            Toast.makeText(this, "Entry updated", Toast.LENGTH_SHORT).show()
            loadData()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to update entry", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteBeerEntry(entry: BeerEntry) {
        try {
            repo.deleteEntry(entry.id)
            Toast.makeText(this, "Entry deleted", Toast.LENGTH_SHORT).show()
            loadData()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to delete entry", Toast.LENGTH_SHORT).show()
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
