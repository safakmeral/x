package tr.yasliasistan.core.masking

import tr.yasliasistan.core.screen.ElementRole

/**
 * Which parts of a screen may be summarised for the cloud (NFG-71, V-14).
 * - [allowedPackages]: only elements of these apps are summarised; others are dropped (no name, no content).
 * - [hiddenAppNames]: names of installed apps outside the allow list. An element showing such a name
 *   (launcher icon, recent apps) is dropped, because the app list can reveal health or belief data.
 */
data class ScreenScope(
    val allowedPackages: Set<String>,
    val hiddenAppNames: Collection<String> = emptyList(),
    val uiLexicon: ScreenUiLexicon = ScreenUiLexicon.DEFAULT,
) {
    override fun toString() = "ScreenScope(allowed=${allowedPackages.size}, hidden=${hiddenAppNames.size})"
}

/** One element of the masked screen summary: id, role, masked label and the flags the model needs. */
class MaskedElement internal constructor(
    val id: Int,
    val role: ElementRole,
    /** Masked label; "" when the element has no text or is a password field. */
    val label: String,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val checkable: Boolean,
    val checked: Boolean,
    val enabled: Boolean,
    val unlabeledIcon: Boolean,
) {
    override fun toString() = "MaskedElement(id=$id, role=$role)"
}

/**
 * Screen summary that went through [MaskingSession.maskScreen]. The constructor is internal to
 * `:core`, so `:app` cannot build a summary from raw screen text (PLAN G-32).
 */
class MaskedScreen internal constructor(
    /** Package of the active allowed app, or null. */
    val packageName: String?,
    val elements: List<MaskedElement>,
    val truncated: Boolean,
) {
    override fun toString() = "MaskedScreen(elements=${elements.size})"
}

sealed interface ScreenMaskResult {
    data class Masked(val screen: MaskedScreen) : ScreenMaskResult

    /** A verification code or secret is on screen: no request may be built (FG-79). */
    data class Blocked(val reason: BlockReason) : ScreenMaskResult
}

/**
 * The only thing the cloud client may send: masked command and screen that passed the final
 * audit (docs/MASKELEME.md §7). Built only by [MaskingSession.audit].
 */
class AuditedRequest internal constructor(
    val command: MaskedText?,
    val screen: MaskedScreen?,
) {
    override fun toString() = "AuditedRequest(command=${command != null}, screen=${screen != null})"
}

sealed interface AuditResult {
    data class Passed(val request: AuditedRequest) : AuditResult

    /** "MaskingRejected" of the plan: the request is not sent; only the reason code is counted. */
    data class Blocked(val reason: BlockReason) : AuditResult
}
