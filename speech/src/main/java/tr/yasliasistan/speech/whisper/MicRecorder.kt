package tr.yasliasistan.speech.whisper

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.audiofx.AcousticEchoCanceler
import android.os.Process
import tr.yasliasistan.core.log.SafeLog

/**
 * Reads the microphone (16 kHz mono PCM16) on its own thread, in 30 ms frames, for one turn.
 * Audio only passes through memory to [Callbacks.onFrame]; nothing is stored here (NFG-49).
 * The microphone is released as soon as [requestStop] is seen or the thread ends.
 */
internal class MicRecorder(
    private val callbacks: Callbacks,
    /**
     * G-16: the assistant is speaking. Use the voice-call source (the platform applies echo
     * cancellation to it) and attach [AcousticEchoCanceler] when the phone has one. Best effort:
     * if the effect is missing or fails, recording goes on and the stricter detector still applies.
     */
    private val echoCancel: Boolean = false,
) {

    interface Callbacks {
        /** The microphone is open (recorder thread). */
        fun onStarted()

        /** One frame of audio (recorder thread). The array is reused: copy what you keep. */
        fun onFrame(frame: ShortArray, length: Int)

        /** The microphone is closed; [error] is non-null if it could not be opened or failed (recorder thread). */
        fun onClosed(error: MicError?)
    }

    enum class MicError { PERMISSION, UNAVAILABLE }

    @Volatile
    private var stopRequested = false
    private var thread: Thread? = null

    fun start() {
        check(thread == null) { "MicRecorder is single-use" }
        thread = Thread(::run, "whisper-mic").also { it.start() }
    }

    fun requestStop() {
        stopRequested = true
    }

    @SuppressLint("MissingPermission") // checked by the engine; SecurityException handled below
    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val minBuffer = AudioRecord.getMinBufferSize(RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) {
            callbacks.onClosed(MicError.UNAVAILABLE)
            return
        }
        val record = try {
            val source = if (echoCancel) SOURCE_VOICE_COMMUNICATION else SOURCE_VOICE_RECOGNITION
            AudioRecord(source, RATE, CHANNEL, ENCODING, maxOf(minBuffer, FRAME * 2 * 4))
        } catch (_: SecurityException) {
            callbacks.onClosed(MicError.PERMISSION)
            return
        } catch (_: IllegalArgumentException) {
            callbacks.onClosed(MicError.UNAVAILABLE)
            return
        }
        var error: MicError? = null
        var canceler: AcousticEchoCanceler? = null
        try {
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                error = MicError.UNAVAILABLE
                return
            }
            if (echoCancel) canceler = attachEchoCanceler(record.audioSessionId)
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                error = MicError.UNAVAILABLE // e.g. another app holds the microphone
                return
            }
            callbacks.onStarted()
            val frame = ShortArray(FRAME)
            while (!stopRequested) {
                val n = record.read(frame, 0, FRAME)
                if (n < 0) {
                    error = MicError.UNAVAILABLE
                    break
                }
                if (n > 0) callbacks.onFrame(frame, n)
            }
            frame.fill(0)
        } catch (_: SecurityException) {
            error = MicError.PERMISSION
        } catch (_: IllegalStateException) {
            error = MicError.UNAVAILABLE
        } finally {
            runCatching { canceler?.release() }
            runCatching { record.stop() }
            record.release()
            callbacks.onClosed(error)
        }
    }

    private fun attachEchoCanceler(sessionId: Int): AcousticEchoCanceler? = runCatching {
        if (!AcousticEchoCanceler.isAvailable()) return@runCatching null
        AcousticEchoCanceler.create(sessionId)?.also { it.enabled = true }
    }.getOrNull().also { SafeLog.event(TAG, "echo canceler=${it != null}") }

    companion object {
        const val RATE = WavAudio.WHISPER_RATE
        const val FRAME = RATE * 30 / 1000 // 30 ms
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        // AudioSource.VOICE_RECOGNITION (tuned for speech, no file recorder class is used here;
        // OfflineOnlyGuardTest forbids that class name in :speech).
        private const val SOURCE_VOICE_RECOGNITION = 6

        // AudioSource.VOICE_COMMUNICATION: echo-cancelled on most phones (G-16 barge-in).
        private const val SOURCE_VOICE_COMMUNICATION = 7
        private const val TAG = "WhisperMic"
    }
}
