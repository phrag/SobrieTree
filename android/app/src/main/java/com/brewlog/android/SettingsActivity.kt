package com.brewlog.android

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
import android.content.ContentResolver
import java.io.InputStream
import java.io.OutputStream

class SettingsActivity : AppCompatActivity() {
    private val prefsName = "brewlog_prefs"

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
        val deleteAllBtn = findViewById<MaterialButton>(R.id.btn_delete_all)
        val infoGuidelines = findViewById<TextView>(R.id.tv_info_guidelines)
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

        // Price per drink (fallback when a drink has no cost; powers money-saved)
        val priceEdit = findViewById<TextInputEditText>(R.id.et_price_per_drink)
        if (appPrefs.pricePerDrink > 0) {
            priceEdit.setText(String.format("%.2f", appPrefs.pricePerDrink))
        }

        // Instant-apply for the text fields: each commits on blur / IME Done, and
        // once more in onPause. Invalid or blank input reverts to the stored value
        // (except price, where blank legitimately means "no price"), so we never
        // persist garbage and never nag with a per-keystroke error.
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
        fun persistPrice() {
            val raw = priceEdit.text?.toString().orEmpty()
            if (raw.isBlank()) {
                appPrefs.pricePerDrink = 0f
                return
            }
            val v = raw.toFloatOrNull()?.coerceAtLeast(0f)
            if (v != null) appPrefs.pricePerDrink = v
            else priceEdit.setText(if (appPrefs.pricePerDrink > 0) String.format("%.2f", appPrefs.pricePerDrink) else "")
        }
        commitOnBlurAndDone(beerSizeEdit) { persistBeerSize() }
        commitOnBlurAndDone(beerStrengthEdit) { persistBeerStrength() }
        commitOnBlurAndDone(eodEdit) { persistEndOfDay() }
        commitOnBlurAndDone(priceEdit) { persistPrice() }
        flushSettings = { persistBeerSize(); persistBeerStrength(); persistEndOfDay(); persistPrice() }

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

        // App updates (opt-in; the only feature that touches the network)
        run {
            val updatesSwitch = findViewById<SwitchMaterial>(R.id.switch_updates)
            val updatesOptions = findViewById<android.view.View>(R.id.group_update_options)
            val channelDropdown = findViewById<AutoCompleteTextView>(R.id.et_update_channel)
            val checkBtn = findViewById<MaterialButton>(R.id.btn_check_updates)
            val statusText = findViewById<TextView>(R.id.tv_update_status)

            val channelValues = listOf(UpdateChecker.CHANNEL_STABLE, UpdateChecker.CHANNEL_LATEST)
            val channelLabels = listOf(
                getString(R.string.update_channel_stable),
                getString(R.string.update_channel_latest)
            )
            channelDropdown.setAdapter(
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, channelLabels)
            )
            val channelIndex = channelValues.indexOf(appPrefs.updateChannel).coerceAtLeast(0)
            channelDropdown.setText(channelLabels[channelIndex], false)
            channelDropdown.setOnItemClickListener { _, _, position, _ ->
                appPrefs.updateChannel = channelValues[position]
                channelDropdown.setText(channelLabels[position], false)
            }
            channelDropdown.setOnClickListener { channelDropdown.showDropDown() }

            updatesSwitch.isChecked = appPrefs.updatesEnabled
            updatesOptions.visibility =
                if (appPrefs.updatesEnabled) android.view.View.VISIBLE else android.view.View.GONE
            updatesSwitch.setOnCheckedChangeListener { _, checked ->
                if (checked && !appPrefs.updatesEnabled) {
                    // Enabling the only networked feature: explain the trade-off first.
                    AlertDialog.Builder(this)
                        .setTitle(R.string.updates_optin_dialog_title)
                        .setMessage(R.string.updates_optin_dialog_body)
                        .setCancelable(false)
                        .setPositiveButton(R.string.updates_optin_enable) { d, _ ->
                            appPrefs.updatesEnabled = true
                            updatesOptions.visibility = android.view.View.VISIBLE
                            d.dismiss()
                        }
                        .setNegativeButton(R.string.cancel) { d, _ ->
                            updatesSwitch.isChecked = false // re-fires this listener with checked=false
                            d.dismiss()
                        }
                        .show()
                } else if (!checked) {
                    appPrefs.updatesEnabled = false
                    updatesOptions.visibility = android.view.View.GONE
                }
            }

