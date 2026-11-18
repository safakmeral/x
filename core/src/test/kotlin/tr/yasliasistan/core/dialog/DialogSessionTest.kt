package tr.yasliasistan.core.dialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import tr.yasliasistan.core.dialog.DialogEffect.Decide
import tr.yasliasistan.core.dialog.DialogEffect.Execute
import tr.yasliasistan.core.dialog.DialogEffect.Speak
import tr.yasliasistan.core.dialog.DialogEvent.BubbleTapped
import tr.yasliasistan.core.dialog.DialogEvent.Heard
import tr.yasliasistan.core.dialog.DialogEvent.SpeechFinished
import tr.yasliasistan.core.dialog.DialogEvent.StopPressed
import tr.yasliasistan.core.dialog.DialogEvent.Understood

class DialogSessionTest {

    private val scheduler = FakeScheduler()
    private val out: MutableList<DialogEffect> = Collections.synchronizedList(mutableListOf())
    private val session = DialogSession(DialogMachine(), scheduler) { out += it }

    private fun progressCount() = out.count { it is Speak && it.utterance == Utterance.Template(Phrase.PROGRESS) }

    @Test
    fun timerEffectsAreHandledInternally() {
        session.send(BubbleTapped)
        session.send(Heard("ayarları aç"))
        assertTrue(out.none { it is DialogEffect.ScheduleTimer || it is DialogEffect.CancelTimer })
        assertEquals(1, scheduler.pendingCount)
    }

    @Test
    fun progressAfterFiveSecondsThenEveryTen() {
        session.send(BubbleTapped)
        session.send(Heard("ayarları aç"))
        scheduler.advanceBy(4_999)
        assertEquals(0, progressCount())
        scheduler.advanceBy(1)
        assertEquals(1, progressCount())
        scheduler.advanceBy(10_000)
        assertEquals(2, progressCount())
    }

    @Test
    fun quickResultProducesNoProgress() {
        session.send(BubbleTapped)
        session.send(Heard("ayarları aç"))
        scheduler.advanceBy(1_000)
        session.send(Understood(out.filterIsInstance<Decide>().single().requestId, "Ayarları açayım mı?"))
        scheduler.advanceBy(60_000)
        assertEquals(0, progressCount())
    }

    @Test
    fun tenSecondsOfSilenceMeansNo() {
        session.send(BubbleTapped)
        session.send(Heard("ayarları aç"))
        session.send(Understood(out.filterIsInstance<Decide>().single().requestId, "Ayarları açayım mı?"))
        session.send(SpeechFinished(out.filterIsInstance<Speak>().last().utteranceId))

        scheduler.advanceBy(9_999)
        assertTrue(session.state is DialogState.AwaitingConfirmation)
        scheduler.advanceBy(1)
        assertTrue(session.state is DialogState.Done)
        assertTrue(out.none { it is Execute })
        assertEquals(Utterance.Template(Phrase.CANCELLED), out.filterIsInstance<Speak>().last().utterance)
    }

    @Test
    fun stopCancelsPendingTimers() {
        session.send(BubbleTapped)
        session.send(Heard("ayarları aç"))
        session.send(StopPressed)
        assertEquals(0, scheduler.pendingCount)
        assertEquals(DialogState.Idle, session.state)
    }

    @Test
    fun effectHandlerMayReenterWithoutRecursion() {
        // A handler that answers "evet" as soon as it is asked to listen during confirmation.
        lateinit var reentrant: DialogSession
        val seen = mutableListOf<DialogEffect>()
        reentrant = DialogSession(DialogMachine(), scheduler) { effect ->
            seen += effect
            if (effect == DialogEffect.StartListening && reentrant.state is DialogState.AwaitingConfirmation) {
                reentrant.send(Heard("evet"))
            }
        }
        reentrant.send(BubbleTapped)
        reentrant.send(Heard("ayarları aç"))
        reentrant.send(Understood(seen.filterIsInstance<Decide>().single().requestId, "Ayarları açayım mı?"))
        reentrant.send(SpeechFinished(seen.filterIsInstance<Speak>().last().utteranceId))
        assertTrue(reentrant.state is DialogState.Working)
        assertEquals(1, seen.count { it is Execute })
    }

    @Test
    fun concurrentEventsKeepMachineConsistent() {
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        repeat(8) { worker ->
            pool.execute {
                start.await()
                repeat(500) { i ->
                    when ((worker + i) % 4) {
                        0 -> session.send(BubbleTapped)
                        1 -> session.send(Heard("ayarları aç"))
                        2 -> session.send(StopPressed)
                        else -> session.send(DialogEvent.ConfirmTapped(true))
                    }
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS))
        session.send(StopPressed)
        assertEquals(DialogState.Idle, session.state)
        assertEquals(0, scheduler.pendingCount)
        // No confirmation question was ever read back, so nothing may have run.
        assertTrue(out.none { it is Execute })
    }
}
