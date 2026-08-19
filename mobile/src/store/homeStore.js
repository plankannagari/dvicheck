import { create } from 'zustand';
import { fetchHomeSummary, fetchRecentBills, fetchMonthlyReport } from '../api/homeApi';

const useHomeStore = create((set, get) => ({
  summary: null,
  recentBills: [],
  monthlyReport: null,
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

  clearDashboard: () => set({
    summary: null,
    recentBills: [],
    monthlyReport: null,
    isLoading: false,
    error: null,
  }),
}));

export default useHomeStore;
