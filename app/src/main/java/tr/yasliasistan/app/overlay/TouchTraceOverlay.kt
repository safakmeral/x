package tr.yasliasistan.app.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import tr.yasliasistan.app.R
import tr.yasliasistan.core.action.Point
import tr.yasliasistan.core.action.Swipe
import tr.yasliasistan.core.action.TouchTrace
import tr.yasliasistan.core.screen.Bounds

/** Shows where the assistant touches and scrolls (G-21); callable from any thread. */
interface TouchIndicator {
    fun showTap(at: Point)
    fun showSwipe(swipe: Swipe)

    companion object {
        val NONE = object : TouchIndicator {
            override fun showTap(at: Point) = Unit
            override fun showSwipe(swipe: Swipe) = Unit
        }
    }
}

/**
 * Touch dot and scroll arrow overlay (PLAN G-21; FG-21, NFG-40, TK-08/V-05). Each mark gets its
 * own small TYPE_ACCESSIBILITY_OVERLAY window, only as big as the mark, that never takes touches
 * (FLAG_NOT_TOUCHABLE: the app below and the assistant's own gestures are not blocked). The window
 * carries the own-overlay title and id, so the screen reader skips it (NFG-40). It grows, holds a
 * moment and fades away by itself.
 */
class TouchTraceOverlay(private val context: Context) : TouchIndicator {

    private val main = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val res = context.resources
    private val radius = res.getDimensionPixelSize(R.dimen.trace_ring_radius)
    private val pad = res.getDimensionPixelSize(R.dimen.trace_stroke) / 2 + res.getDimensionPixelSize(R.dimen.trace_edge) + 1
    private val head = res.getDimensionPixelSize(R.dimen.trace_arrow_head)
    private val shown = LinkedHashMap<TouchTraceView, ValueAnimator>()
    private var released = false

    override fun showTap(at: Point) {
        main.post {
            val mark = TouchTrace.tap(at, radius)
            show(TouchTrace.box(mark, pad)) { origin -> TouchTraceView(context, origin, mark, null) }
                ?.let { animate(it, GROW_MS, TAP_TOTAL_MS) }
        }
    }

    override fun showSwipe(swipe: Swipe) {
        main.post {
            val mark = TouchTrace.swipe(swipe, head) ?: return@post
            show(TouchTrace.box(mark, pad)) { origin -> TouchTraceView(context, origin, null, mark) }
                ?.let { animate(it, DRAW_MS, SWIPE_TOTAL_MS) }
        }
    }

    /**
     * "Ben yapayım" (demo, minimal G-40): a yellow ring around the element the user should press,
     * kept for [holdMs]. It never takes touches, so the user presses the real button through it.
     */
    fun showGuide(box: Bounds, holdMs: Long = GUIDE_MS) {
        main.post {
            removeGuideNow() // only one guide ring at a time
            // A wide row (a chat in a list) gets a ring on the name, right of the profile picture
            // (pressing the picture opens the profile, not the chat).
            val wide = box.width > box.height * 5 / 2
            val cx = if (wide) box.left + box.height * 2 else (box.left + box.right) / 2
            val cy = (box.top + box.bottom) / 2
            val ring = ((if (wide) box.height else minOf(box.width, box.height)) / 2 + pad).coerceIn(minOf(radius, maxGuideRadius), maxGuideRadius)
            val mark = TouchTrace.tap(Point(cx, cy), ring)
            guide = show(TouchTrace.box(mark, pad)) { origin -> TouchTraceView(context, origin, mark, null) }
                ?.also { animate(it, GROW_MS, holdMs) }
        }
    }

    /** Removes the guide ring (the user pressed, or the task ended). */
    fun clearGuide() {
        main.post { removeGuideNow() }
    }

    private fun removeGuideNow() {
        guide?.let { v ->
            shown.remove(v)?.let { a -> a.removeAllListeners(); a.cancel() }
            remove(v)
        }
        guide = null
    }

    private val maxGuideRadius = res.displayMetrics.density.let { (56 * it).toInt() }

    private var guide: TouchTraceView? = null

    /** Removes every mark at once (service ending). Main thread. */
    fun release() {
        released = true
        main.removeCallbacksAndMessages(null)
        shown.values.toList().forEach(ValueAnimator::cancel)
        shown.keys.toList().forEach(::remove)
    }

    private fun show(box: Bounds, create: (Point) -> TouchTraceView): TouchTraceView? {
        if (released || box.isEmpty) return null
        val view = create(Point(box.left, box.top))
        return try {
            windowManager.addView(view, params(box))
            OwnOverlayWindows.register(view)
            view
        } catch (_: RuntimeException) {
            null // the service is going away; a missing mark must never stop the task
        }
    }

    /** Draws the mark in [drawMs], keeps it, then fades it out so it is gone after [totalMs]. */
    private fun animate(view: TouchTraceView, drawMs: Long, totalMs: Long) {
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = totalMs
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val elapsed = it.currentPlayTime.coerceAtMost(totalMs)
                view.progress = (elapsed.toFloat() / drawMs).coerceAtMost(1f)
                val fadeFrom = totalMs - FADE_MS
                view.alpha = if (elapsed <= fadeFrom) 1f else 1f - (elapsed - fadeFrom).toFloat() / FADE_MS
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = remove(view)
            })
        }
        shown[view] = animator
        animator.start()
    }

    private fun remove(view: TouchTraceView) {
        if (shown.remove(view) == null && !view.isAttachedToWindow) return
        OwnOverlayWindows.unregister(view)
        if (view.isAttachedToWindow) windowManager.removeViewImmediate(view)
    }

    private fun params(box: Bounds) = WindowManager.LayoutParams(
        box.width,
        box.height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = box.left
        y = box.top
        title = OverlayController.OVERLAY_TITLE // NFG-40: the screen reader skips it
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    private companion object {
        /** The ring grows quickly, so it is there when the finger lands. */
        const val GROW_MS = 150L
        const val TAP_TOTAL_MS = 900L

        /** The arrow is drawn about as fast as the finger moves (G-20 swipe: 5 x 60 ms + hold). */
        const val DRAW_MS = 380L
        const val SWIPE_TOTAL_MS = 1_200L
        const val FADE_MS = 300L

        /** How long the "Ben yapayım" ring stays if the user does not press. */
        const val GUIDE_MS = 20_000L
    }
}
