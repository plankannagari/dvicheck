import { create } from 'zustand';

let hideTimer = null;

const useToastStore = create((set) => ({
  visible: false,
  message: '',
  type: 'info', // 'info' | 'success' | 'error'

  showToast: (message, type = 'info', duration = 2500) => {
    if (hideTimer) clearTimeout(hideTimer);
    set({ visible: true, message, type });
    hideTimer = setTimeout(() => {
      set({ visible: false });
    }, duration);
  },

  hideToast: () => {
    if (hideTimer) clearTimeout(hideTimer);
    set({ visible: false });
  },
}));

export default useToastStore;
