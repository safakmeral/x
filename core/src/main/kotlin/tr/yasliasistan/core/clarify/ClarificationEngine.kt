package tr.yasliasistan.core.clarify

import tr.yasliasistan.core.contacts.ContactChoice
import tr.yasliasistan.core.contacts.ContactFinder
import tr.yasliasistan.core.contacts.ContactLookup
import tr.yasliasistan.core.contacts.ContactMatch
import tr.yasliasistan.core.contacts.ContactWords
import tr.yasliasistan.core.decision.Action
import tr.yasliasistan.core.decision.Command
import tr.yasliasistan.core.decision.Decision
import tr.yasliasistan.core.decision.InstalledAppsSource
import tr.yasliasistan.core.decision.MessageChannel
import tr.yasliasistan.core.decision.rules.OpenAppRule
import tr.yasliasistan.core.dialog.ConfirmationParser
import tr.yasliasistan.core.masking.ContactName

/**
 * Rule-based clarification (PLAN G-37; FG-06, FG-100..FG-102, DEG-31). Fills the slots of a
 * known command with on-device data (address book, launcher apps) and answers with one of:
 * - [Result.Resolved] with [Decision.Act]: everything is known; no question at all,
 * - [Result.Resolved] with [Decision.Ask]: exactly one question, only when the device data leaves a
 *   real choice or a slot is truly missing (a single contact match is never asked about),
 * - [Result.Resolved] with [Decision.Say]: a final sentence (app not installed, still not found),
 * - [Result.Unresolved]: the rules cannot handle it; the next layer (recipes, language model G-38) decides.
 *
 * The answer to the question never produces a second question: a still-open slot either takes a
 * safe default that the readback names (message app -> Mesajlar) or ends with [ClarifyPhrases.stillNotFound].
 * Rules never send anything off the device; contact data stays in memory (NFG-46).
 */
