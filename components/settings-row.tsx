import { MaterialIcons } from '@expo/vector-icons';
import { useState, type ComponentProps } from 'react';
import { Pressable, StyleSheet, Switch, Text, TextInput, View } from 'react-native';

import { Colors, Fonts } from '@/constants/theme';

type TextProps = {
  title: string;
  detail?: string;
  detailColor?: string;
};

function RowText({ title, detail, detailColor = Colors.muted }: TextProps) {
  return (
    <View style={styles.text}>
      <Text style={styles.title}>{title}</Text>
      {detail != null && <Text style={[styles.detail, { color: detailColor }]}>{detail}</Text>}
    </View>
  );
}

type LinkRowProps = TextProps & {
  onPress: () => void;
  icon?: ComponentProps<typeof MaterialIcons>['name'];
};

/** A row that opens or triggers something. */
export function LinkRow({ onPress, icon = 'chevron-right', ...text }: LinkRowProps) {
  return (
    <Pressable
      accessibilityRole="button"
      // Explicit, so the icon glyph is not read out as part of the row.
      accessibilityLabel={text.detail != null ? `${text.title}, ${text.detail}` : text.title}
      onPress={onPress}
      style={({ pressed }) => [styles.row, pressed && styles.pressed]}
    >
      <RowText {...text} />
      <MaterialIcons name={icon} size={24} color={Colors.muted} />
    </Pressable>
  );
}

type SwitchRowProps = TextProps & {
  value: boolean;
  onValueChange: (value: boolean) => void;
};

/** A row with a switch; tapping anywhere on the row toggles it. */
export function SwitchRow({ value, onValueChange, ...text }: SwitchRowProps) {
  return (
    <Pressable
      accessibilityRole="switch"
      accessibilityState={{ checked: value }}
      onPress={() => onValueChange(!value)}
      style={({ pressed }) => [styles.row, pressed && styles.pressed]}
    >
      <RowText {...text} />
      <Switch
        value={value}
        onValueChange={onValueChange}
        trackColor={{ false: Colors.outline, true: Colors.accent }}
        thumbColor={Colors.text}
        // The row already announces itself as the switch.
        importantForAccessibility="no-hide-descendants"
      />
    </Pressable>
  );
}

/** A read-only row with a value on the right. */
export function ValueRow({ title, value }: { title: string; value: string }) {
  return (
    <View style={styles.row} accessible>
      <RowText title={title} />
      <Text style={styles.value}>{value}</Text>
    </View>
  );
}

type MinutesRowProps = {
  title: string;
  value: number;
  onChange: (minutes: number) => void;
  accessibilityLabel: string;
};

/** A row with a minutes field. Saves on blur or submit; empty or invalid input reverts. */
export function MinutesRow({ title, value, onChange, accessibilityLabel }: MinutesRowProps) {
  // null while not editing, so the field always shows the stored (native-clamped) value otherwise.
  const [draft, setDraft] = useState<string | null>(null);

  // Saved on every valid keystroke, so leaving Settings mid-edit (no blur, no submit) loses nothing.
  const edit = (text: string) => {
    setDraft(text);
    const minutes = Number.parseInt(text, 10);
    if (Number.isNaN(minutes)) return;
    const clamped = Math.min(999, Math.max(1, minutes));
    if (clamped !== value) onChange(clamped);
  };

  // Back to showing the stored value, which also undoes an empty or out-of-range entry.
  const finish = () => setDraft(null);

  return (
    <View style={styles.row}>
      <RowText title={title} />
      <View style={styles.minutes}>
        <TextInput
          value={draft ?? String(value)}
          onFocus={() => setDraft(String(value))}
          onChangeText={edit}
          onBlur={finish}
          onSubmitEditing={finish}
          keyboardType="number-pad"
          maxLength={3}
          selectTextOnFocus
          accessibilityLabel={accessibilityLabel}
          cursorColor={Colors.accent}
          selectionColor={Colors.accent}
          style={[styles.input, draft !== null && styles.inputFocused]}
        />
        <Text style={styles.unit}>min</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    minHeight: 60,
    paddingHorizontal: 16,
    paddingVertical: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  pressed: {
    backgroundColor: Colors.raised,
  },
  text: {
    flex: 1,
    gap: 2,
  },
  title: {
    fontFamily: Fonts.medium,
    fontSize: 16,
    color: Colors.text,
  },
  detail: {
    fontFamily: Fonts.regular,
    fontSize: 14,
  },
  minutes: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  input: {
    width: 64,
    height: 40,
    paddingVertical: 0,
    paddingHorizontal: 12,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: Colors.line,
    backgroundColor: Colors.background,
    textAlign: 'right',
    fontFamily: Fonts.displaySemibold,
    fontSize: 18,
    fontVariant: ['tabular-nums'],
    color: Colors.text,
  },
  inputFocused: {
    borderColor: Colors.accent,
  },
  unit: {
    fontFamily: Fonts.regular,
    fontSize: 14,
    color: Colors.muted,
  },
  value: {
    fontFamily: Fonts.regular,
    fontSize: 16,
    color: Colors.muted,
  },
});
