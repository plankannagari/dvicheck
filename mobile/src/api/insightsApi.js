import apiClient from './apiClient';

export const fetchWeeklyInsights = async () => {
  try {
    const response = await apiClient.get('/insights/weekly');
    return response.data.data;
  } catch (error) {
    console.error('fetchWeeklyInsights error:', error);
    throw error;
  }
};

// Pro weekly coach (Day 35). skipPaywallRedirect: this loads passively when Insights opens,
// so a 402 must NOT pop the paywall — insightsStore turns it into the locked card instead.
export const fetchLatestCoach = async () => {
  const response = await apiClient.get('/coach/latest', { skipPaywallRedirect: true });
  return response.data.data;
};
