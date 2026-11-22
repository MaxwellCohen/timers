package expo.modules.timerengine

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms everything when the system has dropped our alarms or moved the ground under them:
 * after a reboot, after an app update, when the wall clock is set (end times are wall-clock,
 * and widget countdowns need a new base), and when the exact-alarm permission is granted on
 * Android 12. Timers that ran out in the meantime are finished and announced by the same pass.
 */
class TimerBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action
    if (action !in ACTIONS) return
    val app = context.applicationContext
    finishOnWorker(action.orEmpty()) { TimerEngine.rescheduleAll(app) }
  }

  private companion object {
    val ACTIONS = setOf(
      Intent.ACTION_BOOT_COMPLETED,
      Intent.ACTION_MY_PACKAGE_REPLACED,
      Intent.ACTION_TIME_CHANGED,
      AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
    )
  }
}
