import apiClient from './apiClient';

export const fetchBills = async (page = 0, size = 20, search = '') => {
  try {
    const query = `?page=${page}&size=${size}` + (search ? `&search=${encodeURIComponent(search)}` : '');
    const response = await apiClient.get(`/bills${query}`);
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

export const updateBill = async (billId, updates) => {
  try {
    const response = await apiClient.patch(`/bills/${billId}`, updates);
    return response.data.data;
  } catch (error) {
    console.error('updateBill error:', error);
    throw error;
  }
};
