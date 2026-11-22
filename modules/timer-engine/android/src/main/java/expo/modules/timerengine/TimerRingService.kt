package expo.modules.timerengine

import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Rings for finished timers, the way a clock app does: the app plays the alarm itself, as
 * alarm audio, from a foreground service. A notification's own sound is not good enough,
 * because the system silences that with the ringer.
 *
 * The service only makes noise. Which timers should be ringing is decided elsewhere and kept
 * in [requested]; every event makes the service look at that set again ([refresh]) and ring,
 * switch its foreground notification, or stop accordingly. Its foreground notification is the
 * finish alert of one of the ringing timers.
 *
 * One ringing session covers every timer that finishes while it lasts. It ends when none of
 * them is left (dismissed or restarted), when the user has seen it (opened the app, tapped or
 * swiped an alert), when the sound has played once with "repeat" off, or after [MAX_RING_MS].
 */
class TimerRingService : Service() {
  private val handler = Handler(Looper.getMainLooper())
  private val sessionOver = Runnable { endSession() }
  private val noFocusReaction = AudioManager.OnAudioFocusChangeListener { }

  private var lastStartId = 0
  private var finished = true

  /** The notification this service is in the foreground with, and whose timer it shows. */
  private var foregroundId = 0
  private var foregroundTimerId: String? = null

  private var ringing = false
  private var player: MediaPlayer? = null
  private var soundRetried = false
  private var focusRequest: Any? = null // An AudioFocusRequest; the class is newer than our oldest Android.
  private var wakeLock: PowerManager.WakeLock? = null

