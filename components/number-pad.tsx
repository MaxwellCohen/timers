import { MaterialIcons } from '@expo/vector-icons';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Colors, Fonts } from '@/constants/theme';

const ROWS = [
  ['1', '2', '3'],
  ['4', '5', '6'],
  ['7', '8', '9'],
  ['00', '0', 'backspace'],
];

type Props = {
  onDigits: (digits: string) => void;
  onBackspace: () => void;
};

export default function NumberPad({ onDigits, onBackspace }: Props) {
  return (
    <View style={styles.pad}>
      {ROWS.map((row) => (
        <View key={row.join()} style={styles.row}>
          {row.map((key) =>
            key === 'backspace' ? (
              <Pressable
                key={key}
                accessibilityRole="button"
                accessibilityLabel="Delete last digit"
                onPress={onBackspace}
                style={({ pressed }) => [styles.key, pressed && styles.pressed]}
              >
                <MaterialIcons name="backspace" size={26} color={Colors.muted} />
              </Pressable>
            ) : (
              <Pressable
                key={key}
                accessibilityRole="button"
                onPress={() => onDigits(key)}
                style={({ pressed }) => [styles.key, pressed && styles.pressed]}
              >
                <Text style={styles.digit}>{key}</Text>
              </Pressable>
            ),
          )}
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  pad: {
    gap: 6,
  },
  row: {
    flexDirection: 'row',
    gap: 6,
  },
  key: {
    flex: 1,
    height: 50,
    borderRadius: 16,
    backgroundColor: Colors.raised,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: {
    opacity: 0.7,
  },
  digit: {
    fontFamily: Fonts.displaySemibold,
    fontSize: 24,
    color: Colors.text,
  },
});
