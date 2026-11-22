import { useState } from 'react';

import TimerEngine, { type Preferences } from '@/modules/timer-engine';

/** Stored preferences. Call once at the root and pass down, so every screen sees the same values. */
export function usePreferences() {
  const [preferences, setPreferences] = useState(() => TimerEngine.getPreferences());

  const updatePreferences = (patch: Partial<Preferences>) => setPreferences(TimerEngine.setPreferences(patch));

  return [preferences, updatePreferences] as const;
}
