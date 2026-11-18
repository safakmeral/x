package tr.yasliasistan.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import tr.yasliasistan.app.R

/**
 * Owns the bubble and "Dur" windows. They are TYPE_ACCESSIBILITY_OVERLAY windows added by
 * the accessibility service, so no "display over other apps" permission is needed (V-20, NFG-68).
 * Must be created and used on the main thread from inside the connected service.
 */
class OverlayController(
    private val context: Context,
    private val callbacks: OverlayCallbacks,
    private val store: BubblePositionStore = BubblePositionStore(context),
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val res = context.resources
    private val bubbleSize = res.getDimensionPixelSize(R.dimen.bubble_size)
    private val edgeMargin = res.getDimensionPixelSize(R.dimen.bubble_edge_margin)

    private val bubbleParams = overlayParams(bubbleSize, bubbleSize)
    private val stopParams = overlayParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        res.getDimensionPixelSize(R.dimen.stop_height),
    )

    private val bubble = BubbleView(
        context,
        currentPosition = { BubblePosition(bubbleParams.x, bubbleParams.y) },
        onDragTo = { moveBubble(BubbleGeometry.clamp(it, bubbleSize, safeArea())) },
        onDragEnd = ::snapAndRemember,
        onTap = callbacks::onBubbleTapped,
    )
    private val stop = StopButtonView(context, callbacks::onStopPressed)

    // "Yazarak söyle" (G-11, FG-04), shown above "Dur" while the assistant expects input.
    private val type = TypeButtonView(context, callbacks::onTypePressed)
    private val typeParams = overlayParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        res.getDimensionPixelSize(R.dimen.type_height),
    )
    private var typeShown = false

    // "Evet" / "Hayır" (UD-1 Ö-3, FG-07), shown above "Yazarak söyle" while a yes/no question is asked.
    private val confirm = ConfirmButtonsView(context, callbacks::onConfirmTapped)
    private val confirmParams = overlayParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        res.getDimensionPixelSize(R.dimen.confirm_height),
    )
    private var confirmShown = false

    // Caption (G-08, NFG-04): what the assistant says, as text next to the bubble.
    private val caption = CaptionView(context)
    private val captionParams = overlayParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
    ).apply { flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE }
    private val captionMaxWidth = res.getDimensionPixelSize(R.dimen.caption_max_width)
    private val captionGap = res.getDimensionPixelSize(R.dimen.caption_gap)
    private var captionShown = false

    private var snapAnimator: ValueAnimator? = null
    private var bubbleShown = false
    private var stopShown = false
    private var state = OverlayState.IDLE

    fun show() {
        if (bubbleShown) return
        placeFromStore()
        windowManager.addView(bubble, bubbleParams)
        OwnOverlayWindows.register(bubble)
        bubbleShown = true
        render(state)
    }

    fun hide() {
        snapAnimator?.cancel()
        removeIfShown(caption, captionShown).also { captionShown = false }
        removeIfShown(confirm, confirmShown).also { confirmShown = false }
        removeIfShown(type, typeShown).also { typeShown = false }
        removeIfShown(stop, stopShown).also { stopShown = false }
        removeIfShown(bubble, bubbleShown).also { bubbleShown = false }
    }

    /** Called by the session wiring (G-11) whenever the session/listening state changes. */
    fun render(newState: OverlayState) {
        state = newState
        bubble.render(newState.bubbleLook)
        if (newState.stopVisible && bubbleShown && !stopShown) {
            positionStop()
            windowManager.addView(stop, stopParams)
            OwnOverlayWindows.register(stop)
            stopShown = true
        } else if (!newState.stopVisible && stopShown) {
            removeIfShown(stop, true)
            stopShown = false
        }
        renderType(newState.typeVisible && bubbleShown)
        renderConfirm(newState.confirmVisible && bubbleShown)
    }

    private fun renderConfirm(visible: Boolean) {
        if (visible && !confirmShown) {
            positionConfirm()
            windowManager.addView(confirm, confirmParams)
            OwnOverlayWindows.register(confirm)
            confirmShown = true
        } else if (!visible && confirmShown) {
            removeIfShown(confirm, true)
            confirmShown = false
        }
    }

    private fun renderType(visible: Boolean) {
        if (visible && !typeShown) {
            positionType()
            windowManager.addView(type, typeParams)
            OwnOverlayWindows.register(type)
            typeShown = true
        } else if (!visible && typeShown) {
            removeIfShown(type, true)
            typeShown = false
        }
    }

    /** Shows [text] next to the bubble, or removes the caption when null/blank (NFG-04). */
    fun showCaption(text: String?) {
        if (text.isNullOrBlank() || !bubbleShown) {
            removeIfShown(caption, captionShown)
            captionShown = false
            return
        }
        caption.text = text
        placeCaption()
        if (captionShown) {
            windowManager.updateViewLayout(caption, captionParams)
        } else {
            windowManager.addView(caption, captionParams)
            OwnOverlayWindows.register(caption)
            captionShown = true
        }
    }

    /** Re-fit everything after rotation or a screen-size change. */
    fun onScreenChanged() {
        if (!bubbleShown) return
        snapAnimator?.cancel()
        placeFromStore()
        windowManager.updateViewLayout(bubble, bubbleParams)
        if (stopShown) {
            positionStop()
            windowManager.updateViewLayout(stop, stopParams)
        }
        if (typeShown) {
            positionType()
            windowManager.updateViewLayout(type, typeParams)
        }
        if (confirmShown) {
            positionConfirm()
            windowManager.updateViewLayout(confirm, confirmParams)
        }
        followBubbleWithCaption()
    }

    private fun placeCaption() {
        val area = safeArea()
        val width = minOf(area.width, captionMaxWidth)
        caption.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(area.height, View.MeasureSpec.AT_MOST),
        )
        val pos = CaptionGeometry.place(
            BubblePosition(bubbleParams.x, bubbleParams.y),
            bubbleSize,
            width,
            caption.measuredHeight,
            area,
            captionGap,
        )
        captionParams.width = width
        captionParams.x = pos.x
        captionParams.y = pos.y
    }

    private fun followBubbleWithCaption() {
        if (!captionShown) return
        placeCaption()
        windowManager.updateViewLayout(caption, captionParams)
    }

    private fun placeFromStore() {
        val pos = BubbleGeometry.place(store.load(), bubbleSize, safeArea())
        bubbleParams.x = pos.x
        bubbleParams.y = pos.y
    }

    private fun moveBubble(pos: BubblePosition) {
        bubbleParams.x = pos.x
        bubbleParams.y = pos.y
        if (bubbleShown) windowManager.updateViewLayout(bubble, bubbleParams)
        followBubbleWithCaption()
    }

    private fun snapAndRemember() {
        val area = safeArea()
        val from = BubblePosition(bubbleParams.x, bubbleParams.y)
        val target = BubbleGeometry.snapToEdge(from, bubbleSize, area)
        store.save(BubbleGeometry.toStored(target, bubbleSize, area))
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SNAP_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedFraction
                moveBubble(
                    BubblePosition(
                        from.x + ((target.x - from.x) * f).toInt(),
                        from.y + ((target.y - from.y) * f).toInt(),
                    ),
                )
            }
            start()
        }
    }

    private fun positionStop() {
        val area = safeArea()
        stopParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        stopParams.x = 0
        stopParams.y = area.bottom - stopParams.height - res.getDimensionPixelSize(R.dimen.stop_bottom_margin)
    }

    private fun positionType() {
        positionStop()
        typeParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        typeParams.x = 0
        typeParams.y = stopParams.y - typeParams.height - res.getDimensionPixelSize(R.dimen.type_gap)
    }

    /** Above the "Yazarak söyle" slot (both are offered together during a yes/no question). */
    private fun positionConfirm() {
        positionType()
        confirmParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        confirmParams.x = 0
        confirmParams.y = typeParams.y - confirmParams.height - res.getDimensionPixelSize(R.dimen.type_gap)
    }

    private fun removeIfShown(view: View, shown: Boolean) {
        if (!shown) return
        OwnOverlayWindows.unregister(view)
        windowManager.removeViewImmediate(view)
    }

    /** Screen minus status/navigation bars and cutouts, with a small margin. */
    private fun safeArea(): SafeArea {
        val raw = screenArea()
        return SafeArea(
            raw.left + edgeMargin,
            raw.top + edgeMargin,
            raw.right - edgeMargin,
            raw.bottom - edgeMargin,
        )
    }

    private fun screenArea(): SafeArea {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            val b = metrics.bounds
            return SafeArea(b.left + insets.left, b.top + insets.top, b.right - insets.right, b.bottom - insets.bottom)
        }
        // API 29: no WindowMetrics; keep clear of the status and navigation bars by a fixed amount.
        val dm = res.displayMetrics
        val bars = (LEGACY_BAR_DP * dm.density).toInt()
        return SafeArea(0, bars, dm.widthPixels, dm.heightPixels - bars)
    }

    private fun overlayParams(width: Int, height: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        // Not focusable: never steals the keyboard; touches outside go to the app below.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = OVERLAY_TITLE
        // Coordinates are absolute screen pixels; we keep clear of bars ourselves (safeArea).
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    companion object {
        /** Window title of every own overlay; a secondary marker for NFG-40 filtering. */
        const val OVERLAY_TITLE = "TelefonAsistaniOverlay"
        private const val SNAP_MS = 180L
        private const val LEGACY_BAR_DP = 48
    }
}
