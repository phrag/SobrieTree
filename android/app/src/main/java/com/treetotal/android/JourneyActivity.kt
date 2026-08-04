package com.treetotal.android

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.treetotal.android.engine.EducationCard

/**
 * The gamification and education surface: growth stage, health-recovery
 * timeline, badge collection, savings totals and short evidence-based reads.
 */
class JourneyActivity : AppCompatActivity() {

    private val gamification by lazy { GamificationManager(this) }
    private lateinit var timelineAdapter: TimelineAdapter
    private lateinit var badgeAdapter: BadgeAdapter
    private lateinit var educationAdapter: EducationAdapter

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

        findViewById<TreeView>(R.id.tree_view).setProgress(state.treeProgress)
        findViewById<TextView>(R.id.tv_total_af_days).text = state.totalAfDays.toString()

        findViewById<TextView>(R.id.tv_forest_caption).text =
            if (state.treesCollected == 0) {
                getString(R.string.forest_caption_first, state.treeDaysGrown, state.treeDaysNeeded)
            } else {
                getString(R.string.forest_caption_growing, state.treesCollected, state.treeDaysGrown, state.treeDaysNeeded)
            }
        findViewById<ForestView>(R.id.forest_view).setForest(state.treesCollected, state.treeProgress)
        findViewById<TextView>(R.id.tv_current_streak).text = state.displayStreak.toString()
        findViewById<TextView>(R.id.tv_best_streak).text = state.bestStreak.toString()
        findViewById<TextView>(R.id.tv_shields).text = "🛡 ${state.shieldsHeld}"

        timelineAdapter.update(state.timeline)
        badgeAdapter.update(state.badges)
        educationAdapter.update(state.educationCards)

        // Headline is the one number that matters; the line under it shows the two
        // figures it came from, so "0 saved" is never a mystery.
        val moneyHeadline = findViewById<TextView>(R.id.tv_money_row)
        val moneyDetail = findViewById<TextView>(R.id.tv_money_detail)
        if (state.moneyAvailable) {
            moneyHeadline.text = getString(R.string.money_row, Money.format(state.moneySaved))
            moneyDetail.text = if (state.moneyExpected > 0) {
                getString(
                    R.string.money_row_detail,
                    Money.format(state.moneyExpected),
                    Money.format(state.moneySpent)
                )
            } else {
                getString(R.string.money_row_pending)
            }
        } else {
            moneyHeadline.setText(R.string.money_row_unset)
            moneyDetail.setText(R.string.money_row_unset_detail)
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
