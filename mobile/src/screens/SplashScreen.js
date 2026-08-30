import { useEffect } from 'react';
import { ActivityIndicator, Image, StyleSheet, Text, View } from 'react-native';

import { COLORS } from '../constants';
import useAuthStore from '../store/authStore';

export default function SplashScreen({ navigation }) {
  const { loadStoredAuth, isAuthenticated, isLoading, onboardingCompleted } = useAuthStore();

  useEffect(() => {
    loadStoredAuth();
  }, []);

  useEffect(() => {
    if (!isLoading) {
      if (isAuthenticated) {
        navigation.replace(onboardingCompleted ? 'MainApp' : 'Onboarding');
      } else {
        navigation.replace('PhoneEntry');
      }
    }
  }, [isLoading, isAuthenticated, onboardingCompleted]);

  return (
    <View style={styles.container}>
      <Image source={require('../../assets/splash-icon.png')} style={styles.logo} resizeMode="contain" />
      <Text style={styles.title}>dvicheck</Text>
      <Text style={styles.subtitle}>Smart spending. Simplified.</Text>
      {isLoading && (
        <ActivityIndicator color={COLORS.accent} style={styles.loader} />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.bg,
    alignItems: 'center',
    justifyContent: 'center',
  },
  logo: {
    width: 96,
    height: 96,
    marginBottom: 12,
  },
  title: {
    fontSize: 36,
    fontWeight: '300',
    color: COLORS.ink,
  },
  subtitle: {
    fontSize: 14,
    fontStyle: 'italic',
    color: COLORS.inkLight,
    marginTop: 4,
  },
  loader: {
    marginTop: 24,
  },
});
