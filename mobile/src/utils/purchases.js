import Purchases from 'react-native-purchases';
import Constants from 'expo-constants';
import { Platform } from 'react-native';

export async function initPurchases(userId) {
  try {
    const apiKey = Platform.OS === 'ios'
      ? Constants.expoConfig.extra.revenueCatIosKey
      : Constants.expoConfig.extra.revenueCatAndroidKey;
    await Purchases.configure({ apiKey, appUserID: userId });
  } catch (e) {
    console.warn('RevenueCat init failed:', e);
  }
}

export async function getCurrentOffering() {
  const offerings = await Purchases.getOfferings();
  return offerings.current;
}

export async function purchasePackage(pkg) {
  const { customerInfo } = await Purchases.purchasePackage(pkg);
  return customerInfo;
}

export async function restorePurchases() {
  return Purchases.restorePurchases();
}

// Must match the exact Entitlement identifier configured in the RevenueCat
// dashboard — currently 'dvicheck_pro', not the generic 'pro'.
export function isProFromCustomerInfo(customerInfo) {
  return customerInfo?.entitlements?.active?.dvicheck_pro != null;
}
