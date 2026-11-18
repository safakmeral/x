package tr.yasliasistan.core.screen

/**
 * Turns raw accessibility trees into a short, numbered list of meaningful elements (PLAN G-19;
 * TK-04, TK-14, MAKALE-09 "full tree is too long").
 *
 * Rules, in reading (pre-order) order:
 * - Invisible or zero-size nodes are dropped with their subtree.
 * - Actionable nodes (click, long click, scroll, edit, check) and password fields are kept.
 * - An actionable node without its own label takes the texts of its plain descendants
 *   (a clickable list row "Ahmet, Merhaba") and those texts are not repeated as elements.
 * - Plain labeled nodes (texts, described images) are kept unless they repeat the label of the
 *   control they sit in; unlabeled layout containers and decorative images are dropped.
 * - Password and sensitive fields keep no text at all (FG-78, NFG-52).
 * - A clickable element left without any label is marked [ScreenElement.unlabeledIcon] (TK-04).
 */
class ScreenCompressor(private val limits: ScreenLimits = ScreenLimits()) {

    fun compress(windows: List<RawWindow>, filter: OwnWindowFilter): ScreenSnapshot {
        val run = Run()
        val read = ArrayList<ScreenWindow>()
        for (window in windows) {
            if (filter.skip(window)) continue
            val root = window.root ?: continue
            read += ScreenWindow(window.id, window.kind, window.packageName, window.active)
            run.visit(root, window, depth = 0, merge = null, inList = false, ownerLabel = null)
        }
        return ScreenSnapshot(read, run.elements(), run.truncated)
    }

    private inner class Run {
        val builders = ArrayList<Builder>()
        var truncated = false

        fun visit(node: RawNode, window: RawWindow, depth: Int, merge: Builder?, inList: Boolean, ownerLabel: String?) {
            if (depth > limits.maxDepth) {
                truncated = true
                return
            }
            if (!node.visible || node.bounds.isEmpty) return
            val inItem = inList || node.collectionItem
            val label = if (node.isSecret) null else ownLabel(node)
            val actionable = node.clickable || node.longClickable || node.scrollable || node.editable || node.checkable

            var childMerge = merge
            var childOwner = ownerLabel
            if (actionable || node.isSecret) {
                val builder = add(node, window, inItem, label) ?: return
                childMerge = if (!node.scrollable && label == null && !node.isSecret) builder else null
                childOwner = label?.first
            } else if (label != null && !sameText(label.first, ownerLabel)) {
                if (merge != null) {
                    merge.append(label.first)
                } else if (add(node, window, inItem, label) == null) {
                    return
                }
            }
            for (child in node.children) visit(child, window, depth + 1, childMerge, inItem, childOwner)
        }

        private fun add(node: RawNode, window: RawWindow, inItem: Boolean, label: Pair<String, LabelSource>?): Builder? {
            if (builders.size >= limits.maxElements) {
                truncated = true
                return null
            }
            return Builder(node, window, inItem, label?.first, label?.second ?: LabelSource.NONE).also { builders += it }
        }

        private fun ownLabel(node: RawNode): Pair<String, LabelSource>? =
            clean(node.text)?.let { it to LabelSource.TEXT }
                ?: clean(node.description)?.let { it to LabelSource.DESCRIPTION }
                ?: clean(node.hint)?.let { it to LabelSource.HINT }

        fun elements(): List<ScreenElement> = builders.mapIndexed { index, b -> b.build(index + 1) }
    }

    private inner class Builder(
        private val node: RawNode,
        private val window: RawWindow,
        private val inItem: Boolean,
        private var label: String?,
        private var source: LabelSource,
    ) {
        private var parts = 0

        fun append(text: String) {
            if (parts >= limits.maxMergedParts) return
            label = if (label == null) text else cut("$label, $text")
            source = LabelSource.CHILD_TEXT
            parts++
        }

        fun build(id: Int): ScreenElement {
            val secret = node.isSecret
            val clickable = node.clickable || node.longClickable
            var role = RoleClassifier.roleOf(node)
            val unlabeledIcon = label == null && !secret && clickable && role in ICON_LIKE
            if (unlabeledIcon && role in PLAIN_CLICKABLE) role = ElementRole.ICON_BUTTON
            return ScreenElement(
                id = id,
                role = role,
                label = if (secret) null else label,
                labelSource = if (secret) LabelSource.NONE else source,
                textKind = if (secret) TextKind.NONE else textKind(role),
                clickable = node.clickable,
                longClickable = node.longClickable,
                scrollable = node.scrollable,
                editable = node.editable,
                checkable = node.checkable,
                checked = node.checked,
                enabled = node.enabled,
                selected = node.selected,
                focused = node.focused,
                heading = node.heading,
                inListItem = inItem,
                secret = secret,
                unlabeledIcon = unlabeledIcon,
                bounds = node.bounds,
                windowId = window.id,
                packageName = window.packageName,
            )
        }

        private fun textKind(role: ElementRole): TextKind = when {
            label == null -> TextKind.NONE
            source == LabelSource.HINT -> TextKind.UI_LABEL
            node.editable -> TextKind.USER_INPUT
            inItem -> TextKind.LIST_CONTENT
            role in CONTROLS && !node.heading -> TextKind.UI_LABEL
            else -> TextKind.OTHER_TEXT
        }
    }

    private fun clean(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return cut(WHITESPACE.replace(text.trim(), " "))
    }

    private fun cut(text: String): String =
        if (text.length <= limits.maxLabelChars) text else text.take(limits.maxLabelChars - 1).trimEnd() + "…"

    private fun sameText(a: String, b: String?): Boolean = b != null && a.equals(b, ignoreCase = true)

    private companion object {
        val WHITESPACE = Regex("\\s+")
        /** Roles whose meaning comes only from a label; toggles and fields have a role of their own. */
        val ICON_LIKE = setOf(
            ElementRole.BUTTON, ElementRole.ICON_BUTTON, ElementRole.IMAGE, ElementRole.OTHER, ElementRole.LIST_ITEM,
        )
        val PLAIN_CLICKABLE = setOf(ElementRole.BUTTON, ElementRole.IMAGE, ElementRole.OTHER)
        val CONTROLS = setOf(
            ElementRole.BUTTON, ElementRole.ICON_BUTTON, ElementRole.CHECKBOX, ElementRole.SWITCH,
            ElementRole.RADIO, ElementRole.TAB, ElementRole.SLIDER,
        )
    }
}
