package expo.modules.timerengine

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews

/**
 * Draws the four home-screen widgets. Pure rendering: it is handed a snapshot (timers,
 * last-used duration, presets) and turns it into RemoteViews; it never changes anything.
 *
 * Every update is a complete one (no partial updates), so each render sets every view that
 * can differ between states.
 */
internal object TimerWidgets {
  enum class Kind(val provider: Class<out AppWidgetProvider>) {
    WIDE(WideWidgetProvider::class.java),
    SMALL(SmallWidgetProvider::class.java),
    BAR(BarWidgetProvider::class.java),
    MINI(MiniWidgetProvider::class.java)
  }

  private const val TAG = "TimerEngine"

  /** Called by the engine after a change: one update for every widget on the home screen. */
  fun update(context: Context, snapshot: TimerEngine.Snapshot) {
    val manager = AppWidgetManager.getInstance(context) ?: return
    for (kind in Kind.entries) {
      val ids = manager.getAppWidgetIds(ComponentName(context, kind.provider))
      if (ids.isNotEmpty()) manager.updateAppWidget(ids, render(context, kind, snapshot))
    }
  }

  /** Called by a provider when the launcher asks for its widgets (added, restored, reboot). */
  fun update(context: Context, manager: AppWidgetManager, kind: Kind, ids: IntArray) {
    try {
      manager.updateAppWidget(ids, render(context, kind, TimerEngine.snapshot(context)))
    } catch (e: Exception) {
      Log.e(TAG, "Could not draw the ${kind.name} widget", e)
    }
  }

  /**
   * Running first by soonest end, so a finished timer hands its slot to the next one to end.
   * Then done (newest), then paused (newest).
   */
  fun displayOrder(timers: List<Timer>): List<Timer> {
    val byState = timers.groupBy { it.state }
    return byState[TimerState.RUNNING].orEmpty().sortedBy { it.endAt } +
      byState[TimerState.DONE].orEmpty().sortedByDescending { it.createdAt } +
      byState[TimerState.PAUSED].orEmpty().sortedByDescending { it.createdAt }
  }

  fun render(context: Context, kind: Kind, snapshot: TimerEngine.Snapshot): RemoteViews {
    val ordered = displayOrder(snapshot.timers)
    return when (kind) {
      Kind.WIDE -> wide(context, ordered, snapshot.widgetPresets)
      Kind.SMALL -> small(context, ordered, snapshot.lastUsedMs)
      Kind.BAR -> bar(context, snapshot.widgetPresets)
      Kind.MINI -> mini(context, ordered, snapshot.lastUsedMs)
    }
  }

  // The four widgets

