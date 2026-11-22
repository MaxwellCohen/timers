package expo.modules.timerengine

import kotlin.math.roundToInt

/** The user's choices from the app's settings screen. The `Preferences` type of index.ts. */
data class TimerPreferences(
  /** Keep ringing until the alert is handled, instead of playing the sound once. */
  val repeatSound: Boolean = true,
  /** Vibrate while ringing. */
  val vibrate: Boolean = true,
  /** Keep the screen on while the app is open and a timer is running. Applied by JS. */
  val keepScreenOn: Boolean = false,
  /** Quick-start buttons in the app, whole minutes. Always [APP_PRESET_COUNT] values. Used by JS only. */
  val appPresets: List<Int> = DEFAULT_APP_PRESETS,
  /** Quick-start buttons on the widgets, whole minutes. Always [WIDGET_PRESET_COUNT] values; the 4x2 widget shows the first three. */
  val widgetPresets: List<Int> = DEFAULT_WIDGET_PRESETS
) {
  /**
   * These preferences with the valid keys of [patch] applied. [patch] comes straight from JS:
   * unknown keys, values of the wrong type and preset lists of the wrong shape are ignored.
   */
  fun patched(patch: Map<String, Any?>): TimerPreferences = copy(
    repeatSound = patch["repeatSound"] as? Boolean ?: repeatSound,
    vibrate = patch["vibrate"] as? Boolean ?: vibrate,
    keepScreenOn = patch["keepScreenOn"] as? Boolean ?: keepScreenOn,
    appPresets = presetsFromJs(patch["appPresets"], APP_PRESET_COUNT) ?: appPresets,
    widgetPresets = presetsFromJs(patch["widgetPresets"], WIDGET_PRESET_COUNT) ?: widgetPresets
  )

  fun toMap(): Map<String, Any> = mapOf(
    "repeatSound" to repeatSound,
    "vibrate" to vibrate,
    "keepScreenOn" to keepScreenOn,
    "appPresets" to appPresets,
    "widgetPresets" to widgetPresets
  )

  companion object {
    const val APP_PRESET_COUNT = 3
    const val WIDGET_PRESET_COUNT = 4
    const val MIN_PRESET_MINUTES = 1
    const val MAX_PRESET_MINUTES = 999
    val DEFAULT_APP_PRESETS = listOf(1, 10, 30)
    val DEFAULT_WIDGET_PRESETS = listOf(1, 5, 10, 15)

    /** A list of exactly [count] finite numbers, rounded to whole minutes and clamped. Null for anything else. */
    private fun presetsFromJs(value: Any?, count: Int): List<Int>? {
      if (value !is List<*> || value.size != count) return null
      return value.map { item ->
        val minutes = (item as? Number)?.toDouble()
        if (minutes == null || !minutes.isFinite()) return null
        minutes.coerceIn(MIN_PRESET_MINUTES.toDouble(), MAX_PRESET_MINUTES.toDouble()).roundToInt()
      }
    }

    /** The stored form, "1,5,10,15". */
    internal fun encodePresets(presets: List<Int>): String = presets.joinToString(",")

    /** Null unless [stored] is exactly [count] whole numbers in range. */
    internal fun decodePresets(stored: String?, count: Int): List<Int>? {
      val presets = stored?.split(",")?.map { it.toIntOrNull() ?: return null } ?: return null
      return presets.takeIf { it.size == count && it.all { m -> m in MIN_PRESET_MINUTES..MAX_PRESET_MINUTES } }
    }
  }
}
