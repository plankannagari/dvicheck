import { useEffect, useRef, useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet,
  SafeAreaView, StatusBar, ScrollView, Switch, Alert, ActivityIndicator,
} from 'react-native';

import { COLORS } from '../constants';
import useProfileStore from '../store/profileStore';
import useAuthStore from '../store/authStore';
import useToastStore from '../store/toastStore';
import Toast from '../components/Toast';

const CURRENCIES = ['USD', 'AUD', 'GBP', 'EUR', 'CAD', 'INR', 'SGD'];
const MIN_HOUSEHOLD = 1;
const MAX_HOUSEHOLD = 10;

const fmtDate = (d) => d
  ? new Date(d).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })
  : '';

export default function ProfileScreen({ navigation }) {
  const { profile, isLoading, isSaving, loadProfile, savePreferences } = useProfileStore();
  const { clearAuth } = useAuthStore();
  const clearProfile = useProfileStore((state) => state.clearProfile);
  const {
    visible: toastVisible, message: toastMessage, type: toastType, showToast, hideToast,
  } = useToastStore();

  const [householdSize, setHouseholdSize] = useState(1);
  const [currency, setCurrency] = useState('USD');
  const [notificationsEnabled, setNotificationsEnabled] = useState(true);
  const [budget, setBudget] = useState('');
  const initialized = useRef(false);

  useEffect(() => { loadProfile(); }, []);

  useEffect(() => {
    if (profile && !initialized.current) {
      setHouseholdSize(profile.householdSize);
      setCurrency(profile.currency);
      setNotificationsEnabled(profile.notificationsEnabled);
      setBudget(profile.budgetAmount ? profile.budgetAmount.toString() : '');
      initialized.current = true;
    }
  }, [profile]);

  const budgetChanged = budget !== (profile?.budgetAmount?.toString() || '');

  const isDirty = !!profile && (
    householdSize !== profile.householdSize
    || currency !== profile.currency
    || notificationsEnabled !== profile.notificationsEnabled
    || budgetChanged
  );

  const handleSave = async () => {
    // parseFloat("") -> NaN, and JSON.stringify serialises NaN as null — which the
    // backend's partial-update pattern reads as "field omitted, leave unchanged."
    // Without this check, invalid input silently no-ops instead of saving or erroring.
    const budgetAmount = budget ? parseFloat(budget) : null;
    if (budget && Number.isNaN(budgetAmount)) {
      showToast('Enter a valid budget amount.', 'error');
      return;
    }

    try {
      await savePreferences({
        householdSize,
        currency,
        notificationsEnabled,
        budgetAmount,
      });
      showToast('Preferences saved', 'success');
    } catch (err) {
      showToast(err.appError?.message || 'Could not save preferences.', 'error');
    }
  };

  const handleSignOut = () => {
    Alert.alert(
      'Sign out?',
      'You will need to verify your phone number again to sign back in.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Sign Out',
          style: 'destructive',
          onPress: async () => {
            await clearAuth();
            clearProfile();
            navigation.reset({ index: 0, routes: [{ name: 'PhoneEntry' }] });
          },
        },
      ],
    );
  };

  const handleDeleteAccount = () => {
    Alert.alert(
      'Delete account?',
      'This will permanently delete your account and all your data. This cannot be undone.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: () => {
            Alert.alert(
              'Are you absolutely sure?',
              'This action is permanent and cannot be reversed.',
              [
                { text: 'Cancel', style: 'cancel' },
                {
                  text: 'Delete my account',
                  style: 'destructive',
                  onPress: () => showToast('Coming soon', 'info'),
                },
              ],
            );
          },
        },
      ],
    );
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" backgroundColor={COLORS.bg} />

      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.headerSideBtn} activeOpacity={0.7}>
          <Text style={styles.backArrow}>←</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Profile</Text>
        <View style={styles.headerSideBtn}>
          {isDirty && (
            <TouchableOpacity onPress={handleSave} activeOpacity={0.8} disabled={isSaving}>
              <Text style={styles.saveBtnText}>{isSaving ? 'Saving…' : 'Save'}</Text>
            </TouchableOpacity>
          )}
        </View>
      </View>

      {isLoading && !profile ? (
        <View style={styles.centerFill}>
          <ActivityIndicator size="large" color={COLORS.accent} />
        </View>
      ) : !profile ? null : (
        <ScrollView style={styles.scroll} contentContainerStyle={styles.scrollContent}>
          <View style={styles.userCard}>
            <Text style={styles.phone}>{profile.phone}</Text>
            <Text style={styles.memberSince}>Member since {fmtDate(profile.createdAt)}</Text>
          </View>

          <Text style={styles.sectionLabel}>Preferences</Text>
          <View style={styles.prefsCard}>
            <View style={styles.prefRow}>
              <View style={styles.prefMeta}>
                <Text style={styles.prefLabel}>Household size</Text>
                <Text style={styles.prefSub}>Affects pantry estimates</Text>
              </View>
              <View style={styles.stepper}>
                <TouchableOpacity
                  style={styles.stepperBtn}
                  onPress={() => setHouseholdSize((n) => Math.max(MIN_HOUSEHOLD, n - 1))}
                  disabled={householdSize <= MIN_HOUSEHOLD}
                  activeOpacity={0.7}
                >
                  <Text style={[styles.stepperBtnText, householdSize <= MIN_HOUSEHOLD && styles.stepperBtnTextDisabled]}>−</Text>
                </TouchableOpacity>
                <Text style={styles.stepperValue}>{householdSize}</Text>
                <TouchableOpacity
                  style={styles.stepperBtn}
                  onPress={() => setHouseholdSize((n) => Math.min(MAX_HOUSEHOLD, n + 1))}
                  disabled={householdSize >= MAX_HOUSEHOLD}
                  activeOpacity={0.7}
                >
                  <Text style={[styles.stepperBtnText, householdSize >= MAX_HOUSEHOLD && styles.stepperBtnTextDisabled]}>+</Text>
                </TouchableOpacity>
              </View>
            </View>

            <View style={styles.prefRow}>
              <View style={styles.prefMeta}>
                <Text style={styles.prefLabel}>Monthly budget</Text>
                <Text style={styles.prefSub}>Your spending target for the month</Text>
              </View>
              <View style={styles.budgetInputWrap}>
                <Text style={styles.budgetPrefix}>$</Text>
                <TextInput
                  style={styles.budgetInput}
                  value={budget}
                  onChangeText={setBudget}
                  placeholder="Not set"
                  placeholderTextColor={COLORS.inkFaint}
                  keyboardType="decimal-pad"
                />
              </View>
            </View>

            <View style={[styles.prefRow, styles.prefRowColumn]}>
              <View style={styles.prefMeta}>
                <Text style={styles.prefLabel}>Currency</Text>
                <Text style={styles.prefSub}>Used for spend display</Text>
              </View>
              <ScrollView
                horizontal
                showsHorizontalScrollIndicator={false}
                contentContainerStyle={styles.chipsRow}
              >
                {CURRENCIES.map((code) => {
                  const selected = code === currency;
                  return (
                    <TouchableOpacity
                      key={code}
                      style={[styles.chip, { backgroundColor: selected ? COLORS.accent : COLORS.border }]}
                      onPress={() => setCurrency(code)}
                      activeOpacity={0.8}
                    >
                      <Text style={[styles.chipText, { color: selected ? '#fff' : COLORS.inkLight }]}>
                        {code}
                      </Text>
                    </TouchableOpacity>
                  );
                })}
              </ScrollView>
            </View>

            <View style={[styles.prefRow, styles.prefRowLast]}>
              <View style={styles.prefMeta}>
                <Text style={styles.prefLabel}>Push notifications</Text>
                <Text style={styles.prefSub}>Weekly insights reminders</Text>
              </View>
              <Switch
                value={notificationsEnabled}
                onValueChange={setNotificationsEnabled}
                trackColor={{ true: COLORS.accent }}
                thumbColor="#fff"
              />
            </View>
          </View>

          <Text style={styles.sectionLabel}>Account</Text>
          <TouchableOpacity style={styles.signOutBtn} onPress={handleSignOut} activeOpacity={0.8}>
            <Text style={styles.signOutBtnText}>Sign Out</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.deleteBtn} onPress={handleDeleteAccount} activeOpacity={0.7}>
            <Text style={styles.deleteBtnText}>Delete Account</Text>
          </TouchableOpacity>

          <View style={{ height: 20 }} />
        </ScrollView>
      )}

      <Toast visible={toastVisible} message={toastMessage} type={toastType} onHide={hideToast} />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: COLORS.bg },
  centerFill: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  scroll: { flex: 1 },
  scrollContent: { padding: 16, paddingBottom: 20 },

  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 16, paddingVertical: 12,
  },
  headerSideBtn: { width: 56, minHeight: 32, justifyContent: 'center' },
  backArrow: { fontSize: 22, color: COLORS.ink },
  headerTitle: { flex: 1, textAlign: 'center', fontSize: 17, color: COLORS.ink, fontWeight: '600' },
  saveBtnText: { fontSize: 15, color: COLORS.accent, fontWeight: '700', textAlign: 'right' },

  userCard: {
    backgroundColor: COLORS.card, borderRadius: 16, padding: 20,
    borderWidth: 1, borderColor: COLORS.border, marginBottom: 20,
  },
  phone: { fontSize: 17, color: COLORS.ink, fontWeight: '600', marginBottom: 4 },
  memberSince: { fontSize: 12, color: COLORS.inkLight },

  sectionLabel: {
    fontSize: 10, color: COLORS.inkLight, letterSpacing: 2,
    textTransform: 'uppercase', marginBottom: 10,
  },

  prefsCard: {
    backgroundColor: COLORS.card, borderRadius: 16,
    borderWidth: 1, borderColor: COLORS.border, marginBottom: 20, overflow: 'hidden',
  },
  prefRow: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    padding: 16, borderBottomWidth: 1, borderBottomColor: COLORS.border,
  },
  prefRowColumn: { flexDirection: 'column', alignItems: 'stretch', gap: 12 },
  prefRowLast: { borderBottomWidth: 0 },
  prefMeta: { flex: 1, marginRight: 12 },
  prefLabel: { fontSize: 14, color: COLORS.ink, fontWeight: '600', marginBottom: 2 },
  prefSub: { fontSize: 11, color: COLORS.inkLight },

  stepper: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  stepperBtn: {
    width: 30, height: 30, borderRadius: 15,
    backgroundColor: COLORS.bg, borderWidth: 1, borderColor: COLORS.border,
    alignItems: 'center', justifyContent: 'center',
  },
  stepperBtnText: { fontSize: 16, color: COLORS.ink, fontWeight: '700' },
  stepperBtnTextDisabled: { color: COLORS.inkFaint },
  stepperValue: { fontSize: 15, color: COLORS.ink, fontWeight: '600', minWidth: 18, textAlign: 'center' },

  budgetInputWrap: {
    flexDirection: 'row', alignItems: 'center', width: 100,
    borderWidth: 1, borderColor: COLORS.border, borderRadius: 10,
    backgroundColor: COLORS.bg, paddingHorizontal: 8, paddingVertical: 6,
  },
  budgetPrefix: { fontSize: 14, color: COLORS.inkLight, marginRight: 2 },
  budgetInput: { flex: 1, fontSize: 14, color: COLORS.ink, textAlign: 'right', padding: 0 },

  chipsRow: { gap: 8, paddingRight: 4 },
  chip: { borderRadius: 16, paddingHorizontal: 14, paddingVertical: 8 },
  chipText: { fontSize: 12, fontWeight: '600' },

  signOutBtn: {
    borderWidth: 1, borderColor: COLORS.red, borderRadius: 12,
    paddingVertical: 14, alignItems: 'center', marginBottom: 12,
  },
  signOutBtnText: { color: COLORS.red, fontSize: 15, fontWeight: '600' },
  deleteBtn: { alignItems: 'center', paddingVertical: 8 },
  deleteBtnText: { color: 'rgba(192,57,43,0.55)', fontSize: 12, fontWeight: '600' },
});