            checkBtn.setOnClickListener {
                statusText.visibility = android.view.View.VISIBLE
                statusText.text = getString(R.string.update_checking)
                val channel = appPrefs.updateChannel
                val currentVersion = try {
                    packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
                } catch (_: Exception) { "0" }
                kotlin.concurrent.thread {
                    val outcome = UpdateChecker.check(channel)
                    runOnUiThread {
                        appPrefs.lastUpdateCheck = System.currentTimeMillis()
                        when (outcome) {
                            is UpdateChecker.Outcome.Found -> {
                                val release = outcome.release
                                if (UpdateChecker.isNewer(release.versionName, currentVersion)) {
                                    statusText.text =
                                        getString(R.string.update_available_title) + ": " + release.versionName
                                    promptInstall(release)
                                } else {
                                    statusText.text = getString(R.string.update_up_to_date)
                                }
                            }
                            UpdateChecker.Outcome.None ->
                                statusText.text =
                                    if (channel == UpdateChecker.CHANNEL_STABLE)
                                        getString(R.string.update_none_stable)
                                    else getString(R.string.update_none)
                            UpdateChecker.Outcome.Error ->
                                statusText.text = getString(R.string.update_check_failed)
                        }
                    }
                }
            }
        }

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
        
        // Export button
        exportBtn.setOnClickListener {
            try {
                exportFileLauncher.launch("brewlog_export_${System.currentTimeMillis()}.csv")
            } catch (e: Exception) {
                Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Import button
        importBtn.setOnClickListener {
            try {
                importFileLauncher.launch(arrayOf("text/csv", "text/plain"))
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
                        BrewLogNative.delete_all_data()
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
        
        // Guidelines click handler
        infoGuidelines.setOnClickListener {
            Toast.makeText(this, "Source: National health guidelines for low-risk alcohol consumption", Toast.LENGTH_LONG).show()
        }
        
        BottomNavHelper.wire(this, findViewById(R.id.bottom_nav), R.id.nav_settings)
    }

    /** Confirm, then download the release APK and hand it to the system installer. */
    private fun promptInstall(release: UpdateChecker.Release) {
        val notes = release.notes.take(400).trim()
        val message = buildString {
            append("Version ${release.versionName} is available.")
            if (notes.isNotBlank()) append("\n\n$notes")
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.update_available_title)
            .setMessage(message)
            .setPositiveButton(R.string.update_action_update) { d, _ ->
                d.dismiss()
                Toast.makeText(this, getString(R.string.update_downloading), Toast.LENGTH_SHORT).show()
                kotlin.concurrent.thread {
                    val apk = UpdateInstaller.downloadApk(this, release.apkUrl)
                    runOnUiThread {
                        if (apk != null) {
                            UpdateInstaller.installApk(this, apk)
                        } else {
                            Toast.makeText(this, getString(R.string.update_download_failed), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.update_action_later, null)
            .show()
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
            
            importFromCsv(csvData)
            Toast.makeText(this, "Data imported successfully", Toast.LENGTH_SHORT).show()
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
            val json = BrewLogNative.get_beer_entries_json(startDate.toString(), endDate.toString())
            val entries = JSONArray(json)
            
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                val date = entry.optString("date", "")
                val name = entry.optString("name", "").replace(",", ";") // Replace commas to avoid CSV issues
                val alcohol = entry.optDouble("alcoholPercentage", 0.0)
                val volume = entry.optDouble("volume_ml", 0.0) // Fixed field name
                val notes = entry.optString("notes", "").replace(",", ";").replace("\n", " ") // Clean notes
                
                csv.appendLine("$date,$name,$alcohol,$volume,$notes")
            }
        } catch (e: Exception) {
            throw Exception("Failed to export data: ${e.message}")
        }
        
        return csv.toString()
    }
    
    private fun importFromCsv(csvData: String) {
        try {
            val lines = csvData.split("\n")
            if (lines.isEmpty() || lines[0] != "Date,Name,Alcohol%,Volume(ml),Notes") {
                throw Exception("Invalid CSV format")
            }
            
            var importedCount = 0
            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                if (line.isEmpty()) continue
                
                val parts = line.split(",")
                if (parts.size >= 5) {
                    val date = parts[0]
                    val name = parts[1].replace(";", ",")
                    val alcohol = parts[2].toDoubleOrNull() ?: 0.0
                    val volume = parts[3].toDoubleOrNull() ?: 0.0
                    val notes = parts[4].replace(";", ",")
                    
                    // Add entry using native backend
                    val result = BrewLogNative.add_beer_entry_full_jni(
                        java.util.UUID.randomUUID().toString(),
                        name,
                        alcohol,
                        volume,
                        date,
                        notes
                    )
                    
                    if (result == "OK") {
                        importedCount++
                    }
                }
            }
            
            if (importedCount == 0) {
                throw Exception("No valid entries found to import")
            }
        } catch (e: Exception) {
            throw Exception("Failed to import data: ${e.message}")
        }
    }
}
