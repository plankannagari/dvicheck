import { create } from 'zustand';
import { fetchBills, fetchBillDetail } from '../api/billApi';

const PAGE_SIZE = 20;

const useHistoryStore = create((set, get) => ({
  bills: [],
  activeBill: null,
  isLoading: false,
  isLoadingDetail: false,
  hasMore: true,
  page: 0,
  error: null,

  loadBills: async (refresh = false) => {
    const page = refresh ? 0 : get().page;
    if (refresh) {
      set({ page: 0, bills: [] });
    }
    set({ isLoading: true, error: null });
    try {
      const result = await fetchBills(page, PAGE_SIZE);
      set((state) => ({
        bills: refresh ? result : [...state.bills, ...result],
        hasMore: result.length === PAGE_SIZE,
        page: page + 1,
        isLoading: false,
      }));
    } catch (error) {
      console.error('loadBills error:', error);
      set({ error: 'Could not load your bills.', isLoading: false });
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

  clearActiveBill: () => set({ activeBill: null }),
}));

export default useHistoryStore;
