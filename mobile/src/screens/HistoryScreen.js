import { useEffect, useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet,
  SafeAreaView, StatusBar, FlatList, RefreshControl, ActivityIndicator, Modal,
  KeyboardAvoidingView, Platform, TouchableWithoutFeedback, Share,
} from 'react-native';
import * as Linking from 'expo-linking';

import { COLORS } from '../constants';
import useHistoryStore from '../store/historyStore';
import useToastStore from '../store/toastStore';
import EmptyState from '../components/EmptyState';
import { submitFeedback } from '../api/feedbackApi';

// RESTAURANT was in the original spec but isn't a valid BillType — the Java enum and the
// bills.bill_type DB CHECK constraint only allow these three.
const BILL_TYPES = ['GROCERY', 'UTILITY', 'OTHER'];

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
// No relative-time utility exists in the codebase yet — kept simple rather
// than adding a dependency: minute/hour buckets, falling back to a plain
// clock time for anything older than a day.
const formatCacheTime = (isoString) => {
  if (!isoString) return 'earlier';
  const diffMin = Math.round((Date.now() - new Date(isoString).getTime()) / 60000);
  if (diffMin < 1) return 'just now';
  if (diffMin < 60) return `${diffMin} minute${diffMin !== 1 ? 's' : ''} ago`;
  const diffHours = Math.round(diffMin / 60);
  if (diffHours < 24) return `${diffHours} hour${diffHours !== 1 ? 's' : ''} ago`;
  return new Date(isoString).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
};
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
    loadBills, loadBillDetail, clearActiveBill, editBill,
    isOfflineCache, cachedAt,
  } = useHistoryStore();
  const { showToast } = useToastStore();

  const [search, setSearch] = useState('');
  const [editingBill, setEditingBill] = useState(null);
  const [editStoreName, setEditStoreName] = useState('');
  const [editBillType, setEditBillType] = useState('GROCERY');
  const [isSavingEdit, setIsSavingEdit] = useState(false);
  const [feedbackState, setFeedbackState] = useState({});
  const [thankYouItems, setThankYouItems] = useState({});

  useEffect(() => { loadBills(true); }, []);

  useEffect(() => {
    const timer = setTimeout(() => {
      loadBills(true, search); // reset + search
    }, 400);
    return () => clearTimeout(timer);
  }, [search]);

  const handleEndReached = () => {
    if (hasMore && !isLoading) {
      loadBills(false);
    }
  };

  const handleLongPressBill = (bill) => {
    setEditingBill(bill);
    setEditStoreName(bill.storeName);
    setEditBillType(bill.billType);
  };

  const handleSaveEdit = async () => {
    setIsSavingEdit(true);
    try {
      await editBill(editingBill.id, { storeName: editStoreName, billType: editBillType });
      setEditingBill(null);
      showToast('Bill updated', 'success');
    } catch (err) {
      showToast(err.appError?.message || 'Could not update bill.', 'error');
    } finally {
      setIsSavingEdit(false);
    }
  };

  const handleFeedback = async (lineItemId, feedback) => {
    const previous = feedbackState[lineItemId] ?? null;
    setFeedbackState((prev) => ({ ...prev, [lineItemId]: feedback }));
    try {
      await submitFeedback(lineItemId, feedback);
    } catch (err) {
      console.error('submitFeedback error:', err);
      setFeedbackState((prev) => ({ ...prev, [lineItemId]: previous }));
      showToast('Could not save feedback', 'error');
    }
  };

  const handleFeedbackTap = (lineItemId, feedback) => {
    handleFeedback(lineItemId, feedback);
    setThankYouItems((prev) => ({ ...prev, [lineItemId]: true }));
    setTimeout(() => {
      setThankYouItems((prev) => ({ ...prev, [lineItemId]: false }));
    }, 1500);
  };

  const handleShare = async () => {
    if (!activeBill) return;
    const deepLink = Linking.createURL('bill/' + activeBill.id);
    const avoidable = Number(activeBill.avoidableAmount) > 0
      ? ', ' + fmt(activeBill.avoidableAmount) + ' of it avoidable'
      : '';
    const message = 'I spent ' + fmt(activeBill.totalAmount) + ' at ' +
      activeBill.storeName + avoidable + '.\n\nView the receipt: ' + deepLink;
    try {
      await Share.share({ message, url: deepLink, title: 'dvicheck receipt' });
    } catch (e) {
      console.warn('Share failed:', e);
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
          <TouchableOpacity onPress={handleShare} style={styles.shareBtn}>
            <Text style={styles.shareBtnText}>Share</Text>
          </TouchableOpacity>
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
              const showThanks = !!thankYouItems[item.id];
              const feedbackValue = feedbackState[item.id] ?? null;
              return (
                <View style={styles.itemRow}>
                  <View style={[styles.itemDot, { backgroundColor: meta.dot }]} />
                  <View style={styles.itemNameCol}>
                    <Text style={styles.itemName} numberOfLines={1}>{item.name}</Text>
                    {showSuggestion && (
                      <Text style={styles.itemSuggestion} numberOfLines={2}>{item.suggestion}</Text>
                    )}
                    {showSuggestion && (
                      // Same feedback-row layout as ScanScreen's ItemRow — could be
                      // extracted into a shared component if a third screen needs it.
                      <View style={styles.feedbackRow}>
                        {showThanks ? (
                          <Text style={styles.feedbackThanks}>Thanks!</Text>
                        ) : (
                          <>
                            <Text style={styles.feedbackLabel}>Was this helpful?</Text>
                            <TouchableOpacity
                              style={[
                                styles.feedbackBtn,
                                feedbackValue === 'HELPFUL' && styles.feedbackBtnUpSelected,
                                feedbackValue === 'UNHELPFUL' && styles.feedbackBtnGreyed,
                              ]}
                              onPress={() => handleFeedbackTap(item.id, 'HELPFUL')}
                              activeOpacity={0.7}
                            >
                              <Text style={styles.feedbackBtnIcon}>👍</Text>
                            </TouchableOpacity>
                            <TouchableOpacity
                              style={[
                                styles.feedbackBtn,
                                feedbackValue === 'UNHELPFUL' && styles.feedbackBtnDownSelected,
                                feedbackValue === 'HELPFUL' && styles.feedbackBtnGreyed,
                              ]}
                              onPress={() => handleFeedbackTap(item.id, 'UNHELPFUL')}
                              activeOpacity={0.7}
                            >
                              <Text style={styles.feedbackBtnIcon}>👎</Text>
                            </TouchableOpacity>
                          </>
                        )}
                      </View>
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

      {isOfflineCache && (
        <View style={styles.offlineCacheBanner} pointerEvents="none">
          <Text style={styles.offlineCacheBannerText}>
            Offline — showing cached data from {formatCacheTime(cachedAt)}
          </Text>
        </View>
      )}

      <View style={styles.searchBar}>
        <Text style={styles.searchIcon}>🔍</Text>
        <TextInput
          style={styles.searchInput}
          placeholder="Search by store name..."
          placeholderTextColor={COLORS.inkFaint}
          value={search}
          onChangeText={setSearch}
          returnKeyType="search"
        />
        {search.length > 0 && (
          <TouchableOpacity onPress={() => setSearch('')} activeOpacity={0.7} style={styles.clearBtn}>
            <Text style={styles.clearBtnText}>✕</Text>
          </TouchableOpacity>
        )}
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
            search.length > 0 ? (
              <EmptyState
                icon="🔍"
                title="No results"
                subtitle={`No bills matching "${search}"`}
              />
            ) : (
              <EmptyState
                icon="🧾"
                title="No bills yet"
                subtitle="Scan your first receipt!"
              />
            )
          }
          renderItem={({ item }) => (
            <TouchableOpacity
              style={styles.billRow}
              activeOpacity={0.8}
              onPress={() => loadBillDetail(item.id)}
              onLongPress={() => handleLongPressBill(item)}
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
              <Text style={styles.editHint}>✏️</Text>
            </TouchableOpacity>
          )}
          ListFooterComponent={
            isLoading && bills.length > 0
              ? <ActivityIndicator style={styles.footerSpinner} color={COLORS.accent} />
              : null
          }
        />
      )}

      <Modal
        visible={!!editingBill}
        transparent
        animationType="slide"
        onRequestClose={() => setEditingBill(null)}
      >
        <KeyboardAvoidingView
          behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
          style={styles.modalOverlay}
        >
          <TouchableWithoutFeedback onPress={() => setEditingBill(null)}>
            <View style={styles.overlayBackdrop} />
          </TouchableWithoutFeedback>
          <View style={styles.editSheet}>
            <View style={styles.editHeader}>
              <Text style={styles.editTitle}>Edit Bill</Text>
              <TouchableOpacity onPress={() => setEditingBill(null)} activeOpacity={0.7}>
                <Text style={styles.editCloseBtn}>✕</Text>
              </TouchableOpacity>
            </View>

            <Text style={styles.editLabel}>Store name</Text>
            <TextInput
              style={styles.editInput}
              value={editStoreName}
              onChangeText={setEditStoreName}
              placeholder="Store name"
              placeholderTextColor={COLORS.inkFaint}
            />

            <Text style={styles.editLabel}>Bill type</Text>
            <View style={styles.editChipsRow}>
              {BILL_TYPES.map((type) => {
                const selected = type === editBillType;
                return (
                  <TouchableOpacity
                    key={type}
                    style={[styles.editChip, selected && styles.editChipSelected]}
                    onPress={() => setEditBillType(type)}
                    activeOpacity={0.8}
                  >
                    <Text style={[styles.editChipText, selected && styles.editChipTextSelected]}>
                      {type}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </View>

            <TouchableOpacity
              style={styles.editSaveBtn}
              onPress={handleSaveEdit}
              activeOpacity={0.8}
              disabled={isSavingEdit}
            >
              {isSavingEdit ? (
                <ActivityIndicator color="#fff" />
              ) : (
                <Text style={styles.editSaveBtnText}>Save</Text>
              )}
            </TouchableOpacity>
          </View>
        </KeyboardAvoidingView>
      </Modal>
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

  offlineCacheBanner: {
    backgroundColor: COLORS.amberLight, marginHorizontal: 16, marginBottom: 12,
    borderRadius: 10, paddingVertical: 8, paddingHorizontal: 14,
  },
  offlineCacheBannerText: { fontSize: 12, color: COLORS.amber, lineHeight: 17 },

  searchBar: {
    flexDirection: 'row', alignItems: 'center',
    backgroundColor: COLORS.card, borderRadius: 12,
    borderWidth: 1, borderColor: COLORS.border,
    marginHorizontal: 16, marginBottom: 12, paddingHorizontal: 12,
  },
  searchIcon: { fontSize: 13, marginRight: 8 },
  searchInput: { flex: 1, paddingVertical: 10, fontSize: 14, color: COLORS.ink },
  clearBtn: { padding: 4, marginLeft: 4 },
  clearBtnText: { fontSize: 13, color: COLORS.inkLight },

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
  editHint: { fontSize: 11, opacity: 0.35, marginLeft: 8 },
  footerSpinner: { marginVertical: 16 },

  modalOverlay: {
    flex: 1, justifyContent: 'flex-end',
  },
  overlayBackdrop: {
    ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(0,0,0,0.4)',
  },
  editSheet: {
    backgroundColor: COLORS.card, borderTopLeftRadius: 20, borderTopRightRadius: 20,
    padding: 24, width: '100%',
  },
  editHeader: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20,
  },
  editTitle: { fontSize: 17, color: COLORS.ink, fontWeight: '600' },
  editCloseBtn: { fontSize: 18, color: COLORS.inkLight, padding: 4 },
  editLabel: { fontSize: 11, color: COLORS.inkLight, marginBottom: 8, marginTop: 12 },
  editInput: {
    backgroundColor: COLORS.bg, borderRadius: 12,
    borderWidth: 1, borderColor: COLORS.border,
    paddingHorizontal: 14, paddingVertical: 12, fontSize: 14, color: COLORS.ink,
  },
  editChipsRow: { flexDirection: 'row', gap: 8, flexWrap: 'wrap' },
  editChip: { borderRadius: 16, paddingHorizontal: 14, paddingVertical: 8, backgroundColor: COLORS.border },
  editChipSelected: { backgroundColor: COLORS.accent },
  editChipText: { fontSize: 12, fontWeight: '600', color: COLORS.inkLight },
  editChipTextSelected: { color: '#fff' },
  editSaveBtn: {
    backgroundColor: COLORS.accent, borderRadius: 12,
    paddingVertical: 14, alignItems: 'center', marginTop: 24,
  },
  editSaveBtnText: { color: '#fff', fontSize: 15, fontWeight: '600' },

  skeletonFill: { backgroundColor: COLORS.border },

  emptyState: { padding: 40, alignItems: 'center' },
  emptyText: { fontSize: 14, color: COLORS.inkLight, textAlign: 'center' },

  // detail mode
  detailHeader: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 16, paddingVertical: 12,
  },
  backBtn: { width: 32, height: 32, alignItems: 'flex-start', justifyContent: 'center' },
  backArrow: { fontSize: 22, color: COLORS.ink },
  shareBtn: { width: 72, alignItems: 'flex-end' },
  shareBtnText: { fontSize: 14, color: COLORS.accent },
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

  // Feedback row — same layout/values as ScanScreen's ItemRow feedback UI.
  feedbackRow: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 6 },
  feedbackLabel: { fontSize: 11, color: COLORS.inkLight },
  feedbackBtn: {
    width: 26, height: 26, borderRadius: 13,
    alignItems: 'center', justifyContent: 'center',
    backgroundColor: COLORS.bg, borderWidth: 1, borderColor: COLORS.border,
  },
  feedbackBtnIcon: { fontSize: 13 },
  feedbackBtnUpSelected: { backgroundColor: COLORS.accent, borderColor: COLORS.accent },
  feedbackBtnDownSelected: { backgroundColor: COLORS.red, borderColor: COLORS.red },
  feedbackBtnGreyed: { opacity: 0.35 },
  feedbackThanks: { fontSize: 11, color: COLORS.green, fontWeight: '600' },
});
