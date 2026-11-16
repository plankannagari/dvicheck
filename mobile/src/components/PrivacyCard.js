import { useEffect, useState } from 'react';
import { View, Text, Switch, StyleSheet } from 'react-native';

import { COLORS } from '../constants';
import { isOptedOut, setOptOut } from '../analytics/analytics';

// Analytics opt-out (Day 36), under ProfileScreen's other cards. Applies immediately on
// toggle — separate from ProfileScreen's isDirty/Save flow, like BudgetAlertsCard.
// The SDK persists the choice on the device, so nothing is stored on the backend.
export default function PrivacyCard() {
  const [shareUsage, setShareUsage] = useState(null); // null until the stored value is read

  useEffect(() => {
    let active = true;
    // isOptedOut() never rejects
    isOptedOut().then((out) => { if (active) setShareUsage(!out); });
    return () => { active = false; };
  }, []);

  const handleToggle = (value) => {
    setShareUsage(value);
    // OFF stops tracking at once; ON does not re-fire app_opened
    setOptOut(!value);
  };

  return (
    <>
      <Text style={styles.sectionLabel}>Privacy</Text>
      <View style={styles.card}>
        <View style={[styles.row, styles.rowLast]}>
          <View style={styles.meta}>
            <Text style={styles.label}>Share anonymous usage data</Text>
            <Text style={styles.sub}>
              Helps improve dvicheck. We never send your phone number, receipts or amounts.
            </Text>
          </View>
          <Switch
            value={shareUsage ?? true}
            disabled={shareUsage === null}
            onValueChange={handleToggle}
            trackColor={{ true: COLORS.accent }}
            thumbColor="#fff"
          />
        </View>
      </View>
    </>
  );
}

// Same values as BudgetAlertsCard / MonthlyReportPdfCard (no shared style module exists)
const styles = StyleSheet.create({
  sectionLabel: {
    fontSize: 10, color: COLORS.inkLight, letterSpacing: 2,
    textTransform: 'uppercase', marginBottom: 10,
  },
  card: {
    backgroundColor: COLORS.card, borderRadius: 16,
    borderWidth: 1, borderColor: COLORS.border, marginBottom: 20, overflow: 'hidden',
  },
  row: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    padding: 16, borderBottomWidth: 1, borderBottomColor: COLORS.border,
  },
  rowLast: { borderBottomWidth: 0 },
  meta: { flex: 1, marginRight: 12 },
  label: { fontSize: 14, color: COLORS.ink, fontWeight: '600', marginBottom: 2 },
  sub: { fontSize: 11, color: COLORS.inkLight },
});
