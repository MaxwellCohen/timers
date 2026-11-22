# Timers

Timers is an Android app for running several countdown timers at once, with home-screen widgets. You start timers from a number pad, from quick-start buttons, or from a widget. When a timer finishes, the alarm rings and vibrates even if the app is closed.

It's built with Expo and React Native. A local native module, `modules/timer-engine`, does the timing, alarms, notifications, and widgets in Kotlin.

## What it does

- **Timers**: type a duration on the 3x3 number pad or tap a quick-start button (1, 10, or 30 minutes by default). Each running timer can be paused, given another minute, or removed.
- **Alarms**: a finished timer rings with the phone's default alarm sound. Timers survive the app closing and the phone rebooting.
- **Other apps**: any app that sends Android's standard set-timer request can start a timer here. The phone's assistant does this when you ask it for a timer.
- **Settings**: alarm sound, alarm volume, vibration, repeat until dismissed, notification access, a test alert, the quick-start durations, and keep screen on.

The app has four widgets:

| Widget | Size | Shows |
| --- | --- | --- |
| Timers | 4x2 | Your next two timers and four quick-start buttons |
| Next timer | 2x2 | The next timer to finish, with pause and +1 minute |
| Next timer (compact) | 2x1 | The next timer to finish, in one row |
| Quick start | 4x1 | Four quick-start buttons (1, 5, 10, and 15 minutes by default) |

The widgets' quick-start durations are set separately from the app's, in **Settings**.

## Prerequisites

You need:

- Node.js and pnpm
- JDK 17
- The Android SDK, with `ANDROID_HOME` set (for example, `~/Library/Android/sdk` on macOS)
- An Android emulator or a phone with USB debugging on

The `android/` folder is generated and isn't committed.

## Run it locally

To build the app and start it on an emulator or a connected phone:

1. Install dependencies:

   ```sh
   pnpm install
   ```

2. Build and launch the app. This generates `android/` on the first run.

   ```sh
   pnpm android
   ```

If you change `app.config.js` or the native module's manifest, regenerate the native project with `pnpm prebuild`.

## Check your changes

Run the type check and the linter:

```sh
pnpm typecheck
pnpm lint
```

## Build an installable APK

The `preview` profile in `eas.json` builds an APK you can install directly on a phone. You must be signed in to EAS (Expo Application Services) with access to the linked project.

```sh
pnpm build
```

When the cloud build finishes, EAS prints a download link for the APK.

Every push to `main` also starts this build in the cloud through the EAS workflow in `.eas/workflows/build-main.yml`. This only works while the GitHub repository is linked to the EAS project under **Project settings > GitHub** on expo.dev. You can see each run and its APK link on the project's **Workflows** page.

## Project layout

- `App.tsx`: loads fonts and preferences and switches between the two screens.
- `app/pages/`: the home screen and the settings screen.
- `components/`, `hooks/`, `lib/`, `constants/`: UI pieces, timer and preference state, and the theme.
- `modules/timer-engine/`: the Kotlin native module. `index.ts` is its TypeScript interface.
- `assets/images/`: the app icon and splash image.
