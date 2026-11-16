import { useEffect, useMemo, useRef, useState } from 'react';
import {
  View, Text, TouchableOpacity, ScrollView, ActivityIndicator, StyleSheet,
} from 'react-native';

import { COLORS } from '../constants';
import useProfileStore from '../store/profileStore';
import useToastStore from '../store/toastStore';
import { downloadAndShareMonthlyPdf } from '../utils/reportPdf';

// Static, not toLocaleDateString — avoids depending on Hermes' Intl month formatting
const MONTH_ABBR = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const MONTHS_SHOWN = 6; // current + previous 5 (the backend allows up to 12 back)

// Current month first, then the previous 5. new Date(y, m - i, 1) normalises a
// negative month, so January correctly rolls back to December of the prior year.
function buildMonths(today = new Date()) {
  const months = [];
  for (let i = 0; i < MONTHS_SHOWN; i++) {
    const d = new Date(today.getFullYear(), today.getMonth() - i, 1);
    const value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    const label = MONTH_ABBR[d.getMonth()] + (i === 0 ? ' (so far)' : '');
    months.push({ value, label });
  }
  return months;
}

// Pro-only monthly PDF, rendered under BudgetAlertsCard on ProfileScreen.
export default function MonthlyReportPdfCard({ navigation }) {
  const isPro = useProfileStore((s) => s.profile?.isPro);
  const showToast = useToastStore((s) => s.showToast);

  const months = useMemo(() => buildMonths(), []);
  // Default = most recent COMPLETED month (index 1), not the partial current one
  const [selected, setSelected] = useState(months[1].value);
  const [isRunning, setIsRunning] = useState(false);

  // The share sheet can outlive this screen (user navigates back mid-share)
  const mounted = useRef(true);
  useEffect(() => () => { mounted.current = false; }, []);

  const handleGenerate = async () => {
    if (isRunning) return;
    setIsRunning(true);
    try {
      const result = await downloadAndShareMonthlyPdf(selected);
      if (result.ok || result.reason === 'pro') {
        // ok: the share sheet was the feedback. pro: already toasted + on the Paywall
        return;
      }
      if (result.reason === 'empty') {
        showToast(result.message, 'info');
      } else if (result.reason === 'network') {
        showToast("No connection. Try again when you're online.", 'error');
      } else {
        showToast("Couldn't create the report. Please try again.", 'error');
      }
    } finally {
      if (mounted.current) setIsRunning(false);
    }
  };

  if (!isPro) {
    return (
      <>
        <Text style={styles.sectionLabel}>Reports</Text>
        <TouchableOpacity
          style={[styles.card, styles.row]}
          onPress={() => navigation.navigate('Paywall', { source: 'pdf_report' })}
          activeOpacity={0.7}
        >
          <View style={styles.meta}>
            <View style={styles.titleRow}>
              <Text style={styles.label}>Monthly PDF report</Text>
              <View style={styles.proBadge}>
                <Text style={styles.proBadgeText}>PRO</Text>
              </View>
            </View>
            <Text style={styles.sub}>A clean summary of any month to save or share</Text>
          </View>
          <Text style={styles.chevron}>›</Text>
        </TouchableOpacity>
      </>
    );
  }

  return (
    <>
      <Text style={styles.sectionLabel}>Reports</Text>
      <View style={[styles.card, styles.body]}>
        <Text style={styles.label}>Monthly PDF report</Text>
        <Text style={styles.sub}>A clean summary of any month to save or share</Text>

        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.chipsRow}
          style={styles.chipsScroll}
        >
          {months.map(({ value, label }) => {
            const isSelected = value === selected;
            return (
              <TouchableOpacity
                key={value}
                style={[styles.chip, { backgroundColor: isSelected ? COLORS.accent : COLORS.border }]}
                onPress={() => setSelected(value)}
                disabled={isRunning}
                activeOpacity={0.8}
              >
                <Text style={[styles.chipText, { color: isSelected ? '#fff' : COLORS.inkLight }]}>
                  {label}
                </Text>
              </TouchableOpacity>
            );
          })}
        </ScrollView>

        <TouchableOpacity
          style={[styles.button, isRunning && styles.buttonDisabled]}
          onPress={handleGenerate}
          disabled={isRunning}
          activeOpacity={0.8}
        >
          {isRunning
            ? <ActivityIndicator color="#fff" />
            : <Text style={styles.buttonText}>Generate & share</Text>}
        </TouchableOpacity>
      </View>
    </>
  );
}

// Mirrors BudgetAlertsCard / ProfileScreen (sectionLabel, card, chip, PRO badge)
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
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: 16,
  },
  body: { padding: 16 },
  meta: { flex: 1, marginRight: 12 },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 2 },
  label: { fontSize: 14, color: COLORS.ink, fontWeight: '600', marginBottom: 2 },
  sub: { fontSize: 11, color: COLORS.inkLight },

  chipsScroll: { marginTop: 14, marginHorizontal: -16 },
  chipsRow: { gap: 8, paddingHorizontal: 16 },
  chip: { borderRadius: 16, paddingHorizontal: 14, paddingVertical: 8 },
  chipText: { fontSize: 12, fontWeight: '600' },

  button: {
    marginTop: 16, backgroundColor: COLORS.accent, borderRadius: 12,
    paddingVertical: 14, alignItems: 'center', justifyContent: 'center', minHeight: 48,
  },
  buttonDisabled: { opacity: 0.7 },
  buttonText: { color: '#fff', fontSize: 15, fontWeight: '700' },

  proBadge: { backgroundColor: COLORS.ink, borderRadius: 6, paddingHorizontal: 6, paddingVertical: 2 },
  proBadgeText: { fontSize: 9, color: COLORS.accent, fontWeight: '700', letterSpacing: 1 },
  chevron: { fontSize: 22, color: COLORS.inkLight },
});
