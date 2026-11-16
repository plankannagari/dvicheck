import { View, Text, TouchableOpacity, Switch, StyleSheet } from 'react-native';

import { COLORS } from '../constants';
import useProfileStore from '../store/profileStore';
import useToastStore from '../store/toastStore';

const FREE_THRESHOLDS = [80, 100];
const MAX_THRESHOLDS = 4;

// Budget-alert settings, rendered under ProfileScreen's Preferences card. Saves
// immediately on tap (optimistic, via profileStore.saveBudgetAlerts) — deliberately
// separate from ProfileScreen's isDirty/Save-button flow.
export default function BudgetAlertsCard({ navigation }) {
  const budgetAlerts = useProfileStore((s) => s.budgetAlerts);
  const budgetAmount = useProfileStore((s) => s.profile?.budgetAmount);
  const saveBudgetAlerts = useProfileStore((s) => s.saveBudgetAlerts);
  const showToast = useToastStore((s) => s.showToast);

  if (!budgetAlerts) return null;

  if (!budgetAmount) {
    return (
      <>
        <Text style={styles.sectionLabel}>Budget alerts</Text>
        <Text style={styles.hint}>Set a monthly budget to enable alerts</Text>
      </>
    );
  }

  const { isPro, thresholds, paceAlertsEnabled, allowedThresholds } = budgetAlerts;

  const toggleThreshold = (value) => {
    const selected = thresholds.includes(value);
    if (selected && thresholds.length === 1) {
      showToast('Keep at least one alert', 'info');
      return;
    }
    if (!selected && thresholds.length >= MAX_THRESHOLDS) {
      showToast(`Up to ${MAX_THRESHOLDS} alerts`, 'info');
      return;
    }
    const next = selected
      ? thresholds.filter((t) => t !== value)
      : [...thresholds, value].sort((a, b) => a - b);
    saveBudgetAlerts({ thresholds: next });
  };

  return (
    <>
      <Text style={styles.sectionLabel}>Budget alerts</Text>
      <View style={styles.card}>
        <View style={[styles.row, styles.rowColumn]}>
          <View style={styles.chipsRow}>
            {(isPro ? allowedThresholds : FREE_THRESHOLDS).map((value) => {
              const selected = isPro ? thresholds.includes(value) : true;
              const chip = (
                <View style={[styles.chip, { backgroundColor: selected ? COLORS.accent : COLORS.border }]}>
                  <Text style={[styles.chipText, { color: selected ? '#fff' : COLORS.inkLight }]}>
                    {value}%
                  </Text>
                </View>
              );
              return isPro ? (
                <TouchableOpacity key={value} onPress={() => toggleThreshold(value)} activeOpacity={0.8}>
                  {chip}
                </TouchableOpacity>
              ) : (
                <View key={value}>{chip}</View>
              );
            })}
          </View>
          <Text style={styles.sub}>
            {isPro ? 'Notify me when I reach these levels of my budget' : 'Standard alerts'}
          </Text>
        </View>

        {isPro ? (
          <View style={[styles.row, styles.rowLast]}>
            <View style={styles.meta}>
              <Text style={styles.label}>Pace alert</Text>
              <Text style={styles.sub}>Warn me once a month if I'm on track to overspend</Text>
            </View>
            <Switch
              value={paceAlertsEnabled}
              onValueChange={(value) => saveBudgetAlerts({ paceAlertsEnabled: value })}
              trackColor={{ true: COLORS.accent }}
              thumbColor="#fff"
            />
          </View>
        ) : (
          <TouchableOpacity
            style={[styles.row, styles.rowLast]}
            onPress={() => navigation.navigate('Paywall', { source: 'budget_alerts' })}
            activeOpacity={0.7}
          >
            <View style={styles.upsellMeta}>
              <Text style={styles.label}>Custom thresholds and pace alerts</Text>
              <View style={styles.proBadge}>
                <Text style={styles.proBadgeText}>PRO</Text>
              </View>
            </View>
            <Text style={styles.chevron}>›</Text>
          </TouchableOpacity>
        )}
      </View>
    </>
  );
}

// Mirrors ProfileScreen's sectionLabel / prefsCard / prefRow / chip styles so the
// card reads as part of the same screen (no shared style module exists — Day 21 note)
const styles = StyleSheet.create({
  sectionLabel: {
    fontSize: 10, color: COLORS.inkLight, letterSpacing: 2,
    textTransform: 'uppercase', marginBottom: 10,
  },
  hint: { fontSize: 12, color: COLORS.inkLight, marginBottom: 20 },

  card: {
    backgroundColor: COLORS.card, borderRadius: 16,
    borderWidth: 1, borderColor: COLORS.border, marginBottom: 20, overflow: 'hidden',
  },
  row: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    padding: 16, borderBottomWidth: 1, borderBottomColor: COLORS.border,
  },
  rowColumn: { flexDirection: 'column', alignItems: 'stretch', gap: 10 },
  rowLast: { borderBottomWidth: 0 },
  meta: { flex: 1, marginRight: 12 },
  upsellMeta: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 8, marginRight: 12 },
  label: { fontSize: 14, color: COLORS.ink, fontWeight: '600', marginBottom: 2 },
  sub: { fontSize: 11, color: COLORS.inkLight },

  chipsRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { borderRadius: 16, paddingHorizontal: 14, paddingVertical: 8 },
  chipText: { fontSize: 12, fontWeight: '600' },

  proBadge: { backgroundColor: COLORS.ink, borderRadius: 6, paddingHorizontal: 6, paddingVertical: 2 },
  proBadgeText: { fontSize: 9, color: COLORS.accent, fontWeight: '700', letterSpacing: 1 },
  chevron: { fontSize: 22, color: COLORS.inkLight },
});
