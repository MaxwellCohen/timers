package expo.modules.timerengine

import android.content.Context
import android.util.Log
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The timers, and the only code that changes them. Needs nothing but a [Context], so it works
 * from a receiver or a widget in a process that has never loaded React.
 *
 * Every change goes through [mutate]: one lock, one read, one durable write, and then exactly
 * one pass over the things that mirror the state (alarms, finish alerts, widgets, listeners).
 * That pass runs inside the lock so two changes can never be mirrored out of order.
 *
 * Reads never write. A running timer whose end has passed is reported as done straight away,
 * and the read asks the worker to make that official (see [snapshot]).
 */
object TimerEngine {
  const val MIN_DURATION_MS = 1_000L
  const val MAX_DURATION_MS = 100 * 3_600_000L - 1_000L // 99:59:59
  const val DEFAULT_DURATION_MS = 600_000L
  const val MAX_LABEL_LENGTH = 80

  fun interface Listener {
    /** Called after every change with the new list, newest first. May run on any thread. */
    fun onTimersChanged(timers: List<Timer>)
  }

  /** Everything a widget is drawn from. */
  internal class Snapshot(val timers: List<Timer>, val lastUsedMs: Long, val widgetPresets: List<Int>)

  private const val TAG = "TimerEngine"
  private val lock = Any()
  private val listeners = CopyOnWriteArraySet<Listener>()
  private val settleQueued = AtomicBoolean(false)

  /** Serial thread for work that should not run on its caller's thread, such as a receiver's. */
  internal val worker: Executor = Executors.newSingleThreadExecutor { Thread(it, "TimerEngine") }

  fun addListener(listener: Listener) {
    listeners.add(listener)
  }

  fun removeListener(listener: Listener) {
    listeners.remove(listener)
  }

  // Reads

  /** Newest first. */
  fun timers(context: Context): List<Timer> = snapshot(context).timers

  /** Duration of the most recently started timer. */
  fun lastUsedMs(context: Context): Long = TimerStore(context).lastUsedMs()

  internal fun snapshot(context: Context): Snapshot {
    val app = context.applicationContext
    val store = TimerStore(app)
    val now = System.currentTimeMillis()
    val stored = store.timers()
    val settled = stored.map { it.settled(now) }
    // The alarm normally gets there first. If it has not (late, lost, or we are a few
    // milliseconds ahead of it), have the worker do what the alarm would have done.
    if (settled != stored) settleSoon(app)
    return Snapshot(newestFirst(settled), store.lastUsedMs(), store.preferences().widgetPresets)
  }

  fun preferences(context: Context): TimerPreferences = TimerStore(context).preferences()

  // Changes

  fun start(context: Context, durationMs: Long, label: String? = null): Timer = mutate(context) {
    startTimer(durationMs, label)
  }

  fun startLastUsed(context: Context): Timer = mutate(context) { startTimer(lastUsedMs, null) }

  fun pause(context: Context, id: String) = mutate(context) { update(id) { it.paused(now) } }

  fun resume(context: Context, id: String) = mutate(context) { update(id) { it.resumed(now) } }

  /** Pauses a running timer, resumes a paused one, leaves a done one alone. */
  fun toggle(context: Context, id: String) = mutate(context) {
    update(id) { if (it.state == TimerState.RUNNING) it.paused(now) else it.resumed(now) }
  }

  /** Adds 60s; a paused or done timer runs again. */
  fun addMinute(context: Context, id: String) = mutate(context) { update(id) { it.plusMinute(now) } }

  fun remove(context: Context, id: String) = mutate(context) {
    timers = timers.filterNot { it.id == id }
  }

  /**
   * The alarm set for [id] at [scheduledEndAt] went off. Overdue timers are settled by every
   * change, so the only case left to handle is the clock having stepped back between delivery
   * and now: the alarm for this exact schedule has been spent, so the timer is done regardless.
   * An alarm for a schedule the timer no longer has (paused or extended meanwhile) is ignored.
   *
   * The alarm is also the one moment a background app may start ringing. If something else
   * finished the timer first and was refused, this is where it gets to ring after all.
   */
  fun onAlarmFired(context: Context, id: String, scheduledEndAt: Long) = mutate(context, alarm = true) {
    update(id) { if (it.state == TimerState.RUNNING && it.endAt == scheduledEndAt) it.finished() else it }
  }

  /**
   * Brings alarms, alerts and widgets back in line with the stored timers. For the moments
   * the system has dropped our alarms or the mirrors may have drifted: boot, app update,
   * clock change, app start.
   */
  fun rescheduleAll(context: Context) = mutate(context, resync = true) {}

