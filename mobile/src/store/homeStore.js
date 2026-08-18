import { create } from 'zustand';
import { fetchHomeSummary, fetchRecentBills } from '../api/homeApi';

const useHomeStore = create((set) => ({
  summary: null,
  recentBills: [],
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
  },

  clearDashboard: () => set({
    summary: null,
    recentBills: [],
    isLoading: false,
    error: null,
  }),
}));

export default useHomeStore;
