import { create } from 'zustand';
import { fetchWeeklyInsights } from '../api/insightsApi';

const useInsightsStore = create((set) => ({
  insights: null,
  isLoading: false,
  error: null,

  loadInsights: async () => {
    set({ isLoading: true, error: null });
    try {
      const result = await fetchWeeklyInsights();
      set({ insights: result, isLoading: false });
    } catch (error) {
      console.error('loadInsights error:', error);
      set({ error: error.appError?.message || 'Something went wrong.', isLoading: false });
    }
  },

  clearInsights: () => set({ insights: null, isLoading: false, error: null }),
}));

export default useInsightsStore;
