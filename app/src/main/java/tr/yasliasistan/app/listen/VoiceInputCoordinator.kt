package tr.yasliasistan.app.listen

import tr.yasliasistan.core.dialog.EchoCheck
import tr.yasliasistan.core.dialog.VoiceCommand
import tr.yasliasistan.core.dialog.VoiceCommands
import tr.yasliasistan.core.log.SafeLog
import tr.yasliasistan.speech.recognition.EngineAvailability
import tr.yasliasistan.speech.recognition.FailureReason
import tr.yasliasistan.speech.recognition.ListenConfig
import tr.yasliasistan.speech.recognition.SpeechEngine
import tr.yasliasistan.speech.recognition.SpeechListener
import tr.yasliasistan.speech.recognition.SpeechOutcome

/** What the dialog session needs from voice input (G-11). Main thread only. */
interface Microphone {
    /** Opens the microphone for one turn (after permission and offline-model checks). */
    fun startTurn()

    /** "I am done talking": ends a running turn early; false when no turn is running. */
    fun finishTurn(): Boolean

    /** Closes the microphone at once ("Dur", session over). */
    fun stop()

    /**
     * Barge-in (G-16, FG-14): listens while the assistant speaks a question, inside the running
     * session only. [spokenText] is what the assistant is saying (to tell its echo from the user).
     * False when not possible; then nothing happens and the dialog works as without barge-in.
     */
    fun startBargeInTurn(spokenText: () -> String): Boolean = false

    /**
     * G-17: listens only for fixed commands ("dur", "tekrar söyle", ...) while the assistant works or
     * says an information sentence, inside the running session only. A command comes back through
     * [VoiceInputUi.onHeard]; anything else (or silence) through [VoiceInputUi.onNothingHeard].
     * False when not possible; then only the "Dur" button works meanwhile.
     */
    fun startCommandTurn(spokenText: () -> String): Boolean = false
}

/**
 * Connects the dialog session to a [SpeechEngine] (G-07, G-11). The microphone opens only when the
 * dialog asks for it inside a bubble-started session (FG-02) and only after the permission and the
 * offline Turkish model are confirmed (NFG-16/17). G-12 can pass a Whisper engine instead of the
 * system one without changing this class. Main thread only.
 *
 * Barge-in (G-16): a turn opened by [startBargeInTurn] runs while the assistant speaks. Live text that
 * [EchoCheck] judges to be the user, or the engine's own user-voice signal, cuts the speech at once
 * ([VoiceInputUi.onUserInterrupted]); the turn then continues as the user's answer. If nobody spoke,
 * the next [startTurn] drops it and opens a fresh turn, so echo never mixes into the answer.
 * It never shows notices: when it fails, it stays off silently after [MAX_BARGE_IN_FAILURES] failures,
 * and the bubble tap and "Dur" still cut the speech.
 */
