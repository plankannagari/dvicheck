import React from 'react';
import {
  View, Text, TouchableOpacity,
  StyleSheet, StatusBar
} from 'react-native';
import useAuthStore from '../store/authStore';
import { COLORS } from '../constants';

export default function MainAppScreen({ navigation }) {
  const { user, clearAuth } = useAuthStore();

  const handleSignOut = async () => {
    await clearAuth();
    navigation.replace('PhoneEntry');
  };

  return (
    <View style={styles.container}>
      <StatusBar barStyle="dark-content" backgroundColor={COLORS.bg} />
      <Text style={styles.logo}>🧾</Text>
      <Text style={styles.title}>dvicheck</Text>
      <Text style={styles.welcome}>You are logged in!</Text>
      {user?.phone && (
        <Text style={styles.phone}>{user.phone}</Text>
      )}
      <Text style={styles.note}>
        Main app screens coming in Day 6+
      </Text>
      <TouchableOpacity style={styles.signOut} onPress={handleSignOut}>
        <Text style={styles.signOutText}>Sign Out</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1, backgroundColor: COLORS.bg,
    alignItems: 'center', justifyContent: 'center',
    padding: 28,
  },
  logo: { fontSize: 56, marginBottom: 16 },
  title: {
    fontSize: 32, fontWeight: '300',
    color: COLORS.ink, marginBottom: 8,
  },
  welcome: {
    fontSize: 18, color: COLORS.green,
    fontWeight: '600', marginBottom: 8,
  },
  phone: {
    fontSize: 14, color: COLORS.inkLight,
    marginBottom: 24,
  },
  note: {
    fontSize: 12, color: COLORS.inkFaint,
    fontStyle: 'italic', marginBottom: 40,
    textAlign: 'center',
  },
  signOut: {
    paddingVertical: 12, paddingHorizontal: 32,
    borderRadius: 12, borderWidth: 1,
    borderColor: COLORS.border,
  },
  signOutText: { fontSize: 14, color: COLORS.inkLight },
});
