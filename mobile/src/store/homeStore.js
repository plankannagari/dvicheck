import { create } from 'zustand';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { fetchHomeSummary, fetchRecentBills, fetchMonthlyReport, fetchTrends } from '../api/homeApi';

const HOME_CACHE_KEY = 'dvicheck_home_cache';

const useHomeStore = create((set, get) => ({
  summary: null,
  recentBills: [],
  monthlyReport: null,
  trends: null,
  isLoading: false,
  error: null,
  isOfflineCache: false,
  cachedAt: null,

  loadDashboard: async () => {
    set({ isLoading: true, error: null });
    try {
      const [summary, recentBills] = await Promise.all([
        fetchHomeSummary(),
        fetchRecentBills(5),
      ]);
      set({ summary, recentBills, isLoading: false, isOfflineCache: false, cachedAt: null });

      try {
        await AsyncStorage.setItem(
          HOME_CACHE_KEY,
          JSON.stringify({ summary, recentBills, cachedAt: new Date().toISOString() })
        );
      } catch (cacheErr) {
        console.warn('loadDashboard: failed to write cache (ignored):', cacheErr);
      }
    } catch (error) {
      console.error('loadDashboard error:', error);

      let hydratedFromCache = false;
      try {
        const cached = await AsyncStorage.getItem(HOME_CACHE_KEY);
        if (cached) {
          const parsed = JSON.parse(cached);
          set({
            summary: parsed.summary,
            recentBills: parsed.recentBills,
            isLoading: false,
            isOfflineCache: true,
            cachedAt: parsed.cachedAt,
            error: null,
          });
          hydratedFromCache = true;
        }
      } catch (cacheErr) {
        console.warn('loadDashboard: failed to read cache (ignored):', cacheErr);
      }

      if (!hydratedFromCache) {
        set({
          error: error.appError?.message || 'Something went wrong.',
          isLoading: false,
        });
      }
    }
    get().loadMonthlyReport();
    get().loadTrends(); // fire and forget after existing calls
  },

  loadMonthlyReport: async () => {
    try {
      const monthlyReport = await fetchMonthlyReport();
      set({ monthlyReport });
    } catch (error) {
      // Non-critical — the report is a supplementary card, not core dashboard data,
      // so a failure here must never surface as a dashboard-level error.
      console.error('loadMonthlyReport error:', error);
    }
  },

  loadTrends: async () => {
    try {
      const trends = await fetchTrends();
      set({ trends });
    } catch (error) {
      // Non-critical — same reasoning as loadMonthlyReport, sparkline data shouldn't
      // block or error out the dashboard.
      console.warn('loadTrends error:', error);
    }
  },

  clearDashboard: () => set({
    summary: null,
    recentBills: [],
    monthlyReport: null,
    trends: null,
    isLoading: false,
    error: null,
    isOfflineCache: false,
    cachedAt: null,
  }),
}));

export default useHomeStore;
