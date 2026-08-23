import apiClient from './apiClient';

export const submitFeedback = async (lineItemId, feedback) => {
  try {
    const response = await apiClient.post(`/feedback/${lineItemId}`, { feedback });
    return response.data.data;
  } catch (error) {
    console.error('submitFeedback error:', error);
    throw error;
  }
};
