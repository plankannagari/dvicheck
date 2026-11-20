import { create } from 'zustand';
import {
  fetchMe, updatePreferences, fetchBudgetAlerts, updateBudgetAlerts,
} from '../api/userApi';
import useToastStore from './toastStore';
import useInsightsStore from './insightsStore';
import { setProStatus } from '../analytics/analytics';

const useProfileStore = create((set, get) => ({
  profile: null,
  isLoading: false,
  isSaving: false,
  error: null,
  budgetAlerts: null, // { isPro, thresholds, paceAlertsEnabled, allowedThresholds }

  // options.silent (foreground refresh, Day 38): no isLoading flip, no error state, and the
  // coach card is only reloaded if isPro actually changed (loadCoach shows a skeleton)
  loadProfile: async (options = {}) => {
    const silent = options.silent === true;
    const previousIsPro = get().profile?.isPro;
    if (!silent) set({ isLoading: true, error: null });
    try {
      const result = await fetchMe();
      set(silent ? { profile: result } : { profile: result, isLoading: false });
      // Covers mount loads and PaywallScreen's post-purchase/restore refresh (both call
      // loadProfile). Backend isPro is authoritative; never throws.
      setProStatus(result?.isPro === true);
      // Chained here rather than at each call site, so every profile refresh —
      // Home/Profile mount and PaywallScreen's post-purchase/restore refresh —
      // also refreshes budget alerts (their isPro/thresholds follow the profile's).
      // Fire-and-forget; loadBudgetAlerts never throws.
      get().loadBudgetAlerts();
      // Same for the weekly coach card (Day 35): unlocks/locks as soon as isPro changes.
      // Free users make no network call (loadCoach short-circuits); never throws.
      if (!silent || previousIsPro !== result?.isPro) {
        useInsightsStore.getState().loadCoach(result?.isPro === true);
      }
    } catch (error) {
      if (silent) return; // background refresh: keep what's on screen, say nothing
      console.error('loadProfile error:', error);
      set({ error: 'Could not load your profile.', isLoading: false });
    }
  },

  loadBudgetAlerts: async () => {
    try {
      const result = await fetchBudgetAlerts();
      set({ budgetAlerts: result });
    } catch (error) {
      // Silent on purpose — the budget-alerts card just stays hidden
      console.warn('loadBudgetAlerts failed:', error?.appError?.message || error);
    }
  },

  // patch: { thresholds?, paceAlertsEnabled? }. Returns true on success.
  saveBudgetAlerts: async (patch) => {
    const previous = get().budgetAlerts;
    if (previous) {
      // Optimistic — sorted like the server will store them, so the UI doesn't reorder on success
      const optimistic = { ...previous, ...patch };
      if (patch.thresholds) optimistic.thresholds = [...patch.thresholds].sort((a, b) => a - b);
      set({ budgetAlerts: optimistic });
    }
    try {
      const result = await updateBudgetAlerts(patch);
      set({ budgetAlerts: result });
      return true;
    } catch (error) {
      set({ budgetAlerts: previous });
      // 402: apiClient's interceptor already showed a toast and opened the paywall —
      // don't navigate again or stack a second toast on top
      if (error?.appError?.code !== 'PRO_REQUIRED') {
        useToastStore.getState().showToast(
          error?.appError?.message || 'Could not save your alert settings.', 'error');
      }
      return false;
    }
  },

  savePreferences: async (prefs) => {
    set({ isSaving: true, error: null });
    try {
      const result = await updatePreferences(prefs);
      set({ profile: result, isSaving: false });
      setProStatus(result?.isPro === true); // PATCH response is a full profile too
    } catch (error) {
      console.error('savePreferences error:', error);
      set({ error: 'Could not save your preferences.', isSaving: false });
      throw error;
    }
  },

  setBudget: async (amount) => {
    await get().savePreferences({ budgetAmount: amount });
  },

  clearProfile: () => set({
    profile: null, isLoading: false, isSaving: false, error: null, budgetAlerts: null,
  }),
}));

export default useProfileStore;
