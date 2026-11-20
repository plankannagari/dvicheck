import apiClient from './apiClient';

// skipPaywallRedirect: also used by the silent foreground refresh (Day 38), which must
// never navigate. /users/me doesn't 402 today; the flag makes sure it never pops the paywall.
export const fetchMe = async () => {
  try {
    const response = await apiClient.get('/users/me', { skipPaywallRedirect: true });
    return response.data.data;
  } catch (error) {
    console.error('fetchMe error:', error);
    throw error;
  }
};

export const updatePreferences = async (prefs) => {
  try {
    const response = await apiClient.patch('/users/me/preferences', prefs);
    return response.data.data;
  } catch (error) {
    console.error('updatePreferences error:', error);
    throw error;
  }
};

export const completeOnboarding = async () => {
  try {
    const response = await apiClient.post('/users/me/onboarding-complete');
    return response.data.data;
  } catch (error) {
    console.error('completeOnboarding error:', error);
    throw error;
  }
};

// -> { isPro, thresholds, paceAlertsEnabled, allowedThresholds }
export const fetchBudgetAlerts = async () => {
  try {
    // GET is open to every tier, and it runs passively (chained in loadProfile)
    const response = await apiClient.get('/users/me/budget-alerts', { skipPaywallRedirect: true });
    return response.data.data;
  } catch (error) {
    console.error('fetchBudgetAlerts error:', error);
    throw error;
  }
};

// patch: { thresholds?, paceAlertsEnabled? } — omitted fields stay unchanged server-side.
// Pro-only: a free user gets 402, which apiClient's interceptor turns into the paywall.
export const updateBudgetAlerts = async (patch) => {
  try {
    const response = await apiClient.put('/users/me/budget-alerts', patch);
    return response.data.data;
  } catch (error) {
    console.error('updateBudgetAlerts error:', error);
    throw error;
  }
};

// for future use — implement DELETE /users/me on the backend later
export const deleteAccount = async () => {
  try {
    const response = await apiClient.delete('/users/me');
    return response.data;
  } catch (error) {
    console.error('deleteAccount error:', error);
    throw error;
  }
};
