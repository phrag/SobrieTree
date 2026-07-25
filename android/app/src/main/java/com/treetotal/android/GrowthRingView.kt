package com.treetotal.android

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.sin

/**
 * The app's signature visual.
 *
 * An arc ring shows today's consumption against the daily goal: it sweeps
 * clockwise as drinks are logged and shifts from calm green toward soft amber
 * near the goal - never red. On an alcohol-free day the ring renders full and
 * gently pulsing: AF is depicted as the *fullest* state.
 *
 * The centre grows a tree, one alcohol-free day at a time, completing in 30 AF
 * days (see StreakEngine.TREE_DAYS) - finished trees join the Journey forest
 * and a fresh seed starts. Growth is keyed to *cumulative* AF days, so a lapse
 * pauses the tree but never shrinks it. The plant idles with a gentle sway and
 * pops softly when it grows.
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

    private var animatedTreeProgress = 0f
    private var targetTreeProgress = -1f
    private var treePop = 1f
    private var swayPhase = 0f
    private var pulseAlpha = 255

    private var sweepAnimator: ValueAnimator? = null
    private var treeAnimator: ValueAnimator? = null
    private var idleAnimator: ValueAnimator? = null

    fun setState(consumedRatio: Float, isAfToday: Boolean, treeProgress: Float, overGoal: Boolean) {
        this.isAfToday = isAfToday
        this.overGoal = overGoal

        val newTarget = if (isAfToday) 1f else consumedRatio.coerceIn(0f, 1f)
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

        val newTree = treeProgress.coerceIn(0f, 1f)
        if (newTree != targetTreeProgress) {
            val first = targetTreeProgress < 0f
            val from = if (first) newTree else animatedTreeProgress
            targetTreeProgress = newTree
            treeAnimator?.cancel()
            if (first) {
                animatedTreeProgress = newTree
            } else {
                // Grow (or reset to a fresh seed) with a soft pop
                treeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 700
                    interpolator = DecelerateInterpolator()
                    val start = from
                    addUpdateListener { a ->
                        val t = a.animatedValue as Float
                        animatedTreeProgress = start + (newTree - start) * t
                        treePop = 1f + 0.10f * sin(t * Math.PI).toFloat()
                        invalidate()
                    }
                    start()
                }
            }
        }

        startIdle()
        invalidate()
    }

    /** One animator drives both the sway and the AF pulse. */
    private fun startIdle() {
        if (idleAnimator != null) return
        idleAnimator = ValueAnimator.ofFloat(0f, (2.0 * Math.PI).toFloat()).apply {
            duration = 5000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                swayPhase = it.animatedValue as Float
                pulseAlpha = (207 + 48 * sin(swayPhase.toDouble())).toInt()
                invalidate()
            }
            start()
        }
    }

    private fun stopIdle() {
        idleAnimator?.cancel()
        idleAnimator = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sweepAnimator?.cancel()
        treeAnimator?.cancel()
        stopIdle()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) startIdle() else stopIdle()
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

        // Centre tree, growing one AF day at a time
        val inner = size - 2 * (pad + stroke)
        val plantHeight = inner * 0.60f * treePop
        val cx = width / 2f
        val baseY = height / 2f + inner * 0.32f
        val sway = 1.6f * sin(swayPhase.toDouble()).toFloat() * (0.3f + 0.7f * animatedTreeProgress)
        TreePainter.draw(
            canvas, cx, baseY, plantHeight, animatedTreeProgress,
            TreePainter.Palette(
                stem = color(R.color.growth_stem),
                leaf = color(R.color.growth_leaf),
                leafDark = color(R.color.growth_leaf_dark),
                soil = color(R.color.growth_soil)
            ),
            swayDeg = sway
        )
    }
}
