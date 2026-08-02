import { create } from 'zustand';
import * as SecureStore from 'expo-secure-store';

const useAuthStore = create((set) => ({
  user: null,
  accessToken: null,
  isLoading: true,
  isAuthenticated: false,

  setAuth: async (accessToken, refreshToken, userId, phone) => {
    try {
      await SecureStore.setItemAsync('access_token', accessToken);
      await SecureStore.setItemAsync('refresh_token', refreshToken);
      await SecureStore.setItemAsync('user_id', userId);
    } catch (error) {
      console.error('Failed to save auth tokens:', error);
    }
    set({
      user: { userId, phone },
      accessToken,
      isAuthenticated: true,
    });
  },

  clearAuth: async () => {
    try {
      await SecureStore.deleteItemAsync('access_token');
      await SecureStore.deleteItemAsync('refresh_token');
      await SecureStore.deleteItemAsync('user_id');
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
      if (token && userId) {
        set({
          accessToken: token,
          user: { userId },
          isAuthenticated: true,
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
}));

export default useAuthStore;
