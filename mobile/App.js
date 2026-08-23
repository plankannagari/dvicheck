import 'react-native-gesture-handler';

import { NavigationContainer } from '@react-navigation/native';
import { createStackNavigator } from '@react-navigation/stack';
import { StatusBar } from 'expo-status-bar';

import ErrorBoundary from './src/components/ErrorBoundary';
import NotificationListener from './src/components/NotificationListener';
import Toast from './src/components/Toast';
import { navigationRef } from './src/navigation/navigationRef';
import MainTabNavigator from './src/navigation/MainTabNavigator';
import InsightsScreen from './src/screens/InsightsScreen';
import OTPVerifyScreen from './src/screens/OTPVerifyScreen';
import PhoneEntryScreen from './src/screens/PhoneEntryScreen';
import ProfileScreen from './src/screens/ProfileScreen';
import SplashScreen from './src/screens/SplashScreen';

const Stack = createStackNavigator();

export default function App() {
  return (
    <ErrorBoundary>
      <NavigationContainer ref={navigationRef}>
        <Stack.Navigator initialRouteName="Splash" screenOptions={{ headerShown: false }}>
          <Stack.Screen name="Splash" component={SplashScreen} />
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
  );
}
