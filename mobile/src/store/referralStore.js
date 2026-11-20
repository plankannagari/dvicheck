import { create } from 'zustand';
import { fetchReferralSummary, redeemReferralCode } from '../api/referralApi';
import useProfileStore from './profileStore';
import { track, EVENTS } from '../analytics/analytics';

const useReferralStore = create((set, get) => ({
  // ReferralSummaryDto: { code, refereeDays, referrerDays, qualifiedCount, pendingCount,
  //   maxRewardedReferrals, referralProUntil, myReferralStatus, canRedeem }
  summary: null,
  status: 'idle', // 'idle' | 'loading' | 'ready' | 'error'
  error: null,

  // options.silent (foreground refresh, Day 38): no 'loading' status (that drives the
  // pull-to-refresh spinner) and failures leave the current summary untouched
  load: async (options = {}) => {
    const silent = options.silent === true;
    // A refresh keeps the current summary on screen; only the first load shows a skeleton
    if (!silent) set({ status: 'loading', error: null });
    try {
      const summary = await fetchReferralSummary();
      set({ summary, status: 'ready' });
    } catch (error) {
      if (silent) return;
      console.warn('referral load failed:', error?.appError?.message || error);
      set({ status: 'error', error: error.appError?.message || 'Something went wrong.' });
    }
  },

  // Returns { ok, message } with the server's message either way; never throws.
  // source: 'manual' (Referral screen) | 'deep_link' (dvicheck://invite/...) — analytics only.
  redeem: async (code, source = 'manual') => {
    try {
      const result = await redeemReferralCode(code);
      track(EVENTS.REFERRAL_REDEEMED, { result: 'ok', source }); // never the code
      get().load();                              // canRedeem/myReferralStatus change
      useProfileStore.getState().loadProfile();  // keep isPro in step (Pro arrives at first scan)
      return { ok: true, message: result?.message || 'Invite applied.' };
    } catch (error) {
      // 4xx = the server refused this code/user (unknown, own, used, too old...); else network/5xx
      const status = error.response?.status;
      const result = status >= 400 && status < 500 ? 'invalid' : 'error';
      track(EVENTS.REFERRAL_REDEEMED, { result, source });
      return { ok: false, message: error.appError?.message || 'Could not apply that code.' };
    }
  },

  // After a scan or a REFERRAL_REWARD push: Pro may have just been granted server-side
  refreshAfterReward: () => {
    useProfileStore.getState().loadProfile();
    get().load();
  },

  reset: () => set({ summary: null, status: 'idle', error: null }),
}));

export default useReferralStore;
