import React, { useState, useEffect, useRef } from 'react';
import {
  View, Text, TextInput, TouchableOpacity,
  StyleSheet, ActivityIndicator, StatusBar,
  Keyboard
} from 'react-native';
import { verifyOtp, sendOtp } from '../api/authApi';
import useAuthStore from '../store/authStore';
import { COLORS } from '../constants';

const OTP_LENGTH = 6;
const RESEND_DELAY = 30;

export default function OTPVerifyScreen({ navigation, route }) {
  const { phone } = route.params;
  const [otp, setOtp] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [countdown, setCountdown] = useState(RESEND_DELAY);
  const inputRef = useRef(null);
  const { setAuth } = useAuthStore();

  useEffect(() => {
    setTimeout(() => inputRef.current?.focus(), 100);
  }, []);

  useEffect(() => {
    if (countdown <= 0) return;
    const timer = setTimeout(() => setCountdown(c => c - 1), 1000);
    return () => clearTimeout(timer);
  }, [countdown]);

  useEffect(() => {
    if (otp.length === OTP_LENGTH) {
      handleVerify(otp);
    }
  }, [otp]);

  const handleVerify = async (code) => {
    Keyboard.dismiss();
    setError('');
    setIsLoading(true);
    try {
      const data = await verifyOtp(phone, code);
      await setAuth(data.accessToken, data.refreshToken, data.userId, phone);
      navigation.replace('MainApp');
    } catch (err) {
      const msg = err.response?.data?.message || 'Invalid code. Please try again.';
      setError(msg);
      setOtp('');
      setTimeout(() => inputRef.current?.focus(), 100);
    } finally {
      setIsLoading(false);
    }
  };

  const handleResend = async () => {
    if (countdown > 0) return;
    try {
      await sendOtp(phone);
      setCountdown(RESEND_DELAY);
      setError('');
      setOtp('');
      setTimeout(() => inputRef.current?.focus(), 100);
    } catch {
      setError('Failed to resend code. Try again.');
    }
  };

  return (
    <View style={styles.container}>
      <StatusBar barStyle="dark-content" backgroundColor={COLORS.bg} />

      <TouchableOpacity style={styles.backBtn} onPress={() => navigation.goBack()}>
        <Text style={styles.backText}>← Back</Text>
      </TouchableOpacity>

      <View style={styles.inner}>
        <Text style={styles.heading}>Enter the code</Text>
        <Text style={styles.subtext}>Sent to {phone}</Text>

        {/* OTP boxes */}
        <View style={styles.boxRow}>
          {Array(OTP_LENGTH).fill(0).map((_, i) => (
            <TouchableOpacity
              key={i}
              style={[
                styles.box,
                i === otp.length && styles.boxActive,
                otp[i] && styles.boxFilled,
              ]}
              onPress={() => inputRef.current?.focus()}
              activeOpacity={1}
            >
              <Text style={styles.boxText}>{otp[i] || ''}</Text>
            </TouchableOpacity>
          ))}
        </View>

        {/* Hidden input that captures keystrokes */}
        <TextInput
          ref={inputRef}
          style={styles.hiddenInput}
          value={otp}
          onChangeText={(t) => {
            const digits = t.replace(/\D/g, '').slice(0, OTP_LENGTH);
            setOtp(digits);
            setError('');
          }}
          keyboardType="number-pad"
          maxLength={OTP_LENGTH}
          caretHidden
        />

        {error ? <Text style={styles.error}>{error}</Text> : null}

        <TouchableOpacity onPress={handleResend} disabled={countdown > 0}>
          <Text style={[styles.resend, countdown > 0 && styles.resendDisabled]}>
            {countdown > 0 ? ('Resend in ' + countdown + 's') : 'Resend code'}
          </Text>
        </TouchableOpacity>
      </View>

      {/* Loading overlay */}
      {isLoading && (
        <View style={styles.overlay}>
          <ActivityIndicator size="large" color={COLORS.accent} />
          <Text style={styles.overlayText}>Verifying…</Text>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: COLORS.bg },
  backBtn: { padding: 20, paddingBottom: 0 },
  backText: { fontSize: 14, color: COLORS.accent },
  inner: {
    flex: 1, padding: 28,
    alignItems: 'center', justifyContent: 'center',
  },
  heading: {
    fontSize: 26, fontWeight: '400',
    color: COLORS.ink, marginBottom: 8,
    letterSpacing: -0.3,
  },
  subtext: {
    fontSize: 13, color: COLORS.inkLight,
    marginBottom: 36,
  },
  boxRow: {
    flexDirection: 'row', gap: 10, marginBottom: 24,
  },
  box: {
    width: 44, height: 54, borderRadius: 10,
    borderWidth: 1.5, borderColor: COLORS.border,
    alignItems: 'center', justifyContent: 'center',
    backgroundColor: COLORS.card,
  },
  boxActive: { borderColor: COLORS.accent },
  boxFilled: { backgroundColor: COLORS.accentLight },
  boxText: { fontSize: 22, color: COLORS.ink, fontWeight: '600' },
  hiddenInput: {
    position: 'absolute', opacity: 0,
    width: 1, height: 1,
  },
  error: {
    color: COLORS.red, fontSize: 12,
    marginBottom: 16, textAlign: 'center',
  },
  resend: { fontSize: 14, color: COLORS.accent, marginTop: 8 },
  resendDisabled: { color: COLORS.inkFaint },
  overlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(245,242,238,0.9)',
    alignItems: 'center', justifyContent: 'center', gap: 12,
  },
  overlayText: { fontSize: 14, color: COLORS.inkLight },
});
