package expo.modules.timerengine

import org.json.JSONObject

enum class TimerState(val key: String) {
  RUNNING("running"),
  PAUSED("paused"),
  DONE("done");

  companion object {
    fun fromKey(key: String?): TimerState? = entries.firstOrNull { it.key == key }
  }
}

/** One timer. Immutable: every change is a copy, so a list of these is a consistent snapshot. */
data class Timer(
  val id: String,
  val label: String,
  /** Full length, including any added minutes. */
  val totalMs: Long,
  val state: TimerState,
  /** Wall-clock epoch ms at which the timer ends (running) or ended (done). Stale while paused. */
  val endAt: Long,
  /** Time left when paused, 0 when done. While running use [remainingAt]. */
  val remainingMs: Long,
  val createdAt: Long
) {
  fun remainingAt(now: Long): Long = when (state) {
    TimerState.RUNNING -> (endAt - now).coerceAtLeast(0)
    TimerState.PAUSED -> remainingMs
    TimerState.DONE -> 0
  }

  /** A running timer whose end time has passed is done, whoever notices first. */
  fun settled(now: Long): Timer =
    if (state == TimerState.RUNNING && endAt <= now) finished() else this

  fun finished(): Timer = copy(state = TimerState.DONE, remainingMs = 0)

  fun paused(now: Long): Timer =
    if (state == TimerState.RUNNING) copy(state = TimerState.PAUSED, remainingMs = remainingAt(now)) else this

  fun resumed(now: Long): Timer =
    if (state == TimerState.PAUSED) copy(state = TimerState.RUNNING, endAt = now + remainingMs) else this

  /** Adds a minute and (re)starts the timer, whatever state it was in. */
  fun plusMinute(now: Long): Timer {
    val remaining = (remainingAt(now) + 60_000L).coerceAtMost(TimerEngine.MAX_DURATION_MS)
    return copy(
      state = TimerState.RUNNING,
      endAt = now + remaining,
      remainingMs = remaining,
      totalMs = maxOf(totalMs, remaining)
    )
  }

  /** The `Timer` type of index.ts: same field names, numbers as doubles. */
  fun toMap(now: Long): Map<String, Any> = mapOf(
    "id" to id,
    "label" to label,
    "totalMs" to totalMs.toDouble(),
    "state" to state.key,
    "endAt" to endAt.toDouble(),
    "remainingMs" to remainingAt(now).toDouble(),
    "createdAt" to createdAt.toDouble()
  )

  fun toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("label", label)
    .put("totalMs", totalMs)
    .put("state", state.key)
    .put("endAt", endAt)
    .put("remainingMs", remainingMs)
    .put("createdAt", createdAt)

  companion object {
    /** Null for anything that is not a usable timer, so one bad entry cannot take the list down. */
    fun fromJson(json: JSONObject?): Timer? {
      json ?: return null
      val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return null
      val state = TimerState.fromKey(json.optString("state")) ?: return null
      val totalMs = json.optLong("totalMs").coerceIn(TimerEngine.MIN_DURATION_MS, TimerEngine.MAX_DURATION_MS)
      return Timer(
        id = id,
        label = json.optString("label").ifEmpty { TimerFormat.words(totalMs) },
        totalMs = totalMs,
        state = state,
        endAt = json.optLong("endAt"),
        remainingMs = json.optLong("remainingMs").coerceIn(0, TimerEngine.MAX_DURATION_MS),
        createdAt = json.optLong("createdAt")
      )
    }
  }
}
