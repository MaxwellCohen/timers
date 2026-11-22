package expo.modules.timerengine

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.util.Log
import java.util.Locale

/**
 * Handles `android.intent.action.SET_TIMER`, which is how voice assistants (and any other
 * app) ask for a timer. Has no UI of its own: it starts the timer and gets out of the way.
 *
 * Exported, so the extras are untrusted input.
 */
class SetTimerActivity : Activity() {
  private class Request(val durationMs: Long, val label: String?, val skipUi: Boolean)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val request = readRequest(intent)
    if (request != null) {
      // Done before finish(), on purpose: once this activity is gone the process may be too.
      TimerEngine.start(this, request.durationMs, request.label)
    }
    if (request == null || !request.skipUi) {
      TimerIntents.launchIntent(this)?.let(::startActivity)
    }
    finish()
  }

  /** Null when the intent does not ask for a specific, valid length. */
  private fun readRequest(intent: Intent?): Request? {
    if (intent?.action != AlarmClock.ACTION_SET_TIMER) return null
    return try {
      val seconds = intent.getIntExtra(AlarmClock.EXTRA_LENGTH, 0)
      if (seconds <= 0) return null
      Request(
        durationMs = seconds.coerceAtMost(MAX_SECONDS) * 1000L,
        label = TimerEngine.cleanLabel(intent.getStringExtra(AlarmClock.EXTRA_MESSAGE))
          ?.replaceFirstChar { it.titlecase(Locale.getDefault()) },
        skipUi = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)
      )
    } catch (e: RuntimeException) {
      // Extras that cannot be unparcelled (a hostile or broken caller).
      Log.w("TimerEngine", "Ignoring malformed SET_TIMER extras", e)
      null
    }
  }

  private companion object {
    /** The platform contract for EXTRA_LENGTH is 1 to 86400 seconds. */
    const val MAX_SECONDS = 86_400
  }
}
