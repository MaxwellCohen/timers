import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import NumberPad from '@/components/number-pad';
import { Colors, Fonts } from '@/constants/theme';
import TimerEngine from '@/modules/timer-engine';

function startTimer(ms: number) {
  TimerEngine.startTimer(ms, null);
}

/** Always-visible bottom panel: hhmmss entry typed from the right, number pad, presets and Start. */
type Props = {
  /** Quick-start durations in minutes. */
  presets: number[];
};

export default function EntryPanel({ presets }: Props) {
  const insets = useSafeAreaInsets();
  const [entry, setEntry] = useState('');

  const padded = entry.padStart(6, '0');
  const hh = padded.slice(0, 2);
  const mm = padded.slice(2, 4);
  const ss = padded.slice(4, 6);
  const ms = (Number(hh) * 3600 + Number(mm) * 60 + Number(ss)) * 1000;
  const canStart = ms > 0;

  const groups = [
    { digits: hh, unit: 'h', lit: entry.length > 4 },
    { digits: mm, unit: 'm', lit: entry.length > 2 },
    { digits: ss, unit: 's', lit: entry.length > 0 },
  ];

  const onDigits = (digits: string) =>
    setEntry((prev) => (prev === '' && (digits === '0' || digits === '00') ? prev : (prev + digits).slice(0, 6)));

  const onStart = () => {
    startTimer(ms);
    setEntry('');
  };

  return (
    <View style={[styles.panel, { paddingBottom: 20 + insets.bottom }]}>
      <View
        style={styles.display}
        accessible
        accessibilityLabel={`${Number(hh)} hours ${Number(mm)} minutes ${Number(ss)} seconds`}
        accessibilityLiveRegion="polite"
      >
        {groups.map(({ digits, unit, lit }) => (
          <View key={unit} style={styles.group}>
            <Text style={[styles.digits, { color: lit ? Colors.text : Colors.muted }]}>{digits}</Text>
            <Text style={styles.unit}>{unit}</Text>
          </View>
        ))}
      </View>

      <NumberPad onDigits={onDigits} onBackspace={() => setEntry((prev) => prev.slice(0, -1))} />

      <View style={styles.presets}>
        {presets.map((minutes, index) => (
          <Pressable
            key={index}
            accessibilityRole="button"
            accessibilityLabel={`Start a ${minutes} minute timer`}
            onPress={() => startTimer(minutes * 60_000)}
            style={({ pressed }) => [styles.preset, pressed && styles.pressed]}
          >
            <Text style={styles.presetText} numberOfLines={1}>
              {minutes}m
            </Text>
          </Pressable>
        ))}
      </View>

      <Pressable
        accessibilityRole="button"
        accessibilityState={{ disabled: !canStart }}
        disabled={!canStart}
        onPress={onStart}
        style={({ pressed }) => [styles.start, canStart && styles.startEnabled, pressed && styles.pressed]}
      >
        <Text style={[styles.startText, canStart && styles.startTextEnabled]}>Start timer</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    backgroundColor: Colors.surface,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingTop: 16,
    paddingHorizontal: 20,
    gap: 12,
  },
  display: {
    height: 44,
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'center',
    gap: 13, // mock: 3dp gap plus 10dp after each unit
  },
  group: {
    flexDirection: 'row',
    alignItems: 'baseline',
    gap: 3,
  },
  digits: {
    fontFamily: Fonts.displaySemibold,
    fontSize: 40,
    lineHeight: 44,
    fontVariant: ['tabular-nums'],
  },
  unit: {
    fontFamily: Fonts.displaySemibold,
    fontSize: 16,
    color: Colors.muted,
  },
  presets: {
    flexDirection: 'row',
    gap: 6,
  },
  preset: {
    flex: 1,
    height: 44,
    borderRadius: 22,
    borderWidth: 1,
    borderColor: Colors.outline,
    alignItems: 'center',
    justifyContent: 'center',
  },
  presetText: {
    fontFamily: Fonts.semibold,
    fontSize: 15,
    color: Colors.text,
  },
  start: {
    height: 54,
    borderRadius: 27,
    backgroundColor: Colors.raised,
    alignItems: 'center',
    justifyContent: 'center',
  },
  startEnabled: {
    backgroundColor: Colors.accent,
  },
  pressed: {
    opacity: 0.7,
  },
  startText: {
    fontFamily: Fonts.semibold,
    fontSize: 17,
    color: Colors.muted,
  },
  startTextEnabled: {
    color: Colors.onAccent,
  },
});
