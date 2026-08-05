import { create } from 'zustand';
import {
  fetchLists, createList as createListApi, fetchListDetail,
  addItem as addItemApi, toggleItem as toggleItemApi, deleteItem as deleteItemApi,
} from '../api/shoppingApi';

const useShoppingStore = create((set, get) => ({
  lists: [],
  activeList: null,
  items: [],
  isLoading: false,
  error: null,

  loadLists: async () => {
    set({ isLoading: true, error: null });
    try {
      const lists = await fetchLists();
      set({ lists, isLoading: false });
    } catch (error) {
      console.error('loadLists error:', error);
      set({ error: 'Could not load your lists.', isLoading: false });
    }
  },

  createList: async (name) => {
    set({ isLoading: true, error: null });
    try {
      const list = await createListApi(name);
      set((state) => ({
        lists: [list, ...state.lists],
        activeList: list,
        items: [],
        isLoading: false,
      }));
      return list;
    } catch (error) {
      console.error('createList error:', error);
      set({ error: 'Could not create list.', isLoading: false });
      throw error;
    }
  },

  selectList: async (listId) => {
    set({ isLoading: true, error: null });
    try {
      const { list, items } = await fetchListDetail(listId);
      set({ activeList: list, items, isLoading: false });
    } catch (error) {
      console.error('selectList error:', error);
      set({ error: 'Could not load this list.', isLoading: false });
      throw error;
    }
  },

  addItem: async (name, quantity) => {
    try {
      const item = await addItemApi(get().activeList.id, name, quantity);
      set((state) => ({ items: [...state.items, item] }));
      return item;
    } catch (error) {
      console.error('addItem error:', error);
      set({ error: 'Could not add item.' });
      throw error;
    }
  },

  toggleItem: async (itemId) => {
    try {
      const updated = await toggleItemApi(get().activeList.id, itemId);
      set((state) => ({
        items: state.items.map((item) => (item.id === itemId ? updated : item)),
      }));
    } catch (error) {
      console.error('toggleItem error:', error);
      set({ error: 'Could not update item.' });
      throw error;
    }
  },

  removeItem: async (itemId) => {
    try {
      await deleteItemApi(get().activeList.id, itemId);
      set((state) => ({
        items: state.items.filter((item) => item.id !== itemId),
      }));
    } catch (error) {
      console.error('removeItem error:', error);
      set({ error: 'Could not remove item.' });
      throw error;
    }
  },

  clearActive: () => set({ activeList: null, items: [] }),
}));

export default useShoppingStore;