  private fun wide(context: Context, ordered: List<Timer>, presets: List<Int>): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.te_widget_wide)
    for ((index, row) in WIDE_ROWS.withIndex()) {
      val timer = ordered.getOrNull(index)
      views.setViewVisibility(row.content, if (timer != null) View.VISIBLE else View.GONE)
      views.setViewVisibility(row.empty, if (timer != null) View.GONE else View.VISIBLE)
      if (timer != null) bindTimer(context, views, row.slot, timer)
      else views.setOnClickPendingIntent(row.empty, TimerIntents.openApp(context))
    }
    bindPresets(context, views, PRESET_TILES, presets)
    return views
  }

  private fun small(context: Context, ordered: List<Timer>, lastUsedMs: Long): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.te_widget_small)
    val timer = ordered.firstOrNull()
    val others = ordered.size - 1
    views.setViewVisibility(R.id.te_more, if (others > 0) View.VISIBLE else View.GONE)
    views.setViewVisibility(R.id.te_plus, if (timer != null) View.VISIBLE else View.GONE)
    if (timer == null) {
      bindLastUsed(context, views, SMALL_SLOT, lastUsedMs)
      return views
    }
    bindTimer(context, views, SMALL_SLOT, timer)
    if (others > 0) views.setTextViewText(R.id.te_more, context.getString(R.string.te_more, others))
    views.setContentDescription(R.id.te_plus, context.getString(R.string.te_cd_add_minute, timer.label))
    views.setOnClickPendingIntent(R.id.te_plus, TimerIntents.addMinute(context, timer.id))
    return views
  }

  private fun bar(context: Context, presets: List<Int>): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.te_widget_bar)
    bindPresets(context, views, PRESET_TILES, presets)
    return views
  }

  private fun mini(context: Context, ordered: List<Timer>, lastUsedMs: Long): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.te_widget_mini)
    val timer = ordered.firstOrNull()
    if (timer != null) bindTimer(context, views, MINI_SLOT, timer) else bindLastUsed(context, views, MINI_SLOT, lastUsedMs)
    return views
  }

  // A slot: one timer's name, digits and button

  /** View ids of a slot. [pill] is the 2x2 widget's wide button, the others have a round one. */
  private class Slot(val open: Int, val label: Int, val chrono: Int, val digits: Int, val button: Int, val pill: Boolean = false)

  private class Row(val content: Int, val empty: Int, val slot: Slot)

  /** The button's three looks: its complete image when round, icon plus background when a pill. */
  private enum class Glyph(val round: Int, val icon: Int, val pillBackground: Int) {
    PAUSE(R.drawable.te_btn_pause, R.drawable.te_ic_pause, R.drawable.te_bg_pill_accent),
    PLAY(R.drawable.te_btn_play, R.drawable.te_ic_play, R.drawable.te_bg_pill_outline),
    CLOSE(R.drawable.te_btn_close, R.drawable.te_ic_close, R.drawable.te_bg_pill_outline_done)
  }

  private val MINI_SLOT = Slot(R.id.te_open, R.id.te_label, R.id.te_chrono, R.id.te_digits, R.id.te_button)
  private val SMALL_SLOT = Slot(R.id.te_open, R.id.te_label, R.id.te_chrono, R.id.te_digits, R.id.te_button, pill = true)
  private val WIDE_ROWS = listOf(
    Row(
      R.id.te_row1,
      R.id.te_row1_empty,
      Slot(R.id.te_row1_open, R.id.te_row1_label, R.id.te_row1_chrono, R.id.te_row1_digits, R.id.te_row1_button)
    ),
    Row(
      R.id.te_row2,
      R.id.te_row2_empty,
      Slot(R.id.te_row2_open, R.id.te_row2_label, R.id.te_row2_chrono, R.id.te_row2_digits, R.id.te_row2_button)
    )
  )

  /** A preset tile and the number in it. */
  private class Tile(val button: Int, val number: Int)

  /** In preset order. The 4x1 bar has all four, the 4x2 widget the first three. */
  private val PRESET_TILES = listOf(
    Tile(R.id.te_preset1, R.id.te_preset1_number),
    Tile(R.id.te_preset2, R.id.te_preset2_number),
    Tile(R.id.te_preset3, R.id.te_preset3_number),
    Tile(R.id.te_preset4, R.id.te_preset4_number)
  )

  private fun bindTimer(context: Context, views: RemoteViews, slot: Slot, timer: Timer) {
    when (timer.state) {
      TimerState.RUNNING -> {
        views.setTextViewText(slot.label, timer.label)
        showCountdown(views, slot, timer)
        setButton(views, slot, Glyph.PAUSE, context.getString(R.string.te_cd_pause, timer.label))
        views.setOnClickPendingIntent(slot.button, TimerIntents.toggle(context, timer.id))
      }
      TimerState.PAUSED -> {
        views.setTextViewText(slot.label, context.getString(R.string.te_label_paused, timer.label))
        showDigits(views, slot, TimerFormat.clock(timer.remainingMs), context.getColor(R.color.te_muted))
        setButton(views, slot, Glyph.PLAY, context.getString(R.string.te_cd_resume, timer.label))
        views.setOnClickPendingIntent(slot.button, TimerIntents.toggle(context, timer.id))
      }
      TimerState.DONE -> {
        views.setTextViewText(slot.label, context.getString(R.string.te_label_done, timer.label))
        showDigits(views, slot, TimerFormat.clock(0), context.getColor(R.color.te_done))
        setButton(views, slot, Glyph.CLOSE, context.getString(R.string.te_cd_dismiss, timer.label))
        views.setOnClickPendingIntent(slot.button, TimerIntents.remove(context, timer.id))
      }
    }
    views.setOnClickPendingIntent(slot.open, TimerIntents.openApp(context))
  }

  /** Nothing to show: offer the last-used duration again. */
  private fun bindLastUsed(context: Context, views: RemoteViews, slot: Slot, lastUsedMs: Long) {
    views.setTextViewText(slot.label, context.getString(R.string.te_last_used))
    showDigits(views, slot, TimerFormat.clock(lastUsedMs), context.getColor(R.color.te_muted))
    setButton(views, slot, Glyph.PLAY, context.getString(R.string.te_cd_start, TimerFormat.words(lastUsedMs)))
    views.setOnClickPendingIntent(slot.button, TimerIntents.startLastUsed(context))
    views.setOnClickPendingIntent(slot.open, TimerIntents.openApp(context))
  }

  /**
   * A Chronometer ticks in the launcher's process, so the digits keep moving with ours dead.
   * It cannot stop itself at zero; the alarm does that, by finishing the timer and redrawing.
   */
  private fun showCountdown(views: RemoteViews, slot: Slot, timer: Timer) {
    val remaining = (timer.endAt - System.currentTimeMillis()).coerceAtLeast(0)
    views.setViewVisibility(slot.digits, View.GONE)
    views.setViewVisibility(slot.chrono, View.VISIBLE)
    views.setChronometerCountDown(slot.chrono, true)
    // Chronometer rounds down; the extra 999 ms makes it round up like TimerFormat.clock,
    // so the digits do not jump by a second when a timer is paused.
    views.setChronometer(slot.chrono, SystemClock.elapsedRealtime() + remaining + 999, null, true)
  }

  private fun showDigits(views: RemoteViews, slot: Slot, text: String, color: Int) {
    views.setChronometer(slot.chrono, SystemClock.elapsedRealtime(), null, false)
    views.setViewVisibility(slot.chrono, View.GONE)
    views.setViewVisibility(slot.digits, View.VISIBLE)
    views.setTextViewText(slot.digits, text)
    views.setTextColor(slot.digits, color)
  }

  private fun setButton(views: RemoteViews, slot: Slot, glyph: Glyph, description: String) {
    if (slot.pill) {
      views.setImageViewResource(slot.button, glyph.icon)
      views.setInt(slot.button, "setBackgroundResource", glyph.pillBackground)
    } else {
      views.setImageViewResource(slot.button, glyph.round)
    }
    views.setContentDescription(slot.button, description)
  }

  /**
   * Tile n starts presets[n] minutes. The PendingIntent is keyed by its duration, so two tiles
   * holding the same value share one, and no tile can end up with another's duration.
   */
  private fun bindPresets(context: Context, views: RemoteViews, tiles: List<Tile>, presets: List<Int>) {
    for ((tile, minutes) in tiles.zip(presets)) {
      views.setTextViewText(tile.number, minutes.toString())
      // Three digits: the text shrinks to fit by itself from Android 8. Before that, help it.
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && minutes >= 100) {
        views.setTextViewTextSize(tile.number, TypedValue.COMPLEX_UNIT_SP, 15f)
      }
      views.setContentDescription(tile.button, context.getString(R.string.te_cd_start_minutes, minutes))
      views.setOnClickPendingIntent(tile.button, TimerIntents.start(context, minutes * 60_000L))
    }
  }
}
