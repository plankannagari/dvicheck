import { useEffect } from 'react';
import {
  View, Text, StyleSheet, SafeAreaView, StatusBar,
  FlatList, RefreshControl, TouchableOpacity,
} from 'react-native';

import { COLORS } from '../constants';
import usePantryStore from '../store/pantryStore';
import EmptyState from '../components/EmptyState';
import SkeletonList from '../components/SkeletonList';

const DEPLETION_META = {
  LOW: { dot: COLORS.red, bg: COLORS.redLight, text: COLORS.red, label: 'Running low' },
  MEDIUM: { dot: COLORS.amber, bg: COLORS.amberLight, text: COLORS.amber, label: 'Getting low' },
  OK: { dot: COLORS.green, bg: COLORS.greenLight, text: COLORS.green, label: 'Stocked' },
};

const daysAgo = (dateStr) => {
  if (!dateStr) return '';
  const bought = new Date(dateStr);
  const today = new Date();
  const d1 = new Date(bought.getFullYear(), bought.getMonth(), bought.getDate());
  const d2 = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const diffDays = Math.round((d2 - d1) / (1000 * 60 * 60 * 24));
  if (diffDays <= 0) return 'Today';
  if (diffDays === 1) return '1 day ago';
  return `${diffDays} days ago`;
};

export default function PantryScreen() {
  const { items, isLoading, error, loadPantry } = usePantryStore();

  useEffect(() => { loadPantry(); }, []);

  const isFirstLoad = isLoading && items.length === 0;

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" backgroundColor={COLORS.bg} />

      <View style={styles.header}>
        <Text style={styles.heading}>My Pantry</Text>
        <Text style={styles.subtext}>Learned from your scanned receipts</Text>
      </View>

      {isFirstLoad ? (
        <View style={styles.skeletonWrap}>
          <SkeletonList count={5} cardHeight={70} />
        </View>
      ) : error ? (
        <View style={styles.errorState}>
          <Text style={styles.errorStateText}>{error}</Text>
          <TouchableOpacity style={styles.retryBtn} onPress={loadPantry} activeOpacity={0.8}>
            <Text style={styles.retryBtnText}>Retry</Text>
          </TouchableOpacity>
        </View>
      ) : items.length === 0 ? (
        <EmptyState
          icon="🧺"
          title="Pantry is empty"
          subtitle="Items from your scanned receipts will appear here automatically"
        />
      ) : (
        <FlatList
          style={styles.scroll}
          contentContainerStyle={styles.scrollContent}
          data={items}
          keyExtractor={(item, i) => item.id ?? String(i)}
          refreshControl={
            <RefreshControl refreshing={isLoading} onRefresh={loadPantry} tintColor={COLORS.accent} />
          }
          ListHeaderComponent={
            <Text style={styles.sectionLabel}>
              {items.length} item{items.length !== 1 ? 's' : ''} tracked
            </Text>
          }
          renderItem={({ item }) => {
            const meta = DEPLETION_META[item.depletionStatus] || DEPLETION_META.OK;
            return (
              <View style={styles.itemRow}>
                <View style={[styles.itemDot, { backgroundColor: meta.dot }]} />
                <View style={styles.itemMeta}>
                  <Text style={styles.itemName} numberOfLines={1}>{item.itemName}</Text>
                  <Text style={styles.itemSub}>Last bought: {daysAgo(item.lastBoughtDate)}</Text>
                </View>
                <View style={styles.itemRightCol}>
                  <View style={[styles.badge, { backgroundColor: meta.bg }]}>
                    <Text style={[styles.badgeText, { color: meta.text }]}>{meta.label}</Text>
                  </View>
                  <Text style={styles.purchaseCount}>bought {item.purchaseCount} times</Text>
                </View>
              </View>
            );
          }}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: COLORS.bg },
  header: { padding: 16, paddingHorizontal: 24 },
  heading: { fontSize: 22, color: COLORS.ink, fontWeight: '600' },
  subtext: { fontSize: 12, color: COLORS.inkLight, marginTop: 4 },

  skeletonWrap: { padding: 16 },

  errorState: { padding: 32, alignItems: 'center' },
  errorStateText: { fontSize: 14, color: COLORS.red, textAlign: 'center', marginBottom: 16 },
  retryBtn: {
    backgroundColor: COLORS.accent, borderRadius: 12,
    paddingVertical: 12, paddingHorizontal: 24,
  },
  retryBtnText: { color: '#fff', fontSize: 14, fontWeight: '600' },

  scroll: { flex: 1 },
  scrollContent: { padding: 16, paddingBottom: 20 },
  sectionLabel: {
    fontSize: 10, color: COLORS.inkLight, letterSpacing: 2,
    textTransform: 'uppercase', marginBottom: 10,
  },

  itemRow: {
    flexDirection: 'row', alignItems: 'flex-start',
    backgroundColor: COLORS.card, borderRadius: 14, padding: 14,
    borderWidth: 1, borderColor: COLORS.border, marginBottom: 10, gap: 12,
  },
  itemDot: { width: 8, height: 8, borderRadius: 4, marginTop: 4 },
  itemMeta: { flex: 1 },
  itemName: { fontSize: 14, color: COLORS.ink },
  itemSub: { fontSize: 12, color: COLORS.inkLight, marginTop: 3 },
  itemRightCol: { alignItems: 'flex-end' },
  badge: { borderRadius: 10, paddingHorizontal: 8, paddingVertical: 3, marginBottom: 4 },
  badgeText: { fontSize: 11, fontWeight: '600' },
  purchaseCount: { fontSize: 10, color: COLORS.inkFaint },
});
