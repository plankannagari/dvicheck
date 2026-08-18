import apiClient from './apiClient';

export const fetchPantry = async () => {
  try {
    const response = await apiClient.get('/pantry');
    return response.data.data;
  } catch (error) {
    console.error('fetchPantry error:', error);
    throw error;
  }
};