class VoiceInputCoordinator(
    private val engine: SpeechEngine,
    private val micPermissionGranted: () -> Boolean,
    private val ui: VoiceInputUi,
) : Microphone {
    private var availability: EngineAvailability? = null
    private var waitingForCheck = false
    private var turnRunning = false

    // Outcomes of a dropped turn arrive with an old token and are ignored.
    private var turnToken = 0
    private var bargeIn: BargeInTurn? = null
    private var bargeInFailures = 0

    /** A turn that runs while the assistant speaks or works; [commandsOnly] for G-17 command turns. */
    private class BargeInTurn(val spokenText: () -> String, val commandsOnly: Boolean = false) {
        var interrupted = false
    }

    /** Warm-up without the microphone, so the first tap starts listening at once (NFG-23). */
    fun prepare() {
        engine.checkAvailability { availability = it }
    }

    override fun startBargeInTurn(spokenText: () -> String): Boolean {
        if (bargeIn?.commandsOnly == true) dropBargeInTurn() // a question replaces the command turn
        if (turnRunning || waitingForCheck || bargeInFailures >= MAX_BARGE_IN_FAILURES) return false
        // Only when a normal turn would open at once: no checks, notices or setup screens here.
        if (!micPermissionGranted() || availability?.canListen != true) return false
        val turn = BargeInTurn(spokenText)
        bargeIn = turn
        openMicrophone(ListenConfig(duringSpeech = true), turn)
        return true
    }

    override fun startCommandTurn(spokenText: () -> String): Boolean {
        if (turnRunning) return bargeIn?.commandsOnly == true // already listening for commands
        if (waitingForCheck || bargeInFailures >= MAX_BARGE_IN_FAILURES) return false
        if (!micPermissionGranted() || availability?.canListen != true) return false
        val turn = BargeInTurn(spokenText, commandsOnly = true)
        bargeIn = turn
        openMicrophone(ListenConfig(duringSpeech = true), turn)
        return true
    }

    override fun startTurn() {
        bargeIn?.let { if (!it.interrupted) dropBargeInTurn() }
        when {
            turnRunning || waitingForCheck -> Unit
            !micPermissionGranted() -> needsSetup(VoiceNotice.NEEDS_MIC_PERMISSION)
            availability?.canListen == true -> openMicrophone()
            else -> recheckThenStart()
        }
    }

    override fun finishTurn(): Boolean {
        if (!turnRunning || bargeIn?.interrupted == false) return false
        engine.finishListening()
        return true
    }

    /** "Dur": closes the microphone at once, whatever the state. */
    override fun stop() {
        waitingForCheck = false
        if (turnRunning) engine.cancel() else ui.showMic(MicIndicator.CLOSED)
    }

    fun release() {
        waitingForCheck = false
        engine.release()
        turnRunning = false
        bargeIn = null
    }

    /** Nobody talked over the assistant: drop that turn (its audio held only echo). */
    private fun dropBargeInTurn() {
        bargeIn = null
        turnRunning = false
        turnToken++
        engine.cancel()
        ui.showMic(MicIndicator.CLOSED)
    }

    private fun recheckThenStart() {
        waitingForCheck = true
        ui.showMic(MicIndicator.OPENING)
        engine.checkAvailability { result ->
            availability = result
            if (!waitingForCheck) return@checkAvailability // "Dur" was pressed meanwhile
            waitingForCheck = false
            if (result.canListen) {
                openMicrophone()
            } else {
                ui.showMic(MicIndicator.CLOSED)
                needsSetup(noticeFor(result))
            }
        }
    }

    private fun openMicrophone(config: ListenConfig = ListenConfig(), barge: BargeInTurn? = null) {
        turnRunning = true
        val token = ++turnToken
        ui.showMic(MicIndicator.OPENING)
        engine.startListening(
            object : SpeechListener {
                private fun current() = token == turnToken

                override fun onListening() {
                    if (!current()) return
                    if (barge != null) bargeInFailures = 0
                    ui.showMic(MicIndicator.OPEN)
                }

                // Only the moment is passed on (end-of-speech timing for metrics), never the text.
                override fun onPartialText(text: String) {
                    if (!current()) return
                    if (barge != null && barge.commandsOnly) {
                        stopAtOnce(barge, text)
                        return
                    }
                    if (barge != null && !barge.interrupted &&
                        EchoCheck.classify(text, barge.spokenText()) == EchoCheck.Verdict.USER
                    ) {
                        interrupt(barge)
                    }
                    if (barge == null || barge.interrupted) ui.onPartialHeard()
                }

                override fun onUserVoice() {
                    // In a command turn only a recognized command counts, not any voice.
                    if (current() && barge != null && !barge.commandsOnly && !barge.interrupted) interrupt(barge)
                }

                override fun onOutcome(outcome: SpeechOutcome) {
                    if (!current()) return
                    turnRunning = false
                    bargeIn = null
                    ui.showMic(MicIndicator.CLOSED)
                    when {
                        barge == null -> handle(outcome)
                        barge.commandsOnly -> handleCommandTurn(barge, outcome)
                        else -> handleBargeIn(barge, outcome)
                    }
                }
            },
            config,
        )
    }

    private fun interrupt(turn: BargeInTurn) {
        turn.interrupted = true
        SafeLog.event(TAG, "barge-in: user talked over the assistant")
        ui.onUserInterrupted()
    }

    /** "Dur" must act at once (FG-23): a live text that is "dur" ends the command turn now. */
    private fun stopAtOnce(turn: BargeInTurn, partial: String) {
        val userText = EchoCheck.userPart(partial, turn.spokenText()) ?: return
        if (VoiceCommands.match(userText) != VoiceCommand.STOP) return
        SafeLog.event(TAG, "command turn: stop heard")
        turn.interrupted = true
        dropBargeInTurn()
        ui.onHeard(userText, partialOnly = true)
    }

    /** Outcome of a command turn: only a fixed command reaches the dialog; silent on every problem. */
    private fun handleCommandTurn(turn: BargeInTurn, outcome: SpeechOutcome) {
        when (outcome) {
            is SpeechOutcome.Heard -> {
                val userText = EchoCheck.userPart(outcome.text, turn.spokenText())
                if (userText != null && VoiceCommands.match(userText) != null) {
                    SafeLog.event(TAG, "command turn: command heard")
                    ui.onHeard(userText, outcome.partialOnly)
                } else {
                    ui.onNothingHeard() // not a command (or own voice): the dialog listens again
                }
            }
            SpeechOutcome.NothingHeard -> ui.onNothingHeard()
            SpeechOutcome.Cancelled -> Unit
            is SpeechOutcome.Failed -> {
                bargeInFailures++
                if (outcome.reason == FailureReason.LANGUAGE_UNAVAILABLE || outcome.reason == FailureReason.ENGINE_UNAVAILABLE) {
                    availability = null
                }
                SafeLog.event(TAG, "command turn failed: ${outcome.reason} (count $bargeInFailures)", SafeLog.Level.WARN)
            }
        }
    }

    /** Outcome of a turn that ran while the assistant spoke; silent on every problem. */
    private fun handleBargeIn(turn: BargeInTurn, outcome: SpeechOutcome) {
        when (outcome) {
            is SpeechOutcome.Heard -> {
                val userText = EchoCheck.userPart(outcome.text, turn.spokenText())
                if (userText == null) {
                    SafeLog.event(TAG, "barge-in: own voice ignored")
                    if (turn.interrupted) ui.onNothingHeard()
                    return
                }
                if (!turn.interrupted) interrupt(turn)
                ui.onHeard(userText, outcome.partialOnly)
            }
            SpeechOutcome.NothingHeard -> if (turn.interrupted) ui.onNothingHeard()
            SpeechOutcome.Cancelled -> Unit
            is SpeechOutcome.Failed -> {
                bargeInFailures++
                if (outcome.reason == FailureReason.LANGUAGE_UNAVAILABLE || outcome.reason == FailureReason.ENGINE_UNAVAILABLE) {
                    availability = null
                }
                SafeLog.event(TAG, "barge-in failed: ${outcome.reason} (count $bargeInFailures)", SafeLog.Level.WARN)
                if (turn.interrupted) ui.onNothingHeard() // the dialog listens again normally
            }
        }
    }

    private fun handle(outcome: SpeechOutcome) {
        when (outcome) {
            is SpeechOutcome.Heard -> ui.onHeard(outcome.text, outcome.partialOnly)
            SpeechOutcome.NothingHeard -> ui.onNothingHeard()
            SpeechOutcome.Cancelled -> Unit
            is SpeechOutcome.Failed -> {
                val notice = noticeForFailure(outcome.reason)
                when (outcome.reason) {
                    FailureReason.LANGUAGE_UNAVAILABLE, FailureReason.ENGINE_UNAVAILABLE -> {
                        availability = null // check again before the next turn
                        needsSetup(notice)
                    }
                    FailureReason.MIC_PERMISSION_MISSING -> needsSetup(notice)
                    FailureReason.BUSY, FailureReason.AUDIO, FailureReason.OTHER -> ui.showNotice(notice)
                }
            }
        }
    }

    private fun needsSetup(notice: VoiceNotice) {
        ui.showNotice(notice)
        ui.openVoiceSetup()
    }

    companion object {
        /** Consecutive barge-in failures after which it stays off (this app run); taps still work. */
        const val MAX_BARGE_IN_FAILURES = 2
        private const val TAG = "VoiceInput"

        fun noticeFor(availability: EngineAvailability): VoiceNotice = when (availability) {
            EngineAvailability.LANGUAGE_NOT_INSTALLED -> VoiceNotice.LANGUAGE_PACK_MISSING
            EngineAvailability.LANGUAGE_DOWNLOADING -> VoiceNotice.LANGUAGE_DOWNLOADING
            EngineAvailability.AVAILABLE, EngineAvailability.UNKNOWN -> VoiceNotice.FAILED
            EngineAvailability.ANDROID_TOO_OLD,
            EngineAvailability.NO_ON_DEVICE_RECOGNIZER,
            EngineAvailability.LANGUAGE_NOT_SUPPORTED -> VoiceNotice.OFFLINE_NOT_SUPPORTED
        }

        fun noticeForFailure(reason: FailureReason): VoiceNotice = when (reason) {
            FailureReason.MIC_PERMISSION_MISSING -> VoiceNotice.NEEDS_MIC_PERMISSION
            FailureReason.LANGUAGE_UNAVAILABLE -> VoiceNotice.LANGUAGE_PACK_MISSING
            FailureReason.ENGINE_UNAVAILABLE -> VoiceNotice.OFFLINE_NOT_SUPPORTED
            FailureReason.BUSY -> VoiceNotice.MIC_BUSY
            FailureReason.AUDIO -> VoiceNotice.MIC_BLOCKED
            FailureReason.OTHER -> VoiceNotice.FAILED
        }
    }
}