  /** Coming to the app is seeing the alert. Not triggered by an activity that is already in front. */
  private val appOpened = object : Application.ActivityLifecycleCallbacks {
    override fun onActivityResumed(activity: Activity) {
      endSession()
      TimerEngine.silenceAllSoon(applicationContext)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
  }

  override fun onCreate() {
    super.onCreate()
    application.registerActivityLifecycleCallbacks(appOpened)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    lastStartId = startId
    finished = false
    instance = this
    refresh(newRequest = true)
    // If the process dies there is nothing to resume: the timers are already marked as rung.
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    application.unregisterActivityLifecycleCallbacks(appOpened)
    stopRinging()
    if (instance === this) instance = null
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  /**
   * Brings the service in line with [requested]. With [newRequest] (a start command) it must
   * enter the foreground whatever it finds, because Android demands that of every
   * startForegroundService call, and it rings from the top: a new finish is a new ring.
   */
  private fun refresh(newRequest: Boolean) {
    if (finished) return
    val store = TimerStore(this)
    val now = System.currentTimeMillis()
    val done = store.timers().map { it.settled(now) }.filter { it.state == TimerState.DONE }.associateBy { it.id }
    val records = store.alertRecords()
    // The request may be older than the user's reaction to it. Ring only what is still done.
    val primary = requestedIds().firstNotNullOfOrNull { id -> done[id]?.takeIf { id in records } }

    val id = primary?.let { records.getValue(it.id).notificationId } ?: TimerAlerts.PLACEHOLDER_NOTIFICATION_ID
    if (newRequest || id != foregroundId) {
      val notification = primary?.let { TimerAlerts.notification(this, it) } ?: TimerAlerts.placeholder(this)
      if (!enterForeground(id, notification)) return finish()
      foregroundId = id
      foregroundTimerId = primary?.id
    }
    if (primary == null) return finish()
    if (newRequest || !ringing) startRinging(store)
  }

  private fun enterForeground(id: Int, notification: Notification): Boolean = try {
    TimerAlerts.ensureChannel(this)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
    } else {
      startForeground(id, notification)
    }
    true
  } catch (e: RuntimeException) {
    // ForegroundServiceStartNotAllowedException, or a type/permission the system rejects.
    Log.e(TAG, "Could not enter the foreground to ring", e)
    false
  }

  /** The session is over for every timer in it: stop, and forget the request. */
  private fun endSession() {
    clearRequested()
    finish()
  }

  private fun finish() {
    if (finished) return
    finished = true
    stopRinging()
    // The alert of a timer that is still done stays in the shade; one whose timer is gone
    // goes with the service (while we were in the foreground nobody else could cancel it).
    val timerId = foregroundTimerId
    val now = System.currentTimeMillis()
    val stillDone = timerId != null &&
      TimerStore(this).timers().any { it.id == timerId && it.settled(now).state == TimerState.DONE }
    ServiceCompat.stopForeground(this, if (stillDone) ServiceCompat.STOP_FOREGROUND_DETACH else ServiceCompat.STOP_FOREGROUND_REMOVE)
    foregroundId = 0
    foregroundTimerId = null
    // With the start id: if another request is already on its way, this does nothing and
    // onStartCommand follows.
    stopSelf(lastStartId)
  }

  // Making noise

  private fun startRinging(store: TimerStore) {
    stopRinging()
    val preferences = store.preferences()
    ringing = true
    soundRetried = false
    holdWakeLock()
    val playing = startSound(store.alertSound().playable(), preferences.repeatSound)
    if (preferences.vibrate) startVibration()
    when {
      // Silent sound and vibration off: the alert on screen is all the user asked for.
      !playing && !preferences.vibrate -> return endSession()
      // Nothing to play once through, so "once" is a few seconds of vibration.
      !playing && !preferences.repeatSound -> handler.postDelayed(sessionOver, VIBRATE_ONCE_MS)
      else -> handler.postDelayed(sessionOver, MAX_RING_MS)
    }
  }

  private fun stopRinging() {
    handler.removeCallbacks(sessionOver)
    releasePlayer()
    abandonFocus()
    if (ringing) vibrator()?.cancel()
    wakeLock?.let { if (it.isHeld) it.release() }
    wakeLock = null
    ringing = false
  }

  /** Plays the first of [candidates] that can be played. False when none can, or there are none. */
  private fun startSound(candidates: List<Uri>, loop: Boolean): Boolean {
    for (uri in candidates) {
      val candidate = MediaPlayer()
      try {
        candidate.setAudioAttributes(ALARM_AUDIO)
        candidate.setDataSource(this, uri)
        candidate.isLooping = loop
        candidate.prepare()
        candidate.setOnCompletionListener { endSession() }
        candidate.setOnErrorListener { _, what, extra -> onSoundError(what, extra, loop) }
        // Someone on a call gets a quiet reminder in the earpiece, not a full-volume alarm.
        if (audioManager().mode != AudioManager.MODE_NORMAL) candidate.setVolume(IN_CALL_VOLUME, IN_CALL_VOLUME)
        requestFocus()
        candidate.start()
        player = candidate
        // "Once" cannot wait for the completion callback alone: most system alarm sounds
        // carry an ANDROID_LOOP tag, which makes MediaPlayer repeat them whatever isLooping
        // says, and never complete. So the length of the sound ends the session.
        if (!loop && candidate.duration > 0) handler.postDelayed(sessionOver, candidate.duration.toLong())
        return true
      } catch (e: Exception) {
        Log.w(TAG, "Cannot play $uri ($e), trying the next sound")
        candidate.release()
      }
    }
    return false
  }

  /** The player broke mid-ring (the media server died, say). One more try with the system sounds. */
  private fun onSoundError(what: Int, extra: Int, loop: Boolean): Boolean {
    Log.w(TAG, "Alarm playback failed ($what, $extra)")
    releasePlayer()
    val recovered = !soundRetried && ringing &&
      startSound(listOf(Settings.System.DEFAULT_ALARM_ALERT_URI, Settings.System.DEFAULT_NOTIFICATION_URI), loop)
    soundRetried = true
    if (!recovered) endSession()
    return true
  }

  private fun releasePlayer() {
    val current = player ?: return
    player = null
    current.setOnCompletionListener(null)
    current.setOnErrorListener(null)
    try {
      current.stop()
    } catch (e: IllegalStateException) {
      // Never started, or already in an error state.
    }
    current.release()
  }

  /**
   * Transient focus: whatever was playing pauses for the alarm and resumes after it.
   *
   * Android 15+ refuses focus to an app that is not on screen unless its foreground service
   * was started from the foreground; one started by an alarm, like this, never qualifies
   * (seen on API 35 as "Focus request DENIED", with any service type). The alarm plays
   * regardless, over whatever else is playing, so a refusal is not an error.
   */
  private fun requestFocus() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(ALARM_AUDIO)
        .setOnAudioFocusChangeListener(noFocusReaction, handler)
        .build()
      focusRequest = request
      audioManager().requestAudioFocus(request)
    } else {
      @Suppress("DEPRECATION")
      audioManager().requestAudioFocus(noFocusReaction, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
    }
  }

