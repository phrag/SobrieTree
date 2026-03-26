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
import kotlin.math.sin

class BeerGlassView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

	private var progressRatio: Float = 0f // 0.0 to 1.0

	// Celebration animation state
	private var isCelebrating: Boolean = false
	private var celebrationStartMs: Long = 0L
	private val celebrationDurationMs: Long = 1500L
	private val bubbles: MutableList<Bubble> = mutableListOf()
	private val sparkles: MutableList<Sparkle> = mutableListOf()

	// Overflow animation when ratio > 1.0
	private var isOverflowing: Boolean = false
	private var overflowStartMs: Long = 0L
	private val overflowDurationMs: Long = 1200L
	private val droplets: MutableList<Droplet> = mutableListOf()

	// Ambient bubble animation (always on when there's liquid)
	private val ambientBubbles: MutableList<Bubble> = mutableListOf()
	private var lastBubbleSpawnMs: Long = 0L

	// Wave animation phase
	private var wavePhase: Float = 0f

	private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = resources.displayMetrics.density * 2.5f
		color = 0xFF8B7355.toInt() // warm brown glass rim
	}

	private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = 0x15FFFFFF // subtle glass tint
	}

	private val beerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = ContextCompat.getColor(context, R.color.beer_amber)
	}

	private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = 0x44FFFFFF
	}

	private val foamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = 0xFFFFFAF0.toInt() // creamy foam color
	}

	private val foamShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = 0x22000000
	}

	private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = 0x88FFFFFF.toInt()
	}

	private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = resources.displayMetrics.density * 1.4f
		color = 0xCCFFFFFF.toInt()
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
		val now = System.currentTimeMillis()

		// Update wave phase for liquid surface animation
		wavePhase += 0.08f
		if (wavePhase > 6.28f) wavePhase -= 6.28f

		// Define a pint glass shape (wider at top, narrower at bottom)
		val padding = w * 0.08f
		val topWidth = w - padding * 2f
		val bottomWidth = topWidth * 0.85f
		val glassHeight = h * 0.88f
		val glassTop = h * 0.06f
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

		// Draw glass background (subtle)
		canvas.drawPath(glassPath, glassPaint)

		// Draw beer fill based on progress
		if (progressRatio > 0f) {
			val fillHeight = glassHeight * progressRatio
			val fillTop = glassBottom - fillHeight

			// Interpolate width between bottom and top at the fill line
			val t = (fillTop - glassTop) / (glassBottom - glassTop)
			val widthAtFill = bottomWidth + (topWidth - bottomWidth) * (1f - t)
			val leftAtFill = (w - widthAtFill) / 2f
			val rightAtFill = leftAtFill + widthAtFill

			// Create wavy top surface
			val waveAmplitude = w * 0.008f
			val beerPath = Path().apply {
				moveTo(leftBottom, glassBottom)
				lineTo(rightBottom, glassBottom)
				lineTo(rightAtFill, fillTop)
				// Wavy top edge
				val segments = 12
				for (i in segments - 1 downTo 0) {
					val segX = leftAtFill + (rightAtFill - leftAtFill) * i / segments
					val waveOffset = sin(wavePhase + i * 0.5f) * waveAmplitude
					lineTo(segX, fillTop + waveOffset)
				}
				close()
			}

			// Beer gradient: golden amber at top, darker amber in middle, brown at bottom
			beerPaint.shader = LinearGradient(
				0f, fillTop, 0f, glassBottom,
				intArrayOf(
					ContextCompat.getColor(context, R.color.beer_gold),
					ContextCompat.getColor(context, R.color.beer_amber),
					ContextCompat.getColor(context, R.color.beer_brown)
				),
				floatArrayOf(0f, 0.5f, 1f),
				Shader.TileMode.CLAMP
			)
			canvas.drawPath(beerPath, beerPaint)

			// Glass highlight (condensation effect)
			val highlightWidth = (rightAtFill - leftAtFill) * 0.1f
			val highlightLeft = leftAtFill + highlightWidth * 0.5f
			val highlightPath = Path().apply {
				moveTo(highlightLeft, fillTop + (glassBottom - fillTop) * 0.08f)
				lineTo(highlightLeft + highlightWidth, fillTop + (glassBottom - fillTop) * 0.15f)
				lineTo(highlightLeft + highlightWidth * 0.8f, glassBottom - (glassBottom - fillTop) * 0.15f)
				lineTo(highlightLeft, glassBottom - (glassBottom - fillTop) * 0.1f)
				close()
			}
			canvas.drawPath(highlightPath, highlightPaint)

			// Second smaller highlight on the right
			val highlightRight = rightAtFill - highlightWidth * 1.2f
			val highlightPath2 = Path().apply {
				moveTo(highlightRight, fillTop + (glassBottom - fillTop) * 0.25f)
				lineTo(highlightRight + highlightWidth * 0.5f, fillTop + (glassBottom - fillTop) * 0.3f)
				lineTo(highlightRight + highlightWidth * 0.4f, glassBottom - (glassBottom - fillTop) * 0.35f)
				lineTo(highlightRight, glassBottom - (glassBottom - fillTop) * 0.3f)
				close()
			}
			highlightPaint.alpha = 50
			canvas.drawPath(highlightPath2, highlightPaint)
			highlightPaint.alpha = 68 // restore

			// Foam head at the top of the beer
			val foamHeight = (resources.displayMetrics.density * 6).coerceAtMost(h * 0.06f)
			val foamRectTop = (fillTop - foamHeight * 0.5f).coerceAtLeast(glassTop)

			// Draw foam shadow first
			canvas.drawRect(leftAtFill + 2, foamRectTop + 2, rightAtFill - 2, fillTop + 2, foamShadowPaint)

			// Foam base layer
			canvas.drawRect(leftAtFill, foamRectTop, rightAtFill, fillTop + foamHeight * 0.3f, foamPaint)

			// Rounded foam blobs on top for more realistic look
			val blobCount = 8
			val span = rightAtFill - leftAtFill
			for (i in 0 until blobCount) {
				val cx = leftAtFill + span * (i + 0.5f) / blobCount
				val rBase = span * 0.055f
				val rVariation = sin(wavePhase * 0.5f + i * 0.8f) * rBase * 0.15f
				val r = rBase + rVariation
				canvas.drawCircle(cx, foamRectTop - r * 0.3f, r, foamPaint)
			}
			// Second row of smaller blobs
			for (i in 0 until blobCount - 1) {
				val cx = leftAtFill + span * (i + 1f) / blobCount
				val r = span * 0.035f
				canvas.drawCircle(cx, foamRectTop - span * 0.04f, r, foamPaint)
			}

			// Ambient bubbles (always rising when there's beer)
			updateAmbientBubbles(now)
			drawAmbientBubbles(canvas, leftAtFill, rightAtFill, fillTop, glassBottom)

			// Celebration bubbles & sparkles
			if (isCelebrating) {
				updateAndDrawBubbles(canvas, leftAtFill, rightAtFill, fillTop, glassBottom)
				drawSparkles(canvas, leftAtFill, rightAtFill, fillTop)
			}

			// Overflow animation above rim
			if (isOverflowing) {
				val elapsed = now - overflowStartMs
				if (elapsed < overflowDurationMs) {
					val over = (elapsed.toFloat() / overflowDurationMs).coerceIn(0f, 1f)
					val crestHeight = foamHeight * (0.8f + 0.5f * (1f - over))
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

		// Glass outline on top
		canvas.drawPath(glassPath, outlinePaint)

		// Draw glass rim highlight
		val rimHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			style = Paint.Style.STROKE
			strokeWidth = resources.displayMetrics.density * 1f
			color = 0x33FFFFFF
		}
		canvas.drawLine(leftTop + 2, glassTop + 1, rightTop - 2, glassTop + 1, rimHighlight)

		// Continue animation frames
		val needsAnimation = progressRatio > 0f || isCelebrating || isOverflowing
		if (needsAnimation) {
			if (isCelebrating) {
				val elapsed = now - celebrationStartMs
				if (elapsed >= celebrationDurationMs) {
					isCelebrating = false
					bubbles.clear()
					sparkles.clear()
				}
			}
			postInvalidateOnAnimation()
		}
	}

	private fun updateAmbientBubbles(now: Long) {
		// Spawn new ambient bubbles periodically
		if (now - lastBubbleSpawnMs > 400 && ambientBubbles.size < 6 && progressRatio > 0.1f) {
			ambientBubbles.add(Bubble.ambient())
			lastBubbleSpawnMs = now
		}
	}

	private fun drawAmbientBubbles(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float) {
		val iterator = ambientBubbles.iterator()
		while (iterator.hasNext()) {
			val b = iterator.next()
			b.updateAmbient()
			val x = left + (right - left) * b.x
			val y = bottom - (bottom - top) * b.y
			bubblePaint.alpha = (180 * b.alpha).toInt().coerceIn(0, 255)
			val radius = b.radius * (right - left) * 0.02f
			canvas.drawCircle(x, y, radius, bubblePaint)
			// Small highlight on bubble
			bubblePaint.alpha = (100 * b.alpha).toInt().coerceIn(0, 255)
			canvas.drawCircle(x - radius * 0.3f, y - radius * 0.3f, radius * 0.3f, bubblePaint)
			if (b.y >= 1f || b.alpha <= 0f) iterator.remove()
		}
	}

	private fun updateAndDrawBubbles(
		canvas: Canvas,
		left: Float,
		right: Float,
		top: Float,
		bottom: Float
	) {
		// Spawn more bubbles during celebration
		if (Math.random() < 0.35 && bubbles.size < 25) {
			bubbles.add(Bubble.random(progressRatio))
		}
		val iterator = bubbles.iterator()
		while (iterator.hasNext()) {
			val b = iterator.next()
			b.update()
			val x = left + (right - left) * b.x.coerceIn(0.05f, 0.95f)
			val y = bottom - (bottom - top) * b.y
			val radius = b.radius * (right - left) * 0.025f
			bubblePaint.alpha = (220 * b.alpha).toInt().coerceIn(0, 255)
			canvas.drawCircle(x, y, radius, bubblePaint)
			// Bubble highlight
			bubblePaint.alpha = (120 * b.alpha).toInt().coerceIn(0, 255)
			canvas.drawCircle(x - radius * 0.25f, y - radius * 0.25f, radius * 0.35f, bubblePaint)
			if (b.alpha <= 0f || y <= top) iterator.remove()
		}
	}

	private fun drawSparkles(canvas: Canvas, left: Float, right: Float, top: Float) {
		val iterator = sparkles.iterator()
		while (iterator.hasNext()) {
			val s = iterator.next()
			s.update()
			val x = left + (right - left) * s.x
			val y = top - (resources.displayMetrics.density * 4) + sin(s.phase) * 3f
			sparklePaint.alpha = (255 * s.alpha).toInt().coerceIn(0, 255)
			val size = (right - left) * 0.06f * s.scale
			// 4-point star with slight rotation effect
			val angle = s.phase * 0.5f
			val cos = kotlin.math.cos(angle)
			val sin = sin(angle)
			// Rotated cross
			canvas.drawLine(x - size * cos, y - size * sin, x + size * cos, y + size * sin, sparklePaint)
			canvas.drawLine(x + size * sin, y - size * cos, x - size * sin, y + size * cos, sparklePaint)
			// Additional smaller cross for 8-point effect
			sparklePaint.alpha = (180 * s.alpha).toInt().coerceIn(0, 255)
			val smallSize = size * 0.6f
			canvas.drawLine(x - smallSize, y, x + smallSize, y, sparklePaint)
			canvas.drawLine(x, y - smallSize, x, y + smallSize, sparklePaint)
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
		var alpha: Float,
		var wobble: Float = 0f,
		var wobbleSpeed: Float = 0.1f
	) {
		fun update() {
			y += vy
			alpha -= 0.03f
			wobble += wobbleSpeed
			x += sin(wobble) * 0.002f
		}

		fun updateAmbient() {
			y += vy
			wobble += wobbleSpeed
			x += sin(wobble) * 0.003f
			// Slow fade as bubble rises
			if (y > 0.7f) {
				alpha -= 0.02f
			}
		}

		companion object {
			fun random(progressRatio: Float): Bubble {
				val r = (0.6f + Math.random().toFloat() * 0.8f)
				return Bubble(
					x = 0.2f + Math.random().toFloat() * 0.6f,
					y = 0.05f + Math.random().toFloat() * progressRatio.coerceAtLeast(0.05f),
					radius = r,
					vy = (0.008f + Math.random().toFloat() * 0.012f),
					alpha = 0.9f,
					wobble = Math.random().toFloat() * 6.28f,
					wobbleSpeed = 0.08f + Math.random().toFloat() * 0.06f
				)
			}

			fun ambient(): Bubble {
				return Bubble(
					x = 0.15f + Math.random().toFloat() * 0.7f,
					y = 0.02f,
					radius = 0.4f + Math.random().toFloat() * 0.5f,
					vy = 0.004f + Math.random().toFloat() * 0.006f,
					alpha = 0.8f,
					wobble = Math.random().toFloat() * 6.28f,
					wobbleSpeed = 0.05f + Math.random().toFloat() * 0.05f
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


