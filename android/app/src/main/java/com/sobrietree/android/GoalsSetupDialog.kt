package com.sobrietree.android

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
 * SobrieTree; [onSaved] fires after a successful save (callers handle any
 * onboarding chaining themselves).
 */
object GoalsSetupDialog {

    fun show(activity: Activity, onSaved: () -> Unit) {
        val sobrieTree = SobrieTreeProvider.instance
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
        val currentDailyMl = sobrieTree.getDailyGoal()
        val currentWeeklyMl = sobrieTree.getWeeklyGoal()
        val vol = if (defaultSizeMl > 0) defaultSizeMl.toDouble() else 500.0
        // Low-risk guideline anchor: ~12 g pure alcohol/day (lower) to ~24 g (upper)
        val assumedAbv = prefs.getFloat("default_beer_strength", 5.0f).toDouble().coerceAtLeast(1.0)
        val gramsPerDrink = vol * (assumedAbv / 100.0) * 0.8
        val guidelineDailyDrinks = if (gramsPerDrink > 0) (24.0 / gramsPerDrink) else 2.0
        val defaultDailyDrinks = guidelineDailyDrinks.coerceIn(1.0, 5.0)
        val defaultWeeklyDrinks = (defaultDailyDrinks * 7).toInt()
        // Drinks may be fractional once a weekly-only goal is spread across 7 days;
        // show whole numbers cleanly and one decimal otherwise.
        fun fmtDrinks(d: Double): String =
            if (d == Math.floor(d)) d.toInt().toString() else String.format("%.1f", d)

        dailyGoalDrinks.setText(
            if (currentDailyMl > 0) fmtDrinks(currentDailyMl / vol) else defaultDailyDrinks.toInt().toString()
        )
        weeklyGoalDrinks.setText(
            if (currentWeeklyMl > 0) fmtDrinks(currentWeeklyMl / vol) else defaultWeeklyDrinks.toString()
        )

        // Prefill baseline in drinks (blank when unset, so the field can stay empty)
        val baseline = sobrieTree.getCurrentBaseline()
        dailyBaselineDrinks.setText(if ((baseline?.averageDailyConsumption ?: 0.0) > 0) fmtDrinks((baseline!!.averageDailyConsumption) / vol) else "")
        weeklyBaselineDrinks.setText(if ((baseline?.averageWeeklyConsumption ?: 0.0) > 0) fmtDrinks((baseline!!.averageWeeklyConsumption) / vol) else "")

        // Guideline note
        val dailyFemale = (12.0 / gramsPerDrink).coerceAtLeast(0.0).toInt()
        val dailyMale = (24.0 / gramsPerDrink).coerceAtLeast(0.0).toInt()
        dialogView.findViewById<TextView>(R.id.tv_guideline_note)?.text =
            "💡 Low-risk guidelines: ${dailyFemale}-${dailyMale} drinks/day (${vol.toInt()}ml @ ${assumedAbv}%). " +
            "Aim for 2+ alcohol-free days each week - they grow the plant in your ring."

        // A daily amount auto-fills its weekly partner (×7), but only while the
        // user hasn't typed a weekly value of their own. This lets people enter a
        // weekly-only goal without a daily figure being forced back over it.
        var goalWeeklyTouched = false
        var baselineWeeklyTouched = false

        fun recalcWeeklyFromDaily(source: TextInputEditText, target: TextInputEditText, weeklyTouched: () -> Boolean) {
            if (weeklyTouched()) return
            val d = source.text.toString().toDoubleOrNull() ?: return
            target.setText(fmtDrinks(d * 7))
        }
        fun onDailyChanged(source: TextInputEditText, target: TextInputEditText, weeklyTouched: () -> Boolean) =
            object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { recalcWeeklyFromDaily(source, target, weeklyTouched) }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }
        // The weekly field marks itself "touched" only when the user is the one
        // editing it (has focus); programmatic ×7 fills happen while it is unfocused.
        fun markTouchedWatcher(field: TextInputEditText, setTouched: () -> Unit) =
            object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { if (field.hasFocus()) setTouched() }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }

        weeklyGoalDrinks.addTextChangedListener(markTouchedWatcher(weeklyGoalDrinks) { goalWeeklyTouched = true })
        weeklyBaselineDrinks.addTextChangedListener(markTouchedWatcher(weeklyBaselineDrinks) { baselineWeeklyTouched = true })
        dailyGoalDrinks.addTextChangedListener(onDailyChanged(dailyGoalDrinks, weeklyGoalDrinks) { goalWeeklyTouched })
        dailyBaselineDrinks.addTextChangedListener(onDailyChanged(dailyBaselineDrinks, weeklyBaselineDrinks) { baselineWeeklyTouched })

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btn_save).setOnClickListener {
            val sizeInput = defaultDrinkEdit.text.toString().toIntOrNull() ?: 0
            // Daily is optional; a weekly-only entry derives its daily as weekly / 7.
            val dailyGoalIn = dailyGoalDrinks.text.toString().toDoubleOrNull()?.takeIf { it > 0 }
            val weeklyGoalIn = weeklyGoalDrinks.text.toString().toDoubleOrNull()?.takeIf { it > 0 }
            val dailyBaseIn = dailyBaselineDrinks.text.toString().toDoubleOrNull()?.takeIf { it > 0 }
            val weeklyBaseIn = weeklyBaselineDrinks.text.toString().toDoubleOrNull()?.takeIf { it > 0 }

            listOf(layoutDailyGoal, layoutWeeklyGoal, layoutDailyBaseline, layoutWeeklyBaseline).forEach { it.error = null }

            var valid = true
            if (dailyGoalIn == null && weeklyGoalIn == null) {
                layoutWeeklyGoal.error = "Enter a daily or weekly goal"; valid = false
            }
            if (dailyBaseIn == null && weeklyBaseIn == null) {
                layoutWeeklyBaseline.error = "Enter a daily or weekly baseline"; valid = false
            }
            if (!valid) return@setOnClickListener

            // Fill in whichever side of each pair was left blank.
            val weeklyGoal = weeklyGoalIn ?: (dailyGoalIn!! * 7.0)
            val dailyGoal = dailyGoalIn ?: (weeklyGoal / 7.0)
            val weeklyBase = weeklyBaseIn ?: (dailyBaseIn!! * 7.0)
            val dailyBase = dailyBaseIn ?: (weeklyBase / 7.0)

            val effectiveSize = if (sizeInput > 0) sizeInput else defaultSizeMl
            if (effectiveSize > 0) {
                prefs.edit().putInt("default_beer_size", effectiveSize).apply()
            }

            val mlDailyGoal = dailyGoal * effectiveSize
            val mlWeeklyGoal = weeklyGoal * effectiveSize
            val mlDailyBaseline = dailyBase * effectiveSize

            val today = LocalDate.now()
            sobrieTree.setConsumptionGoal(mlDailyGoal, mlWeeklyGoal, today, today.plusWeeks(4))
            sobrieTree.setBaseline(startDate = today, endDate = today.plusWeeks(4), totalConsumption = null, dailyAverage = mlDailyBaseline)

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
