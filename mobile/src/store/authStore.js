import { create } from 'zustand';
import * as SecureStore from 'expo-secure-store';
import { registerForPushToken } from '../utils/notifications';
import { initPurchases } from '../utils/purchases';
import {
  identify, reset as resetAnalytics, track, EVENTS,
} from '../analytics/analytics';

const useAuthStore = create((set) => ({
  user: null,
  accessToken: null,
  isLoading: true,
  isAuthenticated: false,
  onboardingCompleted: true,

  setAuth: async (accessToken, refreshToken, userId, phone, onboardingCompleted) => {
    try {
      await SecureStore.setItemAsync('access_token', accessToken);
      await SecureStore.setItemAsync('refresh_token', refreshToken);
      await SecureStore.setItemAsync('user_id', userId);
      await SecureStore.setItemAsync('onboarding_completed', onboardingCompleted ? '1' : '0');
    } catch (error) {
      console.error('Failed to save auth tokens:', error);
    }
    registerForPushToken(); // fire and forget — no await needed
    initPurchases(userId).catch(() => {}); // fire and forget — same pattern as push token registration
    // Analytics identity = the backend user UUID only (never `phone`). Never throws.
    identify(userId);
    track(EVENTS.LOGIN_COMPLETED, { method: 'phone_otp' });
    set({
      user: { userId, phone },
      accessToken,
      isAuthenticated: true,
      onboardingCompleted: !!onboardingCompleted,
    });
  },

  clearAuth: async () => {
    // Logout event goes out under the current identity, THEN reset so the next
    // user on this device doesn't inherit it. Both never throw.
    track(EVENTS.LOGOUT);
    resetAnalytics();
    try {
      await SecureStore.deleteItemAsync('access_token');
      await SecureStore.deleteItemAsync('refresh_token');
      await SecureStore.deleteItemAsync('user_id');
      await SecureStore.deleteItemAsync('onboarding_completed');
    } catch (error) {
      console.error('Failed to clear auth tokens:', error);
    }
    set({
      user: null,
      accessToken: null,
      isAuthenticated: false,
    });
  },

  loadStoredAuth: async () => {
    try {
      const token = await SecureStore.getItemAsync('access_token');
      const userId = await SecureStore.getItemAsync('user_id');
      const stored = await SecureStore.getItemAsync('onboarding_completed');
      if (token && userId) {
        initPurchases(userId).catch(() => {}); // fire and forget — same pattern as push token registration
        identify(userId); // session restore: re-identify, no LOGIN_COMPLETED event
        set({
          accessToken: token,
          user: { userId },
          isAuthenticated: true,
          onboardingCompleted: stored === '1',
          isLoading: false,
        });
      } else {
        set({ isLoading: false });
      }
    } catch (error) {
      console.error('Failed to load stored auth:', error);
      set({ isLoading: false });
    }
  },

  markOnboardingComplete: async () => {
    await SecureStore.setItemAsync('onboarding_completed', '1');
    set({ onboardingCompleted: true });
  },
}));

export default useAuthStore;
