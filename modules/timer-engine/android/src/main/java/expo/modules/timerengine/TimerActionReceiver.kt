package expo.modules.timerengine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Runs [work] on the engine's worker thread and holds the broadcast open until it returns.
 *
 * While the broadcast is open the process keeps its receiver priority (and, for an alarm,
 * the system keeps its wake lock), so the engine's durable write and its alarm, notification
 * and widget calls are all finished before the process becomes fair game for the system.
 */
internal fun BroadcastReceiver.finishOnWorker(what: String, work: () -> Unit) {
  // Null only when onReceive is called by hand rather than by the system.
  val pending: BroadcastReceiver.PendingResult? = goAsync()
  TimerEngine.worker.execute {
    try {
      work()
    } catch (e: Exception) {
      Log.e("TimerEngine", "Handling $what failed", e)
    } finally {
      pending?.finish()
    }
  }
}

/**
 * Everything that changes a timer from outside the app's UI: the alarm going off, the
 * buttons on widgets and on the finish alert, and the alert being swiped away. Not exported; only reachable through the
 * explicit PendingIntents built by [TimerIntents].
 */
class TimerActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val app = context.applicationContext
    val action = intent.action ?: return
    val id = intent.getStringExtra(TimerIntents.EXTRA_TIMER_ID)
    finishOnWorker(action) {
      when (action) {
        TimerIntents.ACTION_ALARM_FIRED ->
          if (id != null) TimerEngine.onAlarmFired(app, id, intent.getLongExtra(TimerIntents.EXTRA_END_AT, 0))
        TimerIntents.ACTION_TOGGLE -> if (id != null) TimerEngine.toggle(app, id)
        TimerIntents.ACTION_ADD_MINUTE -> if (id != null) TimerEngine.addMinute(app, id)
        TimerIntents.ACTION_REMOVE -> if (id != null) TimerEngine.remove(app, id)
        TimerIntents.ACTION_SILENCE -> TimerEngine.silenceAll(app)
        TimerIntents.ACTION_START -> {
          val durationMs = intent.getLongExtra(TimerIntents.EXTRA_DURATION_MS, 0)
          if (durationMs > 0) TimerEngine.start(app, durationMs)
        }
        TimerIntents.ACTION_START_LAST_USED -> TimerEngine.startLastUsed(app)
      }
    }
  }
}
