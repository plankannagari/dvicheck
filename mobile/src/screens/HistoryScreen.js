import { useEffect } from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet,
  SafeAreaView, StatusBar, FlatList, RefreshControl, ActivityIndicator,
} from 'react-native';

import { COLORS } from '../constants';
import useHistoryStore from '../store/historyStore';

const CATEGORY_ORDER = ['ESSENTIAL', 'REDUCIBLE', 'AVOIDABLE', 'DUPLICATE'];
const CATEGORY_META = {
  ESSENTIAL: { label: 'Essential', dot: COLORS.green, bg: COLORS.greenLight, text: COLORS.green },
  REDUCIBLE: { label: 'Reducible', dot: COLORS.amber, bg: COLORS.amberLight, text: COLORS.amber },
  AVOIDABLE: { label: 'Avoidable', dot: COLORS.red, bg: COLORS.redLight, text: COLORS.red },
  DUPLICATE: { label: 'Duplicate', dot: COLORS.blue, bg: COLORS.blueLight, text: COLORS.blue },
};

const fmt = (n) => '$' + Number(n ?? 0).toFixed(2);
const fmtDate = (d) => d
  ? new Date(d).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
  : '';
const billIcon = (type) => (type === 'UTILITY' ? '⚡' : '🛒');

function SkeletonRow() {
  return (
    <View style={styles.billRow}>
      <View style={[styles.billIconBox, styles.skeletonFill]} />
      <View style={styles.billMeta}>
        <View style={[styles.skeletonFill, { height: 14, width: '60%', marginBottom: 6, borderRadius: 6 }]} />
        <View style={[styles.skeletonFill, { height: 11, width: '40%', borderRadius: 6 }]} />
      </View>
      <View style={{ alignItems: 'flex-end', gap: 6 }}>
        <View style={[styles.skeletonFill, { height: 14, width: 56, borderRadius: 6 }]} />
        <View style={[styles.skeletonFill, { height: 11, width: 40, borderRadius: 6 }]} />
      </View>
    </View>
  );
}

