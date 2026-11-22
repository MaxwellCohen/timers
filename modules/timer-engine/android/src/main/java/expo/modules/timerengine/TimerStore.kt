package expo.modules.timerengine

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * What survives the process: the timers, the last-used duration, what has been done about
 * each finished timer, and the user's preferences.
 *
 * Every write is a synchronous `commit()`. Changes arrive from broadcast receivers whose
 * process may be killed as soon as they finish, and a timer that exists only in memory is a
 * timer that never rings. The file is a few hundred bytes, so the cost is a millisecond or two.
 */
/** A done timer's alert: the id of its notification, and whether its ringing has been started (or waived). */
internal data class AlertRecord(val notificationId: Int, val rung: Boolean)

internal class TimerStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

  /** In stored order: newest first. */
  fun timers(): List<Timer> {
    val raw = prefs.getString(KEY_TIMERS, null) ?: return emptyList()
    return try {
      val array = JSONArray(raw)
      (0 until array.length()).mapNotNull { Timer.fromJson(array.optJSONObject(it)) }
    } catch (e: JSONException) {
      Log.e(TAG, "Stored timers are unreadable, starting empty", e)
      emptyList()
    }
  }

  fun lastUsedMs(): Long =
    prefs.getLong(KEY_LAST_USED, TimerEngine.DEFAULT_DURATION_MS)
      .coerceIn(TimerEngine.MIN_DURATION_MS, TimerEngine.MAX_DURATION_MS)

  fun save(timers: List<Timer>, lastUsedMs: Long) {
    val array = JSONArray()
    timers.forEach { array.put(it.toJson()) }
    val written = prefs.edit()
      .putString(KEY_TIMERS, array.toString())
      .putLong(KEY_LAST_USED, lastUsedMs)
      .commit()
    if (!written) Log.e(TAG, "Could not write timers to disk")
  }

  /**
   * What the alert system knows about each done timer, by timer id. Data written by the
   * versions that only kept a set of announced ids reads as "announced and rung", so an
   * update does not ring for timers that finished before it.
   */
  fun alertRecords(): Map<String, AlertRecord> {
    val raw = read<String?>(null) { prefs.getString(KEY_ALERTS, it) }
    if (raw == null) {
      val legacy = read<Set<String>?>(null) { prefs.getStringSet(KEY_ALERTED_LEGACY, it) }.orEmpty()
      return legacy.sorted().withIndex().associate { (index, id) -> id to AlertRecord(LEGACY_FIRST_ID + index, rung = true) }
    }
    return try {
      val json = JSONObject(raw)
      json.keys().asSequence().associateWith { id ->
        val record = json.getJSONObject(id)
        AlertRecord(record.getInt("n"), record.getBoolean("rung"))
      }
    } catch (e: JSONException) {
      Log.e(TAG, "Stored alert records are unreadable, starting empty", e)
      emptyMap()
    }
  }

  fun saveAlertRecords(records: Map<String, AlertRecord>) {
    val json = JSONObject()
    for ((id, record) in records) json.put(id, JSONObject().put("n", record.notificationId).put("rung", record.rung))
    val written = prefs.edit().putString(KEY_ALERTS, json.toString()).remove(KEY_ALERTED_LEGACY).commit()
    if (!written) Log.e(TAG, "Could not write alert records to disk")
  }

  fun alertSound(): TimerSound = TimerSound.decode(read<String?>(null) { prefs.getString(KEY_ALERT_SOUND, it) })

  fun saveAlertSound(sound: TimerSound) {
    if (!prefs.edit().putString(KEY_ALERT_SOUND, sound.encode()).commit()) Log.e(TAG, "Could not write the alert sound to disk")
  }

  /** Anything missing or unusable on disk reads as its default. */
  fun preferences(): TimerPreferences {
    val defaults = TimerPreferences()
    return TimerPreferences(
      repeatSound = read(defaults.repeatSound) { prefs.getBoolean(KEY_REPEAT_SOUND, it) },
      vibrate = read(defaults.vibrate) { prefs.getBoolean(KEY_VIBRATE, it) },
      keepScreenOn = read(defaults.keepScreenOn) { prefs.getBoolean(KEY_KEEP_SCREEN_ON, it) },
      appPresets = read(defaults.appPresets) {
        TimerPreferences.decodePresets(prefs.getString(KEY_APP_PRESETS, null), TimerPreferences.APP_PRESET_COUNT) ?: it
      },
      widgetPresets = read(defaults.widgetPresets) {
        TimerPreferences.decodePresets(prefs.getString(KEY_WIDGET_PRESETS, null), TimerPreferences.WIDGET_PRESET_COUNT) ?: it
      }
    )
  }

  fun savePreferences(preferences: TimerPreferences) {
    val written = prefs.edit()
      .putBoolean(KEY_REPEAT_SOUND, preferences.repeatSound)
      .putBoolean(KEY_VIBRATE, preferences.vibrate)
      .putBoolean(KEY_KEEP_SCREEN_ON, preferences.keepScreenOn)
      .putString(KEY_APP_PRESETS, TimerPreferences.encodePresets(preferences.appPresets))
      .putString(KEY_WIDGET_PRESETS, TimerPreferences.encodePresets(preferences.widgetPresets))
      .commit()
    if (!written) Log.e(TAG, "Could not write preferences to disk")
  }

  /** SharedPreferences throws when a key holds another type than the one asked for. */
  private inline fun <T> read(default: T, get: (T) -> T): T = try {
    get(default)
  } catch (e: ClassCastException) {
    default
  }

  private companion object {
    const val TAG = "TimerEngine"
    const val FILE = "timer_engine"
    const val KEY_TIMERS = "timers"
    const val KEY_LAST_USED = "last_used_ms"
    const val KEY_ALERTS = "alerts"
    const val KEY_ALERTED_LEGACY = "alerted_ids"
    const val LEGACY_FIRST_ID = 2
    const val KEY_ALERT_SOUND = "alert_sound"
    const val KEY_REPEAT_SOUND = "pref_repeat_sound"
    const val KEY_VIBRATE = "pref_vibrate"
    const val KEY_KEEP_SCREEN_ON = "pref_keep_screen_on"
    const val KEY_APP_PRESETS = "pref_app_presets"
    const val KEY_WIDGET_PRESETS = "pref_widget_presets"
  }
}