/** Results of voice input; the dialog session implements these (G-11). */
interface VoiceInputUi {
    fun showMic(state: MicIndicator)

    /** A partial result arrived; carries no text on purpose (G-18 timing only). */
    fun onPartialHeard() = Unit

    /**
     * Usable command text; may contain personal data, never log it outside SafeLog.content.
     * [partialOnly]: no final result, only the recognizer's last partial text (NFG-18).
     */
    fun onHeard(text: String, partialOnly: Boolean = false)

    /** Silence or noise: no command (NFG-18). */
    fun onNothingHeard()

    /** G-16: the user talked while the assistant spoke; cut the speech now (FG-14). */
    fun onUserInterrupted() = Unit

    /** Listening is not possible; the user must hear and see why. */
    fun showNotice(notice: VoiceNotice)

    /** Opens the screen that explains and fixes permission / language pack problems. */
    fun openVoiceSetup()
}

enum class MicIndicator { OPENING, OPEN, CLOSED }

/** Short user messages; texts in strings.xml (voice_notice_*). */
enum class VoiceNotice {
    NEEDS_MIC_PERMISSION,
    LANGUAGE_PACK_MISSING,
    LANGUAGE_DOWNLOADING,
    OFFLINE_NOT_SUPPORTED,
    NOTHING_HEARD,
    MIC_BUSY,
    MIC_BLOCKED,
    FAILED,
}
