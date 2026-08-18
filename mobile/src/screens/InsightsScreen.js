import { useEffect } from 'react';
import {
  View, Text, StyleSheet, SafeAreaView, StatusBar,
  ScrollView, RefreshControl, TouchableOpacity,
} from 'react-native';

import { COLORS } from '../constants';
import useInsightsStore from '../store/insightsStore';
import EmptyState from '../components/EmptyState';
import SkeletonList from '../components/SkeletonList';

const CATEGORY_ORDER = ['ESSENTIAL', 'REDUCIBLE', 'AVOIDABLE', 'DUPLICATE'];
const CATEGORY_META = {
  ESSENTIAL: { label: 'Essential', bar: COLORS.green },
  REDUCIBLE: { label: 'Reducible', bar: COLORS.amber },
  AVOIDABLE: { label: 'Avoidable', bar: COLORS.red },
  DUPLICATE: { label: 'Duplicate', bar: COLORS.blue },
};

const fmt = (n) => '$' + Number(n ?? 0).toFixed(2);
const fmtPct = (n) => (Number(n ?? 0) >= 0 ? '+' : '') + Number(n ?? 0).toFixed(1) + '%';
const fmtDateRange = (start, end) => {
  if (!start || !end) return '';
  const opts = { weekday: 'short', month: 'short', day: 'numeric' };
  const s = new Date(start).toLocaleDateString('en-US', opts);
  const e = new Date(end).toLocaleDateString('en-US', opts);
  return `${s} — ${e}`;
};