class ClarificationEngine(
    private val contacts: ContactFinder,
    private val apps: InstalledAppsSource,
    private val phrases: ClarifyPhrases,
) {
    sealed interface Result {
        data class Resolved(val decision: Decision) : Result

        /** "Çözemedim": hand the command to the next layer. */
        data object Unresolved : Result
    }

    fun clarify(command: Command): Result {
        val tokens = command.tokens
        when (val person = PersonIntent.parse(tokens)) {
            null -> Unit
            PersonIntent.UNSUPPORTED -> return Result.Unresolved
            else -> return forPerson(person)
        }
        return SettingsIntents.decide(tokens, apps, phrases)?.let(Result::Resolved) ?: Result.Unresolved
    }

    // --- Call / message ---

    private fun forPerson(intent: PersonIntent): Result {
        if (!contacts.hasPermission()) return Result.Resolved(askPermission())
        if (intent.kind != PersonIntent.Kind.CALL && intent.channel.isWhatsApp && whatsApps().isEmpty()) {
            return Result.Resolved(Decision.Say(phrases.appNotInstalled(WHATSAPP_LABEL)))
        }
        if (intent.personTokens.isEmpty()) return Result.Resolved(askWhom(intent))
        if (hasExtraWords(intent.personTokens)) return Result.Unresolved // e.g. a message text: language model
        val match = when (val lookup = contacts.find(intent.personTokens.joinToString(" "))) {
            ContactLookup.NoPermission -> return Result.Resolved(askPermission())
            is ContactLookup.Matched -> lookup.match
        }
        val decision = when (match) {
            is ContactMatch.One -> act(intent, match.contact, match.exact, questionLeft = true)
            is ContactMatch.Several -> Decision.Ask(
                phrases.chooseContact(match.options.map { it.displayName }),
                { answerPick(it, intent, match.options) },
                match.options.size,
            )
            is ContactMatch.TooMany -> Decision.Ask(
                phrases.tooManyContacts(match.count),
                { answerName(it, intent, intent.personTokens) },
                match.count,
            )
            ContactMatch.None -> Decision.Ask(phrases.contactNotFound(), { answerName(it, intent, emptyList()) }, 0)
        }
        return Result.Resolved(decision)
    }

    /** Words that match no saved name and are no kinship word, next to words that do: not a bare name. */
    private fun hasExtraWords(tokens: List<String>): Boolean {
        val unknown = tokens.count { token ->
            !ContactWords.isKinship(token) &&
                (contacts.find(token) as? ContactLookup.Matched)?.match == ContactMatch.None
        }
        return unknown in 1 until tokens.size
    }

    private fun askWhom(intent: PersonIntent): Decision {
        val text = when (intent.kind) {
            PersonIntent.Kind.CALL -> phrases.askWhomToCall()
            PersonIntent.Kind.MESSAGE -> phrases.askWhomToMessage()
            PersonIntent.Kind.PHOTO -> phrases.askWhomToSendPhoto()
        }
        return Decision.Ask(text, { answerName(it, intent, emptyList()) }, 0)
    }

    private fun askPermission() = Decision.Act(
        Action.RequestContactsPermission,
        certain = true,
        readback = phrases.confirmContactsPermission(),
        announcement = phrases.openingContactsPermission(),
    )

    private fun act(intent: PersonIntent, contact: ContactName, exact: Boolean, questionLeft: Boolean): Decision {
        val name = contact.displayName
        if (intent.kind == PersonIntent.Kind.CALL) {
            return Decision.Act(
                Action.CallContact(contact),
                certain = exact,
                readback = if (exact) phrases.confirmCall(name) else phrases.confirmCallInexact(name),
                announcement = phrases.callingAnnouncement(name),
            )
        }
        if (intent.kind == PersonIntent.Kind.PHOTO) {
            val app = whatsApps().firstOrNull() ?: return Decision.Say(phrases.appNotInstalled(WHATSAPP_LABEL))
            return Decision.Act(
                Action.SendPhoto(contact, app, guide = intent.guide),
                certain = exact,
                readback = when {
                    intent.guide -> phrases.confirmPhotoGuide(name)
                    exact -> phrases.confirmPhoto(name)
                    else -> phrases.confirmPhotoInexact(name)
                },
                announcement = phrases.photoAnnouncement(name),
            )
        }
        val options = channelOptions(intent.channel)
        if (options.size > 1 && questionLeft) {
            return Decision.Ask(
                phrases.chooseMessageApp(options.map(::labelOf)),
                { answerChannel(it, contact, exact, options) },
                options.size,
            )
        }
        // One option, or the question was already used: the phone's own messaging app (named in the readback).
        return message(contact, exact, options.singleOrNull() ?: MessageChannel.Sms)
    }

    private fun message(contact: ContactName, exact: Boolean, channel: MessageChannel): Decision {
        val name = contact.displayName
        val app = labelOf(channel)
        return Decision.Act(
            Action.MessageContact(contact, channel),
            certain = exact,
            readback = if (exact) phrases.confirmMessage(name, app) else phrases.confirmMessageInexact(name, app),
            announcement = phrases.messageAnnouncement(name),
        )
    }

    private fun whatsApps() = apps.launchableApps()
        .filter { it.packageName == WHATSAPP_PACKAGE || it.packageName == WHATSAPP_BUSINESS_PACKAGE }
        .distinctBy { it.packageName }
        .sortedBy { if (it.packageName == WHATSAPP_PACKAGE) 0 else 1 }

    private fun channelOptions(request: ChannelRequest): List<MessageChannel> = when (request) {
        ChannelRequest.SMS -> listOf(MessageChannel.Sms)
        ChannelRequest.WHATSAPP -> listOfNotNull(whatsApps().firstOrNull()?.let(MessageChannel::WhatsApp))
        ChannelRequest.WHATSAPP_BUSINESS -> {
            val all = whatsApps()
            listOf(MessageChannel.WhatsApp(all.firstOrNull { it.packageName == WHATSAPP_BUSINESS_PACKAGE } ?: all.first()))
        }
        ChannelRequest.ANY -> listOf(MessageChannel.Sms) + whatsApps().map(MessageChannel::WhatsApp)
    }

    private fun labelOf(channel: MessageChannel) = when (channel) {
        MessageChannel.Sms -> phrases.smsLabel()
        is MessageChannel.WhatsApp -> channel.app.label
    }

    // --- Answers to the one question (never a second question) ---

    private fun answerName(answer: Command, intent: PersonIntent, earlier: List<String>): Decision? {
        rejectedOrNew(answer)?.let { return it.decision }
        val words = earlier + PersonIntent.personWords(answer.tokens)
        if (words.isEmpty()) return Decision.Say(phrases.stillNotFound())
        return when (val lookup = contacts.find(words.joinToString(" "))) {
            ContactLookup.NoPermission -> askPermission()
            is ContactLookup.Matched -> when (val m = lookup.match) {
                is ContactMatch.One -> act(intent, m.contact, m.exact, questionLeft = false)
                else -> Decision.Say(phrases.stillNotFound())
            }
        }
    }

    private fun answerPick(answer: Command, intent: PersonIntent, options: List<ContactName>): Decision? =
        when (val r = ContactChoice.resolve(answer.raw, options)) {
            is ContactChoice.Result.Picked -> act(intent, r.contact, exact = true, questionLeft = false)
            ContactChoice.Result.Rejected -> Decision.Say(phrases.cancelled())
            ContactChoice.Result.NotAnAnswer ->
                if (isNewCommand(answer)) null else Decision.Say(phrases.stillNotFound())
        }

    private fun answerChannel(answer: Command, contact: ContactName, exact: Boolean, options: List<MessageChannel>): Decision? {
        rejectedOrNew(answer)?.let { return it.decision }
        val tokens = answer.tokens
        val whatsApps = options.filterIsInstance<MessageChannel.WhatsApp>()
        val picked = when {
            tokens.any(CommandWords::isWhatsApp) -> {
                val business = tokens.any(CommandWords::isBusiness)
                whatsApps.firstOrNull { (it.app.packageName == WHATSAPP_BUSINESS_PACKAGE) == business } ?: whatsApps.firstOrNull()
            }
            tokens.any { CommandWords.isSms(it) || CommandWords.isMessageNoun(it) || it == "normal" } -> MessageChannel.Sms
            else -> ordinal(tokens)?.let(options::getOrNull)
        } ?: return null
        return message(contact, exact, picked)
    }

    private class Early(val decision: Decision?)

    /** "Hayır" ends the task; a whole new command (with its own verb) is handled as such (null decision). */
    private fun rejectedOrNew(answer: Command): Early? = when {
        ConfirmationParser.parse(answer.raw) == ConfirmationParser.Answer.NO -> Early(Decision.Say(phrases.cancelled()))
        isNewCommand(answer) -> Early(null)
        else -> null
    }

    private fun isNewCommand(answer: Command): Boolean {
        val parsed = PersonIntent.parse(answer.tokens)
        return (parsed != null && (parsed == PersonIntent.UNSUPPORTED || parsed.personTokens.isNotEmpty())) ||
            SettingsIntents.isSettingsCommand(answer.tokens) ||
            answer.tokens.any(OpenAppRule::isOpenVerb)
    }

    private fun ordinal(tokens: List<String>): Int? = tokens.firstNotNullOfOrNull { t ->
        when {
            t.startsWith("birinci") || t.startsWith("ilk") -> 0
            t.startsWith("ikinci") -> 1
            t.startsWith("ucuncu") -> 2
            else -> null
        }
    }

    internal companion object {
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
        const val WHATSAPP_LABEL = "WhatsApp"
    }
}
