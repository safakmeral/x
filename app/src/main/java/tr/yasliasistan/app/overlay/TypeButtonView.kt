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

/** Large "Yazarak söyle" button above "Dur" (FG-04, NFG-02); light so it never looks like "Dur". */
@SuppressLint("ViewConstructor", "AppCompatCustomView")
class TypeButtonView(context: Context, onType: () -> Unit) : TextView(context) {

    init {
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        setTextColor(context.getColor(R.color.overlay_ink))
        text = context.getString(R.string.type_button)
        contentDescription = context.getString(R.string.type_button_description)
        minWidth = resources.getDimensionPixelSize(R.dimen.stop_min_width)
        val pad = resources.getDimensionPixelSize(R.dimen.caption_padding)
        setPadding(pad, 0, pad, 0)
        background = GradientDrawable().apply {
            cornerRadius = resources.getDimension(R.dimen.stop_corner)
            setColor(context.getColor(R.color.overlay_paper))
            setStroke(resources.getDimensionPixelSize(R.dimen.bubble_border), context.getColor(R.color.overlay_ink))
        }
        elevation = resources.getDimension(R.dimen.overlay_elevation)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onType()
        }
    }
}
