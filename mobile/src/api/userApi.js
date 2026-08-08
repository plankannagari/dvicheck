import apiClient from './apiClient';

export const fetchMe = async () => {
  try {
    const response = await apiClient.get('/users/me');
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
