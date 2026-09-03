import { create } from 'zustand';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { fetchBills, fetchBillDetail, updateBill } from '../api/billApi';

const PAGE_SIZE = 20;
const HISTORY_CACHE_KEY = 'dvicheck_history_cache';

const useHistoryStore = create((set, get) => ({
  bills: [],
  activeBill: null,
  isLoading: false,
  isLoadingDetail: false,
  hasMore: true,
  page: 0,
  search: '',
  error: null,
  isOfflineCache: false,
  cachedAt: null,

  loadBills: async (refresh = false, search = '') => {
    // Pagination continuation (refresh=false) reuses whichever search is
    // already active in the store, rather than the caller's default '' —
    // otherwise scrolling to load more during an active search would
    // silently fetch the next page unfiltered.
    const activeSearch = refresh ? search : get().search;
    const page = refresh ? 0 : get().page;
    if (refresh) {
      set({ page: 0, bills: [], search: activeSearch });
    }
    set({ isLoading: true, error: null });
    try {
      const result = await fetchBills(page, PAGE_SIZE, activeSearch);
      set((state) => ({
        bills: refresh ? result : [...state.bills, ...result],
        hasMore: result.length === PAGE_SIZE,
        page: page + 1,
        isLoading: false,
        isOfflineCache: false,
        cachedAt: null,
      }));

      // Only the first page (refresh=true, index view) is cached — pagination
      // pages have no offline fallback anyway.
      if (refresh) {
        try {
          await AsyncStorage.setItem(
            HISTORY_CACHE_KEY,
            JSON.stringify({ bills: result, cachedAt: new Date().toISOString() })
          );
        } catch (cacheErr) {
          console.warn('loadBills: failed to write cache (ignored):', cacheErr);
        }
      }
    } catch (error) {
      console.error('loadBills error:', error);

      let hydratedFromCache = false;
      if (refresh) {
        try {
          const cached = await AsyncStorage.getItem(HISTORY_CACHE_KEY);
          if (cached) {
            const parsed = JSON.parse(cached);
            set({
              bills: parsed.bills,
              isLoading: false,
              isOfflineCache: true,
              cachedAt: parsed.cachedAt,
              error: null,
            });
            hydratedFromCache = true;
          }
        } catch (cacheErr) {
          console.warn('loadBills: failed to read cache (ignored):', cacheErr);
        }
      }

      // Non-refresh (pagination) failures, and refresh failures with no
      // cache available, keep the existing generic-error behavior.
      if (!hydratedFromCache) {
        set({ error: error.appError?.message || 'Something went wrong.', isLoading: false });
      }
    }
  },

  loadBillDetail: async (billId) => {
    set({ isLoadingDetail: true, error: null });
    try {
      const result = await fetchBillDetail(billId);
      set({ activeBill: result, isLoadingDetail: false });
    } catch (error) {
      console.error('loadBillDetail error:', error);
      set({ error: 'Could not load this bill.', isLoadingDetail: false });
    }
  },

  editBill: async (billId, updates) => {
    try {
      const updatedBill = await updateBill(billId, updates);

      set((state) => ({
        bills: state.bills.map((b) => (b.id === billId ? { ...b, ...updates } : b)),
      }));

      if (get().activeBill && get().activeBill.id === billId) {
        set({ activeBill: updatedBill });
      }

      return updatedBill;
    } catch (error) {
      console.error('editBill error:', error);
      set({ error: error.appError?.message || 'Something went wrong.' });
      throw error;
    }
  },

  clearActiveBill: () => set({ activeBill: null }),
}));

export default useHistoryStore;
