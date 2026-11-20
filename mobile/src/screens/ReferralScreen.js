import { useCallback, useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet, SafeAreaView, StatusBar,
  ScrollView, RefreshControl, ActivityIndicator, KeyboardAvoidingView, Platform, Share,
  // Deprecated in RN core but still shipped (0.81); used to avoid adding a native module.
  // Swap for expo-clipboard at the next dev-client rebuild.
  Clipboard,
} from 'react-native';
import { useFocusEffect } from '@react-navigation/native';

import { COLORS } from '../constants';
import { buildInviteMessage } from '../config/referral';
import useReferralStore from '../store/referralStore';
import useToastStore from '../store/toastStore';
import { track, EVENTS } from '../analytics/analytics';
import Toast from '../components/Toast';
import EmptyState from '../components/EmptyState';
import SkeletonList from '../components/SkeletonList';

const fmtDate = (d) => new Date(d).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' });

export default function ReferralScreen({ navigation }) {
  const summary = useReferralStore((s) => s.summary);
  const status = useReferralStore((s) => s.status);
  const error = useReferralStore((s) => s.error);
  const load = useReferralStore((s) => s.load);
  const redeem = useReferralStore((s) => s.redeem);
  const {
    visible: toastVisible, message: toastMessage, type: toastType, showToast, hideToast,
  } = useToastStore();

  const [codeInput, setCodeInput] = useState('');
  const [isRedeeming, setIsRedeeming] = useState(false);

  // Reload on every focus: counts change when friends redeem or scan
  useFocusEffect(useCallback(() => { load(); }, [load]));

  const handleCopy = () => {
    try {
      Clipboard.setString(summary.code);
      track(EVENTS.REFERRAL_SHARED, { method: 'copy' }); // never the code itself
      showToast('Code copied', 'success');
    } catch (e) {
      showToast('Could not copy the code', 'error');
    }
  };

  const handleShare = async () => {
    try {
      const result = await Share.share({ message: buildInviteMessage(summary.code, summary.refereeDays) });
      // Only when actually shared (iOS reports dismissals), same rule as receipt_shared
      if (result?.action === Share.sharedAction) track(EVENTS.REFERRAL_SHARED, { method: 'share_sheet' });
    } catch (e) {
      console.warn('Invite share failed:', e);
    }
  };

  const handleRedeem = async () => {
    if (!codeInput.trim() || isRedeeming) return;
    setIsRedeeming(true);
    const { ok, message } = await redeem(codeInput);
    setIsRedeeming(false);
    showToast(message, ok ? 'success' : 'error');
    if (ok) setCodeInput('');
  };

  const isFirstLoad = status === 'loading' && summary === null;
  const proUntil = summary?.referralProUntil ? new Date(summary.referralProUntil) : null;
  const proActive = proUntil && proUntil.getTime() > Date.now();

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" backgroundColor={COLORS.bg} />

      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn} activeOpacity={0.7}>
          <Text style={styles.backArrow}>←</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Invite friends</Text>
        <View style={styles.backBtn} />
      </View>

      {isFirstLoad ? (
        <View style={styles.scrollContent}>
          <SkeletonList count={3} />
        </View>
      ) : !summary ? (
        <EmptyState
          icon="🎁"
          title="Couldn't load your invites"
          subtitle={error || 'Something went wrong.'}
          actionLabel="Retry"
          onAction={load}
        />
      ) : (
        <KeyboardAvoidingView
          style={styles.flex}
          behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        >
          <ScrollView
            contentContainerStyle={styles.scrollContent}
            keyboardShouldPersistTaps="handled"
            refreshControl={<RefreshControl refreshing={status === 'loading'} onRefresh={load} />}
          >
            <Text style={styles.explainer}>
              You both get Pro: {summary.referrerDays} days for you, {summary.refereeDays} days for
              your friend, after their first scan.
            </Text>

            <View style={styles.codeCard}>
              <Text style={styles.codeLabel}>Your invite code</Text>
              <Text style={styles.code} selectable>{summary.code}</Text>
              <View style={styles.codeActions}>
                <TouchableOpacity style={styles.secondaryBtn} onPress={handleCopy} activeOpacity={0.8}>
                  <Text style={styles.secondaryBtnText}>Copy</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.primaryBtn} onPress={handleShare} activeOpacity={0.8}>
                  <Text style={styles.primaryBtnText}>Share</Text>
                </TouchableOpacity>
              </View>
            </View>

            <Text style={styles.sectionLabel}>Your progress</Text>
            <View style={styles.card}>
              <View style={styles.row}>
                <Text style={styles.rowLabel}>
                  {summary.qualifiedCount} of {summary.maxRewardedReferrals} friends joined
                </Text>
              </View>
              <View style={[styles.row, !proActive && styles.rowLast]}>
                <Text style={styles.rowSub}>
                  {summary.pendingCount} waiting for their first scan
                </Text>
              </View>
              {proActive && (
                <View style={[styles.row, styles.rowLast]}>
                  <Text style={styles.rowLabel}>Pro from referrals until {fmtDate(proUntil)}</Text>
                </View>
              )}
            </View>

            {summary.myReferralStatus === 'PENDING' && (
              <View style={styles.notice}>
                <Text style={styles.noticeText}>
                  Invite applied - scan your first receipt to unlock your Pro days
                </Text>
              </View>
            )}

            {summary.canRedeem && (
              <>
                <Text style={styles.sectionLabel}>Have a friend's code?</Text>
                <View style={styles.card}>
                  <View style={[styles.row, styles.rowLast, styles.redeemRow]}>
                    <TextInput
                      style={styles.input}
                      value={codeInput}
                      onChangeText={setCodeInput}
                      placeholder="Enter code"
                      placeholderTextColor={COLORS.inkLight}
                      autoCapitalize="characters"
                      autoCorrect={false}
                      returnKeyType="done"
                      onSubmitEditing={handleRedeem}
                      maxLength={20}
                    />
                    <TouchableOpacity
                      style={[styles.primaryBtn, (!codeInput.trim() || isRedeeming) && styles.btnDisabled]}
                      onPress={handleRedeem}
                      disabled={!codeInput.trim() || isRedeeming}
                      activeOpacity={0.8}
                    >
                      {isRedeeming
                        ? <ActivityIndicator color="#fff" size="small" />
                        : <Text style={styles.primaryBtnText}>Redeem</Text>}
                    </TouchableOpacity>
                  </View>
                </View>
              </>
            )}
          </ScrollView>
        </KeyboardAvoidingView>
      )}

      <Toast visible={toastVisible} message={toastMessage} type={toastType} onHide={hideToast} />
    </SafeAreaView>
  );
}

