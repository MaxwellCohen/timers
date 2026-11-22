package expo.modules.timerengine

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.IntentCompat

/** What a finished timer plays. Chosen by the user with Android's ringtone picker. */
internal sealed class TimerSound {
  /** The phone's default alarm sound, whichever it is when the timer rings. */
  object Default : TimerSound()

  object Silent : TimerSound()

  data class Custom(val uri: Uri) : TimerSound()

  /**
   * What to try at ring time, best first. A sound can stop being playable after it was chosen
   * (file deleted, permission gone), and a ringing timer must not go quiet for that.
   */
  fun playable(): List<Uri> = when (this) {
    Silent -> emptyList()
    Default -> FALLBACKS
    is Custom -> listOf(uri) + FALLBACKS
  }

  /** The name the picker shows for the sound that will actually play. Null for silence. */
  fun title(context: Context): String? {
    val uri = playable().firstNotNullOfOrNull { actualUri(context, it) } ?: return null
    return try {
      val ringtone = RingtoneManager.getRingtone(context, uri)
      val title = ringtone?.getTitle(context)
      ringtone?.stop() // Releases the player the lookup prepared.
      title ?: context.getString(R.string.te_sound_unknown)
    } catch (e: RuntimeException) {
      Log.w(TAG, "Could not read the name of the alert sound", e)
      context.getString(R.string.te_sound_unknown)
    }
  }

  /** The stored form: null for the default, [SILENT], or the Uri. */
  fun encode(): String? = when (this) {
    Default -> null
    Silent -> SILENT
    is Custom -> uri.toString()
  }

  /** Android's alarm-sound picker, opened on this sound. */
  fun pickerIntent(): Intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
    .putExtra(
      RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
      when (this) {
        Default -> Settings.System.DEFAULT_ALARM_ALERT_URI
        Silent -> null // The picker's way of saying "None".
        is Custom -> uri
      }
    )

  companion object {
    private const val TAG = "TimerEngine"
    private const val SILENT = "silent"
    private val FALLBACKS = listOf(Settings.System.DEFAULT_ALARM_ALERT_URI, Settings.System.DEFAULT_NOTIFICATION_URI)

    fun decode(stored: String?): TimerSound = when {
      stored.isNullOrEmpty() -> Default
      stored == SILENT -> Silent
      else -> Custom(Uri.parse(stored))
    }

    /** What the picker returned with RESULT_OK. */
    fun fromPickerResult(data: Intent?): TimerSound {
      val picked = data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
      return when {
        picked == null -> Silent
        RingtoneManager.isDefault(picked) -> Default
        else -> Custom(picked)
      }
    }

    /** Follows a "default sound" Uri to the ringtone it points at now. Null when that setting is "None". */
    private fun actualUri(context: Context, uri: Uri): Uri? = try {
      val type = RingtoneManager.getDefaultType(uri)
      if (type == -1) uri else RingtoneManager.getActualDefaultRingtoneUri(context, type)
    } catch (e: RuntimeException) {
      uri
    }
  }
}
