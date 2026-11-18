package tr.yasliasistan.core.dialog

/**
 * Thread-safe driver around [DialogMachine].
 *
 * - Events are processed strictly one at a time, in arrival order.
 * - Timer effects are turned into [Scheduler] calls here and are not forwarded.
 * - Other effects go to [onEffect] outside the lock. The handler may call [send] again;
 *   such events are queued and processed after the current one (no recursion).
 * - If another thread is already processing, [send] queues the event and returns at once;
 *   that thread then processes it and calls [onEffect].
 */
class DialogSession(
    private val machine: DialogMachine,
    private val scheduler: Scheduler,
    private val onEffect: (DialogEffect) -> Unit,
) {
    /** Sees every handled event with the states around it (metrics, G-18); called before the effects. */
    fun interface TransitionObserver {
        fun onTransition(event: DialogEvent, before: DialogState, after: DialogState)
    }

    /** Optional; set it before the first [send]. */
    @Volatile
    var observer: TransitionObserver? = null

    private val lock = Any()
    private val queue = ArrayDeque<DialogEvent>()
    private var draining = false
    private var timer: Scheduler.Cancellable? = null

    val state: DialogState get() = synchronized(lock) { machine.state }

    fun send(event: DialogEvent) {
        synchronized(lock) {
            queue.addLast(event)
            if (draining) return
            draining = true
        }
        while (true) {
            val step: Step = synchronized(lock) {
                val next: DialogEvent? = queue.removeFirstOrNull()
                if (next == null) {
                    draining = false
                    return
                }
                val before = machine.state
                val effects = machine.handle(next).filter { consumeTimerEffect(it) }
                Step(next, before, machine.state, effects)
            }
            observer?.onTransition(step.event, step.before, step.after)
            step.effects.forEach(onEffect)
        }
    }

    private class Step(val event: DialogEvent, val before: DialogState, val after: DialogState, val effects: List<DialogEffect>)

    /** Handles timer effects under the lock; returns true for effects that go to the host. */
    private fun consumeTimerEffect(effect: DialogEffect): Boolean = when (effect) {
        is DialogEffect.ScheduleTimer -> {
            timer?.cancel()
            timer = scheduler.schedule(effect.delayMs) { send(DialogEvent.TimerFired(effect.token)) }
            false
        }
        DialogEffect.CancelTimer -> {
            timer?.cancel()
            timer = null
            false
        }
        else -> true
    }
}
