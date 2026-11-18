package tr.yasliasistan.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import tr.yasliasistan.core.action.Point
import tr.yasliasistan.core.action.ScrollGeometry
import tr.yasliasistan.core.action.Swipe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dispatches gestures and waits for them (PLAN G-20; needs canPerformGestures). Blocking: call
 * from the executor's worker thread, never the main thread (callbacks arrive there).
 *
 * Swipes are sent in short continued pieces, so [cancel] ("Dur", FG-23) stops the finger within
 * one piece; the finger is then lifted where it is. A final still moment before lifting keeps a
 * list from flinging past the requested length (TK-14 short/medium/long).
 */
internal class GestureRunner(private val service: AccessibilityService) {

    private val main = Handler(Looper.getMainLooper())
    private val cancelled = AtomicBoolean(false)

    /** "Dur": no new gesture starts and a running swipe ends after its current piece. */
    fun cancel() = cancelled.set(true)

    /** A new task: gestures are allowed again. */
    fun reset() = cancelled.set(false)

    fun tap(at: Point): Boolean =
        !cancelled.get() && dispatch(GestureDescription.StrokeDescription(dot(at), 0, TAP_MS, false))

    /** Not cut into pieces: lifting a long press early would turn it into a tap. */
    fun press(at: Point, durationMs: Long): Boolean =
        !cancelled.get() && dispatch(GestureDescription.StrokeDescription(dot(at), 0, durationMs, false))

    fun swipe(swipe: Swipe): Boolean {
        val points = ScrollGeometry.points(swipe, PIECES)
        var stroke: GestureDescription.StrokeDescription? = null
        for (i in 1 until points.size) {
            if (cancelled.get()) {
                stroke?.let { lift(it, points[i - 1]) } // the finger must not stay down
                return false
            }
            val piece = line(points[i - 1], points[i])
            stroke = stroke?.continueStroke(piece, 0, PIECE_MS, true)
                ?: GestureDescription.StrokeDescription(piece, 0, PIECE_MS, true)
            if (!dispatch(stroke)) return false
        }
        return stroke != null && lift(stroke, points.last()) && !cancelled.get()
    }

    private fun lift(stroke: GestureDescription.StrokeDescription, at: Point): Boolean =
        dispatch(stroke.continueStroke(dot(at), 0, HOLD_MS, false))

    private fun dispatch(stroke: GestureDescription.StrokeDescription): Boolean {
        check(Looper.myLooper() != Looper.getMainLooper()) { "gestures wait; not on the main thread" }
        val done = CountDownLatch(1)
        val completed = AtomicBoolean(false)
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                completed.set(true)
                done.countDown()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) = done.countDown()
        }
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        if (!service.dispatchGesture(gesture, callback, main)) return false
        return done.await(stroke.duration + WAIT_SLACK_MS, TimeUnit.MILLISECONDS) && completed.get()
    }

    private fun dot(p: Point) = Path().apply { moveTo(p.x.toFloat(), p.y.toFloat()) }

    private fun line(a: Point, b: Point) = Path().apply {
        moveTo(a.x.toFloat(), a.y.toFloat())
        lineTo(b.x.toFloat(), b.y.toFloat())
    }

    private companion object {
        const val TAP_MS = 60L
        const val PIECES = 5
        const val PIECE_MS = 60L
        const val HOLD_MS = 80L
        const val WAIT_SLACK_MS = 1_000L
    }
}
