import { PermissionsAndroid, Platform } from 'react-native';

let requested = false;

/** Asks for notification permission (Android 13+) the first time it is called; never blocks the caller. */
export function requestNotificationPermission() {
  if (requested || Platform.OS !== 'android' || Platform.Version < 33) return;
  requested = true;
  PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS).catch(() => {});
}
