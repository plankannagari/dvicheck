import { useRef, useState } from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet,
  FlatList, Dimensions, SafeAreaView, StatusBar,
} from 'react-native';

import { COLORS } from '../constants';
import { completeOnboarding } from '../api/userApi';
import useAuthStore from '../store/authStore';

const { width } = Dimensions.get('window');

const SLIDES = [
  {
    icon: '🧾',
    heading: 'Scan any receipt',
    body: 'Point your camera at a receipt and dvicheck reads every line item automatically.',
  },
  {
    icon: '🎯',
    heading: "See what's avoidable",
    body: 'AI flags spending you can cut back on, right alongside your essentials.',
  },
  {
    icon: '📊',
    heading: 'Stay on budget',
    body: 'Set a monthly target and get a heads up before you go over.',
  },
];

export default function OnboardingScreen({ navigation }) {
  const { markOnboardingComplete } = useAuthStore();
  const [index, setIndex] = useState(0);
  const listRef = useRef(null);

  const isLast = index === SLIDES.length - 1;

  const handleFinish = async () => {
    try {
      await completeOnboarding();
    } catch (err) {
      console.error('completeOnboarding error:', err);
      // Do not block navigation on this — onboarding still completes locally.
    }
    await markOnboardingComplete();
    navigation.replace('MainApp');
  };

  const handleNext = () => {
    if (isLast) {
      handleFinish();
      return;
    }
    const nextIndex = index + 1;
    listRef.current?.scrollToIndex({ index: nextIndex, animated: true });
    setIndex(nextIndex);
  };

  const handleMomentumScrollEnd = (e) => {
    const newIndex = Math.round(e.nativeEvent.contentOffset.x / width);
    if (newIndex !== index) {
      setIndex(newIndex);
    }
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" backgroundColor={COLORS.bg} />

      <FlatList
        ref={listRef}
        data={SLIDES}
        keyExtractor={(item) => item.heading}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onMomentumScrollEnd={handleMomentumScrollEnd}
        getItemLayout={(_, i) => ({ length: width, offset: width * i, index: i })}
        renderItem={({ item }) => (
          <View style={[styles.slide, { width }]}>
            <Text style={styles.icon}>{item.icon}</Text>
            <Text style={styles.heading}>{item.heading}</Text>
            <Text style={styles.body}>{item.body}</Text>
          </View>
        )}
      />

      {/* Rendered after FlatList so it reliably paints on top — zIndex alone is
          inconsistent for absolute-positioned siblings on Android. */}
      {!isLast && (
        <TouchableOpacity style={styles.skipBtn} onPress={handleFinish} activeOpacity={0.7}>
          <Text style={styles.skipText}>Skip</Text>
        </TouchableOpacity>
      )}

      <View style={styles.dotsRow}>
        {SLIDES.map((_, i) => (
          <View key={i} style={[styles.dot, i === index && styles.dotActive]} />
        ))}
      </View>

      <View style={styles.footer}>
        <TouchableOpacity style={styles.nextBtn} onPress={handleNext} activeOpacity={0.8}>
          <Text style={styles.nextBtnText}>{isLast ? 'Get Started' : 'Next'}</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: COLORS.bg },
  skipBtn: {
    position: 'absolute', top: 16, right: 20, zIndex: 10, elevation: 10,
    padding: 8,
  },
  skipText: { fontSize: 14, color: COLORS.inkLight, fontWeight: '600' },
  slide: {
    flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32,
  },
  icon: { fontSize: 64, marginBottom: 32 },
  heading: {
    fontSize: 24, fontWeight: '400', color: COLORS.ink,
    marginBottom: 12, letterSpacing: -0.3, textAlign: 'center',
  },
  body: {
    fontSize: 14, color: COLORS.inkLight, textAlign: 'center', lineHeight: 21,
  },
  dotsRow: {
    flexDirection: 'row', justifyContent: 'center', alignItems: 'center',
    gap: 8, marginBottom: 24,
  },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: COLORS.border },
  dotActive: { backgroundColor: COLORS.accent, width: 20 },
  footer: { paddingHorizontal: 28, paddingBottom: 32 },
  nextBtn: {
    width: '100%', backgroundColor: COLORS.accent,
    borderRadius: 16, padding: 16, alignItems: 'center',
  },
  nextBtnText: { color: '#fff', fontSize: 16, fontWeight: '600', letterSpacing: 0.3 },
});
