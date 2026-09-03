import { manipulateAsync, SaveFormat } from 'expo-image-manipulator';

import apiClient from './apiClient';

export const uploadReceiptImage = async (imageUri, options = {}) => {
  try {
    const compressed = await manipulateAsync(
      imageUri,
      [{ resize: { width: 1200 } }],
      { compress: 0.8, format: SaveFormat.JPEG }
    );

    const formData = new FormData();
    formData.append('image', {
      uri: compressed.uri,
      type: 'image/jpeg',
      name: 'receipt.jpg',
    });
    if (options.storeName) {
      formData.append('storeName', options.storeName);
    }
    if (options.billType) {
      formData.append('billType', options.billType);
    }
    if (options.purchaseDate) {
      formData.append('purchaseDate', options.purchaseDate);
    }

    // Longer timeout than apiClient's global 10s default — this request runs
    // Vision OCR + Gemini extraction + Gemini/Claude categorisation
    // sequentially server-side (~7s typical), leaving almost no margin under
    // the default before a slightly-slow upstream call or network condition
    // (e.g. right after reconnecting from offline) trips a client-side timeout
    // even though the backend would have finished successfully.
    const response = await apiClient.post('/bills/scan', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 30000,
    });
    return response.data.data;
  } catch (error) {
    console.error('uploadReceiptImage error:', error);
    throw error;
  }
};

export default uploadReceiptImage;
