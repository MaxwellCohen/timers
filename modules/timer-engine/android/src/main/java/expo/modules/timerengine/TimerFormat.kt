package expo.modules.timerengine

import android.text.format.DateUtils

internal object TimerFormat {
  /** A duration in words, used as the default label: "10 min", "1 h 30 min", "45 sec". */
  fun words(durationMs: Long): String {
    val seconds = durationMs / 1000
    val parts = ArrayList<String>(3)
    if (seconds >= 3600) parts += "${seconds / 3600} h"
    if (seconds % 3600 >= 60) parts += "${seconds % 3600 / 60} min"
    if (seconds % 60 > 0 || parts.isEmpty()) parts += "${seconds % 60} sec"
    return parts.joinToString(" ")
  }

  /**
   * Countdown digits, "08:42" or "1:05:00", rounded up so a timer reads 00:00 only once it is over.
   * Uses the formatter Chronometer uses, so static and ticking digits look the same.
   */
  fun clock(remainingMs: Long): String =
    DateUtils.formatElapsedTime((remainingMs.coerceAtLeast(0) + 999) / 1000)
}
