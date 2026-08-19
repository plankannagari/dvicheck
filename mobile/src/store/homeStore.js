import { create } from 'zustand';
import { fetchHomeSummary, fetchRecentBills, fetchMonthlyReport, fetchTrends } from '../api/homeApi';

const useHomeStore = create((set, get) => ({
  summary: null,
  recentBills: [],
  monthlyReport: null,
  trends: null,
  isLoading: false,
  error: null,

  loadDashboard: async () => {
    set({ isLoading: true, error: null });
    try {
      const [summary, recentBills] = await Promise.all([
        fetchHomeSummary(),
        fetchRecentBills(5),
      ]);
      set({ summary, recentBills, isLoading: false });
    } catch (error) {
      console.error('loadDashboard error:', error);
      set({
        error: error.appError?.message || 'Something went wrong.',
        isLoading: false,
      });
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
  }),
}));

export default useHomeStore;
