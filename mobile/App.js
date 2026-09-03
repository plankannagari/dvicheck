import 'react-native-gesture-handler';

import { useCallback, useEffect } from 'react';
import { View } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createStackNavigator } from '@react-navigation/stack';
import { StatusBar } from 'expo-status-bar';
// Aliased — this file already has a `SplashScreen` import for the auth-check
// screen component (./src/screens/SplashScreen); `* as SplashScreen` here
// would silently shadow it.
import * as ExpoSplashScreen from 'expo-splash-screen';
import * as Linking from 'expo-linking';

import ErrorBoundary from './src/components/ErrorBoundary';
import NotificationListener from './src/components/NotificationListener';
import Toast from './src/components/Toast';
import { navigationRef } from './src/navigation/navigationRef';
import MainTabNavigator from './src/navigation/MainTabNavigator';
import InsightsScreen from './src/screens/InsightsScreen';
import OnboardingScreen from './src/screens/OnboardingScreen';
import OTPVerifyScreen from './src/screens/OTPVerifyScreen';
import PhoneEntryScreen from './src/screens/PhoneEntryScreen';
import ProfileScreen from './src/screens/ProfileScreen';
import SplashScreen from './src/screens/SplashScreen';
import { handleIncomingUrl, setPendingDeepLink } from './src/utils/deepLinking';
import useAuthStore from './src/store/authStore';
import { initNetworkListener } from './src/store/networkStore';

ExpoSplashScreen.preventAutoHideAsync();

// Cold-start deep link — runs immediately on JS load, before Splash renders.
// Stashed as pending rather than processed here since auth state isn't known
// yet; SplashScreen consumes it once it has decided the user is authenticated.
Linking.getInitialURL().then((url) => {
  if (url) setPendingDeepLink(url);
});

initNetworkListener();

const Stack = createStackNavigator();

export default function App() {
  // Covers only the native-splash-to-JS handoff (this file rendering at all).
  // SplashScreen.js's own auth-check/onboarding routing is unrelated and untouched.
  const onLayoutRootView = useCallback(async () => {
    await ExpoSplashScreen.hideAsync();
  }, []);

  // Warm-start deep link — app already running in the background, user taps
  // a dvicheck:// link. Separate path from the cold-start one above.
  useEffect(() => {
    const sub = Linking.addEventListener('url', ({ url }) => {
      const isAuthenticated = useAuthStore.getState().isAuthenticated;
      handleIncomingUrl(url, isAuthenticated);
    });
    return () => sub.remove();
  }, []);

  return (
    <View style={{ flex: 1 }} onLayout={onLayoutRootView}>
      <ErrorBoundary>
        <NavigationContainer ref={navigationRef}>
          <Stack.Navigator initialRouteName="Splash" screenOptions={{ headerShown: false }}>
            <Stack.Screen name="Splash" component={SplashScreen} />
            <Stack.Screen name="Onboarding" component={OnboardingScreen} />
            <Stack.Screen name="PhoneEntry" component={PhoneEntryScreen} />
            <Stack.Screen name="OTPVerify" component={OTPVerifyScreen} />
            <Stack.Screen name="MainApp" component={MainTabNavigator} />
            <Stack.Screen name="Profile" component={ProfileScreen} />
            <Stack.Screen name="Insights" component={InsightsScreen} />
          </Stack.Navigator>
          <Toast />
          <NotificationListener />
        </NavigationContainer>
        <StatusBar style="auto" />
      </ErrorBoundary>
    </View>
  );
}
