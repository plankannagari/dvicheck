import apiClient from './apiClient';

export const fetchLists = async () => {
  try {
    const response = await apiClient.get('/shopping/lists');
    return response.data.data;
  } catch (error) {
    console.error('fetchLists error:', error);
    throw error;
  }
};

export const createList = async (name) => {
  try {
    const response = await apiClient.post('/shopping/lists', { name });
    return response.data.data;
  } catch (error) {
    console.error('createList error:', error);
    throw error;
  }
};

export const fetchListDetail = async (listId) => {
  try {
    const response = await apiClient.get(`/shopping/lists/${listId}`);
    return response.data.data;
  } catch (error) {
    console.error('fetchListDetail error:', error);
    throw error;
  }
};

export const addItem = async (listId, name, quantity) => {
  try {
    const response = await apiClient.post(`/shopping/lists/${listId}/items`, { name, quantity });
    return response.data.data;
  } catch (error) {
    console.error('addItem error:', error);
    throw error;
  }
};

export const toggleItem = async (listId, itemId) => {
  try {
    const response = await apiClient.patch(`/shopping/lists/${listId}/items/${itemId}/toggle`);
    return response.data.data;
  } catch (error) {
    console.error('toggleItem error:', error);
    throw error;
  }
};

export const deleteItem = async (listId, itemId) => {
  try {
    const response = await apiClient.delete(`/shopping/lists/${listId}/items/${itemId}`);
    return response.data.data;
  } catch (error) {
    console.error('deleteItem error:', error);
    throw error;
  }
};
