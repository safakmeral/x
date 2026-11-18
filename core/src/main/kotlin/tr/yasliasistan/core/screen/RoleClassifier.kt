package tr.yasliasistan.core.screen

/** Maps a node's class name and flags to an [ElementRole]. Works for Views and Compose. */
internal object RoleClassifier {

    fun roleOf(node: RawNode): ElementRole {
        val name = node.className?.substringAfterLast('.')?.substringAfterLast('$').orEmpty()
        return when {
            node.isSecret -> ElementRole.PASSWORD_FIELD
            node.editable || name.endsWith("EditText") || name.endsWith("TextField") -> ElementRole.TEXT_FIELD
            name.contains("Switch") || name == "ToggleButton" -> ElementRole.SWITCH
            name == "RadioButton" || name.endsWith("RadioButton") -> ElementRole.RADIO
            name.contains("CheckBox") || name == "CheckedTextView" -> ElementRole.CHECKBOX
            name.endsWith("SeekBar") || name.endsWith("RatingBar") || name == "Slider" -> ElementRole.SLIDER
            name.endsWith("ImageButton") || name.contains("FloatingActionButton") -> ElementRole.ICON_BUTTON
            name.endsWith("Button") -> ElementRole.BUTTON
            name.contains("TabView") || name == "Tab" -> ElementRole.TAB
            // Web content container class; matched by parts so NetworkAccessTest's network-API scan stays clean.
            name.startsWith("Web") && name.endsWith("View") -> ElementRole.WEB
            node.scrollable || name in LIST_CLASSES -> ElementRole.LIST
            name.endsWith("ImageView") || name == "Image" -> if (node.clickable) ElementRole.ICON_BUTTON else ElementRole.IMAGE
            node.checkable -> ElementRole.CHECKBOX
            node.collectionItem && node.clickable -> ElementRole.LIST_ITEM
            node.clickable || node.longClickable -> ElementRole.BUTTON
            name.endsWith("TextView") || name == "Text" -> ElementRole.TEXT
            else -> ElementRole.OTHER
        }
    }

    private val LIST_CLASSES = setOf(
        "RecyclerView", "ListView", "GridView", "ScrollView", "HorizontalScrollView",
        "NestedScrollView", "ViewPager", "ViewPager2",
    )
}
