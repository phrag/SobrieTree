package com.treetotal.android

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.sin

/** A single gently swaying tree at a given growth progress (Journey header). */
class TreeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 0f
    private var swayPhase = 0f
    private var idleAnimator: ValueAnimator? = null

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        idleAnimator = ValueAnimator.ofFloat(0f, (2.0 * Math.PI).toFloat()).apply {
            duration = 5000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                swayPhase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        idleAnimator?.cancel()
        idleAnimator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val palette = TreePainter.Palette(
            stem = ContextCompat.getColor(context, R.color.growth_stem),
            leaf = ContextCompat.getColor(context, R.color.growth_leaf),
            leafDark = ContextCompat.getColor(context, R.color.growth_leaf_dark),
            soil = ContextCompat.getColor(context, R.color.growth_soil)
        )
        val sway = 1.6f * sin(swayPhase.toDouble()).toFloat() * (0.3f + 0.7f * progress)
        TreePainter.draw(
            canvas,
            cx = width / 2f,
            baseY = height * 0.92f,
            height = height * 0.85f,
            progress = progress,
            palette = palette,
            swayDeg = sway
        )
    }
}