export default function InsightsScreen() {
  const { insights, isLoading, error, loadInsights } = useInsightsStore();

  useEffect(() => { loadInsights(); }, []);

  const isFirstLoad = isLoading && insights === null;
  const isEmpty = insights && Number(insights.totalSpent) === 0;

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" backgroundColor={COLORS.bg} />

      <View style={styles.header}>
        <Text style={styles.heading}>Weekly Insights</Text>
        {insights && (
          <Text style={styles.dateRange}>{fmtDateRange(insights.weekStart, insights.weekEnd)}</Text>
        )}
      </View>

      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl refreshing={isLoading} onRefresh={loadInsights} tintColor={COLORS.accent} />
        }
      >
        {isFirstLoad ? (
          <SkeletonList count={3} cardHeight={100} />
        ) : error ? (
          <View style={styles.errorState}>
            <Text style={styles.errorStateText}>{error}</Text>
            <TouchableOpacity style={styles.retryBtn} onPress={loadInsights} activeOpacity={0.8}>
              <Text style={styles.retryBtnText}>Retry</Text>
            </TouchableOpacity>
          </View>
        ) : !insights || isEmpty ? (
          <EmptyState
            icon="📊"
            title="Nothing to show yet"
            subtitle="Insights appear once you have scanned at least one receipt this week"
          />
        ) : (
          <>
            {/* Hero metrics row */}
            <View style={styles.heroRow}>
              <View style={styles.heroCard}>
                <Text style={styles.heroLabel}>TOTAL THIS WEEK</Text>
                <Text style={styles.heroAmount}>{fmt(insights.totalSpent)}</Text>
                <Text
                  style={[
                    styles.heroSub,
                    { color: insights.vsLastWeekPercent > 0 ? COLORS.red : COLORS.green },
                  ]}
                >
                  {fmtPct(insights.vsLastWeekPercent)} vs last week
                </Text>
              </View>

              <View style={styles.heroSideColumn}>
                <View style={[styles.sideCard, { backgroundColor: COLORS.redLight }]}>
                  <Text style={[styles.sideValue, { color: COLORS.red }]}>{fmt(insights.avoidableSpend)}</Text>
                  <Text style={[styles.sideLabel, { color: COLORS.red }]}>Avoidable</Text>
                </View>
                <View style={[styles.sideCard, { backgroundColor: COLORS.greenLight }]}>
                  <Text style={[styles.sideValue, { color: COLORS.green }]}>{insights.billsScanned}</Text>
                  <Text style={[styles.sideLabel, { color: COLORS.green }]}>Bills scanned</Text>
                </View>
              </View>
            </View>

            {/* Spend breakdown bars */}
            <Text style={styles.sectionLabel}>Spend by category</Text>
            <View style={styles.breakdownCard}>
              {(() => {
                const categorySum = CATEGORY_ORDER.reduce(
                  (sum, cat) => sum + Number(insights.spendByCategory?.[cat] ?? 0), 0
                );
                const visibleCategories = CATEGORY_ORDER.filter(
                  (cat) => Number(insights.spendByCategory?.[cat] ?? 0) > 0
                );
                if (visibleCategories.length === 0) {
                  return <Text style={styles.emptySub}>No categorised spend yet</Text>;
                }
                return visibleCategories.map((cat) => {
                  const meta = CATEGORY_META[cat];
                  const amount = Number(insights.spendByCategory[cat]);
                  const pct = categorySum > 0 ? (amount / categorySum) * 100 : 0;
                  return (
                    <View key={cat} style={styles.barRow}>
                      <View style={styles.barHeader}>
                        <Text style={styles.barLabel}>{meta.label}</Text>
                        <Text style={styles.barAmount}>{fmt(amount)}</Text>
                      </View>
                      <View style={styles.barTrack}>
                        <View style={[styles.barFill, { width: `${pct}%`, backgroundColor: meta.bar }]} />
                      </View>
                    </View>
                  );
                });
              })()}
            </View>

            {/* Spending coach card */}
            {insights.narrative && insights.narrative.trim() ? (
              <View style={styles.coachCard}>
                <View style={styles.coachHeader}>
                  <Text style={styles.coachIcon}>🧠</Text>
                  <Text style={styles.coachLabel}>Spending Coach</Text>
                </View>
                <Text style={styles.coachText}>{insights.narrative}</Text>
                <View style={styles.coachDivider} />
                <Text style={styles.coachFooter}>{insights.pattern}</Text>
              </View>
            ) : (
              <View style={styles.patternCard}>
                <Text style={styles.patternEmoji}>💡</Text>
                <View style={{ flex: 1 }}>
                  <Text style={styles.patternText}>{insights.pattern}</Text>
                  <Text style={styles.patternSub}>Based on this week vs last week</Text>
                </View>
              </View>
            )}

            {/* Top items */}
            <Text style={styles.sectionLabel}>Top items this week</Text>
            <View style={styles.itemsCard}>
              {!insights.topItems || insights.topItems.length === 0 ? (
                <Text style={styles.emptySub}>No items recorded this week</Text>
              ) : (
                insights.topItems.slice(0, 5).map((item, i) => {
                  const meta = CATEGORY_META[item.category] || CATEGORY_META.ESSENTIAL;
                  return (
                    <View
                      key={`${item.name}-${i}`}
                      style={[styles.itemRow, i < insights.topItems.length - 1 && styles.itemBorder]}
                    >
                      <View style={[styles.itemDot, { backgroundColor: meta.bar }]} />
                      <Text style={styles.itemName} numberOfLines={1}>{item.name}</Text>
                      <Text style={styles.itemPrice}>{fmt(item.totalPrice)}</Text>
                    </View>
                  );
                })
              )}
            </View>
          </>
        )}

        <View style={{ height: 20 }} />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: COLORS.bg },
  scroll: { flex: 1 },
  scrollContent: { padding: 16, paddingBottom: 20 },

  header: { paddingHorizontal: 24, paddingTop: 16, paddingBottom: 8 },
  heading: { fontSize: 22, color: COLORS.ink, fontWeight: '600' },
  dateRange: { fontSize: 12, color: COLORS.inkLight, marginTop: 4 },

  errorState: { padding: 32, alignItems: 'center' },
  errorStateText: { fontSize: 14, color: COLORS.red, textAlign: 'center', marginBottom: 16 },
  retryBtn: {
    backgroundColor: COLORS.accent, borderRadius: 12,
    paddingVertical: 12, paddingHorizontal: 24,
  },
  retryBtnText: { color: '#fff', fontSize: 14, fontWeight: '600' },

  emptySub: { fontSize: 13, color: COLORS.inkLight, padding: 4 },

  heroRow: { flexDirection: 'row', gap: 10, marginBottom: 20 },
  heroCard: {
    flex: 1.4, backgroundColor: COLORS.ink, borderRadius: 20, padding: 18,
    justifyContent: 'center',
  },
  heroLabel: { fontSize: 9, color: 'rgba(255,255,255,0.4)', letterSpacing: 1.5, marginBottom: 8 },
  heroAmount: { fontSize: 30, color: COLORS.accent, fontWeight: '300', letterSpacing: -1, marginBottom: 6 },
  heroSub: { fontSize: 12, fontWeight: '600' },

  heroSideColumn: { flex: 1, gap: 10 },
  sideCard: { flex: 1, borderRadius: 16, padding: 14, justifyContent: 'center' },
  sideValue: { fontSize: 18, fontWeight: '700', marginBottom: 2 },
  sideLabel: { fontSize: 10, fontWeight: '600' },

  sectionLabel: {
    fontSize: 10, color: COLORS.inkLight, letterSpacing: 2,
    textTransform: 'uppercase', marginBottom: 10,
  },

  breakdownCard: {
    backgroundColor: COLORS.card, borderRadius: 16, padding: 16,
    borderWidth: 1, borderColor: COLORS.border, marginBottom: 20, gap: 14,
  },
  barRow: {},
  barHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 6 },
  barLabel: { fontSize: 13, color: COLORS.ink, fontWeight: '600' },
  barAmount: { fontSize: 13, color: COLORS.inkLight },
  barTrack: { height: 8, borderRadius: 4, backgroundColor: COLORS.bg, overflow: 'hidden' },
  barFill: { height: '100%', borderRadius: 4 },

  patternCard: {
    flexDirection: 'row', gap: 12, backgroundColor: COLORS.amberLight,
    borderRadius: 16, padding: 16, marginBottom: 20, alignItems: 'flex-start',
  },
  patternEmoji: { fontSize: 22 },
  patternText: { fontSize: 14, color: COLORS.ink, lineHeight: 20, fontWeight: '600' },
  patternSub: { fontSize: 11, color: COLORS.inkLight, marginTop: 4 },

  coachCard: {
    backgroundColor: COLORS.card, borderRadius: 16, padding: 18, marginBottom: 20,
    borderLeftWidth: 3, borderLeftColor: COLORS.accent,
    shadowColor: '#000', shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06, shadowRadius: 6, elevation: 2,
  },
  coachHeader: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 10 },
  coachIcon: { fontSize: 16 },
  coachLabel: {
    fontSize: 10, color: COLORS.accent, fontWeight: '700',
    letterSpacing: 1.5, textTransform: 'uppercase',
  },
  coachText: { fontSize: 14, color: COLORS.ink, lineHeight: 22 },
  coachDivider: { height: 1, backgroundColor: COLORS.border, marginVertical: 12 },
  coachFooter: { fontSize: 11, color: COLORS.inkLight, fontStyle: 'italic' },

  itemsCard: {
    backgroundColor: COLORS.card, borderRadius: 16, overflow: 'hidden',
    borderWidth: 1, borderColor: COLORS.border,
  },
  itemRow: {
    flexDirection: 'row', alignItems: 'center', gap: 12,
    padding: 14, paddingHorizontal: 16,
  },
  itemBorder: { borderBottomWidth: 1, borderBottomColor: COLORS.border },
  itemDot: { width: 8, height: 8, borderRadius: 4 },
  itemName: { flex: 1, fontSize: 13, color: COLORS.ink },
  itemPrice: { fontSize: 13, color: COLORS.ink, fontWeight: '600' },
});
