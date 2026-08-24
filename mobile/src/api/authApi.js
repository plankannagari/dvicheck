import apiClient from './apiClient';

export async function sendOtp(phone) {
  const response = await apiClient.post('/auth/send-otp', { phone });
  return response.data;
}

export async function verifyOtp(phone, otp) {
  const response = await apiClient.post('/auth/verify-otp', { phone, otp });
  const { accessToken, refreshToken, userId, phone: userPhone, onboardingCompleted } = response.data.data;
  return { accessToken, refreshToken, userId, phone: userPhone, onboardingCompleted };
}
