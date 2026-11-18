package tr.yasliasistan.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import tr.yasliasistan.app.R
import tr.yasliasistan.core.action.Point
import tr.yasliasistan.core.action.SwipeMark
import tr.yasliasistan.core.action.TapMark
import tr.yasliasistan.core.action.TouchTrace

/**
 * Draws one touch mark (G-21, FG-21; V-05): a yellow ring with a dark edge where the assistant
 * touched, or a yellow arrow with a dark edge along a scroll. [origin] is the window's top-left
 * corner on the screen; marks are given in screen pixels. Red is never used (errors only).
 */
@SuppressLint("ViewConstructor")
internal class TouchTraceView(
    context: Context,
    private val origin: Point,
    private val tap: TapMark?,
    private val swipe: SwipeMark?,
) : View(context) {

    private val stroke = resources.getDimension(R.dimen.trace_stroke)
    private val edge = resources.getDimension(R.dimen.trace_edge)

    private val dark = paint(context.getColor(R.color.overlay_ink), stroke + 2 * edge)
    private val yellow = paint(context.getColor(R.color.overlay_highlight), stroke)

    /** 0..1: how much of the arrow is drawn / how big the ring is. */
    var progress: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    override fun onDraw(canvas: Canvas) {
        tap?.let { drawRing(canvas, it) }
        swipe?.let { drawArrow(canvas, it) }
    }

    private fun drawRing(canvas: Canvas, mark: TapMark) {
        val c = local(mark.center)
        val r = mark.radius * (RING_START + (1 - RING_START) * progress)
        canvas.drawCircle(c.x.toFloat(), c.y.toFloat(), r, dark)
        canvas.drawCircle(c.x.toFloat(), c.y.toFloat(), r, yellow)
    }

    private fun drawArrow(canvas: Canvas, mark: SwipeMark) {
        val from = local(mark.from)
        val tip = local(TouchTrace.along(mark, progress))
        val path = Path().apply {
            moveTo(from.x.toFloat(), from.y.toFloat())
            lineTo(tip.x.toFloat(), tip.y.toFloat())
            if (progress >= 1f) {
                val left = local(mark.headLeft)
                val right = local(mark.headRight)
                moveTo(left.x.toFloat(), left.y.toFloat())
                lineTo(tip.x.toFloat(), tip.y.toFloat())
                lineTo(right.x.toFloat(), right.y.toFloat())
            }
        }
        canvas.drawPath(path, dark)
        canvas.drawPath(path, yellow)
    }

    private fun local(p: Point) = TouchTrace.local(p, origin)

    private fun paint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }

    private companion object {
        /** The ring starts at this share of its size and grows to full size. */
        const val RING_START = 0.6f
    }
}
