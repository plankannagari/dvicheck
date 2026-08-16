import axios from 'axios';
import * as SecureStore from 'expo-secure-store';

import { API_BASE_URL } from '../constants';

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
