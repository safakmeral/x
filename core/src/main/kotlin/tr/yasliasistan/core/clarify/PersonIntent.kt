package tr.yasliasistan.core.clarify

/** Which messaging app the command named (G-37). */
internal enum class ChannelRequest {
    ANY, SMS, WHATSAPP, WHATSAPP_BUSINESS;

    val isWhatsApp get() = this == WHATSAPP || this == WHATSAPP_BUSINESS
}

/**
 * A call or message command with the words that may name the person (G-37).
 * "Ayşe'yi ara" -> CALL [ayse]; "kızıma WhatsApp'tan mesaj gönder" -> MESSAGE [kizima], WHATSAPP.
 */
internal class PersonIntent(
    val kind: Kind,
    val personTokens: List<String>,
    val channel: ChannelRequest,
    /** "Ben yapayım": show, do not press (demo, photo only). */
    val guide: Boolean = false,
) {

    enum class Kind { CALL, MESSAGE, PHOTO }

    override fun toString() = "PersonIntent($kind, ${personTokens.size} words, $channel)"

    companion object {
        /** Marker: a call/message the rules do not handle (video call, call through WhatsApp, mixed verbs). */
        val UNSUPPORTED = PersonIntent(Kind.CALL, emptyList(), ChannelRequest.ANY)

        /** null when the command is neither a call nor a message. */
        fun parse(tokens: List<String>): PersonIntent? {
            val call = tokens.any(CommandWords::isCallVerb)
            val messageVerb = tokens.any(CommandWords::isMessageVerb)
            val whatsApp = tokens.any(CommandWords::isWhatsApp)
            val messageWord = tokens.any { CommandWords.isMessageNoun(it) || CommandWords.isSms(it) } || whatsApp
            val message = messageVerb && messageWord
            val photo = (messageVerb || tokens.any(CommandWords::isSendStem)) && tokens.any(CommandWords::isPhotoNoun)
            return when {
                photo && call -> UNSUPPORTED
                // Demo (18.11.2026): photos go through WhatsApp only.
                photo -> PersonIntent(
                    Kind.PHOTO,
                    personWords(tokens).filterNot { CommandWords.isSendStem(it) || CommandWords.isGuideWord(it) || it == "ben" },
                    ChannelRequest.WHATSAPP,
                    guide = tokens.any(CommandWords::isGuideWord),
                )
                call && message -> UNSUPPORTED
                call && tokens.any(CommandWords::isAppCallWord) -> UNSUPPORTED
                call -> PersonIntent(Kind.CALL, personWords(tokens), ChannelRequest.ANY)
                message -> PersonIntent(Kind.MESSAGE, personWords(tokens), channelOf(tokens))
                else -> null
            }
        }

        /** Tokens left after verbs, message/app words and fillers: the person's name (maybe empty). */
        fun personWords(tokens: List<String>): List<String> = tokens.filterNot {
            CommandWords.isCallVerb(it) || CommandWords.isMessageVerb(it) || CommandWords.isMessageNoun(it) ||
                CommandWords.isWhatsApp(it) || CommandWords.isBusiness(it) || CommandWords.isSms(it) ||
                CommandWords.isFiller(it) || CommandWords.isPhotoNoun(it) || CommandWords.isPhotoFiller(it)
        }

        private fun channelOf(tokens: List<String>): ChannelRequest = when {
            tokens.any(CommandWords::isWhatsApp) ->
                if (tokens.any(CommandWords::isBusiness)) ChannelRequest.WHATSAPP_BUSINESS else ChannelRequest.WHATSAPP
            tokens.any(CommandWords::isSms) || "kisa" in tokens -> ChannelRequest.SMS
            else -> ChannelRequest.ANY
        }
    }
}
