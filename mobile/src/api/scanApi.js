import { manipulateAsync, SaveFormat } from 'expo-image-manipulator';

import apiClient from './apiClient';

// requestConfig is merged into the axios call — e.g. { skipPaywall: true } from
// background callers so a 402 doesn't open the paywall (see apiClient.js).
export const uploadReceiptImage = async (imageUri, options = {}, requestConfig = {}) => {
  try {
    const compressed = await manipulateAsync(
      imageUri,
      // 1600 (was 1200) keeps small print on long receipts sharper for OCR — Vision
      // bills per image, not per pixel, so this costs nothing extra.
      [{ resize: { width: 1600 } }],
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
      ...requestConfig,
    });
    return response.data.data;
  } catch (error) {
    console.error('uploadReceiptImage error:', error);
    throw error;
  }
};

export const addManualItems = async (billId, items) => {
  const response = await apiClient.patch(`/bills/${billId}/items`, { items });
  return response.data.data;
};

export default uploadReceiptImage;
