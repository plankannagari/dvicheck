import apiClient from './apiClient';

export const fetchBills = async (page = 0, size = 20) => {
  try {
    const response = await apiClient.get(`/bills?page=${page}&size=${size}`);
    return response.data.data;
  } catch (error) {
    console.error('fetchBills error:', error);
    throw error;
  }
};

export const fetchBillDetail = async (billId) => {
  try {
    const response = await apiClient.get(`/bills/${billId}`);
    return response.data.data;
  } catch (error) {
    console.error('fetchBillDetail error:', error);
    throw error;
  }
};
