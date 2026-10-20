import axios from 'axios';
import * as SecureStore from 'expo-secure-store';

import { API_BASE_URL } from '../constants';
import { navigate, navigationRef } from '../navigation/navigationRef';
import useToastStore from '../store/toastStore';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(async (config) => {
  const token = await SecureStore.getItemAsync('access_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    // 402 PRO_REQUIRED — a free-plan limit was hit. Say why, then open the paywall,
    // unless the caller opted out (background work like the offline sync queue must
    // never pop a screen the user didn't ask for), and don't stack a second one.
    // No early return: falls through so appError is still set below — screens and
    // syncQueue check appError.code === 'PRO_REQUIRED'.
    if (error.response?.status === 402 && !error.config?.skipPaywall) {
      useToastStore.getState().showToast(
        error.response.data?.message || 'Upgrade to Pro to continue', 'info');
      if (navigationRef.isReady() && navigationRef.getCurrentRoute()?.name !== 'Paywall') {
        navigate('Paywall');
      }
    }

    if (error.response?.status === 401) {
      await SecureStore.deleteItemAsync('access_token');
      await SecureStore.deleteItemAsync('refresh_token');
      await SecureStore.deleteItemAsync('user_id');
      console.log('Session expired — tokens cleared');
    }

    if (error.response) {
      error.appError = {
        code: error.response.data?.code || 'UNKNOWN_ERROR',
        message: error.response.data?.message || 'Something went wrong. Please try again.',
        isNetworkError: false,
      };
    } else if (error.request) {
      error.appError = {
        code: 'NETWORK_ERROR',
        message: "Can't reach the server. Check your connection and try again.",
        isNetworkError: true,
      };
    } else {
      error.appError = {
        code: 'CLIENT_ERROR',
        message: 'Something went wrong. Please try again.',
        isNetworkError: false,
      };
    }

    return Promise.reject(error);
  }
);

export default apiClient;
