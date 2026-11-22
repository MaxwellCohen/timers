import { NativeModule, requireNativeModule } from 'expo';

export type TimerState = 'running' | 'paused' | 'done';

export type Timer = {
  id: string;
  /** Shown in the app, widgets and notifications. Defaults to the duration ("10 min"). */
  label: string;
  /** Full length of the timer, including any added minutes. */
  totalMs: number;
  state: TimerState;
  /** Epoch ms when the timer ends. Only meaningful while running. */
  endAt: number;
  /** Time left when paused, 0 when done, a snapshot taken at read time while running. */
  remainingMs: number;
  createdAt: number;
};

/** What will happen when a timer finishes. */
export type AlertStatus = {
  /** False when notifications are blocked: the timer still rings, but no alert with Dismiss / +1 min is shown. */
  notificationsEnabled: boolean;
  /** Title of the chosen alarm sound, null when it is set to silent. */
  soundName: string | null;
  /** True when the phone's alarm volume is at zero, so a finished timer would be silent. */
  alarmVolumeOff: boolean;
};

export type Preferences = {
  /** Keep ringing until the alert is handled, instead of playing the sound once. Default true. */
  repeatSound: boolean;
  /** Vibrate while ringing. Default true. */
  vibrate: boolean;
  /** Keep the screen on while the app is open and a timer is running. Default false. */
  keepScreenOn: boolean;
  /** Quick-start buttons under the number pad, in whole minutes. Always 3 values, each 1–999. Default [1, 10, 30]. */
  appPresets: number[];
  /** Quick-start buttons on the widgets, in whole minutes. Always 4 values, each 1–999; the 4x2 widget shows the first 3. Default [1, 5, 10, 15]. */
  widgetPresets: number[];
};

type TimerEngineEvents = {
  /** Fires after every change, whether it came from the app, a widget or an alarm. */
  onTimersChanged: (event: { timers: Timer[] }) => void;
};

declare class TimerEngineModule extends NativeModule<TimerEngineEvents> {
  /** Newest first. */
  getTimers(): Timer[];
  startTimer(durationMs: number, label: string | null): Timer;
  pauseTimer(id: string): void;
  resumeTimer(id: string): void;
  /** Adds 60s. Restarts the timer if it was paused or done. */
  addMinute(id: string): void;
  removeTimer(id: string): void;

  getAlertStatus(): AlertStatus;
  /** Opens Android's alarm-sound picker and resolves with the status once it closes (changed or not). */
  pickAlertSound(): Promise<AlertStatus>;
  /** Opens the phone's sound settings, where the alarm volume lives. */
  openSoundSettings(): void;
  /** Opens this app's notification settings (to unblock notifications). */
  openNotificationSettings(): void;

  getPreferences(): Preferences;
  /** Merges the given keys and returns the full, updated preferences. */
  setPreferences(patch: Partial<Preferences>): Preferences;
  /** Holds or releases the keep-screen-on flag on the app's window. */
  setKeepScreenOn(on: boolean): void;

  /** The installed version name, e.g. "1.0.0". */
  getAppVersion(): string;
}

export default requireNativeModule<TimerEngineModule>('TimerEngine');
