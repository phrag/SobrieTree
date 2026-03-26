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
import java.io.File
import java.io.FileWriter
import java.io.FileReader
import java.io.BufferedReader
import java.time.LocalDate
import java.time.DayOfWeek
import org.json.JSONArray
import org.json.JSONObject
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ContentResolver
import java.io.InputStream
import java.io.OutputStream

class SettingsActivity : AppCompatActivity() {
    private val prefsName = "brewlog_prefs"
    
    // File picker contracts
    private val exportFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { exportToFile(it) }
    }
    
    private val importFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importFromFile(it) }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
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
        val themeSwitch = findViewById<SwitchMaterial>(R.id.switch_theme)
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
        themeSwitch.isChecked = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        secureSwitch.isChecked = prefs.getBoolean("flag_secure", true)
        eodEdit.setText(endOfDay.toString())
        
        // Setup start of week dropdown
        val daysOfWeek = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val dayAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, daysOfWeek)
        startOfWeekDropdown.setAdapter(dayAdapter)
        startOfWeekDropdown.setText(daysOfWeek[startOfWeek - 1], false)
        startOfWeekDropdown.setOnItemClickListener { _, _, position, _ ->
            startOfWeekDropdown.setText(daysOfWeek[position], false)
        }
        startOfWeekDropdown.setOnClickListener {
            startOfWeekDropdown.showDropDown()
        }
        
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            versionText?.text = "Version: ${pInfo.versionName}"
        } catch (_: Exception) { }
        
        // Show info text about the About Health section
        infoGuidelines?.text = "View evidence-based information from WHO, NHS, and CDC about alcohol and health."
        
        // Save button
        val saveBtn = findViewById<MaterialButton>(R.id.btn_save_settings)
        saveBtn.setOnClickListener {
            try {
                val newSize = beerSizeEdit.text.toString().toInt().coerceAtLeast(1)
                val newStrength = beerStrengthEdit.text.toString().toFloat().coerceIn(0.1f, 100f)
                val newEod = eodEdit.text.toString().toInt().coerceIn(0, 23)
                val selectedDay = startOfWeekDropdown.text.toString()
                val newStartOfWeek = daysOfWeek.indexOf(selectedDay) + 1
                
                prefs.edit()
                    .putInt("default_beer_size", newSize)
                    .putFloat("default_beer_strength", newStrength)
                    .putInt("end_of_day_hour", newEod)
                    .putInt("start_of_week", newStartOfWeek)
                    .putBoolean("flag_secure", secureSwitch.isChecked)
                    .apply()
                
                // Apply theme change
                if (themeSwitch.isChecked) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
                
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show()
            }
        }
        
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
                .setMessage("This will permanently delete all your drink entries and settings. Are you sure?")
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
        
        // About Health button handler
        val aboutHealthBtn = findViewById<MaterialButton>(R.id.btn_about_health)
        aboutHealthBtn.setOnClickListener {
            startActivity(Intent(this, AboutHealthActivity::class.java))
        }
        
        // Standard Drink Calculator button handler
        val drinkCalculatorBtn = findViewById<MaterialButton>(R.id.btn_drink_calculator)
        drinkCalculatorBtn.setOnClickListener {
            showDrinkCalculatorDialog()
        }
        
        // Bottom nav
        findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav).apply {
            menu.clear()
            inflateMenu(R.menu.menu_bottom)
            selectedItemId = R.id.nav_settings
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> {
                        startActivity(Intent(this@SettingsActivity, MainActivity::class.java))
                        true
                    }
                    R.id.nav_progress -> {
                        startActivity(Intent(this@SettingsActivity, ProgressActivity::class.java))
                        true
                    }
                    R.id.nav_calendar -> {
                        startActivity(Intent(this@SettingsActivity, CalendarActivity::class.java))
                        true
                    }
                    R.id.nav_settings -> true
                    else -> false
                }
            }
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
    
    private fun showDrinkCalculatorDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_drink_calculator, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        val etVolume = dialogView.findViewById<TextInputEditText>(R.id.et_calc_volume)
        val etAbv = dialogView.findViewById<TextInputEditText>(R.id.et_calc_abv)
        val btnCalculate = dialogView.findViewById<MaterialButton>(R.id.btn_calculate)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btn_close)
        val cardResults = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_results)
        val tvPureAlcohol = dialogView.findViewById<TextView>(R.id.tv_pure_alcohol)
        val tvResultUk = dialogView.findViewById<TextView>(R.id.tv_result_uk)
        val tvResultUs = dialogView.findViewById<TextView>(R.id.tv_result_us)
        
        // Pre-fill with default values from settings
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        etVolume.setText(prefs.getInt("default_beer_size", 500).toString())
        etAbv.setText(prefs.getFloat("default_beer_strength", 5.0f).toString())
        
        btnCalculate.setOnClickListener {
            try {
                val volumeMl = etVolume.text.toString().toDoubleOrNull() ?: 0.0
                val abvPercent = etAbv.text.toString().toDoubleOrNull() ?: 0.0
                
                if (volumeMl <= 0 || abvPercent <= 0) {
                    Toast.makeText(this, getString(R.string.invalid_input), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // Calculate pure alcohol in grams
                // Formula: volume (ml) * ABV (as decimal) * 0.789 (density of ethanol g/ml)
                val pureAlcoholGrams = volumeMl * (abvPercent / 100.0) * 0.789
                
                // Standard drinks: UK/WHO = 10g, US = 14g
                val standardDrinksUk = pureAlcoholGrams / 10.0
                val standardDrinksUs = pureAlcoholGrams / 14.0
                
                // UK units (8g per unit)
                val ukUnits = pureAlcoholGrams / 8.0
                
                cardResults.visibility = android.view.View.VISIBLE
                tvPureAlcohol.text = String.format("Pure alcohol: %.1fg", pureAlcoholGrams)
                tvResultUk.text = String.format(
                    "UK/WHO: %.1f standard drinks (%.1f UK units)",
                    standardDrinksUk, ukUnits
                )
                tvResultUs.text = String.format(
                    "US: %.1f standard drinks",
                    standardDrinksUs
                )
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.invalid_input), Toast.LENGTH_SHORT).show()
            }
        }
        
        btnClose.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
}
