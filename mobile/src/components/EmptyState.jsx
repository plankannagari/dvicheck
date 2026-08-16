import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';

import { COLORS } from '../constants';

export default function EmptyState({ icon, title, subtitle, actionLabel, onAction }) {
  const showAction = !!actionLabel && !!onAction;

  return (
    <View style={styles.container}>
      <Text style={styles.icon}>{icon}</Text>
      <Text style={styles.title}>{title}</Text>
      <Text style={styles.subtitle} numberOfLines={2}>{subtitle}</Text>
      {showAction && (
        <TouchableOpacity style={styles.actionBtn} onPress={onAction} activeOpacity={0.8}>
          <Text style={styles.actionBtnText}>{actionLabel}</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { padding: 32, alignItems: 'center', justifyContent: 'center' },
  icon: { fontSize: 40, marginBottom: 12 },
  title: { fontSize: 16, color: COLORS.ink, fontWeight: '600', marginBottom: 6, textAlign: 'center' },
  subtitle: { fontSize: 13, color: COLORS.inkLight, textAlign: 'center' },
  actionBtn: {
    backgroundColor: COLORS.accent, borderRadius: 12,
    paddingVertical: 12, paddingHorizontal: 20, marginTop: 16,
  },
  actionBtnText: { color: '#fff', fontSize: 14, fontWeight: '600' },
});
