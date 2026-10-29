import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

/**
 * Token storage: the OS keychain/keystore on phones (expo-secure-store).
 * expo-secure-store has no web implementation, so the browser build (`npm run web`, for demos without an
 * emulator) falls back to sessionStorage, which ends with the tab. Phones never use the fallback.
 */
const web = Platform.OS === 'web';

export async function getItem(key: string): Promise<string | null> {
  if (web) return typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem(key);
  return SecureStore.getItemAsync(key);
}

export async function setItem(key: string, value: string): Promise<void> {
  if (web) {
    sessionStorage.setItem(key, value);
    return;
  }
  await SecureStore.setItemAsync(key, value);
}

export async function deleteItem(key: string): Promise<void> {
  if (web) {
    if (typeof sessionStorage !== 'undefined') sessionStorage.removeItem(key);
    return;
  }
  await SecureStore.deleteItemAsync(key);
}
