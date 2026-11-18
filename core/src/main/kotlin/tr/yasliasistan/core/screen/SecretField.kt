package tr.yasliasistan.core.screen

/**
 * Decides whether a field's text must never be read (FG-78, NFG-52). The app calls this BEFORE
 * reading any text of a node. Besides isPassword it also catches "visible password" and numeric
 * PIN fields (isPassword is false for those) and fields an app marks as sensitive (API 34+).
 * Input type constants are copied from android.text.InputType so this stays Android-free.
 */
object SecretField {
    private const val CLASS_MASK = 0x0000000f
    private const val VARIATION_MASK = 0x00000ff0
    private const val CLASS_TEXT = 0x00000001
    private const val CLASS_NUMBER = 0x00000002
    private const val TEXT_PASSWORD = 0x00000080
    private const val TEXT_VISIBLE_PASSWORD = 0x00000090
    private const val TEXT_WEB_PASSWORD = 0x000000e0
    private const val NUMBER_PASSWORD = 0x00000010

    fun isSecret(isPassword: Boolean, inputType: Int, dataSensitive: Boolean): Boolean =
        isPassword || dataSensitive || isPasswordInputType(inputType)

    fun isPasswordInputType(inputType: Int): Boolean {
        val variation = inputType and VARIATION_MASK
        return when (inputType and CLASS_MASK) {
            CLASS_TEXT -> variation == TEXT_PASSWORD || variation == TEXT_VISIBLE_PASSWORD || variation == TEXT_WEB_PASSWORD
            CLASS_NUMBER -> variation == NUMBER_PASSWORD
            else -> false
        }
    }
}
