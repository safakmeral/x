package tr.yasliasistan.speech.recognition

import android.content.Intent
import android.os.Build
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Device probe (G-07): asks the on-device recognizer whether Turkish is installed offline.
 * Never opens the microphone and needs no permission or UI. The result is printed to logcat
 * (tag "G07Probe") and is content-free.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceAvailabilityProbeTest {

    @Test
    fun reportsTurkishOfflineAvailability() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val latch = CountDownLatch(1)
        var result: EngineAvailability? = null
        Handler(Looper.getMainLooper()).post {
            OnDeviceSystemEngine(context).checkAvailability {
                result = it
                latch.countDown()
            }
        }
        latch.await(10, TimeUnit.SECONDS)
        Log.i("G07Probe", "sdk=${Build.VERSION.SDK_INT} turkishOffline=$result")
        assertNotNull("no availability answer", result)
    }

    /** Logs the raw on-device language lists (language tags only) for diagnosis. */
    @Test
    fun logsOnDeviceLanguageLists() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            Log.i("G07Probe", "onDeviceAvailable=${SpeechRecognizer.isOnDeviceRecognitionAvailable(context)}")
            val rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            rec.checkRecognitionSupport(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR"),
                context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(s: RecognitionSupport) {
                        Log.i("G07Probe", "installed=${s.installedOnDeviceLanguages}")
                        Log.i("G07Probe", "pending=${s.pendingOnDeviceLanguages}")
                        Log.i("G07Probe", "supportedOnDevice=${s.supportedOnDeviceLanguages}")
                        rec.destroy()
                        latch.countDown()
                    }

                    override fun onError(error: Int) {
                        Log.i("G07Probe", "supportError=$error")
                        rec.destroy()
                        latch.countDown()
                    }
                },
            )
        }
        latch.await(10, TimeUnit.SECONDS)
    }
}
