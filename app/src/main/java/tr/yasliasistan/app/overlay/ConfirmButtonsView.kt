package tr.yasliasistan.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.LinearLayout
import android.widget.TextView
import tr.yasliasistan.app.R

/**
 * Large "Evet" and "Hayır" buttons shown while a yes/no question is asked (FG-07, NFG-02, NFG-03;
 * UD-1 Ö-3). A tap answers without speaking or typing. "Evet" is dark blue with white text (about
 * 10:1 contrast), "Hayır" white with black text (about 18:1); both carry their word as text.
 */
@SuppressLint("ViewConstructor")
class ConfirmButtonsView(context: Context, onAnswer: (yes: Boolean) -> Unit) : LinearLayout(context) {

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        val gap = resources.getDimensionPixelSize(R.dimen.confirm_gap)
        addView(
            answerButton(R.string.confirm_yes, R.string.confirm_yes_description, R.color.overlay_accent, R.color.overlay_paper) {
                onAnswer(true)
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply { marginEnd = gap / 2 },
        )
        addView(
            answerButton(R.string.confirm_no, R.string.confirm_no_description, R.color.overlay_paper, R.color.overlay_ink) {
                onAnswer(false)
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply { marginStart = gap / 2 },
        )
    }

    private fun answerButton(label: Int, description: Int, fill: Int, ink: Int, onClick: () -> Unit) =
        TextView(context).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            setTextSize(TypedValue.COMPLEX_UNIT_SP, CONFIRM_TEXT_SP)
            setTextColor(context.getColor(ink))
            text = context.getString(label)
            contentDescription = context.getString(description)
            minWidth = resources.getDimensionPixelSize(R.dimen.confirm_min_width)
            background = GradientDrawable().apply {
                cornerRadius = resources.getDimension(R.dimen.stop_corner)
                setColor(context.getColor(fill))
                setStroke(resources.getDimensionPixelSize(R.dimen.bubble_border), context.getColor(R.color.overlay_ink))
            }
            elevation = resources.getDimension(R.dimen.overlay_elevation)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }

    private companion object {
        const val CONFIRM_TEXT_SP = 30f
    }
}
