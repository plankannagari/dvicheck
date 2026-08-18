import { create } from 'zustand';
import { fetchPantry } from '../api/pantryApi';

const usePantryStore = create((set) => ({
  items: [],
  isLoading: false,
  error: null,

  loadPantry: async () => {
    set({ isLoading: true, error: null });
    try {
      const items = await fetchPantry();
      set({ items, isLoading: false });
    } catch (error) {
      console.error('loadPantry error:', error);
      set({ error: error.appError?.message || 'Could not load pantry', isLoading: false });
    }
  },

  clearPantry: () => set({ items: [], isLoading: false, error: null }),
}));

export default usePantryStore;
