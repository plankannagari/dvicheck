import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';

import { COLORS } from '../constants';
import useInsightsStore from '../store/insightsStore';
import SkeletonCard from './SkeletonCard';

// Static sample for the locked (free) card — no real user data, clearly labelled
const EXAMPLE = {
  headline: 'You spent USD 142.30 this week, down 8% from the week before.',
  win: 'Essential items made up 63% of the week.',
  watch: 'Snacks rose 40% compared with last week.',
  action: 'Plan two meals before your next shop to keep Costco trips focused.',
};

// Pro weekly AI coach, rendered at the top of InsightsScreen (Day 35)
export default function WeeklyCoachCard({ navigation }) {
  const coach = useInsightsStore((s) => s.coach);
  const coachStatus = useInsightsStore((s) => s.coachStatus);

  if (coachStatus === 'loading') {
    return (
      <View style={styles.skeleton}>
        <SkeletonCard height={140} />
      </View>
    );
  }

  if (coachStatus === 'ready' && coach) {
    return (
      <View style={styles.card}>
        <Text style={styles.label}>Weekly coach - {coach.weekLabel}</Text>
        <Text style={styles.headline}>{coach.headline}</Text>
        <Row label="Win" text={coach.win} />
        <Row label="Watch" text={coach.watch} />
        <Row label="Next step" text={coach.action} last />
      </View>
    );
  }

  if (coachStatus === 'none') {
    return (
      <View style={[styles.card, styles.mutedCard]}>
        <Text style={styles.label}>Weekly coach</Text>
        <Text style={styles.mutedText}>Your first coach note arrives Monday, after a week of scans.</Text>
      </View>
    );
  }

  if (coachStatus === 'locked') {
    return (
      <View style={styles.card}>
        <View style={styles.titleRow}>
          <Text style={styles.label}>Weekly coach</Text>
          <View style={styles.proBadge}>
            <Text style={styles.proBadgeText}>PRO</Text>
          </View>
        </View>

        <View style={styles.example}>
          <Text style={styles.exampleTag}>Example</Text>
          <Text style={[styles.headline, styles.exampleText]}>{EXAMPLE.headline}</Text>
          <Row label="Win" text={EXAMPLE.win} muted />
          <Row label="Watch" text={EXAMPLE.watch} muted />
          <Row label="Next step" text={EXAMPLE.action} muted last />
        </View>

        <TouchableOpacity
          style={styles.button}
          onPress={() => navigation.navigate('Paywall', { source: 'coach' })}
          activeOpacity={0.8}
        >
          <Text style={styles.buttonText}>Unlock weekly coach</Text>
        </TouchableOpacity>
      </View>
    );
  }

  // 'idle' (not loaded yet) and 'error' render nothing
  return null;
}

function Row({ label, text, muted, last }) {
  return (
    <View style={[styles.row, last && styles.rowLast]}>
      <Text style={styles.rowLabel}>{label}</Text>
      <Text style={[styles.rowText, muted && styles.exampleText]}>{text}</Text>
    </View>
  );
}

// Matches InsightsScreen's existing coachCard / coachLabel look
const styles = StyleSheet.create({
  skeleton: { marginBottom: 20 },
  card: {
    backgroundColor: COLORS.card, borderRadius: 16, padding: 18, marginBottom: 20,
    borderLeftWidth: 3, borderLeftColor: COLORS.accent,
    shadowColor: '#000', shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06, shadowRadius: 6, elevation: 2,
  },
  mutedCard: { borderLeftColor: COLORS.border },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  label: {
    fontSize: 10, color: COLORS.accent, fontWeight: '700',
    letterSpacing: 1.5, textTransform: 'uppercase',
  },
  headline: { fontSize: 16, color: COLORS.ink, fontWeight: '700', lineHeight: 22, marginTop: 8, marginBottom: 10 },
  mutedText: { fontSize: 13, color: COLORS.inkLight, lineHeight: 20, marginTop: 8 },

  row: { paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: COLORS.border },
  rowLast: { borderBottomWidth: 0, paddingBottom: 0 },
  rowLabel: {
    fontSize: 10, color: COLORS.inkLight, fontWeight: '700',
    letterSpacing: 1, textTransform: 'uppercase', marginBottom: 3,
  },
  rowText: { fontSize: 14, color: COLORS.ink, lineHeight: 20 },

  example: {
    marginTop: 12, padding: 12, borderRadius: 12,
    borderWidth: 1, borderColor: COLORS.border, borderStyle: 'dashed', backgroundColor: COLORS.bg,
  },
  exampleTag: {
    alignSelf: 'flex-start', fontSize: 9, fontWeight: '700', letterSpacing: 1,
    textTransform: 'uppercase', color: COLORS.inkLight,
    backgroundColor: COLORS.border, borderRadius: 6, paddingHorizontal: 6, paddingVertical: 2,
  },
  exampleText: { color: COLORS.inkLight },

  proBadge: { backgroundColor: COLORS.ink, borderRadius: 6, paddingHorizontal: 6, paddingVertical: 2 },
  proBadgeText: { fontSize: 9, color: COLORS.accent, fontWeight: '700', letterSpacing: 1 },

  button: {
    marginTop: 14, backgroundColor: COLORS.accent, borderRadius: 12,
    paddingVertical: 13, alignItems: 'center',
  },
  buttonText: { color: '#fff', fontSize: 15, fontWeight: '700' },
});
