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
}
