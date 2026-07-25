package com.treetotal.android

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.ceil
import kotlin.random.Random

/**
 * The collected forest: one small tree per completed 30-AF-day cycle, plus the
 * tree currently growing (drawn last, at its real progress). Trees get a
 * deterministic per-tree size/offset jitter so the forest looks organic
 * rather than stamped.
 */
class ForestView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var treesCollected = 0
    private var currentProgress = 0f

    private val density = resources.displayMetrics.density
    private val cellW get() = 52 * density
    private val cellH get() = 60 * density

    fun setForest(treesCollected: Int, currentProgress: Float) {
        this.treesCollected = treesCollected.coerceAtLeast(0)
        this.currentProgress = currentProgress.coerceIn(0f, 1f)
        requestLayout()
        invalidate()
    }

    private fun columns(width: Int): Int =
        ((width / cellW).toInt()).coerceAtLeast(1)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val totalTrees = treesCollected + 1 // include the growing one
        val rows = ceil(totalTrees / columns(width).toFloat()).toInt().coerceAtLeast(1)
        setMeasuredDimension(width, (rows * cellH).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val palette = TreePainter.Palette(
            stem = ContextCompat.getColor(context, R.color.growth_stem),
            leaf = ContextCompat.getColor(context, R.color.growth_leaf),
            leafDark = ContextCompat.getColor(context, R.color.growth_leaf_dark),
            soil = ContextCompat.getColor(context, R.color.growth_soil)
        )
        val cols = columns(width)
        val totalTrees = treesCollected + 1
        for (i in 0 until totalTrees) {
            val col = i % cols
            val row = i / cols
            // Deterministic organic jitter per tree
            val rnd = Random(i * 7919 + 31)
            val jx = (rnd.nextFloat() - 0.5f) * 8 * density
            val jh = 0.88f + rnd.nextFloat() * 0.24f

            // Weekly starter trees stay small; month trees stand tall
            val sizeFactor = if (com.treetotal.android.engine.StreakEngine.isBigTree(i)) 1.0f else 0.62f

            val cx = (col + 0.5f) * cellW + jx
            val baseY = (row + 1) * cellH - 6 * density
            val h = cellH * 0.82f * jh * sizeFactor
            val progress = if (i < treesCollected) 1f else currentProgress
            TreePainter.draw(canvas, cx, baseY, h, progress, palette)
        }
    }
}
