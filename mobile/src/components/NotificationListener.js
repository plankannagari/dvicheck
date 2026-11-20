import { useEffect } from 'react';
import * as Notifications from 'expo-notifications';

import { navigate } from '../navigation/navigationRef';
import { track, EVENTS } from '../analytics/analytics';
import useReferralStore from '../store/referralStore';

export default function NotificationListener() {
  useEffect(() => {
    const responseSub = Notifications.addNotificationResponseReceivedListener((response) => {
      const data = response.notification.request.content.data;
      track(EVENTS.NOTIFICATION_OPENED, { type: typeof data?.type === 'string' ? data.type : 'unknown' });
      // Both types open the existing 'Insights' route (App.js) — there is no
      // 'InsightsDetail' screen; that route doesn't exist anywhere in this app.
      // WEEKLY_COACH (Day 35): WeeklyCoachCard sits at the top of InsightsScreen.
      if (data && (data.type === 'BUDGET_ALERT' || data.type === 'WEEKLY_COACH')) {
        navigate('Insights');
      } else if (data?.type === 'REFERRAL_REWARD') {
        // Day 37: Pro days were just granted — refresh isPro + the summary, then show them
        useReferralStore.getState().refreshAfterReward();
        navigate('Referral');
      }
    });
    return () => responseSub.remove();
  }, []);

  return null;
}
