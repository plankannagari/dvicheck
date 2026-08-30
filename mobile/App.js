import 'react-native-gesture-handler';

import { useCallback } from 'react';
import { View } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createStackNavigator } from '@react-navigation/stack';
import { StatusBar } from 'expo-status-bar';
// Aliased — this file already has a `SplashScreen` import for the auth-check
// screen component (./src/screens/SplashScreen); `* as SplashScreen` here
// would silently shadow it.
import * as ExpoSplashScreen from 'expo-splash-screen';

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

ExpoSplashScreen.preventAutoHideAsync();

const Stack = createStackNavigator();

export default function App() {
  // Covers only the native-splash-to-JS handoff (this file rendering at all).
  // SplashScreen.js's own auth-check/onboarding routing is unrelated and untouched.
  const onLayoutRootView = useCallback(async () => {
    await ExpoSplashScreen.hideAsync();
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
