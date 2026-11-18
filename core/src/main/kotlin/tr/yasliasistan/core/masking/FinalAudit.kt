package tr.yasliasistan.core.masking

/**
 * Final audit (docs/MASKELEME.md §7): every outgoing string is scanned again right before sending,
 * with the masker's own detectors; placeholders known to the task table are transparent. The most
 * important finding is returned, in [BlockReason] order; null means the request may be sent.
 * Context rules and intent mode are not used here (§7).
 */
internal class FinalAudit(
    private val dict: MaskingDictionary,
    private val contacts: ContactIndex,
) {
    private val anyBracketed = Regex("\\[[^\\[\\]]*]")
    private val wellFormed = Regex("\\[(${MaskCategory.entries.joinToString("|") { it.name }})_[1-9]\\d*]")

    fun check(texts: List<String>, table: PlaceholderTable): BlockReason? {
        var worst: BlockReason? = null
        for (text in texts) {
            val found = checkOne(text, table) ?: continue
            if (worst == null || found.ordinal < worst.ordinal) worst = found
            if (worst.ordinal == 0) break
        }
        return worst
    }

    private fun checkOne(text: String, table: PlaceholderTable): BlockReason? {
        if (text.isEmpty()) return null
        var unknownPlaceholder = false
        // A known placeholder becomes a neutral word without letters or digits ("[KISI_1]'i" -> "#'i").
        val transparent = anyBracketed.replace(text) { m ->
            if (!wellFormed.matches(m.value) || table.valueOf(m.value) == null) unknownPlaceholder = true
            "#"
        }
        if ('[' in transparent || ']' in transparent) unknownPlaceholder = true

        val ctx = MaskContext(TokenizedText(TokenizedText.preprocess(transparent)), dict)
        SecretDetector.detect(ctx)?.let { return it }

        var worst: BlockReason? = null
        fun note(r: BlockReason?) {
            if (r != null && (worst == null || r.ordinal < worst!!.ordinal)) worst = r
        }
        (StructuralDetector.detect(ctx) + AddressDetector.detect(ctx)).forEach { note(reasonOf(it)) }
        if (ContactDetector.detect(ctx, contacts).isNotEmpty()) note(BlockReason.REHBER_ADI)
        if (SensitiveTermDetector.detect(ctx).isNotEmpty()) note(BlockReason.OZEL_TERIM)
        if (unknownPlaceholder) note(BlockReason.BILINMEYEN_YER_TUTUCU)
        return worst
    }

    private fun reasonOf(s: Span): BlockReason? = when (s.category) {
        MaskCategory.EPOSTA -> BlockReason.EPOSTA
        MaskCategory.IBAN -> BlockReason.IBAN
        MaskCategory.KART -> BlockReason.KART
        MaskCategory.TCKN -> BlockReason.TCKN
        MaskCategory.TEL -> BlockReason.TEL
        MaskCategory.ADRES -> BlockReason.ADRES
        MaskCategory.SAYI -> if (s.value.any { it.isDigit() }) BlockReason.RAKAM_DIZISI else BlockReason.YAZIYLA_SAYI
        // URLs are masked as METIN by every path; §7 lists no code for them.
        MaskCategory.METIN, MaskCategory.KISI, MaskCategory.OZEL -> null
    }
}
