import { MaterialIcons } from '@expo/vector-icons';
import type { ComponentProps } from 'react';
import { Pressable, StyleSheet } from 'react-native';

import { Colors } from '@/constants/theme';

type Props = {
  icon: ComponentProps<typeof MaterialIcons>['name'];
  color: string;
  label: string;
  onPress: () => void;
};

/** 52dp round header button on the raised surface. */
export default function IconButton({ icon, color, label, onPress }: Props) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      style={({ pressed }) => [styles.button, pressed && styles.pressed]}
    >
      <MaterialIcons name={icon} size={24} color={color} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: Colors.raised,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: {
    opacity: 0.7,
  },
});
