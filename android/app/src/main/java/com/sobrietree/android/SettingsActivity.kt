package com.sobrietree.android

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.button.MaterialButton
import android.widget.TextView
import android.widget.Toast
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.view.View
import android.view.inputmethod.EditorInfo
import java.io.File
import java.io.FileWriter
import java.io.FileReader
import java.io.BufferedReader
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.sobrietree.android.engine.AbvRepair
import android.content.ContentResolver
import java.io.InputStream
import java.io.OutputStream

class SettingsActivity : AppCompatActivity() {
    private val prefsName = "sobrietree_prefs"
    private val RELEASES_URL = "https://github.com/phrag/SobrieTree/releases"

    // Flushes the text-field settings to prefs; reassigned in onCreate. Called
    // from onPause so a typed-but-not-blurred value is still saved on exit.
    private var flushSettings: () -> Unit = {}

    /** Commit a text field's value the moment it loses focus or the user taps Done. */
    private fun commitOnBlurAndDone(edit: TextInputEditText, onCommit: () -> Unit) {
        edit.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) onCommit() }
        edit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) onCommit()
            false
        }
    }

    override fun onPause() {
        super.onPause()
        flushSettings()
    }
    
    // File picker contracts
    private val exportFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { exportToFile(it) }
    }
    
    private val importFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importFromFile(it) }
    }

    // Encrypted backup. The passphrase is asked for at the moment it's needed and
    // never stored, so nothing on the device can open a backup on its own.
    private val backupFileLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            uri?.let { target ->
                askPassphrase(confirm = true, message = getString(R.string.backup_set_passphrase)) { pass ->
                    writeBackup(target, pass)
                }
            }
        }

    private val restoreFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { source ->
            askPassphrase(confirm = false, message = getString(R.string.backup_enter_passphrase)) { pass ->
                restoreBackup(source, pass)
            }
        }
    }

    // Whatever feature requested notification permission runs its callback here
    private var onNotifPermission: ((Boolean) -> Unit)? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            onNotifPermission?.invoke(granted)
            onNotifPermission = null
        }

    /** Run [action] once notification permission is available, requesting it on API 33+ if needed. */
    private fun withNotificationPermission(action: (granted: Boolean) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            onNotifPermission = action
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action(true)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        SecureWindow.apply(this)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val defaultSize = prefs.getInt("default_beer_size", 500)
        val defaultStrength = prefs.getFloat("default_beer_strength", 5.0f)
        val endOfDay = prefs.getInt("end_of_day_hour", 3)
        val startOfWeek = prefs.getInt("start_of_week", 1) // Default to Monday (1)
        
        val beerSizeEdit = findViewById<TextInputEditText>(R.id.et_beer_size)
        val beerStrengthEdit = findViewById<TextInputEditText>(R.id.et_beer_strength)
        val beerSizeLayout = findViewById<TextInputLayout>(R.id.beer_size_layout)
        val beerStrengthLayout = findViewById<TextInputLayout>(R.id.beer_strength_layout)
        val themeDropdown = findViewById<AutoCompleteTextView>(R.id.et_theme)
        val secureSwitch = findViewById<SwitchMaterial>(R.id.switch_secure)
        val eodEdit = findViewById<TextInputEditText>(R.id.et_end_of_day)
        val exportBtn = findViewById<MaterialButton>(R.id.btn_export)
        val importBtn = findViewById<MaterialButton>(R.id.btn_import)
        val backupBtn = findViewById<MaterialButton>(R.id.btn_backup)
        val restoreBtn = findViewById<MaterialButton>(R.id.btn_restore)
        val deleteAllBtn = findViewById<MaterialButton>(R.id.btn_delete_all)
        val redoSetupBtn = findViewById<MaterialButton>(R.id.btn_redo_initial_setup)
        val versionText = findViewById<TextView>(R.id.tv_version)
        val startOfWeekDropdown = findViewById<AutoCompleteTextView>(R.id.et_start_of_week)
        
        beerSizeEdit.setText(defaultSize.toString())
        beerStrengthEdit.setText(defaultStrength.toString())
        secureSwitch.isChecked = prefs.getBoolean("flag_secure", true)
        // Apply immediately so the change is visible without leaving Settings
        secureSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("flag_secure", checked).apply()
            SecureWindow.apply(this, checked)
        }
        eodEdit.setText(endOfDay.toString())

        // Theme dropdown (System / Light / Dark), persisted so it survives restart
        val appPrefs = AppPrefs(this)
        val themeOptions = arrayOf("System default", "Light", "Dark")
        val themeModes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )
        val themeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, themeOptions)
        themeDropdown.setAdapter(themeAdapter)
        val currentThemeIndex = themeModes.indexOf(appPrefs.themeMode).coerceAtLeast(0)
        themeDropdown.setText(themeOptions[currentThemeIndex], false)
        themeDropdown.setOnClickListener { themeDropdown.showDropDown() }
        themeDropdown.setOnItemClickListener { _, _, position, _ ->
            appPrefs.themeMode = themeModes[position]
            AppCompatDelegate.setDefaultNightMode(themeModes[position])
        }

        // Currency for money displays (code, label); null code = follow device locale
        val currencyDropdown = findViewById<AutoCompleteTextView>(R.id.et_currency)
        val currencyOptions = listOf(
            null to getString(R.string.currency_system_default),
            "USD" to "US Dollar ($)",
            "EUR" to "Euro (€)",
            "GBP" to "British Pound (£)",
            "CAD" to "Canadian Dollar ($)",
            "AUD" to "Australian Dollar ($)",
            "CHF" to "Swiss Franc (CHF)",
            "SEK" to "Swedish Krona (kr)",
            "NOK" to "Norwegian Krone (kr)",
            "DKK" to "Danish Krone (kr)",
            "PLN" to "Polish Złoty (zł)",
            "JPY" to "Japanese Yen (¥)",
            "INR" to "Indian Rupee (₹)",
            "BRL" to "Brazilian Real (R$)",
            "MXN" to "Mexican Peso ($)",
            "ZAR" to "South African Rand (R)"
        )
        val currencyLabels = currencyOptions.map { it.second }
        val currencyAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, currencyLabels)
        currencyDropdown.setAdapter(currencyAdapter)
        val currentCurrencyIndex = currencyOptions.indexOfFirst { it.first == appPrefs.currencyCode }.coerceAtLeast(0)
        currencyDropdown.setText(currencyLabels[currentCurrencyIndex], false)
        currencyDropdown.setOnClickListener { currencyDropdown.showDropDown() }
        currencyDropdown.setOnItemClickListener { _, _, position, _ ->
            appPrefs.currencyCode = currencyOptions[position].first
            Money.applyFrom(appPrefs)
        }

        // The single money input: everything "money saved" compares against.
        // Per-drink prices live on the drinks themselves, not here.
        val weeklySpendEdit = findViewById<TextInputEditText>(R.id.et_baseline_weekly_spend)
        if (appPrefs.baselineWeeklySpend > 0) {
            weeklySpendEdit.setText(String.format("%.2f", appPrefs.baselineWeeklySpend))
        }

        // Instant-apply for the text fields: each commits on blur / IME Done, and
        // once more in onPause. Invalid or blank input reverts to the stored value
        // (except weekly spend, where blank legitimately means "not set"), so we
        // never persist garbage and never nag with a per-keystroke error.
        fun persistBeerSize() {
            val v = beerSizeEdit.text.toString().toIntOrNull()?.coerceAtLeast(1)
            if (v != null) prefs.edit().putInt("default_beer_size", v).apply()
            else beerSizeEdit.setText(prefs.getInt("default_beer_size", 500).toString())
        }
        fun persistBeerStrength() {
            val v = beerStrengthEdit.text.toString().toFloatOrNull()?.coerceIn(0.1f, 100f)
            if (v != null) prefs.edit().putFloat("default_beer_strength", v).apply()
            else beerStrengthEdit.setText(prefs.getFloat("default_beer_strength", 5.0f).toString())
        }
        fun persistEndOfDay() {
            val v = eodEdit.text.toString().toIntOrNull()?.coerceIn(0, 23)
            if (v != null) prefs.edit().putInt("end_of_day_hour", v).apply()
            else eodEdit.setText(prefs.getInt("end_of_day_hour", 3).toString())
        }
        fun persistWeeklySpend() {
            val raw = weeklySpendEdit.text?.toString().orEmpty()
            if (raw.isBlank()) {
                appPrefs.baselineWeeklySpend = 0f
                return
            }
            val v = raw.toFloatOrNull()?.coerceAtLeast(0f)
            if (v != null) appPrefs.baselineWeeklySpend = v
            else weeklySpendEdit.setText(if (appPrefs.baselineWeeklySpend > 0) String.format("%.2f", appPrefs.baselineWeeklySpend) else "")
        }
        commitOnBlurAndDone(beerSizeEdit) { persistBeerSize() }
        commitOnBlurAndDone(beerStrengthEdit) { persistBeerStrength() }
        commitOnBlurAndDone(eodEdit) { persistEndOfDay() }
        commitOnBlurAndDone(weeklySpendEdit) { persistWeeklySpend() }
        flushSettings = { persistBeerSize(); persistBeerStrength(); persistEndOfDay(); persistWeeklySpend() }

        // Edit goals & baseline directly from Settings
        findViewById<MaterialButton>(R.id.btn_edit_goals).setOnClickListener {
            GoalsSetupDialog.show(this) {}
        }

        // Daily check-in reminder (opt-in, local only)
        val reminderSwitch = findViewById<SwitchMaterial>(R.id.switch_reminder)
        val reminderTimeText = findViewById<TextView>(R.id.tv_reminder_time)
        fun renderReminderTime() {
            reminderTimeText.text = String.format("%02d:%02d", appPrefs.reminderHour, appPrefs.reminderMinute)
        }
        reminderSwitch.isChecked = appPrefs.reminderEnabled
        renderReminderTime()
        reminderSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                withNotificationPermission { granted ->
                    if (granted) {
                        appPrefs.reminderEnabled = true
                        ReminderScheduler.schedule(this, appPrefs.reminderHour, appPrefs.reminderMinute)
                    } else {
                        appPrefs.reminderEnabled = false
                        reminderSwitch.isChecked = false
                        Toast.makeText(this, getString(R.string.reminder_permission_denied), Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                appPrefs.reminderEnabled = false
                ReminderScheduler.cancel(this)
            }
        }
        findViewById<android.view.View>(R.id.row_reminder_time).setOnClickListener {
            android.app.TimePickerDialog(
                this,
                { _, hour, minute ->
                    appPrefs.reminderHour = hour
                    appPrefs.reminderMinute = minute
                    renderReminderTime()
                    if (appPrefs.reminderEnabled) {
                        ReminderScheduler.schedule(this, hour, minute)
                    }
                },
                appPrefs.reminderHour,
                appPrefs.reminderMinute,
                true
            ).show()
        }

        // Support around your usual start-drinking time (opt-in, local only)
        val highRiskSwitch = findViewById<SwitchMaterial>(R.id.switch_high_risk)
        val highRiskTimeText = findViewById<TextView>(R.id.tv_high_risk_time)
        fun renderHighRiskTime() {
            highRiskTimeText.text = String.format("%02d:%02d", appPrefs.highRiskHour, appPrefs.highRiskMinute)
        }
        highRiskSwitch.isChecked = appPrefs.highRiskEnabled
        renderHighRiskTime()
        highRiskSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                withNotificationPermission { granted ->
                    if (granted) {
                        appPrefs.highRiskEnabled = true
                        HighRiskScheduler.schedule(this, appPrefs.highRiskHour, appPrefs.highRiskMinute)
                    } else {
                        appPrefs.highRiskEnabled = false
                        highRiskSwitch.isChecked = false
                        Toast.makeText(this, getString(R.string.reminder_permission_denied), Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                appPrefs.highRiskEnabled = false
                HighRiskScheduler.cancel(this)
            }
        }
        findViewById<android.view.View>(R.id.row_high_risk_time).setOnClickListener {
            android.app.TimePickerDialog(
                this,
                { _, hour, minute ->
                    appPrefs.highRiskHour = hour
                    appPrefs.highRiskMinute = minute
                    renderHighRiskTime()
                    if (appPrefs.highRiskEnabled) {
                        HighRiskScheduler.schedule(this, hour, minute)
                    }
                },
                appPrefs.highRiskHour,
                appPrefs.highRiskMinute,
                true
            ).show()
        }
        
        // Journey start date — what all progress counts from
        val startDateText = findViewById<TextView>(R.id.tv_start_date)
        val startDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")
        fun renderStartDate() {
            startDateText.text = (appPrefs.baselineSetDate ?: LocalDate.now()).format(startDateFormat)
        }
        renderStartDate()
        findViewById<android.view.View>(R.id.row_start_date).setOnClickListener {
            val current = appPrefs.baselineSetDate ?: LocalDate.now()
            val picker = android.app.DatePickerDialog(
                this,
                { _, year, month, day ->
                    val picked = LocalDate.of(year, month + 1, day)
                    if (picked.isAfter(LocalDate.now())) {
                        Toast.makeText(this, getString(R.string.start_date_future_error), Toast.LENGTH_SHORT).show()
                    } else {
                        appPrefs.baselineSetDate = picked
                        renderStartDate()
                    }
                },
                current.year, current.monthValue - 1, current.dayOfMonth
            )
            picker.datePicker.maxDate = System.currentTimeMillis()
            picker.show()
        }

        // Setup start of week dropdown
        val daysOfWeek = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val dayAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, daysOfWeek)
        startOfWeekDropdown.setAdapter(dayAdapter)
        startOfWeekDropdown.setText(daysOfWeek[startOfWeek - 1], false)
        startOfWeekDropdown.setOnItemClickListener { _, _, position, _ ->
            startOfWeekDropdown.setText(daysOfWeek[position], false)
            prefs.edit().putInt("start_of_week", position + 1).apply()
        }
        startOfWeekDropdown.setOnClickListener {
            startOfWeekDropdown.showDropDown()
        }
        
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            versionText?.text = "Version: ${pInfo.versionName}"
        } catch (_: Exception) { }

        // App updates: SobrieTree has no network access of its own, so updates open
        // in the browser where you download and install the latest build yourself.
        findViewById<MaterialButton>(R.id.btn_view_release).setOnClickListener {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(RELEASES_URL))
                )
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.update_open_failed), Toast.LENGTH_SHORT).show()
            }
        }

        // Export button
        bindBackupStatus()
        findViewById<MaterialButton>(R.id.btn_fix_strengths).setOnClickListener { showAbvRepair() }
        backupBtn.setOnClickListener {
            backupFileLauncher.launch(BackupManager.backupFileName(LocalDate.now()))
        }
        restoreBtn.setOnClickListener {
            // Widened deliberately: .ttbk has no registered MIME type, so a
            // narrow filter greys out the very file we wrote.
            restoreFileLauncher.launch(arrayOf("*/*"))
        }

        exportBtn.setOnClickListener {
            try {
                exportFileLauncher.launch("sobrietree_export_${System.currentTimeMillis()}.csv")
            } catch (e: Exception) {
                Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Import button
        importBtn.setOnClickListener {
            try {
                // Exported CSVs often arrive tagged as octet-stream or
                // comma-separated-values; a narrow filter greys them out in the
                // picker, which looks like "import is broken".
                importFileLauncher.launch(arrayOf("*/*"))
            } catch (e: Exception) {
                Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Delete all button
        deleteAllBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete All Data")
                .setMessage("This will permanently delete all your beer entries and settings. Are you sure?")
                .setPositiveButton("Delete") { _, _ ->
                    try {
                        SobrieTreeNative.delete_all_data()
                        prefs.edit().clear().apply()
                        Toast.makeText(this, "All data deleted", Toast.LENGTH_SHORT).show()
                        // Restart app to reset everything
                        val intent = packageManager.getLaunchIntentForPackage(packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        finish()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        // Redo initial setup button
        redoSetupBtn.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("open_setup_dialog", true)
            })
        }
        
        BottomNavHelper.wire(this, findViewById(R.id.bottom_nav), R.id.nav_settings)
    }

    /**
     * Asks for a passphrase, optionally twice. The characters go straight to the
     * crypto and are wiped afterwards - they are never written to prefs, which
     * is what keeps a stolen phone from being able to open its own backups.
     */
    private fun askPassphrase(confirm: Boolean, message: String, onEntered: (CharArray) -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_passphrase, null)
        view.findViewById<TextView>(R.id.tv_passphrase_message).text = message
        val field = view.findViewById<TextInputEditText>(R.id.et_passphrase)
        val confirmLayout = view.findViewById<TextInputLayout>(R.id.layout_passphrase_confirm)
        val confirmField = view.findViewById<TextInputEditText>(R.id.et_passphrase_confirm)
        val warning = view.findViewById<TextView>(R.id.tv_passphrase_warning)
        if (!confirm) {
            confirmLayout.visibility = View.GONE
            warning.visibility = View.GONE
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel) { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entered = field.text?.toString().orEmpty()
                if (entered.isEmpty()) {
                    field.error = getString(R.string.backup_passphrase_empty)
                    return@setOnClickListener
                }
                if (confirm && entered != confirmField.text?.toString().orEmpty()) {
                    confirmField.error = getString(R.string.backup_passphrase_mismatch)
                    return@setOnClickListener
                }
                dialog.dismiss()
                val chars = entered.toCharArray()
                try {
                    onEntered(chars)
                } finally {
                    chars.fill('\u0000')
                }
            }
        }
        dialog.show()
    }

    private fun writeBackup(uri: Uri, passphrase: CharArray) {
        try {
            BackupManager.writeTo(this, uri, passphrase)
            Toast.makeText(this, R.string.backup_written, Toast.LENGTH_SHORT).show()
            bindBackupStatus()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.backup_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun restoreBackup(uri: Uri, passphrase: CharArray) {
        try {
            val r = BackupManager.restoreFrom(this, uri, passphrase)
            val message = buildString {
                append(
                    getString(
                        R.string.backup_restored,
                        r.entriesRestored,
                        getString(if (r.entriesRestored == 1) R.string.entry_singular else R.string.entry_plural),
                        r.presetsRestored,
                        getString(if (r.presetsRestored == 1) R.string.drink_singular else R.string.drink_plural)
                    )
                )
                if (r.entriesAlreadyPresent > 0) {
                    append(
                        getString(
                            R.string.backup_restored_skipped,
                            r.entriesAlreadyPresent,
                            getString(if (r.entriesAlreadyPresent == 1) R.string.entry_singular else R.string.entry_plural)
                        )
                    )
                }
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.backup_restore)
                .setMessage(message)
                .setPositiveButton(R.string.got_it, null)
                .show()
            bindBackupStatus()
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle(R.string.backup_restore)
                .setMessage(getString(R.string.backup_restore_failed, e.message ?: ""))
                .setPositiveButton(R.string.got_it, null)
                .show()
        }
    }

    /**
     * Offers to replace placeholder drink strengths with real ones.
     *
     * Import, "set total for day" and the old export all left entries carrying
     * the default ABV, which makes units, calories and money quietly wrong on
     * an otherwise correct calculation. Nothing is changed without the user
     * confirming each name, because a drink that genuinely is the default
     * strength looks identical to a placeholder.
     */
    private fun showAbvRepair() {
        val prefs = AppPrefs(this)
        val defaultAbv = prefs.defaultDrinkStrength.toDouble()
        val today = LocalDate.now()
        val entries = EntryRepository().getEntries(today.minusYears(20), today.plusDays(1))
        val drinks = DrinkPresetStore.getPresets(prefs.prefs).map { AbvRepair.Drink(it.name, it.abv) }
        val groups = AbvRepair.groups(entries, drinks, defaultAbv)

        if (groups.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.abv_fix_title)
                .setMessage(R.string.abv_fix_none)
                .setPositiveButton(R.string.got_it, null)
                .show()
            return
        }

        // A custom view rather than setMessage + setItems: AlertDialog gives the
        // content slot to the message and silently drops the list, which left
        // this dialog with intro text and nothing to tap.
        val view = layoutInflater.inflate(R.layout.dialog_abv_repair, null)
        view.findViewById<TextView>(R.id.tv_abv_intro).text =
            getString(R.string.abv_fix_intro, trimNumber(defaultAbv))
        val container = view.findViewById<android.widget.LinearLayout>(R.id.ll_abv_groups)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.abv_fix_title)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .create()

        for (g in groups) {
            val row = layoutInflater.inflate(R.layout.item_abv_group, container, false)
            val noun = getString(if (g.count == 1) R.string.entry_singular else R.string.entry_plural)
            row.findViewById<TextView>(R.id.tv_abv_name).text = g.name
            row.findViewById<TextView>(R.id.tv_abv_detail).text = if (g.suggestedAbv != null) {
                getString(
                    R.string.abv_fix_row_suggested,
                    g.count, noun, trimNumber(g.currentAbv), trimNumber(g.suggestedAbv!!), g.suggestedFrom ?: ""
                )
            } else {
                getString(R.string.abv_fix_row_plain, g.count, noun, trimNumber(g.currentAbv))
            }
            row.setOnClickListener {
                dialog.dismiss()
                promptStrength(entries, g, defaultAbv)
            }
            container.addView(row)
        }

        dialog.show()
    }

    private fun promptStrength(
        entries: List<BeerEntry>,
        group: AbvRepair.Group,
        defaultAbv: Double
    ) {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setHint(R.string.alcohol_percentage)
            group.suggestedAbv?.let { setText(trimNumber(it)) }
        }
        val noun = getString(if (group.count == 1) R.string.entry_singular else R.string.entry_plural)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.abv_fix_prompt, group.name, group.count, noun))
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val abv = input.text.toString().replace(',', '.').toDoubleOrNull()
                if (abv == null || abv < 0.0 || abv > 100.0) {
                    Toast.makeText(this, R.string.abv_fix_invalid, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                applyStrength(entries, group, defaultAbv, abv)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyStrength(
        entries: List<BeerEntry>,
        group: AbvRepair.Group,
        defaultAbv: Double,
        newAbv: Double
    ) {
        val repo = EntryRepository()
        val ids = AbvRepair.entryIdsFor(entries, group, defaultAbv).toSet()
        var updated = 0
        for (e in entries) {
            if (e.id !in ids) continue
            repo.updateEntry(e.id, e.name, newAbv, e.volumeMl, e.notes)
            updated++
        }
        val noun = getString(if (updated == 1) R.string.entry_singular else R.string.entry_plural)
        Toast.makeText(
            this,
            getString(R.string.abv_fix_applied, updated, noun, trimNumber(newAbv)),
            Toast.LENGTH_LONG
        ).show()
        SobrieTreeWidget.refresh(this)
        // More names may still need fixing, so drop straight back to the list.
        showAbvRepair()
    }

    /** "5.2" and "5", never "5.0" - a trailing zero reads like precision that isn't there. */
    private fun trimNumber(value: Double): String =
        if (value == value.toInt().toDouble()) value.toInt().toString()
        else String.format("%.1f", value)

    private fun bindBackupStatus() {
        val status = findViewById<TextView>(R.id.tv_backup_status) ?: return
        val at = AppPrefs(this).lastBackupAt
        status.text = if (at <= 0L) {
            getString(R.string.backup_never)
        } else {
            getString(
                R.string.backup_last,
                android.text.format.DateUtils.getRelativeTimeSpanString(
                    at, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
                )
            )
        }
    }

    private fun exportToFile(uri: Uri) {
        try {
            val csvData = exportToCsv()
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(csvData.toByteArray())
            }
            Toast.makeText(this, "Data exported successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun importFromFile(uri: Uri) {
        try {
            val csvData = contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            } ?: throw Exception("Could not read file")
            
            val result = importFromCsv(csvData)
            val message = buildString {
                append("Imported ${result.imported} ${if (result.imported == 1) "entry" else "entries"}.")
                if (result.skipped > 0) append("\n\n${result.skipped} row(s) were skipped as unreadable.")
                if (result.abvFilled > 0) {
                    append("\n\n${result.abvFilled} had no alcohol % recorded, so your default of ")
                    append("${result.defaultAbv}% was used — otherwise those days would have counted ")
                    append("as alcohol-free. You can correct any of them in the Calendar.")
                }
            }
            // Streaks, trees and savings only count days from the journey start
            // onward, so imported history that predates it would silently do
            // nothing. Offer to move the start back rather than leaving the user
            // wondering why the forest never grew.
            val appPrefs = AppPrefs(this)
            val journeyStart = appPrefs.baselineSetDate
            val rewindTo = result.earliestDate
                ?.takeIf { journeyStart != null && it < journeyStart }

            val builder = AlertDialog.Builder(this).setTitle("Import complete")
            if (rewindTo != null && journeyStart != null) {
                val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
                builder.setMessage(
                    message + "\n\nYour imported history starts ${rewindTo.format(fmt)}, but your " +
                        "journey currently starts ${journeyStart.format(fmt)} — so those earlier days " +
                        "won't count toward your streak, trees or savings. Move your journey start back?"
                )
                builder.setPositiveButton("Move to ${rewindTo.format(fmt)}") { _, _ ->
                    appPrefs.baselineSetDate = rewindTo
                    Toast.makeText(
                        this, "Journey start moved to ${rewindTo.format(fmt)}", Toast.LENGTH_LONG
                    ).show()
                    recreate()
                }
                builder.setNegativeButton("Keep current", null)
            } else {
                builder.setMessage(message)
                builder.setPositiveButton(R.string.got_it, null)
            }
            builder.show()
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun exportToCsv(): String {
        val csv = StringBuilder()
        csv.appendLine("Date,Name,Alcohol%,Volume(ml),Notes")
        
        try {
            // Get all entries from the last year
            val startDate = LocalDate.now().minusYears(1)
            val endDate = LocalDate.now()
            val json = SobrieTreeNative.get_beer_entries_json(startDate.toString(), endDate.toString())
            val entries = JSONArray(json)
            
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                val date = entry.optString("date", "")
                val name = entry.optString("name", "")
                // The native layer serialises snake_case; accept camelCase too.
                // Reading the wrong key silently exported 0% for every entry.
                val alcohol = entry.optDouble("alcohol_percentage", entry.optDouble("alcoholPercentage", 0.0))
                val volume = entry.optDouble("volume_ml", entry.optDouble("volumeMl", 0.0))
                val notes = entry.optString("notes", "").replace("\n", " ")

                csv.appendLine(
                    listOf(date, name, alcohol.toString(), volume.toString(), notes)
                        .joinToString(",") { csvField(it) }
                )
            }
        } catch (e: Exception) {
            throw Exception("Failed to export data: ${e.message}")
        }
        
        return csv.toString()
    }
    
    /** Quote a CSV field only when it needs it, doubling any embedded quotes. */
    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' })
            "\"" + value.replace("\"", "\"\"") + "\""
        else value

    /** Split one CSV line, honouring quoted fields. */
    private fun splitCsvLine(line: String): List<String> {
        val fields = ArrayList<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    cur.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { fields.add(cur.toString()); cur.setLength(0) }
                else -> cur.append(c)
            }
            i++
        }
        fields.add(cur.toString())
        return fields
    }

    data class ImportResult(
        val imported: Int,
        val skipped: Int,
        /** Rows whose alcohol % was missing/zero and were filled with the default. */
        val abvFilled: Int,
        val defaultAbv: Float,
        /** Earliest date successfully imported, used to offer moving the journey start back. */
        val earliestDate: LocalDate? = null
    )

    /**
     * Import entries from a CSV export. Deliberately forgiving: a byte-order
     * mark, CRLF endings, a missing or differently-worded header, quoted fields
     * and the older ";"-escaped format are all accepted, and a single bad row
     * never aborts the rest of the file.
     */
    private fun importFromCsv(csvData: String): ImportResult {
        val defaultAbv = AppPrefs(this).defaultDrinkStrength
        val lines = csvData.removePrefix("\uFEFF").split(Regex("\r?\n"))

        var imported = 0
        var skipped = 0
        var abvFilled = 0
        var earliest: LocalDate? = null

        for ((index, raw) in lines.withIndex()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            // Skip a header row wherever it appears, rather than demanding an exact match.
            if (index == 0 && line.substringBefore(',').trim().equals("date", ignoreCase = true)) continue

            val parts = splitCsvLine(line)
            if (parts.size < 4) { skipped++; continue }

            val date = parts[0].trim()
            val name = parts[1].trim().replace(";", ",").ifBlank { "Drink" }
            val volume = parts[3].trim().toDoubleOrNull() ?: 0.0
            // A drink logged without a usable alcohol % would otherwise count as an
            // alcohol-free day and quietly distort streaks and savings, so fall back
            // to the user's default strength and report how often that happened.
            val parsedAbv = parts[2].trim().toDoubleOrNull() ?: 0.0
            val alcohol = if (parsedAbv > 0.0) parsedAbv else defaultAbv.toDouble()
            if (parsedAbv <= 0.0) abvFilled++
            val notes = parts.getOrElse(4) { "" }.replace(";", ",")

            val parsedDate = try { LocalDate.parse(date) } catch (_: Exception) { null }
            if (parsedDate == null || volume <= 0.0) { skipped++; continue }

            val result = SobrieTreeNative.add_beer_entry_full_jni(
                java.util.UUID.randomUUID().toString(), name, alcohol, volume, date, notes
            )
            if (result == "OK") {
                imported++
                if (earliest == null || parsedDate < earliest) earliest = parsedDate
            } else skipped++
        }

        if (imported == 0) {
            throw Exception(
                if (skipped > 0) "No usable rows found ($skipped skipped). Expected: Date,Name,Alcohol%,Volume(ml),Notes"
                else "The file contained no entries."
            )
        }
        return ImportResult(imported, skipped, abvFilled, defaultAbv, earliest)
    }
}
