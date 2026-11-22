import { Children, Fragment, type ReactNode } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { Colors, Fonts } from '@/constants/theme';

type Props = {
  title: string;
  /** Muted note under the card. */
  footnote?: string;
  children: ReactNode;
};

/** Section label above a grouped card; rows are separated by hairlines. */
export default function SettingsSection({ title, footnote, children }: Props) {
  return (
    <View>
      <Text style={styles.title} accessibilityRole="header">
        {title}
      </Text>
      <View style={styles.card}>
        {Children.toArray(children).map((row, index) => (
          <Fragment key={index}>
            {index > 0 && <View style={styles.separator} />}
            {row}
          </Fragment>
        ))}
      </View>
      {footnote != null && <Text style={styles.footnote}>{footnote}</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  title: {
    fontFamily: Fonts.semibold,
    fontSize: 12,
    letterSpacing: 1,
    textTransform: 'uppercase',
    color: Colors.muted,
    paddingHorizontal: 16,
    marginBottom: 8,
  },
  card: {
    backgroundColor: Colors.surface,
    borderRadius: 24,
    overflow: 'hidden',
  },
  footnote: {
    fontFamily: Fonts.regular,
    fontSize: 13,
    lineHeight: 18,
    color: Colors.muted,
    paddingHorizontal: 16,
    marginTop: 8,
  },
  separator: {
    height: 1,
    backgroundColor: Colors.line,
  },
});
