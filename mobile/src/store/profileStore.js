import { create } from 'zustand';
import { fetchMe, updatePreferences } from '../api/userApi';

const useProfileStore = create((set) => ({
  profile: null,
  isLoading: false,
  isSaving: false,
  error: null,

  loadProfile: async () => {
    set({ isLoading: true, error: null });
    try {
      const result = await fetchMe();
      set({ profile: result, isLoading: false });
    } catch (error) {
      console.error('loadProfile error:', error);
      set({ error: 'Could not load your profile.', isLoading: false });
    }
  },

  savePreferences: async (prefs) => {
    set({ isSaving: true, error: null });
    try {
      const result = await updatePreferences(prefs);
      set({ profile: result, isSaving: false });
    } catch (error) {
      console.error('savePreferences error:', error);
      set({ error: 'Could not save your preferences.', isSaving: false });
      throw error;
    }
  },

  clearProfile: () => set({ profile: null, isLoading: false, isSaving: false, error: null }),
}));

export default useProfileStore;
