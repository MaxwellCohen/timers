package expo.modules.timerengine

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

internal class InvalidDurationException(durationMs: Double) :
  CodedException("A timer needs a positive duration in milliseconds, got $durationMs")

/** The JS face of [TimerEngine]. See index.ts for the contract. */
class TimerEngineModule : Module() {
  private val context: Context
    get() = appContext.reactContext?.applicationContext ?: throw Exceptions.ReactContextLost()

  /** The pickAlertSound call that is waiting for the picker to close. */
  @Volatile
  private var soundPick: Promise? = null

  /** What JS last asked for with setKeepScreenOn. JS decides when; this only applies it. */
  @Volatile
  private var keepScreenOn = false

  // One listener per module instance, registered by identity. A React reload creates the new
  // module before or after destroying the old one; neither order can unhook the wrong one.
  private val listener = TimerEngine.Listener { timers ->
    val now = System.currentTimeMillis()
    try {
      sendEvent(EVENT_TIMERS_CHANGED, mapOf("timers" to timers.map { it.toMap(now) }))
    } catch (e: Exception) {
      // The runtime is being torn down. JS reads the list again when it comes back.
      Log.w("TimerEngine", "Could not deliver $EVENT_TIMERS_CHANGED", e)
    }
  }

  override fun definition() = ModuleDefinition {
    Name("TimerEngine")

    Events(EVENT_TIMERS_CHANGED)

    OnCreate {
      TimerEngine.addListener(listener)
      appContext.reactContext?.let(TimerEngine::onAppOpened)
    }

    OnDestroy {
      TimerEngine.removeListener(listener)
      // JS asked for the screen to stay on; with JS gone nobody would ever ask to release it.
      keepScreenOn = false
      applyKeepScreenOn()
    }

    Function("getTimers") {
      val now = System.currentTimeMillis()
      TimerEngine.timers(context).map { it.toMap(now) }
    }

    Function("startTimer") { durationMs: Double, label: String? ->
      if (!durationMs.isFinite() || durationMs <= 0) throw InvalidDurationException(durationMs)
      val timer = TimerEngine.start(context, durationMs.toLong(), label)
      timer.toMap(System.currentTimeMillis())
    }

    Function("pauseTimer") { id: String -> TimerEngine.pause(context, id) }

    Function("resumeTimer") { id: String -> TimerEngine.resume(context, id) }

    Function("addMinute") { id: String -> TimerEngine.addMinute(context, id) }

    Function("removeTimer") { id: String -> TimerEngine.remove(context, id) }

    // Settings

    Function("getAlertStatus") { TimerAlerts.status(context).toMap() }

    AsyncFunction("pickAlertSound") { promise: Promise -> pickAlertSound(promise) }

    OnActivityResult { _, result ->
      if (result.requestCode == REQUEST_PICK_SOUND) onAlertSoundPicked(result.resultCode, result.data)
    }

    Function("openSoundSettings") { TimerIntents.openSoundSettings(context) }

    Function("openNotificationSettings") { TimerIntents.openNotificationSettings(context) }

    Function("getPreferences") { TimerEngine.preferences(context).toMap() }

    // A partial Preferences object; see TimerPreferences.patched for what is accepted.
    Function("setPreferences") { patch: Map<String, Any?> ->
      TimerEngine.updatePreferences(context) { it.patched(patch) }.toMap()
    }

    Function("setKeepScreenOn") { on: Boolean ->
      keepScreenOn = on
      applyKeepScreenOn()
    }

    OnActivityEntersForeground {
      // The flag lives on a window. A recreated activity has a new one, so it is applied again.
      applyKeepScreenOn()
      // Coming to the app is seeing the alert. (The ring service notices this too, without
      // React; this also covers timers whose ringing could not be started.)
      appContext.reactContext?.let(TimerEngine::silenceAllSoon)
    }

    Function("getAppVersion") {
      val app = context
      app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
    }
  }

  /**
   * Opens Android's alarm-sound picker. The promise is settled by [onAlertSoundPicked] when
   * the picker closes, with the status as it then is, whether anything was picked or not.
   */
  private fun pickAlertSound(promise: Promise) {
    val activity = appContext.currentActivity ?: return promise.reject(Exceptions.MissingActivity())
    val app = context
    // A picker that never reported back (its activity was destroyed) must not leave JS waiting.
    soundPick?.resolve(TimerAlerts.status(app).toMap())
    soundPick = promise
    activity.runOnUiThread {
      try {
        activity.startActivityForResult(TimerStore(app).alertSound().pickerIntent(), REQUEST_PICK_SOUND)
      } catch (e: ActivityNotFoundException) {
        soundPick = null
        promise.reject("ERR_SOUND_PICKER_UNAVAILABLE", "This device has no alarm sound picker", e)
      }
    }
  }

  private fun onAlertSoundPicked(resultCode: Int, data: Intent?) {
    val promise = soundPick ?: return
    soundPick = null
    val app = appContext.reactContext?.applicationContext ?: return promise.reject(Exceptions.ReactContextLost())
    if (resultCode == Activity.RESULT_OK) TimerStore(app).saveAlertSound(TimerSound.fromPickerResult(data))
    promise.resolve(TimerAlerts.status(app).toMap())
  }

  /** Sets or clears FLAG_KEEP_SCREEN_ON on the app's window. Nothing to do without an activity. */
  private fun applyKeepScreenOn() {
    val on = keepScreenOn
    val activity = appContext.currentActivity ?: return
    activity.runOnUiThread {
      if (on) activity.window.addFlags(FLAG_KEEP_SCREEN_ON) else activity.window.clearFlags(FLAG_KEEP_SCREEN_ON)
    }
  }

  private companion object {
    const val EVENT_TIMERS_CHANGED = "onTimersChanged"
    const val REQUEST_PICK_SOUND = 0x7E51
  }
}
