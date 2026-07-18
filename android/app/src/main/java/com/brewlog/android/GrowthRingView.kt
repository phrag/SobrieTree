package com.brewlog.android

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat

/**
 * The app's signature visual, replacing the old beer glass.
 *
 * An arc ring shows today's consumption against the daily goal: it sweeps
 * clockwise as drinks are logged and shifts from calm green toward soft amber
 * near the goal - never red. On an alcohol-free day the ring renders full and
 * gently pulsing: AF is depicted as the *fullest* state.
 *
 * The centre shows a plant that grows with total (cumulative) alcohol-free
 * days, so it never regresses after a lapse.
 */
class GrowthRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcBounds = RectF()

    private var animatedRatio = 0f
    private var targetRatio = 0f
    private var isAfToday = false
    private var overGoal = false
    private var growthStage = 0
    private var pulseAlpha = 255

    private var sweepAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    private val stageDrawables = intArrayOf(
        R.drawable.ic_growth_stage_0,
        R.drawable.ic_growth_stage_1,
        R.drawable.ic_growth_stage_2,
        R.drawable.ic_growth_stage_3,
        R.drawable.ic_growth_stage_4
    )

    fun setState(consumedRatio: Float, isAfToday: Boolean, growthStage: Int, overGoal: Boolean) {
        this.isAfToday = isAfToday
        this.overGoal = overGoal
        this.growthStage = growthStage.coerceIn(0, stageDrawables.size - 1)

        val newTarget = when {
            isAfToday -> 1f
            else -> consumedRatio.coerceIn(0f, 1f)
        }
        if (newTarget != targetRatio) {
            targetRatio = newTarget
            sweepAnimator?.cancel()
            sweepAnimator = ValueAnimator.ofFloat(animatedRatio, targetRatio).apply {
                duration = 350
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    animatedRatio = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        if (isAfToday) startPulse() else stopPulse()
        invalidate()
    }

    private fun startPulse() {
        if (pulseAnimator != null) return
        pulseAnimator = ValueAnimator.ofInt(160, 255).apply {
            duration = 1600
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                pulseAlpha = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        pulseAlpha = 255
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sweepAnimator?.cancel()
        stopPulse()
    }

    private fun color(res: Int): Int = ContextCompat.getColor(context, res)

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        val clamped = t.coerceIn(0f, 1f)
        val a = (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * clamped).toInt()
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * clamped).toInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * clamped).toInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * clamped).toInt()
        return Color.argb(a, r, g, b)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        val stroke = size * 0.09f
        trackPaint.strokeWidth = stroke
        progressPaint.strokeWidth = stroke
        glowPaint.strokeWidth = stroke * 1.8f

        val pad = stroke * 1.2f
        arcBounds.set(
            (width - size) / 2f + pad,
            (height - size) / 2f + pad,
            (width + size) / 2f - pad,
            (height + size) / 2f - pad
        )

        trackPaint.color = color(R.color.ring_track)
        canvas.drawArc(arcBounds, 0f, 360f, false, trackPaint)

        when {
            isAfToday -> {
                // Fullest state: complete ring, softly pulsing green with a glow.
                glowPaint.color = color(R.color.ring_af_glow)
                glowPaint.alpha = (pulseAlpha * 0.25f).toInt()
                canvas.drawArc(arcBounds, 0f, 360f, false, glowPaint)
                progressPaint.color = color(R.color.state_positive)
                progressPaint.alpha = pulseAlpha
                canvas.drawArc(arcBounds, -90f, 360f, false, progressPaint)
            }
            overGoal -> {
                // Muted amber full ring plus a small overflow tick - calm, not alarming.
                progressPaint.color = color(R.color.state_caution)
                progressPaint.alpha = 190
                canvas.drawArc(arcBounds, -90f, 360f, false, progressPaint)
                progressPaint.alpha = 255
                canvas.drawArc(arcBounds, -98f, 6f, false, progressPaint)
            }
            else -> {
                // Green sweeping toward soft amber from 70% of the goal onward.
                val t = ((animatedRatio - 0.7f) / 0.3f).coerceIn(0f, 1f)
                progressPaint.color = lerpColor(color(R.color.ring_progress_start), color(R.color.ring_progress_end), t)
                progressPaint.alpha = 255
                if (animatedRatio > 0f) {
                    canvas.drawArc(arcBounds, -90f, animatedRatio * 360f, false, progressPaint)
                }
            }
        }

        // Centre plant, keyed to cumulative AF days.
        val drawable = AppCompatResources.getDrawable(context, stageDrawables[growthStage]) ?: return
        val inner = size - 2 * (pad + stroke)
        val plantSize = (inner * 0.62f).toInt()
        val cx = width / 2
        val cy = height / 2
        drawable.setBounds(cx - plantSize / 2, cy - plantSize / 2, cx + plantSize / 2, cy + plantSize / 2)
        drawable.draw(canvas)
    }
}