  private fun abandonFocus() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      (focusRequest as? AudioFocusRequest)?.let { audioManager().abandonAudioFocusRequest(it) }
      focusRequest = null
    } else {
      @Suppress("DEPRECATION")
      audioManager().abandonAudioFocus(noFocusReaction)
    }
  }

  /** Alarm usage, so the ringer mode and Do Not Disturb treat it as an alarm too. */
  private fun startVibration() {
    val vibrator = vibrator()?.takeIf { it.hasVibrator() } ?: return
    when {
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> vibrator.vibrate(
        VibrationEffect.createWaveform(VIBRATION, 0),
        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
      )
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
        @Suppress("DEPRECATION")
        vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION, 0), ALARM_AUDIO)
      }
      else -> {
        @Suppress("DEPRECATION")
        vibrator.vibrate(VIBRATION, 0, ALARM_AUDIO)
      }
    }
  }

  /** Keeps the CPU up for the session, so "once" and the ten-minute limit end on time. */
  private fun holdWakeLock() {
    val power = ContextCompat.getSystemService(this, PowerManager::class.java) ?: return
    wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "timerengine:ring").apply {
      setReferenceCounted(false)
      acquire(MAX_RING_MS + 10_000)
    }
  }

  private fun audioManager() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

  @Suppress("DEPRECATION")
  private fun vibrator() = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

  companion object {
    /** A timer nobody reacts to stops ringing by itself. */
    const val MAX_RING_MS = 10 * 60_000L

    private const val TAG = "TimerEngine"
    private const val VIBRATE_ONCE_MS = 4_000L
    private const val IN_CALL_VOLUME = 0.125f
    private val VIBRATION = longArrayOf(500, 500)
    private val ALARM_AUDIO: AudioAttributes = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_ALARM)
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
      .build()

    private val main = Handler(Looper.getMainLooper())

    /** Main thread only. */
    private var instance: TimerRingService? = null

    /** Ids of the timers that should be ringing, oldest request first. Guarded by itself. */
    private val requested = LinkedHashSet<String>()

    /**
     * Asks for [ids] to ring. False when Android does not allow a foreground service to be
     * started right now, which is the case for an app in the background unless the request
     * comes from an exact alarm (or boot, or an app update); nothing rings then.
     */
    fun ring(context: Context, ids: Collection<String>): Boolean {
      synchronized(requested) { requested += ids }
      return try {
        ContextCompat.startForegroundService(context, Intent(context, TimerRingService::class.java))
        true
      } catch (e: RuntimeException) {
        synchronized(requested) { requested -= ids.toSet() }
        Log.w(TAG, "Not allowed to start ringing now: $e")
        false
      }
    }

    /** Timers that are no longer done stop ringing; the session ends with the last of them. */
    fun retain(doneIds: Set<String>) {
      if (synchronized(requested) { requested.retainAll(doneIds) }) nudge()
    }

    /** The user has seen the alert: nothing rings any more. */
    fun silence() {
      if (clearRequested()) nudge()
    }

    private fun clearRequested(): Boolean = synchronized(requested) {
      val had = requested.isNotEmpty()
      requested.clear()
      had
    }

    private fun requestedIds(): List<String> = synchronized(requested) { requested.toList() }

    private fun nudge() {
      main.post { instance?.refresh(newRequest = false) }
    }
  }
}
