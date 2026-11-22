package expo.modules.timerengine

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * The providers only answer the launcher's own requests to draw. Updates caused by a timer
 * changing are pushed by [TimerEngine], and taps go to [TimerActionReceiver].
 */
abstract class TimerWidgetProvider internal constructor(private val kind: TimerWidgets.Kind) : AppWidgetProvider() {
  override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    TimerWidgets.update(context, appWidgetManager, kind, appWidgetIds)
  }
}

/** 4x2 "Timers". */
class WideWidgetProvider : TimerWidgetProvider(TimerWidgets.Kind.WIDE)

/** 2x2 "Next timer". */
class SmallWidgetProvider : TimerWidgetProvider(TimerWidgets.Kind.SMALL)

/** 4x1 "Quick start". */
class BarWidgetProvider : TimerWidgetProvider(TimerWidgets.Kind.BAR)

/** 2x1 "Next timer (compact)". */
class MiniWidgetProvider : TimerWidgetProvider(TimerWidgets.Kind.MINI)
