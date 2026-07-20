package com.brewlog.android

import android.app.Activity
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.time.LocalDate

/**
 * Goals + baseline editor, shared by Home and Settings so the plan can be
 * edited from either place. Persists to SharedPreferences and the in-memory
 * BrewLog; [onSaved] fires after a successful save (callers handle any
 * onboarding chaining themselves).
 */
object GoalsSetupDialog {

    fun show(activity: Activity, onSaved: () -> Unit) {
        val brewLog = BrewLogProvider.instance
        val prefs = activity.getSharedPreferences(AppPrefs.NAME, Context.MODE_PRIVATE)
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_setup, null)

        val defaultDrinkEdit = dialogView.findViewById<TextInputEditText>(R.id.et_default_drink_ml)
        val dailyGoalDrinks = dialogView.findViewById<TextInputEditText>(R.id.et_goal_daily_drinks)
        val weeklyGoalDrinks = dialogView.findViewById<TextInputEditText>(R.id.et_goal_weekly_drinks)
        val dailyBaselineDrinks = dialogView.findViewById<TextInputEditText>(R.id.et_baseline_daily_drinks)
        val weeklyBaselineDrinks = dialogView.findViewById<TextInputEditText>(R.id.et_baseline_weekly_drinks)

        val layoutDailyGoal = dialogView.findViewById<TextInputLayout>(R.id.layout_daily_goal_drinks)
        val layoutWeeklyGoal = dialogView.findViewById<TextInputLayout>(R.id.layout_weekly_goal_drinks)
        val layoutDailyBaseline = dialogView.findViewById<TextInputLayout>(R.id.layout_daily_baseline_drinks)
        val layoutWeeklyBaseline = dialogView.findViewById<TextInputLayout>(R.id.layout_weekly_baseline_drinks)

        val defaultDrink = DrinkPresetStore.defaultPreset(prefs)
        val defaultSizeMl = defaultDrink?.volume ?: prefs.getInt("default_beer_size", 500)
        defaultDrinkEdit.setText(defaultSizeMl.toString())

        // Prefill goals in drinks
        val currentDailyMl = brewLog.getDailyGoal()
        val currentWeeklyMl = brewLog.getWeeklyGoal()
        val vol = if (defaultSizeMl > 0) defaultSizeMl.toDouble() else 500.0
        // Low-risk guideline anchor: ~12 g pure alcohol/day (lower) to ~24 g (upper)
        val assumedAbv = prefs.getFloat("default_beer_strength", 5.0f).toDouble().coerceAtLeast(1.0)
        val gramsPerDrink = vol * (assumedAbv / 100.0) * 0.8
        val guidelineDailyDrinks = if (gramsPerDrink > 0) (24.0 / gramsPerDrink) else 2.0
        val defaultDailyDrinks = guidelineDailyDrinks.coerceIn(1.0, 5.0)
        val defaultWeeklyDrinks = (defaultDailyDrinks * 7).toInt()
        dailyGoalDrinks.setText(
            if (currentDailyMl > 0) (currentDailyMl / vol).toInt().toString() else defaultDailyDrinks.toInt().toString()
        )
        weeklyGoalDrinks.setText(
            if (currentWeeklyMl > 0) (currentWeeklyMl / vol).toInt().toString() else defaultWeeklyDrinks.toString()
        )

        // Prefill baseline in drinks
        val baseline = brewLog.getCurrentBaseline()
        dailyBaselineDrinks.setText(if ((baseline?.averageDailyConsumption ?: 0.0) > 0) ((baseline!!.averageDailyConsumption) / vol).toInt().toString() else "0")
        weeklyBaselineDrinks.setText(if ((baseline?.averageWeeklyConsumption ?: 0.0) > 0) ((baseline!!.averageWeeklyConsumption) / vol).toInt().toString() else "0")

        // Guideline note
        val dailyFemale = (12.0 / gramsPerDrink).coerceAtLeast(0.0).toInt()
        val dailyMale = (24.0 / gramsPerDrink).coerceAtLeast(0.0).toInt()
        dialogView.findViewById<TextView>(R.id.tv_guideline_note)?.text =
            "💡 Low-risk guidelines: ${dailyFemale}-${dailyMale} drinks/day (${vol.toInt()}ml @ ${assumedAbv}%). " +
            "Aim for 2+ alcohol-free days each week - they grow the plant in your ring."

        fun recalcWeeklyFromDaily(source: TextInputEditText, target: TextInputEditText) {
            val d = source.text.toString().toDoubleOrNull() ?: 0.0
            target.setText((d * 7).toInt().toString())
        }
        dailyGoalDrinks.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { recalcWeeklyFromDaily(dailyGoalDrinks, weeklyGoalDrinks) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        dailyBaselineDrinks.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { recalcWeeklyFromDaily(dailyBaselineDrinks, weeklyBaselineDrinks) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val dialog = AlertDialog.Builder(activity)
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
            brewLog.setConsumptionGoal(mlDailyGoal, mlWeeklyGoal, today, today.plusWeeks(4))
            brewLog.setBaseline(startDate = today, endDate = today.plusWeeks(4), totalConsumption = null, dailyAverage = mlDailyBaseline)

            // Keep the journey start date if one already exists; only set on first setup
            val editor = prefs.edit()
                .putFloat("goal_daily_ml", mlDailyGoal.toFloat())
                .putFloat("goal_weekly_ml", mlWeeklyGoal.toFloat())
                .putFloat("baseline_daily_ml", mlDailyBaseline.toFloat())
            if (prefs.getString("baseline_set_date", null) == null) {
                editor.putString("baseline_set_date", today.toString())
            }
            editor.apply()

            Toast.makeText(activity, "Saved", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            onSaved()
        }
        dialog.show()
    }
}
