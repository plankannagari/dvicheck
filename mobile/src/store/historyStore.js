import { create } from 'zustand';
import { fetchBills, fetchBillDetail, updateBill } from '../api/billApi';

const PAGE_SIZE = 20;

const useHistoryStore = create((set, get) => ({
  bills: [],
  activeBill: null,
  isLoading: false,
  isLoadingDetail: false,
  hasMore: true,
  page: 0,
  search: '',
  error: null,

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
      }));
    } catch (error) {
      console.error('loadBills error:', error);
      set({ error: error.appError?.message || 'Something went wrong.', isLoading: false });
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
