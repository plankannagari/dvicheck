import 'react-native-gesture-handler';

import { useCallback, useEffect, useRef } from 'react';
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
import PaywallScreen from './src/screens/PaywallScreen';
import PhoneEntryScreen from './src/screens/PhoneEntryScreen';
import ProfileScreen from './src/screens/ProfileScreen';
import ReferralScreen from './src/screens/ReferralScreen';
import SplashScreen from './src/screens/SplashScreen';
import { handleIncomingUrl, setPendingDeepLink } from './src/utils/deepLinking';
import useAuthStore from './src/store/authStore';
import { initNetworkListener } from './src/store/networkStore';
import { initAnalytics, track, EVENTS } from './src/analytics/analytics';

ExpoSplashScreen.preventAutoHideAsync();

// Cold-start deep link — runs immediately on JS load, before Splash renders.
// Stashed as pending rather than processed here since auth state isn't known
// yet; SplashScreen consumes it once it has decided the user is authenticated.
Linking.getInitialURL().then((url) => {
  if (url) setPendingDeepLink(url);
});

initNetworkListener();

// Fire-and-forget at module load — never awaited, so it can't delay rendering or the
// splash handoff. Calls made before init resolves are queued inside the analytics module.
initAnalytics().then(() => track(EVENTS.APP_OPENED));

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

  // Screen tracking (Day 36). Route NAMES only — never route params, which can carry
  // bill ids or amounts. getCurrentRoute() is the focused leaf route, so tab switches
  // inside MainApp report 'Home'/'Scan'/... rather than 'MainApp'.
  const previousRouteName = useRef(undefined);

  const onNavigationReady = useCallback(() => {
    previousRouteName.current = navigationRef.getCurrentRoute()?.name;
  }, []);

  const onNavigationStateChange = useCallback(() => {
    const current = navigationRef.getCurrentRoute()?.name;
    const previous = previousRouteName.current;
    if (current && current !== previous) {
      track(EVENTS.SCREEN_VIEWED, { screen: current, previous_screen: previous });
      previousRouteName.current = current;
    }
  }, []);

  return (
    <View style={{ flex: 1 }} onLayout={onLayoutRootView}>
      <ErrorBoundary>
        <NavigationContainer
          ref={navigationRef}
          onReady={onNavigationReady}
          onStateChange={onNavigationStateChange}
        >
          <Stack.Navigator initialRouteName="Splash" screenOptions={{ headerShown: false }}>
            <Stack.Screen name="Splash" component={SplashScreen} />
            <Stack.Screen name="Onboarding" component={OnboardingScreen} />
            <Stack.Screen name="PhoneEntry" component={PhoneEntryScreen} />
            <Stack.Screen name="OTPVerify" component={OTPVerifyScreen} />
            <Stack.Screen name="MainApp" component={MainTabNavigator} />
            <Stack.Screen name="Profile" component={ProfileScreen} />
            <Stack.Screen name="Insights" component={InsightsScreen} />
            <Stack.Screen name="Paywall" component={PaywallScreen} />
            <Stack.Screen name="Referral" component={ReferralScreen} />
          </Stack.Navigator>
          <Toast />
          <NotificationListener />
        </NavigationContainer>
        <StatusBar style="auto" />
      </ErrorBoundary>
    </View>
  );
}