  /**
   * Applies [change] to the stored preferences and returns the result. Takes the same lock as
   * timer changes because the widgets show the presets: their one redraw must not interleave
   * with a redraw caused by a timer. Nothing else mirrors preferences, so nothing else runs.
   */
  fun updatePreferences(context: Context, change: (TimerPreferences) -> TimerPreferences): TimerPreferences {
    val app = context.applicationContext
    synchronized(lock) {
      val store = TimerStore(app)
      val before = store.preferences()
      val after = change(before)
      if (after == before) return before
      store.savePreferences(after)
      if (after.widgetPresets != before.widgetPresets) {
        val now = System.currentTimeMillis()
        val timers = store.timers().map { it.settled(now) }
        guarded("widgets") { TimerWidgets.update(app, Snapshot(timers, store.lastUsedMs(), after.widgetPresets)) }
      }
      return after
    }
  }

  /**
   * The user has seen the finished timers: stop ringing, and do not start ringing later for
   * any that has not rung yet. Changes no timer.
   */
  fun silenceAll(context: Context) {
    val app = context.applicationContext
    synchronized(lock) { guarded("silence") { TimerAlerts.silence(TimerStore(app)) } }
  }

  internal fun silenceAllSoon(context: Context) {
    val app = context.applicationContext
    worker.execute { silenceAll(app) }
  }

  /**
   * The app's UI is starting, so the user is about to look at the timers. A resync that
   * rings for nothing: not for what is already finished, and not for what it finds finished
   * itself (a timer whose alarm was lost to a force stop, say, possibly long ago).
   * The resync is for alarms, which a force stop drops; this is the first chance to notice.
   */
  internal fun onAppOpened(context: Context) {
    val app = context.applicationContext
    worker.execute { guarded("resync") { mutate(app, resync = true, quiet = true) {} } }
  }

  /** Trims, flattens whitespace and control characters, caps the length. Null when nothing is left. */
  internal fun cleanLabel(raw: String?): String? {
    raw ?: return null
    var label = raw.replace(WHITESPACE, " ").filterNot { it.isISOControl() }.trim()
    if (label.length > MAX_LABEL_LENGTH) {
      label = label.substring(0, MAX_LABEL_LENGTH)
      if (label.last().isHighSurrogate()) label = label.dropLast(1)
      label = label.trimEnd()
    }
    return label.ifEmpty { null }
  }

  // Internals

  private val WHITESPACE = Regex("\\s+")

  /** The state a change works on: already settled, and written back if it differs afterwards. */
  private class Draft(val now: Long, var timers: List<Timer>, var lastUsedMs: Long) {
    fun update(id: String, change: (Timer) -> Timer) {
      timers = timers.map { if (it.id == id) change(it) else it }
    }

    fun startTimer(durationMs: Long, label: String?): Timer {
      val duration = durationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
      val timer = Timer(
        id = UUID.randomUUID().toString(),
        label = cleanLabel(label) ?: TimerFormat.words(duration),
        totalMs = duration,
        state = TimerState.RUNNING,
        endAt = now + duration,
        remainingMs = duration,
        createdAt = now
      )
      timers = listOf(timer) + timers
      lastUsedMs = duration
      return timer
    }
  }

  private fun <R> mutate(
    context: Context,
    resync: Boolean = false,
    alarm: Boolean = false,
    quiet: Boolean = false,
    change: Draft.() -> R
  ): R {
    val app = context.applicationContext
    synchronized(lock) {
      val store = TimerStore(app)
      val before = store.timers()
      val lastUsedBefore = store.lastUsedMs()
      val now = System.currentTimeMillis()
      val draft = Draft(now, before.map { it.settled(now) }, lastUsedBefore)
      val result = draft.change()

      val changed = draft.timers != before || draft.lastUsedMs != lastUsedBefore
      if (changed) store.save(draft.timers, draft.lastUsedMs)
      if (changed || resync) {
        publish(app, store, before, draft, resync, quiet)
      } else if (alarm) {
        guarded("alerts") { TimerAlerts.sync(app, store, draft.timers) }
      }
      return result
    }
  }

  /** Each mirror is isolated: a launcher or notification failure must not cost us the alarm. */
  private fun publish(context: Context, store: TimerStore, before: List<Timer>, draft: Draft, resync: Boolean, quiet: Boolean) {
    val timers = draft.timers
    guarded("alarms") { TimerAlarms.sync(context, before, timers, resync) }
    guarded("alerts") { TimerAlerts.sync(context, store, timers, quiet) }
    guarded("widgets") {
      TimerWidgets.update(context, Snapshot(timers, draft.lastUsedMs, store.preferences().widgetPresets))
    }
    val newestFirst = newestFirst(timers)
    for (listener in listeners) guarded("listener") { listener.onTimersChanged(newestFirst) }
  }

  private fun settleSoon(app: Context) {
    if (!settleQueued.compareAndSet(false, true)) return
    worker.execute {
      settleQueued.set(false)
      guarded("settle") { mutate(app) {} }
    }
  }

  // Stored newest first already; the stable sort only matters for hand-edited or legacy data.
  private fun newestFirst(timers: List<Timer>) = timers.sortedByDescending { it.createdAt }

  private inline fun guarded(what: String, block: () -> Unit) {
    try {
      block()
    } catch (e: Exception) {
      Log.e(TAG, "Timer $what failed", e)
    }
  }
}
