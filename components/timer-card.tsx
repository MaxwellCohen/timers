import { MaterialIcons } from '@expo/vector-icons';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Colors, Fonts } from '@/constants/theme';
import { formatClock, remainingMs } from '@/lib/time';
import TimerEngine, { type Timer } from '@/modules/timer-engine';

const STATUS = { running: 'Running', paused: 'Paused', done: 'Done' };
const TIME_COLOR = { running: Colors.text, paused: Colors.muted, done: Colors.done };

type Props = { timer: Timer; now: number };

export default function TimerCard({ timer, now }: Props) {
  const { id, label, state, totalMs } = timer;
  const left = remainingMs(timer, now);
  const clock = formatClock(left);
  const running = state === 'running';
  const done = state === 'done';
  const progress = done ? 1 : Math.min(1, left / totalMs);

  return (
    <View style={styles.card}>
      <View style={styles.top}>
        <View style={styles.info}>
          <Text style={styles.status} numberOfLines={1}>
            <Text style={styles.label}>{label}</Text> · {STATUS[state]}
          </Text>
          <View style={styles.clockRow}>
            <Text style={[styles.clock, clock.length > 5 && styles.clockLong, { color: TIME_COLOR[state] }]}>
              {clock}
            </Text>
          </View>
        </View>

        <View style={styles.actions}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`Add one minute to ${label}`}
            onPress={() => TimerEngine.addMinute(id)}
            style={({ pressed }) => [styles.button, pressed && styles.pressed]}
          >
            <Text style={styles.addText}>+1m</Text>
          </Pressable>
          {!done && (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={`${running ? 'Pause' : 'Resume'} ${label}`}
              onPress={() => (running ? TimerEngine.pauseTimer(id) : TimerEngine.resumeTimer(id))}
              style={({ pressed }) => [
                styles.button,
                styles.toggle,
                running && styles.toggleRunning,
                pressed && styles.pressed,
              ]}
            >
              <MaterialIcons
                name={running ? 'pause' : 'play-arrow'}
                size={20}
                color={running ? Colors.onAccent : Colors.accent}
              />
            </Pressable>
          )}
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`Remove ${label}`}
            onPress={() => TimerEngine.removeTimer(id)}
            style={({ pressed }) => [styles.button, pressed && styles.pressed]}
          >
            <MaterialIcons name="close" size={20} color={Colors.muted} />
          </Pressable>
        </View>
      </View>

      <View style={styles.track}>
        <View
          style={[styles.fill, { width: `${progress * 100}%`, backgroundColor: done ? Colors.done : Colors.accent }]}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: Colors.surface,
    borderRadius: 24,
    paddingTop: 16,
    paddingHorizontal: 16,
    paddingBottom: 14,
    gap: 12,
  },
  top: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  info: {
    flex: 1,
    gap: 6,
  },
  status: {
    fontFamily: Fonts.medium,
    fontSize: 14,
    color: Colors.muted,
  },
  label: {
    color: Colors.text,
  },
  // Fixed height so switching to the smaller h:mm:ss size does not move the card.
  clockRow: {
    height: 46,
    justifyContent: 'center',
  },
  clock: {
    fontFamily: Fonts.displaySemibold,
    fontSize: 46,
    lineHeight: 46,
    includeFontPadding: false,
    fontVariant: ['tabular-nums'],
  },
  clockLong: {
    fontSize: 34,
    lineHeight: 34,
  },
  actions: {
    flexDirection: 'row',
    gap: 8,
  },
  button: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: Colors.raised,
    alignItems: 'center',
    justifyContent: 'center',
  },
  toggle: {
    borderWidth: 2,
    borderColor: Colors.accent,
    backgroundColor: 'transparent',
  },
  toggleRunning: {
    backgroundColor: Colors.accent,
  },
  pressed: {
    opacity: 0.7,
  },
  addText: {
    fontFamily: Fonts.semibold,
    fontSize: 13,
    color: Colors.text,
  },
  track: {
    height: 6,
    borderRadius: 3,
    backgroundColor: Colors.line,
    overflow: 'hidden',
  },
  fill: {
    height: '100%',
    borderRadius: 3,
  },
});