// Header matches PaywallScreen; cards/rows match ProfileScreen's prefsCard / sectionLabel
const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: COLORS.bg },
  flex: { flex: 1 },

  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 16, paddingVertical: 12,
  },
  backBtn: { width: 32, height: 32, justifyContent: 'center' },
  backArrow: { fontSize: 22, color: COLORS.ink },
  headerTitle: { flex: 1, textAlign: 'center', fontSize: 17, color: COLORS.ink, fontWeight: '600' },

  scrollContent: { padding: 16, paddingBottom: 32 },
  explainer: { fontSize: 14, color: COLORS.inkLight, lineHeight: 20, textAlign: 'center', marginBottom: 20 },

  codeCard: {
    backgroundColor: COLORS.card, borderRadius: 16, padding: 20, alignItems: 'center',
    borderWidth: 1, borderColor: COLORS.border, marginBottom: 20,
  },
  codeLabel: {
    fontSize: 10, color: COLORS.inkLight, letterSpacing: 2, textTransform: 'uppercase', marginBottom: 10,
  },
  code: { fontSize: 32, color: COLORS.ink, fontWeight: '700', letterSpacing: 4, marginBottom: 16 },
  codeActions: { flexDirection: 'row', gap: 10, alignSelf: 'stretch' },

  primaryBtn: {
    flex: 1, backgroundColor: COLORS.accent, borderRadius: 12,
    paddingVertical: 12, paddingHorizontal: 16, alignItems: 'center', justifyContent: 'center',
  },
  primaryBtnText: { color: '#fff', fontSize: 15, fontWeight: '700' },
  secondaryBtn: {
    flex: 1, borderRadius: 12, borderWidth: 1, borderColor: COLORS.border,
    paddingVertical: 12, alignItems: 'center', justifyContent: 'center', backgroundColor: COLORS.bg,
  },
  secondaryBtnText: { color: COLORS.ink, fontSize: 15, fontWeight: '600' },
  btnDisabled: { opacity: 0.5 },

  sectionLabel: {
    fontSize: 10, color: COLORS.inkLight, letterSpacing: 2, textTransform: 'uppercase', marginBottom: 10,
  },
  card: {
    backgroundColor: COLORS.card, borderRadius: 16,
    borderWidth: 1, borderColor: COLORS.border, marginBottom: 20, overflow: 'hidden',
  },
  row: { padding: 16, borderBottomWidth: 1, borderBottomColor: COLORS.border },
  rowLast: { borderBottomWidth: 0 },
  rowLabel: { fontSize: 14, color: COLORS.ink, fontWeight: '600' },
  rowSub: { fontSize: 13, color: COLORS.inkLight },

  notice: {
    backgroundColor: COLORS.card, borderRadius: 12, padding: 14, marginBottom: 20,
    borderLeftWidth: 3, borderLeftColor: COLORS.accent,
  },
  noticeText: { fontSize: 13, color: COLORS.ink, lineHeight: 19 },

  redeemRow: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  input: {
    flex: 2, fontSize: 16, color: COLORS.ink, letterSpacing: 2,
    borderWidth: 1, borderColor: COLORS.border, borderRadius: 10,
    paddingHorizontal: 12, paddingVertical: 10, backgroundColor: COLORS.bg,
  },
});
