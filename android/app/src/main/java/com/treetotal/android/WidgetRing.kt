package com.treetotal.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.ContextCompat

/**
 * Draws the home ring into a bitmap for the widget. RemoteViews can't host a
 * custom View, so the same idea as [GrowthRingView] is rendered off-screen and
 * handed over as an image.
 *
 * The ring reads the same way as on Home: it starts full and empties as drinks
 * are logged, so a full circle means an untouched day.
 */
object WidgetRing {

    private const val SIZE_PX = 160
    private const val STROKE_PX = 16f

    /**
     * @param remaining 0..1 of the day's allowance still untouched
     * @param isAf true when nothing has been logged today
     */
    fun render(context: Context, remaining: Float, isAf: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val inset = STROKE_PX / 2f + 2f
        val bounds = RectF(inset, inset, SIZE_PX - inset, SIZE_PX - inset)

        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = STROKE_PX
            strokeCap = Paint.Cap.ROUND
            color = ContextCompat.getColor(context, R.color.ring_track)
        }
        canvas.drawArc(bounds, 0f, 360f, false, track)

        val sweep = 360f * remaining.coerceIn(0f, 1f)
        if (sweep > 0f) {
            val progress = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = STROKE_PX
                strokeCap = Paint.Cap.ROUND
                color = ContextCompat.getColor(
                    context,
                    if (isAf) R.color.ring_af_glow else R.color.ring_progress_start
                )
            }
            // Start at the top and travel clockwise, as on Home.
            canvas.drawArc(bounds, -90f, sweep, false, progress)
        } else {
            // Nothing left: a single amber tick, so the ring never reads as "broken".
            val over = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = STROKE_PX
                strokeCap = Paint.Cap.ROUND
                color = ContextCompat.getColor(context, R.color.ring_progress_end)
            }
            canvas.drawArc(bounds, -90f, 6f, false, over)
        }
        return bitmap
    }
}