export default function HistoryScreen() {
  const {
    bills, activeBill, isLoading, isLoadingDetail, hasMore, error,
    loadBills, loadBillDetail, clearActiveBill,
  } = useHistoryStore();

  useEffect(() => { loadBills(true); }, []);

  const handleEndReached = () => {
    if (hasMore && !isLoading) {
      loadBills(false);
    }
  };

  if (activeBill || isLoadingDetail) {
    const lineItems = activeBill?.lineItems ?? [];
    const categoryCounts = CATEGORY_ORDER.reduce((acc, cat) => {
      acc[cat] = lineItems.filter((item) => item.category === cat).length;
      return acc;
    }, {});
    const flaggedCount = categoryCounts.REDUCIBLE + categoryCounts.AVOIDABLE;

    return (
      <SafeAreaView style={styles.safeArea}>
        <StatusBar barStyle="dark-content" backgroundColor={COLORS.bg} />

        <View style={styles.detailHeader}>
          <TouchableOpacity onPress={clearActiveBill} style={styles.backBtn} activeOpacity={0.7}>
            <Text style={styles.backArrow}>←</Text>
          </TouchableOpacity>
          <Text style={styles.detailTitle} numberOfLines={1}>{activeBill?.storeName ?? ''}</Text>
          <View style={styles.backBtn} />
        </View>

        {isLoadingDetail || !activeBill ? (
          <View style={styles.centerFill}>
            <ActivityIndicator size="large" color={COLORS.accent} />
          </View>
        ) : (
          <FlatList
            style={styles.scroll}
            contentContainerStyle={styles.itemsScrollContent}
            data={lineItems}
            keyExtractor={(item, i) => item.id ?? String(i)}
            ListHeaderComponent={
              <View>
                <View style={styles.summaryCard}>
                  <Text style={styles.storeName}>{activeBill.storeName}</Text>
                  <Text style={styles.billDate}>{fmtDate(activeBill.purchaseDate)}</Text>
                  <Text style={styles.totalAmount}>{fmt(activeBill.totalAmount)}</Text>
                  {Number(activeBill.avoidableAmount) > 0 && (
                    <View style={styles.savingsBadge}>
                      <Text style={styles.savingsBadgeText}>
                        {fmt(activeBill.avoidableAmount)} avoidable
                      </Text>
                    </View>
                  )}
                  {Number(activeBill.avoidableAmount) > 0 && (
                    <Text style={styles.flaggedText}>
                      {flaggedCount} item{flaggedCount !== 1 ? 's' : ''} flagged
                    </Text>
                  )}
                </View>

                <View style={styles.pillsRow}>
                  {CATEGORY_ORDER.map((cat) => {
                    const meta = CATEGORY_META[cat];
                    return (
                      <View key={cat} style={[styles.pill, { backgroundColor: meta.bg }]}>
                        <View style={[styles.pillDot, { backgroundColor: meta.dot }]} />
                        <Text style={[styles.pillText, { color: meta.text }]}>
                          {meta.label} {categoryCounts[cat]}
                        </Text>
                      </View>
                    );
                  })}
                </View>
              </View>
            }
            renderItem={({ item }) => {
              const meta = CATEGORY_META[item.category] || CATEGORY_META.ESSENTIAL;
              const showSuggestion = (item.category === 'REDUCIBLE' || item.category === 'AVOIDABLE') && !!item.suggestion;
              const showSaving = Number(item.savingEstimate) > 0;
              return (
                <View style={styles.itemRow}>
                  <View style={[styles.itemDot, { backgroundColor: meta.dot }]} />
                  <View style={styles.itemNameCol}>
                    <Text style={styles.itemName} numberOfLines={1}>{item.name}</Text>
                    {showSuggestion && (
                      <Text style={styles.itemSuggestion} numberOfLines={2}>{item.suggestion}</Text>
                    )}
                  </View>
                  <View style={styles.itemPriceCol}>
                    <Text style={styles.itemPrice}>{fmt(item.totalPrice)}</Text>
                    {showSaving && (
                      <Text style={styles.itemSaving}>Save {fmt(item.savingEstimate)}</Text>
                    )}
                  </View>
                </View>
              );
            }}
            ListEmptyComponent={
              <View style={styles.emptyState}>
                <Text style={styles.emptyText}>No line items on this bill</Text>
              </View>
            }
          />
        )}
      </SafeAreaView>
    );
  }

  // index mode
  const isFirstLoad = isLoading && bills.length === 0;

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" backgroundColor={COLORS.bg} />

      <View style={styles.header}>
        <Text style={styles.heading}>Bill History</Text>
      </View>

      {error ? (
        <View style={styles.errorBanner}>
          <Text style={styles.errorText}>{error}</Text>
        </View>
      ) : null}

      {isFirstLoad ? (
        <View style={styles.scrollContent}>
          {[1, 2, 3, 4, 5].map((i) => <SkeletonRow key={i} />)}
        </View>
      ) : (
        <FlatList
          style={styles.scroll}
          contentContainerStyle={styles.scrollContent}
          data={bills}
          keyExtractor={(item) => item.id}
          refreshControl={
            <RefreshControl refreshing={isLoading} onRefresh={() => loadBills(true)} tintColor={COLORS.accent} />
          }
          onEndReached={handleEndReached}
          onEndReachedThreshold={0.4}
          ListEmptyComponent={
            <View style={styles.emptyState}>
              <Text style={styles.emptyIcon}>🧾</Text>
              <Text style={styles.emptyText}>No bills yet. Scan your first receipt!</Text>
            </View>
          }
          renderItem={({ item }) => (
            <TouchableOpacity
              style={styles.billRow}
              activeOpacity={0.8}
              onPress={() => loadBillDetail(item.id)}
            >
              <View style={styles.billIconBox}>
                <Text style={styles.billIconText}>{billIcon(item.billType)}</Text>
              </View>
              <View style={styles.billMeta}>
                <Text style={styles.billStore} numberOfLines={1}>{item.storeName}</Text>
                <Text style={styles.billDateSmall}>{fmtDate(item.purchaseDate)}</Text>
              </View>
              <View style={styles.billAmounts}>
                <Text style={styles.billTotal}>{fmt(item.totalAmount)}</Text>
                {Number(item.avoidableAmount) > 0 && (
                  <Text style={styles.billAvoidable}>-{fmt(item.avoidableAmount)}</Text>
                )}
              </View>
            </TouchableOpacity>
          )}
          ListFooterComponent={
            isLoading && bills.length > 0
              ? <ActivityIndicator style={styles.footerSpinner} color={COLORS.accent} />
              : null
          }
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: COLORS.bg },
  scroll: { flex: 1 },
  scrollContent: { padding: 16, paddingBottom: 20 },
  centerFill: { flex: 1, alignItems: 'center', justifyContent: 'center' },

  header: { padding: 16, paddingHorizontal: 24 },
  heading: { fontSize: 22, color: COLORS.ink, fontWeight: '600' },

  errorBanner: {
    backgroundColor: COLORS.redLight, padding: 10, marginHorizontal: 16,
    borderRadius: 10, marginBottom: 12,
  },
  errorText: { fontSize: 12, color: COLORS.red },

  billRow: {
    flexDirection: 'row', alignItems: 'center',
    backgroundColor: COLORS.card, borderRadius: 16, padding: 14,
    borderWidth: 1, borderColor: COLORS.border, marginBottom: 10, gap: 12,
  },
  billIconBox: {
    width: 44, height: 44, borderRadius: 12,
    backgroundColor: COLORS.bg, alignItems: 'center', justifyContent: 'center',
  },
  billIconText: { fontSize: 20 },
  billMeta: { flex: 1 },
  billStore: { fontSize: 14, color: COLORS.ink, fontWeight: '600' },
  billDateSmall: { fontSize: 11, color: COLORS.inkLight, marginTop: 2 },
  billAmounts: { alignItems: 'flex-end' },
  billTotal: { fontSize: 15, color: COLORS.ink, fontWeight: '600' },
  billAvoidable: { fontSize: 11, color: COLORS.red, marginTop: 2 },
  footerSpinner: { marginVertical: 16 },

  skeletonFill: { backgroundColor: COLORS.border },

  emptyState: { padding: 40, alignItems: 'center' },
  emptyIcon: { fontSize: 40, marginBottom: 12 },
  emptyText: { fontSize: 14, color: COLORS.inkLight, textAlign: 'center' },

  // detail mode
  detailHeader: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 16, paddingVertical: 12,
  },
  backBtn: { width: 32, height: 32, alignItems: 'flex-start', justifyContent: 'center' },
  backArrow: { fontSize: 22, color: COLORS.ink },
  detailTitle: { flex: 1, textAlign: 'center', fontSize: 17, color: COLORS.ink, fontWeight: '600' },

  itemsScrollContent: { padding: 16, paddingBottom: 20 },
  summaryCard: {
    backgroundColor: COLORS.card, borderRadius: 16,
    padding: 20, alignItems: 'center', marginBottom: 16,
    borderWidth: 1, borderColor: COLORS.border,
  },
  storeName: { fontSize: 17, color: COLORS.ink, fontWeight: '600', marginBottom: 4 },
  billDate: { fontSize: 12, color: COLORS.inkLight, marginBottom: 12 },
  totalAmount: { fontSize: 32, color: COLORS.accent, fontWeight: '300' },
  savingsBadge: {
    backgroundColor: COLORS.redLight, borderRadius: 20,
    paddingHorizontal: 12, paddingVertical: 6, marginTop: 10,
  },
  savingsBadgeText: { fontSize: 12, color: COLORS.red, fontWeight: '600' },
  flaggedText: { fontSize: 11, color: COLORS.inkLight, marginTop: 6 },

  pillsRow: {
    flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 16,
  },
  pill: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: 10, paddingVertical: 6, borderRadius: 16,
  },
  pillDot: { width: 8, height: 8, borderRadius: 4, marginRight: 6 },
  pillText: { fontSize: 11, fontWeight: '600' },

  itemRow: {
    flexDirection: 'row', alignItems: 'flex-start',
    backgroundColor: COLORS.card, padding: 14, gap: 12,
    borderWidth: 1, borderColor: COLORS.border, borderRadius: 14, marginBottom: 8,
  },
  itemDot: { width: 8, height: 8, borderRadius: 4, marginTop: 3 },
  itemNameCol: { flex: 1 },
  itemName: { fontSize: 13, color: COLORS.ink },
  itemSuggestion: { fontSize: 11, color: COLORS.inkLight, fontStyle: 'italic', marginTop: 2 },
  itemPriceCol: { alignItems: 'flex-end' },
  itemPrice: { fontSize: 13, color: COLORS.ink, fontWeight: '600' },
  itemSaving: { fontSize: 11, color: COLORS.green, marginTop: 2 },
});
