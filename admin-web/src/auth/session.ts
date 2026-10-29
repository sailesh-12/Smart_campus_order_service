'use client';

import { useSyncExternalStore } from 'react';
import type { Role, Session } from '@/api/adapters';

/**
 * Session in sessionStorage: it survives reloads but ends when the tab closes, and is not shared between tabs.
 * Trade-off (see README): any script running on the page can read it (XSS), unlike an httpOnly cookie,
 * which the backend does not offer; a closed tab on a shared counter tablet does log the vendor out.
 */
const KEY = 'sc.admin.session';
export const APP_ROLE: Role = 'ADMIN';

const listeners = new Set<() => void>();
let cached: Session | null | undefined;
let cachedRaw: string | null = null;
let notice: string | null = null;

function read(): Session | null {
  if (typeof window === 'undefined') return null;
  const raw = window.sessionStorage.getItem(KEY);
  if (raw === cachedRaw && cached !== undefined) return cached;
  cachedRaw = raw;
  try {
    cached = raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    cached = null;
  }
  return cached;
}

function emit() {
  for (const l of listeners) l();
}

export class WrongRoleError extends Error {
  constructor(readonly role: Role) {
    super(
      role === 'STUDENT'
        ? 'Students order in the Smart Campus mobile app. This dashboard is for campus admins.'
        : 'Canteen staff use the vendor dashboard. This dashboard is for campus admins.',
    );
    this.name = 'WrongRoleError';
  }
}

/** Each app accepts only its own role. */
export function assertAppRole(session: Session, expected: Role = APP_ROLE): Session {
  if (session.user.role !== expected) throw new WrongRoleError(session.user.role);
  return session;
}

export function saveSession(session: Session) {
  const ok = assertAppRole(session);
  window.sessionStorage.setItem(KEY, JSON.stringify(ok));
  notice = null;
  emit();
}

export function clearSession(reason?: string) {
  if (typeof window === 'undefined') return;
  window.sessionStorage.removeItem(KEY);
  notice = reason ?? null;
  emit();
}

export function getToken(): string | null {
  return read()?.token ?? null;
}

/** Why the last session ended (e.g. 401), shown on the login page until the next login. */
export function useLogoutNotice(): string | null {
  return useSyncExternalStore(subscribe, () => notice, () => null);
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/**
 * undefined while rendering on the server / before hydration (unknown), then the session or null.
 */
export function useSession(): Session | null | undefined {
  return useSyncExternalStore(subscribe, read, () => undefined);
}
