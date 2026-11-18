package tr.yasliasistan.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.TextView
import tr.yasliasistan.app.R

/** Large text-labelled "Dur" button (FG-11, NFG-02, NFG-03); one tap stops the session. */
@SuppressLint("ViewConstructor", "AppCompatCustomView")
class StopButtonView(context: Context, onStop: () -> Unit) : TextView(context) {

    init {
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
        setTextColor(context.getColor(R.color.overlay_paper))
        text = context.getString(R.string.stop_label)
        contentDescription = context.getString(R.string.stop_description)
        minWidth = resources.getDimensionPixelSize(R.dimen.stop_min_width)
        background = GradientDrawable().apply {
            cornerRadius = resources.getDimension(R.dimen.stop_corner)
            setColor(context.getColor(R.color.overlay_ink))
            setStroke(resources.getDimensionPixelSize(R.dimen.bubble_border), context.getColor(R.color.overlay_highlight))
        }
        elevation = resources.getDimension(R.dimen.overlay_elevation)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onStop()
        }
    }

    // Instant visual feedback on touch-down, before the click fires on release.
    override fun drawableStateChanged() {
        super.drawableStateChanged()
        val scale = if (isPressed) 0.94f else 1f
        scaleX = scale
        scaleY = scale
    }
}
