import { StyleSheet, Text, View } from 'react-native';

import { COLORS } from '../constants';

export default function ScanScreen() {
  return (
    <View style={styles.container}>
      <Text style={styles.emoji}>🧾</Text>
      <Text style={styles.heading}>Scan Receipt</Text>
      <Text style={styles.subtext}>Coming in Day 8</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.bg,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 28,
  },
  emoji: { fontSize: 48, marginBottom: 16 },
  heading: { fontSize: 22, color: COLORS.ink, fontWeight: '600', marginBottom: 6 },
  subtext: { fontSize: 13, color: COLORS.inkLight, textAlign: 'center' },
});
