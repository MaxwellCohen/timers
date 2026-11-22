package expo.modules.timerengine

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

/**
 * Every Intent and PendingIntent the engine hands out. All are explicit and immutable.
 * A PendingIntent's identity ignores extras, so each one gets a data URI naming its action
 * and target (`timerengine://toggle/<id>`); that keeps them apart whatever else they carry.
 */
internal object TimerIntents {
  private const val PREFIX = "expo.modules.timerengine"
  const val ACTION_ALARM_FIRED = "$PREFIX.action.ALARM_FIRED"
  const val ACTION_TOGGLE = "$PREFIX.action.TOGGLE"
  const val ACTION_ADD_MINUTE = "$PREFIX.action.ADD_MINUTE"
  const val ACTION_REMOVE = "$PREFIX.action.REMOVE"
  const val ACTION_SILENCE = "$PREFIX.action.SILENCE"
  const val ACTION_START = "$PREFIX.action.START"
  const val ACTION_START_LAST_USED = "$PREFIX.action.START_LAST_USED"
  const val EXTRA_TIMER_ID = "$PREFIX.extra.TIMER_ID"
  const val EXTRA_END_AT = "$PREFIX.extra.END_AT"
  const val EXTRA_DURATION_MS = "$PREFIX.extra.DURATION_MS"

  private const val TAG = "TimerEngine"

  // Settings.ACTION_APP_NOTIFICATION_SETTINGS, public since API 26, same value before.
  private const val ACTION_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS"
  private const val UPDATE = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
  private const val LOOK_UP = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE

  /** Carries the end time it was set for, so a late delivery can be told from a current one. */
  fun alarm(context: Context, timer: Timer): PendingIntent =
    PendingIntent.getBroadcast(context, 0, alarmIntent(context, timer.id).putExtra(EXTRA_END_AT, timer.endAt), UPDATE)

  /** The alarm PendingIntent for [id] if one exists, without creating it. */
  fun existingAlarm(context: Context, id: String): PendingIntent? =
    PendingIntent.getBroadcast(context, 0, alarmIntent(context, id), LOOK_UP)

  fun toggle(context: Context, id: String) = timerAction(context, ACTION_TOGGLE, "toggle", id)

  fun addMinute(context: Context, id: String) = timerAction(context, ACTION_ADD_MINUTE, "add-minute", id)

  fun remove(context: Context, id: String) = timerAction(context, ACTION_REMOVE, "remove", id)

  /** "I have seen it": stops the ringing, leaves the timers as they are. */
  fun silence(context: Context): PendingIntent =
    PendingIntent.getBroadcast(context, 0, broadcast(context, ACTION_SILENCE, "silence", "all"), UPDATE)

  fun start(context: Context, durationMs: Long): PendingIntent = PendingIntent.getBroadcast(
    context,
    0,
    broadcast(context, ACTION_START, "start", durationMs.toString()).putExtra(EXTRA_DURATION_MS, durationMs),
    UPDATE
  )

  fun startLastUsed(context: Context): PendingIntent =
    PendingIntent.getBroadcast(context, 0, broadcast(context, ACTION_START_LAST_USED, "start", "last-used"), UPDATE)

  /** Opens the app's main screen. Null only if the app somehow has no launcher activity. */
  fun openApp(context: Context): PendingIntent? =
    launchIntent(context)?.let { PendingIntent.getActivity(context, 0, it, UPDATE) }

  fun launchIntent(context: Context): Intent? =
    context.packageManager.getLaunchIntentForPackage(context.packageName)
      ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  /** The phone's sound settings, where the alarm volume lives. */
  fun openSoundSettings(context: Context) {
    if (startSettings(context, Intent(Settings.ACTION_SOUND_SETTINGS))) return
    if (!startSettings(context, Intent(Settings.ACTION_SETTINGS))) Log.w(TAG, "No settings screen could be opened")
  }

  /** This app's notification settings, or its app-info page on a device that has no such screen. */
  fun openNotificationSettings(context: Context) {
    val notifications = Intent(ACTION_APP_NOTIFICATION_SETTINGS)
      .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
      // What the same screen reads on Android 7, where it exists but is not public API.
      .putExtra("app_package", context.packageName)
      .putExtra("app_uid", context.applicationInfo.uid)
    if (startSettings(context, notifications)) return
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    if (!startSettings(context, details)) Log.w(TAG, "No settings screen could be opened")
  }

  private fun startSettings(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
  } catch (e: ActivityNotFoundException) {
    false
  } catch (e: SecurityException) {
    false
  }

  private fun timerAction(context: Context, action: String, kind: String, id: String): PendingIntent =
    PendingIntent.getBroadcast(context, 0, broadcast(context, action, kind, id).putExtra(EXTRA_TIMER_ID, id), UPDATE)

  private fun alarmIntent(context: Context, id: String): Intent =
    broadcast(context, ACTION_ALARM_FIRED, "alarm", id).putExtra(EXTRA_TIMER_ID, id)

  // Foreground priority: these are either an alarm going off or the user tapping a button,
  // and must not wait in the background broadcast queue (which can take seconds after boot).
  private fun broadcast(context: Context, action: String, kind: String, key: String): Intent =
    Intent(action, Uri.Builder().scheme("timerengine").authority(kind).appendPath(key).build())
      .setClass(context, TimerActionReceiver::class.java)
      .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
}
