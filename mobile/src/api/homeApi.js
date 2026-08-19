import apiClient from './apiClient';

export const fetchHomeSummary = async () => {
  try {
    const response = await apiClient.get('/home/summary');
    return response.data.data;
  } catch (error) {
    console.error('fetchHomeSummary error:', error);
    throw error;
  }
};

export const fetchRecentBills = async (limit = 5) => {
  try {
    const response = await apiClient.get('/home/recent-bills', {
      params: { limit },
    });
    return response.data.data;
  } catch (error) {
    console.error('fetchRecentBills error:', error);
    throw error;
  }
};

export const fetchMonthlyReport = async () => {
  try {
    const response = await apiClient.get('/reports/monthly');
    return response.data.data;
  } catch (error) {
    console.error('fetchMonthlyReport error:', error);
    throw error;
  }
};

export const fetchTrends = async () => {
  try {
    const response = await apiClient.get('/reports/trends');
    return response.data.data;
  } catch (error) {
    console.error('fetchTrends error:', error);
    throw error;
  }
};
