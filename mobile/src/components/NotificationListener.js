import { useEffect } from 'react';
import * as Notifications from 'expo-notifications';

import { navigate } from '../navigation/navigationRef';

export default function NotificationListener() {
  useEffect(() => {
    const responseSub = Notifications.addNotificationResponseReceivedListener((response) => {
      const data = response.notification.request.content.data;
      if (data && data.type === 'BUDGET_ALERT') {
        // 'Insights' is the actual registered route name (App.js) — there is no
        // 'InsightsDetail' screen; that route doesn't exist anywhere in this app.
        navigate('Insights');
      }
    });
    return () => responseSub.remove();
  }, []);

  return null;
}
