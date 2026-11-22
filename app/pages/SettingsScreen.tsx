import { useEffect, useState } from 'react';
import {
  AppState,
  BackHandler,
  KeyboardAvoidingView,
  PermissionsAndroid,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  ToastAndroid,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import IconButton from '@/components/icon-button';
import { LinkRow, MinutesRow, SwitchRow, ValueRow } from '@/components/settings-row';
import SettingsSection from '@/components/settings-section';
import { Colors, Fonts } from '@/constants/theme';
import TimerEngine, { type Preferences } from '@/modules/timer-engine';

type Props = {
  preferences: Preferences;
  onChangePreferences: (patch: Partial<Preferences>) => void;
  onBack: () => void;
};

export default function SettingsScreen({ preferences, onChangePreferences, onBack }: Props) {
  const insets = useSafeAreaInsets();
  const [status, setStatus] = useState(() => TimerEngine.getAlertStatus());
  const [version] = useState(() => TimerEngine.getAppVersion());

  useEffect(() => {
    // Volume and notification access change outside the app, so re-read on every return to it.
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') setStatus(TimerEngine.getAlertStatus());
    });
    return () => subscription.remove();
  }, []);

  useEffect(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      onBack();
      return true;
    });
    return () => subscription.remove();
  }, [onBack]);

  const onNotificationsPress = async () => {
    if (!status.notificationsEnabled && Platform.OS === 'android' && Platform.Version >= 33) {
      const result = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
      const next = TimerEngine.getAlertStatus();
      setStatus(next);
      // Granted can still leave the channel blocked; only settings can fix that.
      if (result === PermissionsAndroid.RESULTS.GRANTED && next.notificationsEnabled) return;
    }
    TimerEngine.openNotificationSettings();
  };

  const onPickSound = () => {
    // Resolves when the picker closes; nothing to do if it could not open.
    TimerEngine.pickAlertSound().then(setStatus, () => {});
  };

  const setAppPreset = (index: number, minutes: number) =>
    onChangePreferences({ appPresets: preferences.appPresets.map((m, i) => (i === index ? minutes : m)) });
  const setWidgetPreset = (index: number, minutes: number) =>
    onChangePreferences({ widgetPresets: preferences.widgetPresets.map((m, i) => (i === index ? minutes : m)) });

  const onTestAlert = () => {
    TimerEngine.startTimer(5000, 'Test alert');
    ToastAndroid.show('Test alert in 5 seconds', ToastAndroid.SHORT);
  };

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.header}>
        <IconButton icon="arrow-back" color={Colors.text} label="Back to timers" onPress={onBack} />
        <Text style={styles.title} accessibilityRole="header">
          Settings
        </Text>
      </View>

      {/* Edge-to-edge windows are not resized for the keyboard, so make room for it here. */}
      <KeyboardAvoidingView style={styles.body} behavior="padding">
        <ScrollView
          keyboardShouldPersistTaps="handled"
          contentContainerStyle={[styles.content, { paddingBottom: 24 + insets.bottom }]}
        >
          <SettingsSection title="Alerts">
            <LinkRow
              title="Alarm sound"
              detail={status.soundName ?? 'Silent'}
              onPress={onPickSound}
            />
            <LinkRow
              title="Alarm volume"
              detail={status.alarmVolumeOff ? 'Off: finished timers will be silent' : 'On'}
              detailColor={status.alarmVolumeOff ? Colors.done : Colors.muted}
              onPress={() => TimerEngine.openSoundSettings()}
            />
            <SwitchRow
              title="Vibration"
              detail="Vibrates while ringing"
              value={preferences.vibrate}
              onValueChange={(vibrate) => onChangePreferences({ vibrate })}
            />
            <SwitchRow
              title="Repeat until dismissed"
              detail="Keeps ringing for up to 10 minutes"
              value={preferences.repeatSound}
              onValueChange={(repeatSound) => onChangePreferences({ repeatSound })}
            />
            <LinkRow
              title="Notifications"
              detail={status.notificationsEnabled ? 'Allowed' : 'Blocked: timers ring without an alert to dismiss'}
              detailColor={status.notificationsEnabled ? Colors.muted : Colors.done}
              onPress={onNotificationsPress}
            />
            <LinkRow title="Test alert" detail="Rings in 5 seconds" icon="play-arrow" onPress={onTestAlert} />
          </SettingsSection>

          <SettingsSection title="Quick start · App">
            {preferences.appPresets.map((minutes, index) => (
              <MinutesRow
                key={index}
                title={`Button ${index + 1}`}
                value={minutes}
                onChange={(next) => setAppPreset(index, next)}
                accessibilityLabel={`App quick start button ${index + 1}, minutes`}
              />
            ))}
          </SettingsSection>

          <SettingsSection title="Quick start · Widgets" footnote="The large widget shows the first three.">
            {preferences.widgetPresets.map((minutes, index) => (
              <MinutesRow
                key={index}
                title={`Button ${index + 1}`}
                value={minutes}
                onChange={(next) => setWidgetPreset(index, next)}
                accessibilityLabel={`Widget quick start button ${index + 1}, minutes`}
              />
            ))}
          </SettingsSection>

          <SettingsSection title="Display">
            <SwitchRow
              title="Keep screen on"
              detail="While the app is open and a timer is running"
              value={preferences.keepScreenOn}
              onValueChange={(keepScreenOn) => onChangePreferences({ keepScreenOn })}
            />
          </SettingsSection>

          <SettingsSection title="About">
            <ValueRow title="Version" value={version} />
          </SettingsSection>
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: Colors.background,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingTop: 28,
    paddingHorizontal: 20,
    paddingBottom: 12,
  },
  title: {
    fontFamily: Fonts.displayBold,
    fontSize: 22,
    color: Colors.text,
  },
  body: {
    flex: 1,
  },
  content: {
    paddingTop: 8,
    paddingHorizontal: 20,
    gap: 24,
  },
});
