package tr.yasliasistan.core.screen

/**
 * Decides which windows the reader skips (NFG-40, privacy).
 *
 * - The assistant's own windows: by window id (OwnOverlayWindows), by the overlay title marker,
 *   or because the window's root belongs to the assistant's package (bubble, "Dur", typing screen).
 * - The keyboard: its suggestion strip can show words the user is typing.
 * - Split-screen divider and magnifier: no content.
 */
class OwnWindowFilter(
    private val ownPackage: String,
    private val ownWindowIds: Set<Int>,
    private val overlayTitle: String,
) {
    /** Cheap check before the root node is fetched (no package known yet). */
    fun skipBeforeRoot(id: Int, kind: WindowKind, title: String?): Boolean =
        id in ownWindowIds ||
            kind == WindowKind.INPUT_METHOD ||
            kind == WindowKind.SPLIT_DIVIDER ||
            kind == WindowKind.MAGNIFICATION ||
            (kind == WindowKind.ACCESSIBILITY_OVERLAY && title == overlayTitle)

    fun skip(window: RawWindow): Boolean =
        skipBeforeRoot(window.id, window.kind, window.title) || window.packageName == ownPackage
}
