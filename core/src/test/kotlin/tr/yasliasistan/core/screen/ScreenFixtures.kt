package tr.yasliasistan.core.screen

import tr.yasliasistan.core.masking.MiniJson

/** Loads JSON tree fixtures from src/test/resources/screen into [RawWindow]s. */
internal object ScreenFixtures {

    const val OWN_PACKAGE = "tr.yasliasistan.app"
    const val OVERLAY_TITLE = "TelefonAsistaniOverlay"

    fun load(name: String): List<RawWindow> {
        val text = checkNotNull(javaClass.getResource("/screen/$name")) { "Missing fixture $name" }.readText()
        @Suppress("UNCHECKED_CAST")
        return (MiniJson.parse(text) as List<Map<String, Any?>>).map(::window)
    }

    fun filter(ownIds: Set<Int> = emptySet()) = OwnWindowFilter(OWN_PACKAGE, ownIds, OVERLAY_TITLE)

    private fun window(m: Map<String, Any?>) = RawWindow(
        id = (m["id"] as Long).toInt(),
        kind = WindowKind.valueOf(m["kind"] as String),
        title = m["title"] as String?,
        packageName = m["package"] as String?,
        active = m["active"] as Boolean? ?: false,
        root = (m["root"] as Map<String, Any?>?)?.let(::node),
    )

    @Suppress("UNCHECKED_CAST")
    fun node(m: Map<String, Any?>): RawNode = RawNode(
        className = m["class"] as String?,
        text = m["text"] as String?,
        description = m["desc"] as String?,
        hint = m["hint"] as String?,
        isPassword = flag(m, "password"),
        inputType = (m["inputType"] as Long?)?.toInt() ?: 0,
        dataSensitive = flag(m, "sensitive"),
        clickable = flag(m, "clickable"),
        longClickable = flag(m, "longClickable"),
        scrollable = flag(m, "scrollable"),
        editable = flag(m, "editable"),
        checkable = flag(m, "checkable"),
        checked = flag(m, "checked"),
        enabled = m["enabled"] as Boolean? ?: true,
        selected = flag(m, "selected"),
        visible = m["visible"] as Boolean? ?: true,
        heading = flag(m, "heading"),
        collectionItem = flag(m, "collectionItem"),
        bounds = (m["bounds"] as List<Long>).let { Bounds(it[0].toInt(), it[1].toInt(), it[2].toInt(), it[3].toInt()) },
        children = (m["children"] as List<Map<String, Any?>>?).orEmpty().map(::node),
    )

    private fun flag(m: Map<String, Any?>, key: String) = m[key] as Boolean? ?: false
}
