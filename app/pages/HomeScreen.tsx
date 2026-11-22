import { useEffect } from 'react';
import { ScrollView, StatusBar, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import EntryPanel from '@/components/entry-panel';
import IconButton from '@/components/icon-button';
import TimerCard from '@/components/timer-card';
import { Colors, Fonts } from '@/constants/theme';
import { useTimers } from '@/hooks/use-timers';
import { requestNotificationPermission } from '@/lib/notifications';
import TimerEngine, { type Preferences } from '@/modules/timer-engine';

type Props = {
  preferences: Preferences;
  onOpenSettings: () => void;
};

export default function HomeScreen({ preferences, onOpenSettings }: Props) {
  const insets = useSafeAreaInsets();
  const { timers, now } = useTimers();

  // Asked on open, not on first start: timers started from a widget need it too.
  useEffect(requestNotificationPermission, []);

  const running = timers.filter((t) => t.state === 'running').length;
  const done = timers.filter((t) => t.state === 'done').length;
  const holdScreen = preferences.keepScreenOn && running > 0;

  useEffect(() => {
    TimerEngine.setKeepScreenOn(holdScreen);
    return () => TimerEngine.setKeepScreenOn(false);
  }, [holdScreen]);

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <StatusBar barStyle="light-content" />

      <View style={styles.header}>
        <View style={styles.heading}>
          <Text style={styles.title}>Timers</Text>
          <Text style={styles.summary}>
            {running} running{done > 0 && ` · ${done} done`}
          </Text>
        </View>
        <View style={styles.headerButtons}>
          <IconButton icon="settings" color={Colors.muted} label="Settings" onPress={onOpenSettings} />
        </View>
      </View>

      <ScrollView style={styles.list} contentContainerStyle={styles.listContent}>
        {timers.length === 0 ? (
          <View style={styles.empty}>
            <Text style={styles.emptyTitle}>No timers yet</Text>
            <Text style={styles.emptyText}>Type a time on the pad or tap a preset.</Text>
          </View>
        ) : (
          timers.map((timer) => <TimerCard key={timer.id} timer={timer} now={now} />)
        )}
      </ScrollView>

      <EntryPanel presets={preferences.appPresets} />
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
    justifyContent: 'space-between',
    gap: 16,
    paddingTop: 28,
    paddingHorizontal: 20,
    paddingBottom: 12,
  },
  heading: {
    flex: 1,
    gap: 4,
  },
  title: {
    fontFamily: Fonts.displayBold,
    fontSize: 30,
    lineHeight: 30,
    includeFontPadding: false,
    color: Colors.text,
  },
  summary: {
    fontFamily: Fonts.regular,
    fontSize: 14,
    color: Colors.muted,
  },
  headerButtons: {
    flexDirection: 'row',
    gap: 8,
  },
  list: {
    flex: 1,
  },
  listContent: {
    paddingTop: 8,
    paddingHorizontal: 20,
    paddingBottom: 16,
    gap: 12,
  },
  empty: {
    borderWidth: 1,
    borderStyle: 'dashed',
    borderColor: Colors.line,
    borderRadius: 24,
    paddingVertical: 28,
    paddingHorizontal: 20,
    gap: 6,
    alignItems: 'center',
  },
  emptyTitle: {
    fontFamily: Fonts.displaySemibold,
    fontSize: 20,
    color: Colors.text,
  },
  emptyText: {
    fontFamily: Fonts.regular,
    fontSize: 14,
    lineHeight: 20,
    color: Colors.muted,
    textAlign: 'center',
  },
});
