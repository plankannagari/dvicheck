import * as Notifications from 'expo-notifications';
import * as Device from 'expo-device';
import apiClient from '../api/apiClient';

export async function registerForPushToken() {
  try {
    // Only works on physical devices
    if (!Device.isDevice) {
      console.log('Push notifications only work on physical devices');
      return;
    }
    const { status: existingStatus } = await Notifications.getPermissionsAsync();
    let finalStatus = existingStatus;
    if (existingStatus !== 'granted') {
      const { status } = await Notifications.requestPermissionsAsync();
      finalStatus = status;
    }
    if (finalStatus !== 'granted') {
      console.log('Push notification permission not granted');
      return;
    }
    const tokenData = await Notifications.getExpoPushTokenAsync();
    const token = tokenData.data;
    // Send to backend
    await apiClient.post('/users/me/push-token', { token });
    console.log('Push token registered:', token.substring(0, 20));
  } catch (err) {
    // Never crash the app over notifications
    console.log('Push token registration failed:', err.message);
  }
}
