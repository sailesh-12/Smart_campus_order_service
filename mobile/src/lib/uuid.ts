import { uuid } from 'expo';

/**
 * UUID v4 for Idempotency-Key (Hermes has no crypto.randomUUID).
 * Uses expo-modules-core's generator, which is part of every Expo runtime (Expo Go included); expo-crypto's native
 * module is not available in every Expo Go build and crashed the app at startup.
 */
export function newUuid(): string {
  return uuid.v4();
}
