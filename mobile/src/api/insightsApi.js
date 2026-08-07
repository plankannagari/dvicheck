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
