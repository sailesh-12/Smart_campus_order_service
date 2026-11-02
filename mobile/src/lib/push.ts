import Constants, { ExecutionEnvironment } from 'expo-constants';
import * as Device from 'expo-device';
import * as SecureStore from '@/lib/secureStorage';
import { Platform } from 'react-native';
import { api } from '@/api/client';
import { ENABLE_PUSH } from '@/config';

const PUSH_TOKEN_KEY = 'sc.pushToken';

/**
 * Registers this phone for push (POST /devices) when EXPO_PUBLIC_ENABLE_PUSH=true.
 * Uses the native device token (FCM on Android, APNs on iOS), which is what the backend's FcmPushSender expects.
 * Not available in Expo Go: it needs a development build with Firebase configured.
 * Never throws: push is a nice-to-have and must not block using the app.
 */
export async function registerForPush(): Promise<void> {
  if (!ENABLE_PUSH || Platform.OS === 'web') return;
  if (Constants.executionEnvironment === ExecutionEnvironment.StoreClient) return; // Expo Go
  if (!Device.isDevice) return;
  try {
    // Loaded lazily so the module is not initialised at all when push is off
    const Notifications = await import('expo-notifications');
    const existing = await Notifications.getPermissionsAsync();
    const granted = existing.granted || (await Notifications.requestPermissionsAsync()).granted;
    if (!granted) return;
    const { data: token } = await Notifications.getDevicePushTokenAsync();
    if (typeof token !== 'string' || !token) return;
    const { response } = await api.POST('/devices', {
      body: { token, platform: Platform.OS === 'ios' ? 'IOS' : 'ANDROID' },
    });
    if (response.ok) await SecureStore.setItem(PUSH_TOKEN_KEY, token);
  } catch {
    // ignore: the app works without push
  }
}

/** DELETE /devices/{token} on logout, while the session token is still valid. */
export async function unregisterPush(): Promise<void> {
  const token = await SecureStore.getItem(PUSH_TOKEN_KEY);
  if (!token) return;
  try {
    await api.DELETE('/devices/{token}', { params: { path: { token } } });
  } finally {
    await SecureStore.deleteItem(PUSH_TOKEN_KEY);
  }
}
