package com.brewlog.android

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat

class BeerGlassView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

	private var progressRatio: Float = 0f
	private var displayRatio: Float = 0f
	private var progressAnimator: ValueAnimator? = null

	private var isCelebrating: Boolean = false
	private var celebrationStartMs: Long = 0L
	private val celebrationDurationMs: Long = 1200L
	private val bubbles: MutableList<Bubble> = mutableListOf()
	private val sparkles: MutableList<Sparkle> = mutableListOf()

	private var isOverflowing: Boolean = false
	private var overflowStartMs: Long = 0L
	private val overflowDurationMs: Long = 1200L
	private val droplets: MutableList<Droplet> = mutableListOf()

	// Wave animation
	private var wavePhase: Float = 0f
	private val waveSpeed: Float = 0.06f

	private val dp = resources.displayMetrics.density

	// Beer amber/gold colors
	private val beerAmberLight = 0xFFFFC107.toInt()
	private val beerAmberMid = 0xFFFF9800.toInt()
	private val beerAmberDark = 0xFFF57C00.toInt()

	private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp * 2.5f
		color = ContextCompat.getColor(context, R.color.text_secondary)
	}

	private val beerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
	}

	private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = 0x22FFFFFF
	}

	private val foamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = 0xFFFFF8E1.toInt()
	}

	private val foamShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = 0x11000000
	}

	private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = 0x44FFFFFF
	}

	private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp * 1.4f
		color = 0xAAFFFFFF.toInt()
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

		progressAnimator?.cancel()
		progressAnimator = ValueAnimator.ofFloat(displayRatio, progressRatio).apply {
			duration = 600
			interpolator = DecelerateInterpolator()
			addUpdateListener { animation ->
				displayRatio = animation.animatedValue as Float
				invalidate()
			}
			start()
		}

		if (unclamped > 1.0 && !isOverflowing) {
			isOverflowing = true
			overflowStartMs = System.currentTimeMillis()
			droplets.clear()
			postInvalidateOnAnimation()
		}
	}

	fun celebrate() {
		isCelebrating = true
		celebrationStartMs = System.currentTimeMillis()
		bubbles.clear()
		sparkles.clear()
		repeat(10) { bubbles.add(Bubble.random(displayRatio)) }
		repeat(6) { sparkles.add(Sparkle.random()) }
		postInvalidateOnAnimation()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)

		val w = width.toFloat()
		val h = height.toFloat()
		if (w <= 0 || h <= 0) return

		// Pint glass dimensions: wider at top, narrower at bottom
		val padding = w * 0.12f
		val topWidth = w - padding * 2f
		val bottomWidth = topWidth * 0.72f
		val glassHeight = h * 0.88f
		val glassTop = h * 0.06f
		val glassBottom = glassTop + glassHeight

		val leftTop = (w - topWidth) / 2f
		val rightTop = leftTop + topWidth
		val leftBottom = (w - bottomWidth) / 2f
		val rightBottom = leftBottom + bottomWidth

		val glassPath = Path().apply {
			moveTo(leftTop, glassTop)
			lineTo(leftBottom, glassBottom)
			lineTo(rightBottom, glassBottom)
			lineTo(rightTop, glassTop)
			close()
		}

		if (displayRatio > 0f) {
			val fillHeight = glassHeight * displayRatio
			val fillTop = glassBottom - fillHeight

			// Width at fill line (interpolate between bottom and top)
			val t = 1f - displayRatio
			val widthAtFill = bottomWidth + (topWidth - bottomWidth) * (1f - t)
			val leftAtFill = (w - widthAtFill) / 2f
			val rightAtFill = leftAtFill + widthAtFill

			// Wave effect on the liquid surface
			wavePhase += waveSpeed
			val waveAmplitude = dp * 2f * displayRatio.coerceAtMost(0.5f) * 2f

			val beerPath = Path().apply {
				moveTo(leftBottom, glassBottom)
				lineTo(rightBottom, glassBottom)
				lineTo(rightAtFill, fillTop)

				// Wavy top edge
				val steps = 20
				for (i in steps downTo 0) {
					val frac = i.toFloat() / steps
					val x = rightAtFill - (rightAtFill - leftAtFill) * (1f - frac)
					val waveY = fillTop + Math.sin((frac * 3 * Math.PI + wavePhase).toDouble()).toFloat() * waveAmplitude
					lineTo(x, waveY)
				}
				close()
			}

			// Amber gradient fill
			beerPaint.shader = LinearGradient(
				0f, fillTop, 0f, glassBottom,
				intArrayOf(beerAmberLight, beerAmberMid, beerAmberDark),
				floatArrayOf(0f, 0.5f, 1f),
				Shader.TileMode.CLAMP
			)
			canvas.drawPath(beerPath, beerPaint)

			// Glass highlight (left side reflection)
			val highlightWidth = (rightAtFill - leftAtFill) * 0.10f
			val highlightLeft = leftAtFill + highlightWidth * 0.7f
			val hlTop = fillTop + (glassBottom - fillTop) * 0.08f
			val hlBottom = glassBottom - (glassBottom - fillTop) * 0.12f
			val highlightPath = Path().apply {
				moveTo(highlightLeft, hlTop)
				lineTo(highlightLeft + highlightWidth, hlTop + (glassBottom - fillTop) * 0.04f)
				lineTo(highlightLeft + highlightWidth, hlBottom)
				lineTo(highlightLeft, hlBottom - (glassBottom - fillTop) * 0.03f)
				close()
			}
			canvas.drawPath(highlightPath, highlightPaint)

			// Foam head
			val foamHeight = (dp * 5f).coerceAtMost(h * 0.06f)
			val foamRectTop = (fillTop - foamHeight * 0.3f).coerceAtLeast(glassTop)
			canvas.drawRect(leftAtFill, foamRectTop, rightAtFill, fillTop + dp, foamPaint)

			// Rounded foam blobs on top
			val blobCount = 7
			val span = rightAtFill - leftAtFill
			for (i in 0 until blobCount) {
				val cx = leftAtFill + span * (i + 0.5f) / blobCount
				val r = span * 0.05f + (Math.sin((i * 1.7 + wavePhase * 0.3).toDouble()).toFloat() * span * 0.01f)
				canvas.drawCircle(cx, foamRectTop, r, foamPaint)
			}

			// Foam shadow underneath blobs
			canvas.drawRect(leftAtFill, foamRectTop + foamHeight * 0.15f, rightAtFill, foamRectTop + foamHeight * 0.3f, foamShadowPaint)

			// Ambient bubbles rising through the beer
			drawAmbientBubbles(canvas, leftAtFill, rightAtFill, fillTop, glassBottom)

			// Celebration effects
			if (isCelebrating) {
				updateAndDrawBubbles(canvas, leftAtFill, rightAtFill, fillTop, glassBottom)
				drawSparkles(canvas, leftAtFill, rightAtFill, fillTop)
			}

			// Overflow animation
			if (isOverflowing) {
				val elapsed = System.currentTimeMillis() - overflowStartMs
				if (elapsed < overflowDurationMs) {
					val over = (elapsed.toFloat() / overflowDurationMs).coerceIn(0f, 1f)
					val crestHeight = foamHeight * (0.6f + 0.6f * (1f - over))
					canvas.drawRect(leftTop, glassTop - crestHeight, rightTop, glassTop, foamPaint)
					spawnDropletsIfNeeded()
					drawDroplets(canvas, leftTop, rightTop, glassTop, glassBottom)
					postInvalidateOnAnimation()
				} else {
					isOverflowing = false
					droplets.clear()
				}
			}
		}

		// Glass outline
		canvas.drawPath(glassPath, outlinePaint)

		// Continue animation frames
		val needsRedraw = isCelebrating || displayRatio > 0.01f
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
		if (displayRatio > 0.01f) {
			postInvalidateOnAnimation()
		}
	}

	private fun drawAmbientBubbles(
		canvas: Canvas,
		left: Float,
		right: Float,
		top: Float,
		bottom: Float
	) {
		val time = System.currentTimeMillis()
		val count = 4
		for (i in 0 until count) {
			val speed = 4000L + i * 1500L
			val cycle = (time % speed).toFloat() / speed
			val x = left + (right - left) * (0.2f + i * 0.18f)
			val y = bottom - (bottom - top) * cycle
			val r = dp * (1.2f + (i % 2) * 0.6f)
			val alpha = ((1f - cycle) * 80).toInt().coerceIn(0, 255)
			bubblePaint.alpha = alpha
			canvas.drawCircle(x, y, r, bubblePaint)
		}
		bubblePaint.alpha = 255
	}

	private fun updateAndDrawBubbles(
		canvas: Canvas,
		left: Float,
		right: Float,
		top: Float,
		bottom: Float
	) {
		if (Math.random() < 0.3 && bubbles.size < 24) {
			bubbles.add(Bubble.random(displayRatio))
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
		bubblePaint.alpha = 255
	}

	private fun drawSparkles(canvas: Canvas, left: Float, right: Float, top: Float) {
		val iterator = sparkles.iterator()
		while (iterator.hasNext()) {
			val s = iterator.next()
			s.update()
			val x = left + (right - left) * s.x
			val y = top + (dp * 8) + (Math.sin(s.phase.toDouble()).toFloat() * 2f)
			sparklePaint.alpha = (255 * s.alpha).toInt().coerceIn(0, 255)
			val size = (right - left) * 0.05f * s.scale
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
		val beerDropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			style = Paint.Style.FILL
			color = beerAmberMid
		}
		val it = droplets.iterator()
		while (it.hasNext()) {
			val d = it.next()
			d.update()
			val x = left + (right - left) * d.x
			val y = top - (dp * 6) + d.y
			beerDropPaint.alpha = (255 * d.alpha).toInt().coerceIn(0, 255)
			canvas.drawCircle(x, y, (right - left) * 0.02f, beerDropPaint)
			if (y > bottom || d.alpha <= 0f) it.remove()
		}
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		progressAnimator?.cancel()
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
			alpha -= 0.025f
		}

		companion object {
			fun random(progressRatio: Float): Bubble {
				val r = (0.6f + Math.random().toFloat() * 0.8f)
				return Bubble(
					x = 0.15f + Math.random().toFloat() * 0.7f,
					y = 0.05f + Math.random().toFloat() * progressRatio.coerceAtLeast(0.05f),
					radius = r,
					vy = (0.005f + Math.random().toFloat() * 0.008f),
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
			alpha -= 0.035f
			phase += 0.2f
		}

		companion object {
			fun random(): Sparkle = Sparkle(
				x = 0.1f + Math.random().toFloat() * 0.8f,
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
				x = 0.1f + Math.random().toFloat() * 0.8f,
				y = 0f,
				vy = (6f + Math.random().toFloat() * 10f),
				alpha = 1f
			)
		}
	}
}
