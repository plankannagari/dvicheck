import React from 'react';
import { View, Text } from 'react-native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import ScanScreen from '../screens/ScanScreen';
import ListsScreen from '../screens/ListsScreen';
import PantryScreen from '../screens/PantryScreen';
import HistoryScreen from '../screens/HistoryScreen';
import { COLORS } from '../constants';

// Temporary placeholder — replaced in Step 6
import HomeScreen from '../screens/HomeScreen';

const Tab = createBottomTabNavigator();

function TabIcon({ emoji, active }) {
  return (
    <Text style={{ fontSize: 20, opacity: active ? 1 : 0.4 }}>
      {emoji}
    </Text>
  );
}

function ScanTabIcon() {
  return (
    <View style={{
      width: 52, height: 52, borderRadius: 26,
      backgroundColor: COLORS.accent,
      alignItems: 'center', justifyContent: 'center',
      marginBottom: 8,
      shadowColor: COLORS.accent,
      shadowOffset: { width: 0, height: 4 },
      shadowOpacity: 0.3,
      shadowRadius: 8,
      elevation: 8,
    }}>
      <Text style={{ fontSize: 22 }}>🧾</Text>
    </View>
  );
}

export default function MainTabNavigator() {
  return (
    <Tab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: COLORS.accent,
        tabBarInactiveTintColor: COLORS.inkFaint,
        tabBarStyle: {
          backgroundColor: COLORS.card,
          borderTopColor: COLORS.border,
          borderTopWidth: 1,
          height: 80,
          paddingBottom: 16,
          paddingTop: 8,
        },
        tabBarLabelStyle: {
          fontSize: 10,
        },
      }}
    >
      <Tab.Screen
        name="Home"
        component={HomeScreen}
        options={{
          tabBarIcon: ({ focused }) => (
            <TabIcon emoji="🏠" active={focused} />
          ),
        }}
      />
      <Tab.Screen
        name="Scan"
        component={ScanScreen}
        options={{
          tabBarIcon: () => <ScanTabIcon />,
          tabBarShowLabel: false,
        }}
      />
      <Tab.Screen
        name="Lists"
        component={ListsScreen}
        options={{
          tabBarIcon: ({ focused }) => (
            <TabIcon emoji="✓" active={focused} />
          ),
        }}
      />
      <Tab.Screen
        name="Pantry"
        component={PantryScreen}
        options={{
          tabBarIcon: ({ focused }) => (
            <TabIcon emoji="🧺" active={focused} />
          ),
        }}
      />
      <Tab.Screen
        name="History"
        component={HistoryScreen}
        options={{
          tabBarIcon: ({ focused }) => (
            <TabIcon emoji="🗂️" active={focused} />
          ),
        }}
      />
    </Tab.Navigator>
  );
}
