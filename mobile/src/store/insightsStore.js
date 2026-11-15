import { create } from 'zustand';
import { fetchWeeklyInsights, fetchLatestCoach } from '../api/insightsApi';

const useInsightsStore = create((set) => ({
  insights: null,
  isLoading: false,
  error: null,

  // Pro weekly coach (Day 35) — independent of `insights`/`error` above, so a coach
  // failure never turns the whole Insights screen into an error state
  coach: null,
  coachStatus: 'idle', // 'idle' | 'loading' | 'ready' | 'none' | 'locked' | 'error'

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

  loadCoach: async (isPro) => {
    if (!isPro) {
      // Free: render the locked card without a network call
      set({ coach: null, coachStatus: 'locked' });
      return;
    }
    set({ coachStatus: 'loading' });
    try {
      const result = await fetchLatestCoach();
      set({ coach: result, coachStatus: 'ready' });
    } catch (error) {
      const status = error.response?.status;
      // 404 = Pro but no message generated yet; 402 = lapsed since the profile loaded.
      // Anything else is silent: the card just doesn't render.
      set({
        coach: null,
        coachStatus: status === 404 ? 'none' : status === 402 ? 'locked' : 'error',
      });
    }
  },

  clearInsights: () => set({
    insights: null, isLoading: false, error: null, coach: null, coachStatus: 'idle',
  }),
}));

export default useInsightsStore;
