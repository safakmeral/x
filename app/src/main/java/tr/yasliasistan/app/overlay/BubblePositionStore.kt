package tr.yasliasistan.app.overlay

import android.content.Context
import androidx.core.content.edit

/** Remembers where the user left the bubble (app-private storage, no personal data). */
class BubblePositionStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): StoredBubblePosition {
        val edgeName = prefs.getString(KEY_EDGE, null) ?: return StoredBubblePosition.DEFAULT
        val edge = BubbleEdge.entries.firstOrNull { it.name == edgeName }
            ?: return StoredBubblePosition.DEFAULT
        val fraction = prefs.getFloat(KEY_FRACTION, StoredBubblePosition.DEFAULT.verticalFraction)
        return StoredBubblePosition(edge, fraction.coerceIn(0f, 1f))
    }

    fun save(position: StoredBubblePosition) {
        prefs.edit {
            putString(KEY_EDGE, position.edge.name)
            putFloat(KEY_FRACTION, position.verticalFraction)
        }
    }

    private companion object {
        const val PREFS = "bubble_overlay"
        const val KEY_EDGE = "edge"
        const val KEY_FRACTION = "vertical_fraction"
    }
}
