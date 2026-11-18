package tr.yasliasistan.core.masking

import tr.yasliasistan.core.log.SafeLog
import tr.yasliasistan.core.screen.ScreenSnapshot

/** Where the command text came from: speech recognizer (no reliable case) or typing. */
enum class InputSource { SPEECH, TYPED }

/** FULL: rules only. INTENT: long narration, unknown words also masked (§5.8). */
enum class MaskMode { FULL, INTENT }

sealed interface CommandMaskResult {
    /** Safe to hand to the cloud client (after the final audit of G-32). */
    data class Masked(val text: MaskedText, val mode: MaskMode) : CommandMaskResult

    /** Nothing may be sent for this request; the assistant warns on the device only. */
    data class Blocked(val reason: BlockReason) : CommandMaskResult
}

/**
 * Masking state of one task (docs/MASKELEME.md). Keeps the placeholder table in memory so the
 * same value gets the same placeholder in the command and on screen (G-32), and so the model's
 * answer can be decoded on the device. Call [clear] when the task ends.
 *
 * Not thread-safe: use it from the single task coroutine.
 */
class MaskingSession(
    private val contacts: ContactIndex = ContactIndex.EMPTY,
    lexicon: MaskingLexicon = MaskingLexicon.default(),
) {
    private val dict = MaskingDictionary(lexicon)
    internal val table = PlaceholderTable()
    private val screenMasker = ScreenTextMasker(dict, contacts, table)
    private val finalAudit = FinalAudit(dict, contacts)

    fun maskCommand(input: String, source: InputSource): CommandMaskResult {
        val ctx = MaskContext(TokenizedText(TokenizedText.preprocess(input)), dict)

        SecretDetector.detect(ctx)?.let { reason ->
            SafeLog.event(TAG, "command blocked: $reason")
            return CommandMaskResult.Blocked(reason)
        }

        val structural = StructuralDetector.detect(ctx) + AddressDetector.detect(ctx)
        val names = ContactDetector.detect(ctx, contacts)
        val dictated = DictationDetector.detect(ctx, structural + names)
        val sensitive = SensitiveTermDetector.detect(ctx)
        ctx.accept(structural + names + dictated + sensitive)

        // Length of the narration once numbers, addresses, known contacts and dictated text are
        // replaced (a span counts as one word): "kart numaram [KART_1]" is 3 words, not 18.
        val wordCount = ctx.tokens.count { !ctx.isCovered(it) } + ctx.accepted.size

        ContextNameDetector.detect(ctx, typed = source == InputSource.TYPED)

        val mode = if (wordCount > dict.settings.intentModeWordThreshold) MaskMode.INTENT else MaskMode.FULL
        if (mode == MaskMode.INTENT) IntentModeRule.apply(ctx)

        val masked = renderSpans(ctx.text, ctx.accepted, table)
        SafeLog.event(TAG, "command masked: mode=$mode ${categoryCounts(ctx.accepted.map { it.category })}")
        return CommandMaskResult.Masked(MaskedText(masked), mode)
    }

    /**
     * Builds the cloud summary of [snapshot] (docs/MASKELEME.md §6, PLAN G-32): elements of apps
     * outside [ScreenScope.allowedPackages] and elements naming hidden apps are dropped; the rest
     * keep id, role and flags, with labels masked into this task's placeholder table. A
     * verification code or secret anywhere on screen blocks the request (§5.9).
     */
    fun maskScreen(snapshot: ScreenSnapshot, scope: ScreenScope): ScreenMaskResult {
        val hidden = scope.hiddenAppNames.mapTo(HashSet()) { ScreenUiLexicon.key(it) }
        val kept = snapshot.elements.filter { e ->
            e.packageName != null && e.packageName in scope.allowedPackages &&
                (e.label == null || ScreenUiLexicon.key(e.label) !in hidden)
        }
        val parts = kept.map { e ->
            ScreenTextClassifier.parts(e, scope.uiLexicon).filterNot { ScreenUiLexicon.key(it.text) in hidden }
        }
        val labels = when (val r = maskParts(parts)) {
            is PartsResult.Block -> return ScreenMaskResult.Blocked(r.reason)
            is PartsResult.Texts -> r.texts
        }
        val elements = kept.mapIndexed { i, e ->
            MaskedElement(
                id = e.id, role = e.role, label = labels[i], clickable = e.clickable || e.longClickable,
                editable = e.editable, scrollable = e.scrollable, checkable = e.checkable, checked = e.checked,
                enabled = e.enabled, unlabeledIcon = e.unlabeledIcon,
            )
        }
        val active = snapshot.windows.firstOrNull { it.active }?.packageName?.takeIf { it in scope.allowedPackages }
        SafeLog.event(TAG, "screen masked: elements=${elements.size} dropped=${snapshot.elements.size - kept.size}")
        return ScreenMaskResult.Masked(MaskedScreen(active, elements, snapshot.truncated))
    }

    /**
     * Final audit right before sending (§7): scans the command and every screen string again.
     * Only a [AuditResult.Passed] request may reach the cloud client.
     */
    fun audit(command: MaskedText?, screen: MaskedScreen?): AuditResult {
        val texts = buildList {
            command?.let { add(it.text) }
            screen?.packageName?.let { add(it) }
            screen?.elements?.forEach { add(it.label) }
        }
        val reason = finalAudit.check(texts, table)
        if (reason != null) {
            SafeLog.event(TAG, "final audit blocked: $reason")
            return AuditResult.Blocked(reason)
        }
        return AuditResult.Passed(AuditedRequest(command, screen))
    }

    internal sealed interface PartsResult {
        data class Texts(val texts: List<String>) : PartsResult
        data class Block(val reason: BlockReason) : PartsResult
    }

    /** Masks the classified parts of each element; one text per element (parts joined back). */
    internal fun maskParts(elements: List<List<ScreenPart>>): PartsResult {
        val flat = elements.flatten()
        val outcomes = screenMasker.maskAll(flat)
        (outcomes.singleOrNull() as? ScreenTextMasker.Outcome.Block)?.let {
            SafeLog.event(TAG, "screen blocked: ${it.reason}")
            return PartsResult.Block(it.reason)
        }
        val texts = outcomes.map { (it as ScreenTextMasker.Outcome.Text) }
        SafeLog.event(TAG, "screen categories: ${categoryCounts(texts.flatMap { it.categories })}")
        var next = 0
        return PartsResult.Texts(elements.map { parts ->
            ScreenTextClassifier.join(parts.indices.map { texts[next++].value })
        })
    }

    /** Replaces placeholders in a model answer with the real values (§8); stays on the device. */
    fun unmask(response: String): UnmaskResult = ResponseDecoder.decode(response, table, dict)

    /** Ends the task: forgets every real value. */
    fun clear() = table.clear()

    private fun categoryCounts(categories: List<MaskCategory>) =
        categories.groupingBy { it }.eachCount().entries.joinToString(",") { "${it.key}=${it.value}" }

    private companion object {
        const val TAG = "Masking"
    }
}
