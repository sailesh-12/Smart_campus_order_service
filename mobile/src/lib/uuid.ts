import { randomUUID } from 'expo-crypto';

/** UUID v4 for Idempotency-Key (Hermes has no crypto.randomUUID). */
export function newUuid(): string {
  return randomUUID();
}
