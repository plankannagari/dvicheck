import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { COLORS } from '../constants';
import useAuthStore from '../store/authStore';

const MOCK_STATS = [
  { label: 'Bills scanned', value: '23', icon: '🧾' },
  { label: 'Duplicates caught', value: '8', icon: '🔁' },
  { label: 'Saved this month', value: '$31', icon: '💰' },
];

const MOCK_BILLS = [
  { id: '1', store: 'Woolworths', date: 'Apr 19', total: '$87.40', avoidable: '-$22.50', icon: '🛒' },
  { id: '2', store: 'AGL Energy', date: 'Apr 15', total: '$143.20', avoidable: '-$38.00', icon: '⚡' },
  { id: '3', store: 'Coles', date: 'Apr 12', total: '$64.30', avoidable: '-$8.00', icon: '🛒' },
];

export default function HomeScreen({ navigation }) {
  const user = useAuthStore((state) => state.user);

  return (
    <View style={styles.screen}>
      <View style={styles.header}>
        <View>
          <Text style={styles.greeting}>Good morning</Text>
          <Text style={styles.phone}>{user?.phone}</Text>
        </View>
        <TouchableOpacity onPress={() => {}}>
          <Text style={styles.settingsIcon}>⚙️</Text>
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.heroCard}>
          <Text style={styles.heroLabel}>This month you could save</Text>
          <Text style={styles.heroAmount}>$47.20</Text>
          <Text style={styles.heroSubtext}>Based on your last 8 scans</Text>
          <TouchableOpacity
            style={styles.heroButton}
            onPress={() => navigation.navigate('Scan')}
          >
            <Text style={styles.heroButtonText}>+ Scan a receipt</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.statsRow}>
          {MOCK_STATS.map((stat) => (
            <View key={stat.label} style={styles.statCard}>
              <Text style={styles.statIcon}>{stat.icon}</Text>
              <Text style={styles.statValue}>{stat.value}</Text>
              <Text style={styles.statLabel}>{stat.label}</Text>
            </View>
          ))}
        </View>

        <View style={styles.suggestionCard}>
          <Text style={styles.suggestionLabel}>💡 Top suggestion this week</Text>
          <Text style={styles.suggestionText}>
            Switching to homebrand dishwasher tabs could save $13.50/month
          </Text>
        </View>

        <Text style={styles.sectionLabel}>Recent bills</Text>
        {MOCK_BILLS.map((bill) => (
          <View key={bill.id} style={styles.billRow}>
            <Text style={styles.billIcon}>{bill.icon}</Text>
            <View style={styles.billInfo}>
              <Text style={styles.billStore}>{bill.store}</Text>
              <Text style={styles.billDate}>{bill.date}</Text>
            </View>
            <View style={styles.billAmounts}>
              <Text style={styles.billTotal}>{bill.total}</Text>
              <Text style={styles.billAvoidable}>{bill.avoidable} avoidable</Text>
            </View>
          </View>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLORS.bg },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: COLORS.bg,
    paddingVertical: 16,
    paddingHorizontal: 24,
  },
  greeting: { fontSize: 13, color: COLORS.inkLight },
  phone: { fontSize: 18, color: COLORS.ink, fontWeight: '600', marginTop: 2 },
  settingsIcon: { fontSize: 22 },
  scrollContent: { paddingBottom: 40 },

  heroCard: {
    backgroundColor: COLORS.ink,
    borderRadius: 20,
    margin: 16,
    padding: 20,
  },
  heroLabel: {
    fontSize: 11,
    color: COLORS.inkFaint,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 8,
  },
  heroAmount: {
    fontSize: 40,
    fontWeight: '300',
    color: COLORS.accent,
  },
  heroSubtext: {
    fontSize: 12,
    color: COLORS.inkFaint,
    marginTop: 4,
    marginBottom: 16,
  },
  heroButton: {
    backgroundColor: COLORS.accent,
    borderRadius: 12,
    paddingVertical: 12,
    alignItems: 'center',
  },
  heroButtonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '600',
  },

  statsRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 0,
    marginHorizontal: 16,
    marginBottom: 16,
  },
  statCard: {
    flex: 1,
    backgroundColor: COLORS.card,
    borderRadius: 14,
    padding: 14,
    borderWidth: 1,
    borderColor: COLORS.border,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 4,
    elevation: 2,
  },
  statIcon: { fontSize: 18, marginBottom: 6 },
  statValue: { fontSize: 18, color: COLORS.ink, fontWeight: '700' },
  statLabel: { fontSize: 11, color: COLORS.inkLight, marginTop: 2 },

  suggestionCard: {
    marginTop: 0,
    marginHorizontal: 16,
    marginBottom: 16,
    backgroundColor: COLORS.amberLight,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: COLORS.amber,
    padding: 16,
  },
  suggestionLabel: {
    fontSize: 10,
    color: COLORS.amber,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 8,
    fontWeight: '700',
  },
  suggestionText: {
    fontSize: 14,
    color: COLORS.ink,
  },

  sectionLabel: {
    fontSize: 11,
    color: COLORS.inkLight,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginTop: 0,
    marginHorizontal: 16,
    marginBottom: 10,
  },
  billRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: COLORS.card,
    paddingVertical: 14,
    paddingHorizontal: 16,
    marginHorizontal: 16,
    marginBottom: 10,
    borderRadius: 12,
  },
  billIcon: { fontSize: 20, marginRight: 12 },
  billInfo: { flex: 1 },
  billStore: { fontSize: 14, color: COLORS.ink, fontWeight: '600' },
  billDate: { fontSize: 12, color: COLORS.inkLight, marginTop: 2 },
  billAmounts: { alignItems: 'flex-end' },
  billTotal: { fontSize: 14, color: COLORS.ink, fontWeight: '600' },
  billAvoidable: { fontSize: 11, color: COLORS.red, marginTop: 2 },
});
