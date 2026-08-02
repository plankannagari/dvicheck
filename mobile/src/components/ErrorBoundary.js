import React from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet
} from 'react-native';
import { COLORS } from '../constants';

export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, info) {
    console.error('ErrorBoundary caught:', error, info);
  }

  render() {
    if (this.state.hasError) {
      return (
        <View style={styles.container}>
          <Text style={styles.emoji}>🧾</Text>
          <Text style={styles.heading}>Something went wrong</Text>
          <Text style={styles.sub}>Please restart the app</Text>
          <TouchableOpacity
            style={styles.btn}
            onPress={() => this.setState({ hasError: false, error: null })}
          >
            <Text style={styles.btnText}>Try Again</Text>
          </TouchableOpacity>
        </View>
      );
    }
    return this.props.children;
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1, backgroundColor: COLORS.bg,
    alignItems: 'center', justifyContent: 'center', padding: 32,
  },
  emoji: { fontSize: 56, marginBottom: 20 },
  heading: { fontSize: 22, color: COLORS.ink, fontWeight: '400', marginBottom: 8 },
  sub: { fontSize: 14, color: COLORS.inkLight, marginBottom: 32, textAlign: 'center' },
  btn: {
    paddingVertical: 12, paddingHorizontal: 32,
    backgroundColor: COLORS.accent, borderRadius: 12,
  },
  btnText: { color: '#fff', fontSize: 15, fontWeight: '600' },
});
