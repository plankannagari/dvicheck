import { create } from 'zustand';
import * as SecureStore from 'expo-secure-store';
import { registerForPushToken } from '../utils/notifications';

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
    set({
      user: { userId, phone },
      accessToken,
      isAuthenticated: true,
      onboardingCompleted: !!onboardingCompleted,
    });
  },

  clearAuth: async () => {
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
