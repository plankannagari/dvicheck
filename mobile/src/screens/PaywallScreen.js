import { useEffect, useState } from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet,
  SafeAreaView, StatusBar, ActivityIndicator, ScrollView,
} from 'react-native';

import { COLORS } from '../constants';
import useToastStore from '../store/toastStore';
import useProfileStore from '../store/profileStore';
import Toast from '../components/Toast';
import {
  getCurrentOffering, purchasePackage, restorePurchases, isProFromCustomerInfo,
} from '../utils/purchases';

export default function PaywallScreen({ navigation }) {
  const [offering, setOffering] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [purchasingId, setPurchasingId] = useState(null);
  const [isRestoring, setIsRestoring] = useState(false);
  const {
    visible: toastVisible, message: toastMessage, type: toastType, showToast, hideToast,
  } = useToastStore();

  useEffect(() => {
    (async () => {
      try {
        const current = await getCurrentOffering();
        setOffering(current);
      } catch (e) {
        console.error('getCurrentOffering error:', e);
        showToast('Could not load subscription plans.', 'error');
      } finally {
        setIsLoading(false);
      }
    })();
  }, []);

  const handlePurchase = async (pkg) => {
    setPurchasingId(pkg.identifier);
    try {
      const customerInfo = await purchasePackage(pkg);
      if (isProFromCustomerInfo(customerInfo)) {
        showToast('Welcome to dvicheck Pro!', 'success');
        // Fire-and-forget: refresh profileStore so isPro reflects the purchase as soon
        // as the RevenueCat webhook has had a moment to land, rather than waiting for
        // the next natural profile fetch. loadProfile() never throws (sets its own
        // error state internally), so no catch is needed here.
        useProfileStore.getState().loadProfile();
        navigation.goBack();
      }
    } catch (e) {
      if (!e.userCancelled) {
        showToast('Purchase failed. Please try again.', 'error');
      }
    } finally {
      setPurchasingId(null);
    }
  };

  const handleRestore = async () => {
    setIsRestoring(true);
    try {
      const customerInfo = await restorePurchases();
      const isPro = isProFromCustomerInfo(customerInfo);
      showToast(
        isPro ? 'Pro access restored!' : 'No active subscription found',
        isPro ? 'success' : 'info'
      );
      if (isPro) useProfileStore.getState().loadProfile();
    } catch (e) {
      showToast('Restore failed. Please try again.', 'error');
    } finally {
      setIsRestoring(false);
    }
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" backgroundColor={COLORS.bg} />

      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn} activeOpacity={0.7}>
          <Text style={styles.backArrow}>←</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>dvicheck Pro</Text>
        <View style={styles.backBtn} />
      </View>

      {isLoading ? (
        <View style={styles.centerFill}>
          <ActivityIndicator size="large" color={COLORS.accent} />
        </View>
      ) : (
        <ScrollView contentContainerStyle={styles.scrollContent}>
          <Text style={styles.heading}>Unlock everything</Text>
          <Text style={styles.subheading}>
            Unlimited scans, AI suggestions, and full spending insights.
          </Text>

          {!offering || offering.availablePackages.length === 0 ? (
            <Text style={styles.emptyText}>No plans available right now.</Text>
          ) : (
            <View style={styles.packagesCard}>
              {offering.availablePackages.map((pkg, i) => (
                <TouchableOpacity
                  key={pkg.identifier}
                  style={[
                    styles.packageRow,
                    i < offering.availablePackages.length - 1 && styles.packageRowBorder,
                  ]}
                  onPress={() => handlePurchase(pkg)}
                  disabled={purchasingId !== null}
                  activeOpacity={0.8}
                >
                  <View style={styles.packageMeta}>
                    <Text style={styles.packageTitle}>{pkg.product.title}</Text>
                    <Text style={styles.packagePrice}>{pkg.product.priceString}</Text>
                  </View>
                  {purchasingId === pkg.identifier ? (
                    <ActivityIndicator color={COLORS.accent} />
                  ) : (
                    <Text style={styles.packageArrow}>→</Text>
                  )}
                </TouchableOpacity>
              ))}
            </View>
          )}

          <TouchableOpacity
            style={styles.restoreBtn}
            onPress={handleRestore}
            disabled={isRestoring}
            activeOpacity={0.7}
          >
            {isRestoring ? (
              <ActivityIndicator color={COLORS.inkLight} size="small" />
            ) : (
              <Text style={styles.restoreBtnText}>Restore Purchases</Text>
            )}
          </TouchableOpacity>
        </ScrollView>
      )}

      <Toast visible={toastVisible} message={toastMessage} type={toastType} onHide={hideToast} />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: COLORS.bg },
  centerFill: { flex: 1, alignItems: 'center', justifyContent: 'center' },

  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 16, paddingVertical: 12,
  },
  backBtn: { width: 32, height: 32, justifyContent: 'center' },
  backArrow: { fontSize: 22, color: COLORS.ink },
  headerTitle: { flex: 1, textAlign: 'center', fontSize: 17, color: COLORS.ink, fontWeight: '600' },

  scrollContent: { padding: 16, paddingBottom: 32 },
  heading: {
    fontSize: 24, fontWeight: '400', color: COLORS.ink,
    marginBottom: 8, letterSpacing: -0.3, textAlign: 'center',
  },
  subheading: {
    fontSize: 14, color: COLORS.inkLight, textAlign: 'center',
    lineHeight: 20, marginBottom: 28,
  },

  emptyText: { fontSize: 14, color: COLORS.inkLight, textAlign: 'center', padding: 20 },

  packagesCard: {
    backgroundColor: COLORS.card, borderRadius: 16, overflow: 'hidden',
    borderWidth: 1, borderColor: COLORS.border, marginBottom: 20,
  },
  packageRow: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    padding: 18,
  },
  packageRowBorder: { borderBottomWidth: 1, borderBottomColor: COLORS.border },
  packageMeta: { flex: 1, marginRight: 12 },
  packageTitle: { fontSize: 15, color: COLORS.ink, fontWeight: '600', marginBottom: 4 },
  packagePrice: { fontSize: 13, color: COLORS.inkLight },
  packageArrow: { fontSize: 18, color: COLORS.accent },

  restoreBtn: { alignItems: 'center', paddingVertical: 12 },
  restoreBtnText: { fontSize: 13, color: COLORS.inkLight, fontWeight: '600' },
});
