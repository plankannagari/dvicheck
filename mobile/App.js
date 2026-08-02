import 'react-native-gesture-handler';

import { NavigationContainer } from '@react-navigation/native';
import { createStackNavigator } from '@react-navigation/stack';
import { StatusBar } from 'expo-status-bar';

import MainAppScreen from './src/screens/MainAppScreen';
import OTPVerifyScreen from './src/screens/OTPVerifyScreen';
import PhoneEntryScreen from './src/screens/PhoneEntryScreen';
import SplashScreen from './src/screens/SplashScreen';

const Stack = createStackNavigator();

export default function App() {
  return (
    <NavigationContainer>
      <Stack.Navigator initialRouteName="Splash" screenOptions={{ headerShown: false }}>
        <Stack.Screen name="Splash" component={SplashScreen} />
        <Stack.Screen name="PhoneEntry" component={PhoneEntryScreen} />
        <Stack.Screen name="OTPVerify" component={OTPVerifyScreen} />
        <Stack.Screen name="MainApp" component={MainAppScreen} />
      </Stack.Navigator>
      <StatusBar style="auto" />
    </NavigationContainer>
  );
}
