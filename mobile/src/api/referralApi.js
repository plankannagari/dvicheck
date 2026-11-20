import apiClient from './apiClient';

// Referral summary (Day 37). skipPaywallRedirect: loads passively when the screen opens —
// the endpoint is open to every tier, but a passive load must never pop the paywall.
export const fetchReferralSummary = async () => {
  const response = await apiClient.get('/referrals/me', { skipPaywallRedirect: true });
  return response.data.data;
};

// Returns RedeemResultDto { status, refereeDays, message }
export const redeemReferralCode = async (code) => {
  const response = await apiClient.post('/referrals/redeem', { code });
  return response.data.data;
};
