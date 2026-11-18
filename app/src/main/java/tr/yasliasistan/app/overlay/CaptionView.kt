package tr.yasliasistan.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import tr.yasliasistan.app.R

/**
 * Large, high-contrast text of what the assistant is saying, shown next to the bubble
 * (NFG-04: output is both spoken and visible). Not touchable; taps pass through.
 */
@SuppressLint("ViewConstructor", "AppCompatCustomView")
class CaptionView(context: Context) : TextView(context) {

    init {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        setTextColor(context.getColor(R.color.overlay_paper))
        maxLines = MAX_LINES
        ellipsize = TextUtils.TruncateAt.END
        val pad = resources.getDimensionPixelSize(R.dimen.caption_padding)
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            cornerRadius = resources.getDimension(R.dimen.caption_corner)
            setColor(context.getColor(R.color.overlay_ink))
            setStroke(resources.getDimensionPixelSize(R.dimen.bubble_border), context.getColor(R.color.overlay_highlight))
        }
        elevation = resources.getDimension(R.dimen.overlay_elevation)
        // The same sentence is already spoken by the app; avoid TalkBack reading it twice.
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private companion object {
        const val MAX_LINES = 6
    }
}
