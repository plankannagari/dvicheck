import { useEffect } from 'react';
import { AppState } from 'react-native';

import useAuthStore from '../store/authStore';
import useProfileStore from '../store/profileStore';
import useReferralStore from '../store/referralStore';

// Minimum gap between background refreshes, so quick app switches don't refetch every time
const MIN_INTERVAL_MS = 60 * 1000;

// Module-level on purpose: survives re-renders and listener re-subscriptions
let lastRefreshAt = 0;

/**
 * Day 38: when the app comes back to the foreground, silently refresh the profile (isPro,
 * budget alerts, coach unlock) and the referral summary — so Pro granted by an admin, a
 * referral reward or a webhook shows up without a manual refresh. No spinner, no toast,
 * errors ignored, no analytics event. The listener only exists while the user is signed in.
 */
export default function useForegroundRefresh() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  useEffect(() => {
    if (!isAuthenticated) return undefined;

    const sub = AppState.addEventListener('change', (state) => {
      if (state !== 'active') return;
      if (!useAuthStore.getState().isAuthenticated) return;
      const now = Date.now();
      if (now - lastRefreshAt < MIN_INTERVAL_MS) return;
      lastRefreshAt = now;
      // Both never throw; silent = no loading flags, failures keep what's on screen
      useProfileStore.getState().loadProfile({ silent: true });
      // Only if the summary was loaded this session: GET /referrals/me creates an invite code
      // lazily, so refreshing unconditionally would mint codes for users who never opened it
      const referral = useReferralStore.getState();
      if (referral.summary) referral.load({ silent: true });
    });

    // Removed on unmount AND on sign-out (isAuthenticated -> false re-runs this effect)
    return () => sub.remove();
  }, [isAuthenticated]);
}
