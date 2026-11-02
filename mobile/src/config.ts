import { Platform } from 'react-native';

/**
 * The only configuration: the API base URL and the push flag.
 * EXPO_PUBLIC_* values are inlined at build time, so they must be read with direct property access.
 * Defaults: the Android emulator reaches the host machine at 10.0.2.2; the browser build uses localhost.
 */
const DEFAULT_API = Platform.OS === 'web' ? 'http://localhost:8080' : 'http://10.0.2.2:8080';

export const API_BASE_URL = (process.env.EXPO_PUBLIC_API_BASE_URL || DEFAULT_API).replace(/\/$/, '');

export const ENABLE_PUSH = process.env.EXPO_PUBLIC_ENABLE_PUSH === 'true';
