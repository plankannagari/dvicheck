import { useEffect } from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import { COLORS } from '../constants';
import useAuthStore from '../store/authStore';

export default function SplashScreen({ navigation }) {
  const loadStoredAuth = useAuthStore((state) => state.loadStoredAuth);
  const isLoading = useAuthStore((state) => state.isLoading);

  useEffect(() => {
    const checkAuth = async () => {
      await loadStoredAuth();
      const isAuthenticated = useAuthStore.getState().isAuthenticated;
      navigation.replace(isAuthenticated ? 'MainApp' : 'PhoneEntry');
    };

    checkAuth();
  }, []);

  return (
    <View style={styles.container}>
      <Text style={styles.logo}>🧾</Text>
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
    fontSize: 64,
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
