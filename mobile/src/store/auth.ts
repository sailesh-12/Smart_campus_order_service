import * as SecureStore from '@/lib/secureStorage';
import { create } from 'zustand';
import { configureAuth } from '@/api/client';
import type { Session, SessionUser } from '@/api/adapters';
import { assertAppRole } from '@/lib/roleGuard';

const TOKEN_KEY = 'sc.token';
const USER_KEY = 'sc.user';

type AuthState = {
  status: 'loading' | 'signedOut' | 'signedIn';
  token: string | null;
  user: SessionUser | null;
  /** Set when the server ended the session (401), so the login screen can say why. */
  notice: string | null;
  hydrate: () => Promise<void>;
  signIn: (session: Session) => Promise<void>;
  signOut: (notice?: string) => Promise<void>;
  setUserName: (name: string) => void;
};

const signOutListeners = new Set<() => void | Promise<void>>();

/** Lets other modules (cart, push, query cache) clean up on logout without the store importing them. */
export function onSignOut(listener: () => void | Promise<void>) {
  signOutListeners.add(listener);
  return () => {
    signOutListeners.delete(listener);
  };
}

export const useAuth = create<AuthState>((set, get) => ({
  status: 'loading',
  token: null,
  user: null,
  notice: null,

  hydrate: async () => {
    try {
      const [token, userJson] = await Promise.all([
        SecureStore.getItem(TOKEN_KEY),
        SecureStore.getItem(USER_KEY),
      ]);
      if (token && userJson) {
        const session = assertAppRole({ token, user: JSON.parse(userJson) as SessionUser });
        set({ status: 'signedIn', token: session.token, user: session.user });
        return;
      }
    } catch {
      await Promise.all([SecureStore.deleteItem(TOKEN_KEY), SecureStore.deleteItem(USER_KEY)]);
    }
    set({ status: 'signedOut', token: null, user: null });
  },

  signIn: async (session) => {
    const ok = assertAppRole(session);
    await SecureStore.setItem(TOKEN_KEY, ok.token);
    await SecureStore.setItem(USER_KEY, JSON.stringify(ok.user));
    set({ status: 'signedIn', token: ok.token, user: ok.user, notice: null });
  },

  signOut: async (notice) => {
    if (get().status === 'signedOut') return;
    for (const listener of signOutListeners) {
      try {
        await listener();
      } catch {
        // cleanup is best effort
      }
    }
    await Promise.all([SecureStore.deleteItem(TOKEN_KEY), SecureStore.deleteItem(USER_KEY)]);
    set({ status: 'signedOut', token: null, user: null, notice: notice ?? null });
  },

  setUserName: (name) => {
    const user = get().user;
    if (!user) return;
    const next = { ...user, name };
    set({ user: next });
    void SecureStore.setItem(USER_KEY, JSON.stringify(next));
  },
}));

configureAuth({
  getToken: () => useAuth.getState().token,
  onUnauthorized: () => void useAuth.getState().signOut('Your session ended. Please log in again.'),
});
