package expo.modules.timerengine

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * One alarm per running timer.
 *
 * `setAlarmClock` is used rather than `setExactAndAllowWhileIdle` because it is the only alarm
 * the system never defers: Doze is left ahead of it, and it is exempt from the
 * allow-while-idle quota (roughly one alarm per app every few minutes when idle), which two
 * timers ending close together would hit. The price is the alarm icon in the status bar.
 */
internal object TimerAlarms {
  private const val TAG = "TimerEngine"

  /**
   * Arms and cancels what changed between [before] and [after]. With [resync] nothing is
   * assumed about what is currently armed: every running timer is armed again and every other
   * known timer is cancelled.
   */
  fun sync(context: Context, before: List<Timer>, after: List<Timer>, resync: Boolean) {
    val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
    val armed = before.filter { it.state == TimerState.RUNNING }.associate { it.id to it.endAt }
    val running = after.filter { it.state == TimerState.RUNNING }
    val runningIds = running.mapTo(HashSet()) { it.id }

    val candidates = if (resync) (before + after).map { it.id } else armed.keys
    for (id in candidates.toSet() - runningIds) {
      TimerIntents.existingAlarm(context, id)?.let {
        alarmManager.cancel(it)
        it.cancel()
      }
    }
    for (timer in running) {
      if (resync || armed[timer.id] != timer.endAt) arm(context, alarmManager, timer)
    }
  }

  private fun arm(context: Context, alarmManager: AlarmManager, timer: Timer) {
    // Same PendingIntent identity per timer, so this replaces any alarm it already has.
    val operation = TimerIntents.alarm(context, timer)
    if (canScheduleExact(alarmManager)) {
      try {
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(timer.endAt, TimerIntents.openApp(context)), operation)
        return
      } catch (e: SecurityException) {
        // The permission was revoked between the check and the call.
        Log.w(TAG, "Exact alarm refused, falling back to an inexact one", e)
      }
    }
    // Only reachable on Android 12/12L after the user turned off "Alarms & reminders" for the
    // app (13+ has USE_EXACT_ALARM, which cannot be revoked). The system may deliver this
    // late, but it still wakes the device and gets through Doze. TimerBootReceiver re-arms
    // exactly when the permission comes back.
    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endAt, operation)
  }

  private fun canScheduleExact(alarmManager: AlarmManager): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
}
