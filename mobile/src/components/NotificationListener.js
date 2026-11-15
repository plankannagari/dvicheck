import { useEffect } from 'react';
import * as Notifications from 'expo-notifications';

import { navigate } from '../navigation/navigationRef';

export default function NotificationListener() {
  useEffect(() => {
    const responseSub = Notifications.addNotificationResponseReceivedListener((response) => {
      const data = response.notification.request.content.data;
      // Both types open the existing 'Insights' route (App.js) — there is no
      // 'InsightsDetail' screen; that route doesn't exist anywhere in this app.
      // WEEKLY_COACH (Day 35): WeeklyCoachCard sits at the top of InsightsScreen.
      if (data && (data.type === 'BUDGET_ALERT' || data.type === 'WEEKLY_COACH')) {
        navigate('Insights');
      }
    });
    return () => responseSub.remove();
  }, []);

  return null;
}
