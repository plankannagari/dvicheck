import { StatusBar } from 'expo-status-bar';
import { StyleSheet, View, Text } from 'react-native';

export default function App() {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>BillWise</Text>
      <Text style={styles.subtitle}>Day 1 — Setup complete ✓</Text>
      <StatusBar style="auto" />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f2ee',
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    fontSize: 32,
    fontWeight: '300',
    color: '#1a1612',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 14,
    color: '#7a6e64',
  },
});