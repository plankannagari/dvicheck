import React, { useEffect, useRef } from 'react';
import { Animated, Text, StyleSheet } from 'react-native';
import { COLORS } from '../constants';

const TYPE_CONFIG = {
  success: { bg: COLORS.green, prefix: '✓ ' },
  error:   { bg: COLORS.red,   prefix: '✗ ' },
  info:    { bg: COLORS.blue,  prefix: 'ℹ ' },
};

export default function Toast({ visible, message, type = 'success', onHide }) {
  const translateY = useRef(new Animated.Value(-80)).current;
  const config = TYPE_CONFIG[type] || TYPE_CONFIG.info;

  useEffect(() => {
    if (visible) {
      Animated.spring(translateY, {
        toValue: 0, useNativeDriver: true, tension: 80, friction: 10,
      }).start();
      const timer = setTimeout(() => {
        Animated.timing(translateY, {
          toValue: -80, duration: 250, useNativeDriver: true,
        }).start(() => onHide?.());
      }, 3000);
      return () => clearTimeout(timer);
    }
  }, [visible]);

  if (!visible) return null;

  return (
    <Animated.View style={[
      styles.toast,
      { backgroundColor: config.bg, transform: [{ translateY }] }
    ]}>
      <Text style={styles.text}>{config.prefix}{message}</Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  toast: {
    position: 'absolute', top: 50, left: 16, right: 16,
    zIndex: 999, borderRadius: 12, padding: 14,
    paddingHorizontal: 16, flexDirection: 'row',
    shadowColor: '#000', shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.15, shadowRadius: 12, elevation: 8,
  },
  text: { color: '#fff', fontSize: 14, flex: 1, lineHeight: 20 },
});
