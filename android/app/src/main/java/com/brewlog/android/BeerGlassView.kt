package com.brewlog.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class BeerGlassView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

	private var progressRatio: Float = 0f // 0.0 to 1.0

	// Celebration animation state
	private var isCelebrating: Boolean = false
	private var celebrationStartMs: Long = 0L
	private val celebrationDurationMs: Long = 1000L
	private val bubbles: MutableList<Bubble> = mutableListOf()
	private val sparkles: MutableList<Sparkle> = mutableListOf()

	// Overflow animation when ratio > 1.0
	private var isOverflowing: Boolean = false
	private var overflowStartMs: Long = 0L
	private val overflowDurationMs: Long = 1200L
	private val droplets: MutableList<Droplet> = mutableListOf()

	private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = resources.displayMetrics.density * 2f
		color = ContextCompat.getColor(context, R.color.text_secondary)
	}

	private val beerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = ContextCompat.getColor(context, R.color.beer_amber)
	}

	private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = 0x33FFFFFF
	}

	private val foamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = ContextCompat.getColor(context, R.color.white)
	}

	private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = 0x66FFFFFF
	}

	private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = resources.displayMetrics.density * 1.4f
		color = 0xBBFFFFFF.toInt()
	}

	fun setProgress(ratio: Double) {
		val unclamped = ratio
		val clamped = when {
			unclamped.isNaN() || unclamped.isInfinite() -> 0.0
			unclamped < 0 -> 0.0
			unclamped > 1.0 -> 1.0
			else -> unclamped
		}
		progressRatio = clamped.toFloat()
		if (unclamped > 1.0 && !isOverflowing) {
			isOverflowing = true
			overflowStartMs = System.currentTimeMillis()
			droplets.clear()
			postInvalidateOnAnimation()
		}
		invalidate()
	}

	fun celebrate() {
		isCelebrating = true
		celebrationStartMs = System.currentTimeMillis()
		bubbles.clear()
		sparkles.clear()
		repeat(8) { bubbles.add(Bubble.random(progressRatio)) }
		repeat(6) { sparkles.add(Sparkle.random()) }
		postInvalidateOnAnimation()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)

		val w = width.toFloat()
		val h = height.toFloat()

		// Define a simple straight-sided glass silhouette
		val padding = w * 0.1f
		val topWidth = w - padding * 2f
		val bottomWidth = topWidth
		val glassHeight = h * 0.9f
		val glassTop = h * 0.05f
		val glassBottom = glassTop + glassHeight

		val leftTop = padding
		val rightTop = padding + topWidth
		val leftBottom = (w - bottomWidth) / 2f
		val rightBottom = leftBottom + bottomWidth

		val glassPath = Path().apply {
			moveTo(leftTop, glassTop)
			lineTo(leftBottom, glassBottom)
			lineTo(rightBottom, glassBottom)
			lineTo(rightTop, glassTop)
			close()
		}

		// Draw beer fill based on progress
		if (progressRatio > 0f) {
			val fillHeight = glassHeight * progressRatio
			val fillTop = glassBottom - fillHeight

			// Interpolate width between bottom and top at the fill line
			val t = (fillTop - glassTop) / (glassBottom - glassTop)
			val widthAtFill = bottomWidth + (topWidth - bottomWidth) * t
			val leftAtFill = (w - widthAtFill) / 2f
			val rightAtFill = leftAtFill + widthAtFill

			val beerPath = Path().apply {
				moveTo(leftBottom, glassBottom)
				lineTo(rightBottom, glassBottom)
				lineTo(rightAtFill, fillTop)
				lineTo(leftAtFill, fillTop)
				close()
			}
			// Gradient fill: golden top, amber middle, darker bottom
			beerPaint.shader = LinearGradient(
				0f, fillTop, 0f, glassBottom,
				intArrayOf(
					ContextCompat.getColor(context, R.color.beer_gold),
					ContextCompat.getColor(context, R.color.beer_amber),
					ContextCompat.getColor(context, R.color.beer_brown)
				),
				floatArrayOf(0f, 0.6f, 1f),
				Shader.TileMode.CLAMP
			)
			canvas.drawPath(beerPath, beerPaint)

			// Glass highlight
			val highlightWidth = (rightAtFill - leftAtFill) * 0.12f
			val highlightLeft = leftAtFill + highlightWidth * 0.6f
			val highlightPath = Path().apply {
				moveTo(highlightLeft, fillTop + (glassBottom - fillTop) * 0.05f)
				lineTo(highlightLeft + highlightWidth, fillTop + (glassBottom - fillTop) * 0.12f)
				lineTo(highlightLeft + highlightWidth, glassBottom - (glassBottom - fillTop) * 0.2f)
				lineTo(highlightLeft, glassBottom - (glassBottom - fillTop) * 0.15f)
				close()
			}
			canvas.drawPath(highlightPath, highlightPaint)

			// Foam strip at the top of the beer + rounded blobs
			val foamHeight = (resources.displayMetrics.density * 3).coerceAtMost(h * 0.03f)
			val foamRectTop = (fillTop - foamHeight).coerceAtLeast(glassTop)
			canvas.drawRect(leftAtFill, foamRectTop, rightAtFill, fillTop, foamPaint)
			// rounded blobs
			val blobCount = 6
			val span = rightAtFill - leftAtFill
			for (i in 0 until blobCount) {
				val cx = leftAtFill + span * (i + 0.5f) / blobCount
				val r = span * 0.04f
				canvas.drawCircle(cx, foamRectTop, r, foamPaint)
			}

			// Subtle foam shading via radial gradient
			val foamShade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
				style = Paint.Style.FILL
				shader = RadialGradient(
					(leftAtFill + rightAtFill) / 2f,
					foamRectTop,
					(rightAtFill - leftAtFill) / 2f,
					0x55FFFFFF.toInt(),
					0x00FFFFFF,
					Shader.TileMode.CLAMP
				)
			}
			canvas.drawRect(leftAtFill, foamRectTop - foamHeight, rightAtFill, foamRectTop, foamShade)

			// Celebration bubbles & sparkles
			if (isCelebrating) {
				updateAndDrawBubbles(canvas, leftAtFill, rightAtFill, fillTop, glassBottom)
				drawSparkles(canvas, leftAtFill, rightAtFill, fillTop)
			}

			// Overflow animation above rim
			if (isOverflowing) {
				val elapsed = System.currentTimeMillis() - overflowStartMs
				if (elapsed < overflowDurationMs) {
					val over = (elapsed.toFloat() / overflowDurationMs).coerceIn(0f, 1f)
					val crestHeight = foamHeight * (0.6f + 0.6f * (1f - over))
					// foam crest above rim
					canvas.drawRect(leftTop, glassTop - crestHeight, rightTop, glassTop, foamPaint)
					// droplets falling
					spawnDropletsIfNeeded()
					drawDroplets(canvas, leftTop, rightTop, glassTop, glassBottom)
					postInvalidateOnAnimation()
				} else {
					isOverflowing = false
					droplets.clear()
				}
			}
		}

		// Outline on top
		canvas.drawPath(glassPath, outlinePaint)

		// Continue animation frames while celebrating
		if (isCelebrating) {
			val elapsed = System.currentTimeMillis() - celebrationStartMs
			if (elapsed < celebrationDurationMs) {
				postInvalidateOnAnimation()
			} else {
				isCelebrating = false
				bubbles.clear()
				sparkles.clear()
			}
		}
	}

	private fun updateAndDrawBubbles(
		canvas: Canvas,
		left: Float,
		right: Float,
		top: Float,
		bottom: Float
	) {
		if (Math.random() < 0.25 && bubbles.size < 20) {
			bubbles.add(Bubble.random(progressRatio))
		}
		val iterator = bubbles.iterator()
		while (iterator.hasNext()) {
			val b = iterator.next()
			b.update()
			val x = left + (right - left) * b.x
			val y = bottom - (bottom - top) * b.y
			bubblePaint.alpha = (255 * b.alpha).toInt().coerceIn(0, 255)
			canvas.drawCircle(x, y, b.radius * (right - left) * 0.03f, bubblePaint)
			if (b.alpha <= 0f || y <= top) iterator.remove()
		}
	}

	private fun drawSparkles(canvas: Canvas, left: Float, right: Float, top: Float) {
		val iterator = sparkles.iterator()
		while (iterator.hasNext()) {
			val s = iterator.next()
			s.update()
			val x = left + (right - left) * s.x
			val y = top + (resources.displayMetrics.density * 8) + (Math.sin(s.phase.toDouble()).toFloat() * 2f)
			sparklePaint.alpha = (255 * s.alpha).toInt().coerceIn(0, 255)
			val size = (right - left) * 0.05f * s.scale
			// simple 4-point star
			canvas.drawLine(x - size, y, x + size, y, sparklePaint)
			canvas.drawLine(x, y - size, x, y + size, sparklePaint)
			if (s.alpha <= 0f) iterator.remove()
		}
	}

	private fun spawnDropletsIfNeeded() {
		if (Math.random() < 0.2 && droplets.size < 10) {
			droplets.add(Droplet.random())
		}
	}

	private fun drawDroplets(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float) {
		val it = droplets.iterator()
		while (it.hasNext()) {
			val d = it.next()
			d.update()
			val x = left + (right - left) * d.x
			val y = top - (resources.displayMetrics.density * 6) + d.y
			bubblePaint.alpha = (255 * d.alpha).toInt().coerceIn(0, 255)
			canvas.drawCircle(x, y, (right - left) * 0.02f, bubblePaint)
			if (y > bottom || d.alpha <= 0f) it.remove()
		}
	}

	private data class Bubble(
		var x: Float,
		var y: Float,
		var radius: Float,
		var vy: Float,
		var alpha: Float
	) {
		fun update() {
			y += vy
			alpha -= 0.03f
		}

		companion object {
			fun random(progressRatio: Float): Bubble {
				val r = (0.6f + Math.random().toFloat() * 0.8f)
				return Bubble(
					x = Math.random().toFloat(),
					y = 0.05f + Math.random().toFloat() * progressRatio.coerceAtLeast(0.05f),
					radius = r,
					vy = (0.006f + Math.random().toFloat() * 0.01f),
					alpha = 0.9f
				)
			}
		}
	}

	private data class Sparkle(
		var x: Float,
		var alpha: Float,
		var scale: Float,
		var phase: Float
	) {
		fun update() {
			alpha -= 0.04f
			phase += 0.2f
		}

		companion object {
			fun random(): Sparkle = Sparkle(
				x = Math.random().toFloat(),
				alpha = 1f,
				scale = 0.7f + Math.random().toFloat() * 0.6f,
				phase = Math.random().toFloat() * 6.28f
			)
		}
	}

	private data class Droplet(
		var x: Float,
		var y: Float,
		var vy: Float,
		var alpha: Float
	) {
		fun update() {
			y += vy
			alpha -= 0.03f
		}

		companion object {
			fun random(): Droplet = Droplet(
				x = Math.random().toFloat(),
				y = 0f,
				vy = (6f + Math.random().toFloat() * 10f),
				alpha = 1f
			)
		}
	}
}


