package com.brewlog.android

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brewlog.android.engine.EducationCard

/**
 * The gamification and education surface: growth stage, health-recovery
 * timeline, badge collection, savings totals and short evidence-based reads.
 */
class JourneyActivity : AppCompatActivity() {

    private val gamification by lazy { GamificationManager(this) }
    private lateinit var timelineAdapter: TimelineAdapter
    private lateinit var badgeAdapter: BadgeAdapter
    private lateinit var educationAdapter: EducationAdapter

    private val stageDrawables = intArrayOf(
        R.drawable.ic_growth_stage_0,
        R.drawable.ic_growth_stage_1,
        R.drawable.ic_growth_stage_2,
        R.drawable.ic_growth_stage_3,
        R.drawable.ic_growth_stage_4
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_journey)

        SecureWindow.apply(this)

        setSupportActionBar(findViewById(R.id.toolbar))

        timelineAdapter = TimelineAdapter(emptyList())
        findViewById<RecyclerView>(R.id.rv_timeline).apply {
            layoutManager = LinearLayoutManager(this@JourneyActivity)
            adapter = timelineAdapter
        }

        badgeAdapter = BadgeAdapter(emptyList())
        findViewById<RecyclerView>(R.id.rv_badges).apply {
            layoutManager = GridLayoutManager(this@JourneyActivity, 3)
            adapter = badgeAdapter
        }

        educationAdapter = EducationAdapter(emptyList()) { showEducationDetail(it) }
        findViewById<RecyclerView>(R.id.rv_education).apply {
            layoutManager = LinearLayoutManager(this@JourneyActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = educationAdapter
        }

        BottomNavHelper.wire(this, findViewById(R.id.bottom_nav), R.id.nav_journey)
    }

    override fun onResume() {
        super.onResume()
        SecureWindow.apply(this)
        bindState()
    }

    private fun bindState() {
        val state = gamification.journeyState()

        findViewById<ImageView>(R.id.iv_growth_stage)
            .setImageResource(stageDrawables[state.growthStage.coerceIn(0, stageDrawables.size - 1)])
        findViewById<TextView>(R.id.tv_total_af_days).text = state.totalAfDays.toString()
        findViewById<TextView>(R.id.tv_current_streak).text = state.displayStreak.toString()
        findViewById<TextView>(R.id.tv_best_streak).text = state.bestStreak.toString()
        findViewById<TextView>(R.id.tv_shields).text = "🛡 ${state.shieldsHeld}"

        timelineAdapter.update(state.timeline)
        badgeAdapter.update(state.badges)
        educationAdapter.update(state.educationCards)

        findViewById<TextView>(R.id.tv_money_row).text = when {
            state.moneyAvailable && state.moneySpent > 0 ->
                getString(R.string.money_row_with_spent, Money.format(state.moneySaved), Money.format(state.moneySpent))
            state.moneyAvailable -> getString(R.string.money_row, Money.format(state.moneySaved))
            else -> getString(R.string.money_row_unset)
        }
        findViewById<TextView>(R.id.tv_calories_row).text =
            if (state.burgersEquivalent > 0) {
                getString(R.string.calories_row_with_burgers, state.caloriesSaved.toInt(), state.burgersEquivalent)
            } else {
                getString(R.string.calories_row, state.caloriesSaved.toInt())
            }
    }

    private fun showEducationDetail(card: EducationCard) {
        val view = layoutInflater.inflate(R.layout.dialog_education_detail, null)
        view.findViewById<TextView>(R.id.tv_edu_detail_title).text = card.title
        view.findViewById<TextView>(R.id.tv_edu_detail_body).text = card.body
        view.findViewById<TextView>(R.id.tv_edu_detail_source).text = card.source
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        view.findViewById<android.view.View>(R.id.btn_edu_close).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
