// NOTE: 'localhost' only works from a simulator on the same Mac. For a physical
// phone on Expo Go, this must be your machine's LAN IP (e.g. via `ipconfig getifaddr en0`)
// and the phone must be on the same Wi-Fi network as the backend.
export const API_BASE_URL = __DEV__
  ? 'http://192.168.1.220:8080/api'
  : 'https://your-production-url.railway.app/api';

export const APP_NAME = 'BillWise';
export const APP_VERSION = '0.1.0';
export const FREE_SCAN_LIMIT = 10;

export const COLORS = {
  bg: '#f5f2ee',
  card: '#ffffff',
  ink: '#1a1612',
  inkLight: '#7a6e64',
  inkFaint: '#c4bdb6',
  accent: '#e8622a',
  accentLight: '#fdf0ea',
  green: '#2a7a4b',
  greenLight: '#e8f5ee',
  amber: '#b87c0a',
  amberLight: '#fdf5e0',
  red: '#c0392b',
  redLight: '#fdecea',
  blue: '#1a5fa8',
  blueLight: '#e8f0fb',
  border: '#e8e2db',
};
