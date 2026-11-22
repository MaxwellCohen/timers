package expo.modules.timerengine

import android.app.Notification
import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * What happens when a timer finishes: an alert in the notification shade (title, "Timer
 * finished", Dismiss, +1 min) and ringing by [TimerRingService].
 *
 * The notification itself is silent. The sound is ours to play, which is the only way to be
 * heard with the ringer muted; see [TimerRingService].
 *
 * Per done timer an [AlertRecord] is kept on disk: that its alert has been posted (so nothing
 * posts it twice) and whether its ringing has started (so nothing rings twice, not after a
 * later unrelated change, a process restart or a reboot).
 */
internal object TimerAlerts {
  private const val TAG = "TimerEngine"

  // The channel of the first versions played the sound itself. A channel's sound cannot be
  // changed after creation, so the silent one is a new channel and the old one is removed.
  private const val CHANNEL_ID = "timer_finished"
  private const val LEGACY_CHANNEL_ID = "timer_done"

  /** For the ring service when it must show something and has no timer left to show. */
  const val PLACEHOLDER_NOTIFICATION_ID = 1
  private const val FIRST_ALERT_NOTIFICATION_ID = 2

  /**
   * Brings alerts in line with [timers]: posts the alert of timers that have just become done,
   * starts their ringing, and takes both away from timers that no longer are (removed or
   * restarted).
   *
   * Ringing needs a foreground service, which Android only lets a background app start from
   * an exact alarm (and at boot or after an update). When that is refused the timer keeps
   * its alert but stays un-rung, and the next sync tries again: in practice that is its own
   * alarm arriving a moment later, when something else noticed the end of the timer first.
   *
   * A timer is marked as rung only after ringing was accepted. If the process dies in
   * between, the next sync rings rather than never.
   *
   * With [quiet] nothing rings: the user is looking at the app, so whatever is finished,
   * including what this very sync finds finished, counts as seen. Alerts are still posted.
   */
  fun sync(context: Context, store: TimerStore, timers: List<Timer>, quiet: Boolean = false) {
    val done = timers.filter { it.state == TimerState.DONE }
    val before = store.alertRecords()
    if (done.isEmpty() && before.isEmpty()) return

    val manager = NotificationManagerCompat.from(context)
    val doneIds = done.mapTo(HashSet()) { it.id }
    // Ignored by Android for the one alert the ring service is in the foreground with; the
    // service removes that one itself when retain() below tells it the timer is gone.
    for ((id, record) in before) if (id !in doneIds) manager.cancel(record.notificationId)

    var records = LinkedHashMap<String, AlertRecord>()
    for (timer in done) {
      records[timer.id] = before[timer.id]
        ?: AlertRecord(notificationId = freeNotificationId(before, records), rung = false)
    }
    // On disk before anything is posted or started: the service reads the ids from there.
    if (records != before) store.saveAlertRecords(records)

    val fresh = done.filter { it.id !in before }
    if (fresh.isNotEmpty()) {
      // Without the notification permission the timer still rings; there is just no alert.
      if (manager.areNotificationsEnabled()) {
        ensureChannel(context)
        for (timer in fresh) post(context, manager, records.getValue(timer.id).notificationId, timer)
      } else {
        Log.w(TAG, "Notifications are off; ${fresh.size} finished timer(s) have no alert")
      }
    }

    val unrung = records.filterValues { !it.rung }.keys
    if (unrung.isNotEmpty() && (quiet || TimerRingService.ring(context, unrung))) {
      records = records.mapValuesTo(LinkedHashMap()) { it.value.copy(rung = true) }
      store.saveAlertRecords(records)
    }
    if (quiet) TimerRingService.silence() else TimerRingService.retain(doneIds)
  }

  /**
   * The user has seen the finished timers (opened the app, tapped or swiped an alert).
   * Ringing stops, and timers whose ringing never got to start will not start it later.
   * The alerts stay: each goes when its timer is dismissed or restarted.
   */
  fun silence(store: TimerStore) {
    val records = store.alertRecords()
    if (records.values.any { !it.rung }) {
      store.saveAlertRecords(records.mapValues { it.value.copy(rung = true) })
    }
    TimerRingService.silence()
  }

  /** The finish alert of [timer]. Also what the ring service is in the foreground with. */
  fun notification(context: Context, timer: Timer): Notification =
    NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.te_ic_notification)
      .setColor(ContextCompat.getColor(context, R.color.te_accent))
      .setContentTitle(timer.label)
      .setContentText(context.getString(R.string.te_timer_finished))
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setWhen(timer.endAt)
      .setShowWhen(true)
      // Posted by us and again by the service when it enters the foreground: one heads-up.
      .setOnlyAlertOnce(true)
      // Android 12+ would otherwise hold back a foreground service's notification for 10 s.
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .setContentIntent(TimerIntents.openApp(context))
      .setDeleteIntent(TimerIntents.silence(context))
      .addAction(0, context.getString(R.string.te_action_dismiss), TimerIntents.remove(context, timer.id))
      .addAction(0, context.getString(R.string.te_action_add_minute), TimerIntents.addMinute(context, timer.id))
      // Before Android 8 there are no channels and these come from the notification itself.
      // A heads-up there needs a vibration pattern, so it gets one that does nothing.
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setSound(null)
      .setVibrate(longArrayOf(0))
      .build()

  fun placeholder(context: Context): Notification =
    NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.te_ic_notification)
      .setContentTitle(context.getString(R.string.te_timer_finished))
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setOnlyAlertOnce(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()

  /** Idempotent. High importance for the heads-up, no sound or vibration of its own. */
  fun ensureChannel(context: Context) {
    val manager = NotificationManagerCompat.from(context)
    manager.createNotificationChannel(
      NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
        .setName(context.getString(R.string.te_channel_name))
        .setDescription(context.getString(R.string.te_channel_description))
        .setSound(null, null)
        .setVibrationEnabled(false)
        .build()
    )
    manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
  }

  /** What will happen when a timer finishes. The `AlertStatus` type of index.ts. */
  class Status(val notificationsEnabled: Boolean, val soundName: String?, val alarmVolumeOff: Boolean) {
    fun toMap(): Map<String, Any?> = mapOf(
      "notificationsEnabled" to notificationsEnabled,
      "soundName" to soundName,
      "alarmVolumeOff" to alarmVolumeOff
    )
  }

  fun status(context: Context): Status {
    val manager = NotificationManagerCompat.from(context)
    ensureChannel(context)
    // Null before Android 8, which has no channels to block.
    val channel = manager.getNotificationChannelCompat(CHANNEL_ID)
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return Status(
      notificationsEnabled = manager.areNotificationsEnabled() &&
        (channel == null || channel.importance != NotificationManagerCompat.IMPORTANCE_NONE),
      soundName = TimerStore(context).alertSound().title(context),
      alarmVolumeOff = audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0
    )
  }

  private fun post(context: Context, manager: NotificationManagerCompat, id: Int, timer: Timer) {
    try {
      manager.notify(id, notification(context, timer))
    } catch (e: SecurityException) {
      // Permission revoked between the check and the call.
      Log.w(TAG, "Not allowed to post the finish alert", e)
    }
  }

  /** The lowest id no alert uses. Ids are per done timer and die with its record. */
  private fun freeNotificationId(vararg inUse: Map<String, AlertRecord>): Int {
    val taken = inUse.flatMapTo(HashSet()) { records -> records.values.map { it.notificationId } }
    return generateSequence(FIRST_ALERT_NOTIFICATION_ID) { it + 1 }.first { it !in taken }
  }
}
