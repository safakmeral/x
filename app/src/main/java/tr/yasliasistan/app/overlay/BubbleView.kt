package tr.yasliasistan.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.TextView
import tr.yasliasistan.app.R

/**
 * The round "Konuş" bubble (FG-01, NFG-02, NFG-03). Tap = [onTap]; press-and-drag moves it.
 * A press gives instant visual and haptic feedback so the user knows the touch registered.
 */
@SuppressLint("ViewConstructor", "AppCompatCustomView")
class BubbleView(
    context: Context,
    private val currentPosition: () -> BubblePosition,
    private val onDragTo: (BubblePosition) -> Unit,
    private val onDragEnd: () -> Unit,
    private val onTap: () -> Unit,
) : TextView(context) {

    private val tracker = DragTracker(resources.getDimension(R.dimen.bubble_drag_slop))
    private val background = GradientDrawable().apply { shape = GradientDrawable.OVAL }

    init {
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        // Follows the phone's font size, but shrinks to fit inside the circle.
        setAutoSizeTextTypeUniformWithConfiguration(14, 24, 1, TypedValue.COMPLEX_UNIT_SP)
        val pad = resources.getDimensionPixelSize(R.dimen.bubble_border) * 2
        setPadding(pad, pad, pad, pad)
        setBackground(background)
        elevation = resources.getDimension(R.dimen.overlay_elevation)
        isClickable = true
        isFocusable = true
        setOnClickListener { onTap() } // also reached by TalkBack double-tap
        render(BubbleLook.IDLE)
    }

    fun render(look: BubbleLook) {
        val (fill, ink, border) = when (look) {
            BubbleLook.IDLE -> Triple(R.color.overlay_accent, R.color.overlay_paper, R.color.overlay_highlight)
            // Privacy: an unmistakable yellow bubble while the microphone is open.
            BubbleLook.LISTENING -> Triple(R.color.overlay_highlight, R.color.overlay_ink, R.color.overlay_ink)
            // G-34 (NFG-58): light bubble with "İnternet" while data goes to the cloud.
            BubbleLook.CLOUD -> Triple(R.color.overlay_paper, R.color.overlay_ink, R.color.overlay_accent)
        }
        background.setColor(context.getColor(fill))
        background.setStroke(resources.getDimensionPixelSize(R.dimen.bubble_border), context.getColor(border))
        setTextColor(context.getColor(ink))
        text = context.getString(
            when (look) {
                BubbleLook.IDLE -> R.string.bubble_label_idle
                BubbleLook.LISTENING -> R.string.bubble_label_listening
                BubbleLook.CLOUD -> R.string.bubble_label_cloud
            },
        )
        contentDescription = context.getString(
            when (look) {
                BubbleLook.IDLE -> R.string.bubble_description_idle
                BubbleLook.LISTENING -> R.string.bubble_description_listening
                BubbleLook.CLOUD -> R.string.bubble_description_cloud
            },
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracker.down(event.rawX, event.rawY, currentPosition())
                showPressed(true)
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            MotionEvent.ACTION_MOVE -> tracker.move(event.rawX, event.rawY)?.let(onDragTo)
            MotionEvent.ACTION_UP -> {
                showPressed(false)
                if (tracker.up()) performClick() else onDragEnd()
            }
            MotionEvent.ACTION_CANCEL -> {
                showPressed(false)
                if (tracker.isDragging) onDragEnd()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun showPressed(pressed: Boolean) {
        val scale = if (pressed) PRESSED_SCALE else 1f
        animate().cancel()
        scaleX = scale
        scaleY = scale
    }

    private companion object {
        const val PRESSED_SCALE = 0.88f
    }
}
