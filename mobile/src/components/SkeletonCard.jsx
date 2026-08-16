import { useEffect, useRef } from 'react';
import { Animated, StyleSheet } from 'react-native';

import { COLORS } from '../constants';

export default function SkeletonCard({ height = 72 }) {
  const opacity = useRef(new Animated.Value(0.4)).current;

  useEffect(() => {
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(opacity, { toValue: 0.8, duration: 800, useNativeDriver: true }),
        Animated.timing(opacity, { toValue: 0.4, duration: 800, useNativeDriver: true }),
      ])
    );
    loop.start();
    return () => loop.stop();
  }, [opacity]);

  return <Animated.View style={[styles.card, { height, opacity }]} />;
}

const styles = StyleSheet.create({
  card: {
    width: '100%', borderRadius: 12,
    backgroundColor: COLORS.border,
  },
});
