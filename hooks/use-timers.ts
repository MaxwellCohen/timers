import { useEffect, useState } from 'react';
import { AppState } from 'react-native';

import TimerEngine, { type Timer } from '@/modules/timer-engine';

const TICK_MS = 250;

/** Timers from the native engine, plus a clock that ticks while any of them is running. */
export function useTimers() {
  const [timers, setTimers] = useState(() => TimerEngine.getTimers());
  const [now, setNow] = useState(Date.now);

  useEffect(() => {
    // Refresh the clock with the list so a newly started timer never renders against a stale `now`.
    const update = (next: Timer[]) => {
      setNow(Date.now());
      setTimers(next);
    };
    const subscription = TimerEngine.addListener('onTimersChanged', ({ timers: next }) => update(next));
    // Covers a change that landed between the first render and the subscription.
    update(TimerEngine.getTimers());
    const appState = AppState.addEventListener('change', (state) => {
      if (state === 'active') update(TimerEngine.getTimers());
    });
    return () => {
      subscription.remove();
      appState.remove();
    };
  }, []);

  const anyRunning = timers.some((t) => t.state === 'running');

  useEffect(() => {
    if (!anyRunning) return;
    const interval = setInterval(() => setNow(Date.now()), TICK_MS);
    return () => clearInterval(interval);
  }, [anyRunning]);

  return { timers, now };
}
