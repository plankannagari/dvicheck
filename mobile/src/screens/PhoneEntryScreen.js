import React, { useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity,
  StyleSheet, KeyboardAvoidingView, Platform,
  Keyboard, ActivityIndicator, StatusBar
} from 'react-native';
import { sendOtp } from '../api/authApi';
import { COLORS } from '../constants';

export default function PhoneEntryScreen({ navigation }) {
  const [phone, setPhone] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');

  // Matches the backend's E.164 validation exactly (Day 2 pattern) — the old
  // regex had no upper bound, so e.g. a 25-digit string passed client-side
  // with no feedback and only failed (or silently truncated) server-side.
  const isValidPhone = (p) => /^\+[1-9]\d{7,19}$/.test(p.replace(/\s/g, ''));

  const handleSendOtp = async () => {
    Keyboard.dismiss();
    setError('');

    const cleanPhone = phone.replace(/\s/g, '');
    if (!isValidPhone(cleanPhone)) {
      setError('Enter a valid phone number starting with + and country code');
      return;
    }

    setIsLoading(true);
    try {
      await sendOtp(cleanPhone);
      navigation.navigate('OTPVerify', { phone: cleanPhone });
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to send OTP. Try again.';
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <StatusBar barStyle="dark-content" backgroundColor={COLORS.bg} />
      <View style={styles.inner}>
        <Text style={styles.logo}>🧾</Text>
        <Text style={styles.heading}>Enter your phone</Text>
        <Text style={styles.subtext}>We'll send you a one-time code</Text>

        <TextInput
          style={[styles.input, error ? styles.inputError : null]}
          value={phone}
          onChangeText={(t) => { setPhone(t); setError(''); }}
          placeholder="+1 234 567 8900"
          placeholderTextColor={COLORS.inkFaint}
          keyboardType="phone-pad"
          autoFocus
          editable={!isLoading}
        />

        {error ? <Text style={styles.error}>{error}</Text> : null}

        <TouchableOpacity
          style={[styles.button, isLoading && styles.buttonDisabled]}
          onPress={handleSendOtp}
          disabled={isLoading}
          activeOpacity={0.8}
        >
          {isLoading ? (
            <ActivityIndicator color="#fff" size="small" />
          ) : (
            <Text style={styles.buttonText}>Send Code</Text>
          )}
        </TouchableOpacity>

        <Text style={styles.disclaimer}>
          By continuing you agree to receive SMS messages.
          Standard rates may apply.
        </Text>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: COLORS.bg },
  inner: {
    flex: 1, padding: 28,
    alignItems: 'center', justifyContent: 'center',
  },
  logo: { fontSize: 48, marginBottom: 24 },
  heading: {
    fontSize: 26, fontWeight: '400',
    color: COLORS.ink, marginBottom: 8,
    letterSpacing: -0.3,
  },
  subtext: {
    fontSize: 13, color: COLORS.inkLight,
    marginBottom: 32, textAlign: 'center',
  },
  input: {
    width: '100%', backgroundColor: COLORS.card,
    borderWidth: 1, borderColor: COLORS.border,
    borderRadius: 12, padding: 16,
    fontSize: 18, color: COLORS.ink,
    marginBottom: 8,
  },
  inputError: { borderColor: COLORS.red },
  error: {
    color: COLORS.red, fontSize: 12,
    marginBottom: 12, alignSelf: 'flex-start',
  },
  button: {
    width: '100%', backgroundColor: COLORS.accent,
    borderRadius: 16, padding: 16,
    alignItems: 'center', marginTop: 8,
  },
  buttonDisabled: { opacity: 0.6 },
  buttonText: {
    color: '#fff', fontSize: 16,
    fontWeight: '600', letterSpacing: 0.3,
  },
  disclaimer: {
    fontSize: 11, color: COLORS.inkFaint,
    textAlign: 'center', marginTop: 24,
    lineHeight: 16,
  },
});
