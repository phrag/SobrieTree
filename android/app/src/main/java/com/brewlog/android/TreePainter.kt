package com.brewlog.android

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.sin

/**
 * Draws a tree procedurally at any growth stage, shared by the home ring,
 * the Journey header and the forest collection. Growth is continuous, so
 * every alcohol-free day visibly changes the plant:
 *
 *  0.00-0.12  seed in the soil, first shoot breaking through
 *  0.12-0.50  stem climbs, leaf pairs unfurl one by one
 *  0.50-1.00  trunk thickens and a layered canopy fills out
 *
 * All geometry is relative to a given height, so the same code renders the
 * 140dp hero plant and 40dp forest trees.
 */
object TreePainter {

    data class Palette(
        val stem: Int,
        val leaf: Int,
        val leafDark: Int,
        val soil: Int
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val oval = RectF()

    /**
     * @param cx horizontal centre of the plant
     * @param baseY ground line (soil sits on it)
     * @param height maximum plant height at full growth
     * @param progress growth 0..1
     * @param swayDeg gentle idle sway in degrees, rotated around the base
     */
    fun draw(
        canvas: Canvas,
        cx: Float,
        baseY: Float,
        height: Float,
        progress: Float,
        palette: Palette,
        swayDeg: Float = 0f
    ) {
        val p = progress.coerceIn(0f, 1f)

        // Soil mound (doesn't sway)
        paint.style = Paint.Style.FILL
        paint.color = palette.soil
        oval.set(cx - height * 0.22f, baseY - height * 0.055f, cx + height * 0.22f, baseY + height * 0.055f)
        canvas.drawOval(oval, paint)

        canvas.save()
        canvas.rotate(swayDeg, cx, baseY)

        if (p < 0.12f) {
            drawSeedling(canvas, cx, baseY, height, p / 0.12f, palette)
        } else {
            drawGrowingTree(canvas, cx, baseY, height, p, palette)
        }

        canvas.restore()
    }

    private fun drawSeedling(canvas: Canvas, cx: Float, baseY: Float, height: Float, t: Float, palette: Palette) {
        // Seed peeking out of the soil
        paint.color = palette.stem
        oval.set(cx - height * 0.06f, baseY - height * 0.075f, cx + height * 0.06f, baseY + height * 0.02f)
        canvas.drawOval(oval, paint)

        // First shoot rises with t
        if (t > 0.2f) {
            val shootH = height * 0.16f * ((t - 0.2f) / 0.8f)
            paint.color = palette.leaf
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = height * 0.018f
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(cx, baseY - height * 0.05f, cx, baseY - height * 0.05f - shootH, paint)
            paint.style = Paint.Style.FILL
            // A single tiny leaf at the tip
            drawLeaf(canvas, cx, baseY - height * 0.05f - shootH, height * 0.05f * t, -50f, palette.leaf)
        }
    }

    private fun drawGrowingTree(canvas: Canvas, cx: Float, baseY: Float, height: Float, p: Float, palette: Palette) {
        // Stem: from a sprout to a full trunk with a gentle curve
        val growth = (p - 0.12f) / 0.88f          // 0..1 across the growing phase
        val stemH = height * (0.18f + 0.72f * growth)
        val stemW = height * (0.02f + 0.05f * growth)
        val topX = cx + height * 0.03f * sin(growth * 3f)   // slight organic lean
        val topY = baseY - stemH

        paint.style = Paint.Style.FILL
        paint.color = palette.stem
        path.reset()
        path.moveTo(cx - stemW, baseY)
        path.quadTo(cx - stemW * 0.6f, baseY - stemH * 0.55f, topX - stemW * 0.35f, topY)
        path.lineTo(topX + stemW * 0.35f, topY)
        path.quadTo(cx + stemW * 0.6f, baseY - stemH * 0.55f, cx + stemW, baseY)
        path.close()
        canvas.drawPath(path, paint)

        // Leaf pairs unfurl along the stem while it's young, thinning as the canopy takes over
        val canopyT = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
        val leafPairs = ((growth * 6f).toInt()).coerceIn(1, 4)
        if (canopyT < 0.85f) {
            val leafSize = height * 0.085f * (1f - canopyT * 0.7f)
            for (i in 1..leafPairs) {
                val f = i / (leafPairs + 1f)
                val ly = baseY - stemH * f
                val lx = cx + (topX - cx) * f
                drawLeaf(canvas, lx, ly, leafSize, -55f, palette.leaf)
                drawLeaf(canvas, lx, ly, leafSize, 55f + 180f, palette.leafDark)
            }
        }

        // Canopy: layered circles that scale in from 50% growth onward
        if (canopyT > 0f) {
            val r = height * 0.30f * (0.35f + 0.65f * canopyT)
            val cyTop = topY - r * 0.25f
            // Dark under-layer for depth
            paint.color = palette.leafDark
            canvas.drawCircle(topX - r * 0.55f, cyTop + r * 0.35f, r * 0.72f, paint)
            canvas.drawCircle(topX + r * 0.55f, cyTop + r * 0.35f, r * 0.72f, paint)
            // Bright main crown
            paint.color = palette.leaf
            canvas.drawCircle(topX, cyTop - r * 0.15f, r * 0.85f, paint)
            canvas.drawCircle(topX - r * 0.45f, cyTop + r * 0.22f, r * 0.62f, paint)
            canvas.drawCircle(topX + r * 0.45f, cyTop + r * 0.22f, r * 0.62f, paint)
        }
    }

    private fun drawLeaf(canvas: Canvas, x: Float, y: Float, size: Float, angleDeg: Float, color: Int) {
        if (size <= 0f) return
        canvas.save()
        canvas.rotate(angleDeg, x, y)
        paint.color = color
        oval.set(x, y - size * 0.35f, x + size * 1.6f, y + size * 0.35f)
        canvas.drawOval(oval, paint)
        canvas.restore()
    }
}
