import createClient, { type Middleware } from 'openapi-fetch';
import { API_BASE_URL } from '@/config';
import type { paths } from './schema';
import { ApiError, networkError, toApiError } from './errors';

type AuthHooks = { getToken: () => string | null; onUnauthorized: () => void };
let hooks: AuthHooks = { getToken: () => null, onUnauthorized: () => {} };

/** Wired by the auth store, so this module does not import the store (no cycle). */
export function configureAuth(next: AuthHooks) {
  hooks = next;
}

export function currentToken(): string | null {
  return hooks.getToken();
}

const authMiddleware: Middleware = {
  onRequest({ request }) {
    const token = hooks.getToken();
    if (token) request.headers.set('Authorization', `Bearer ${token}`);
    return request;
  },
  onResponse({ request, response }) {
    // A 401 on an authenticated call means the session ended (expired, or password reset elsewhere)
    if (response.status === 401 && request.headers.has('Authorization')) hooks.onUnauthorized();
    return response;
  },
};

export const api = createClient<paths>({ baseUrl: API_BASE_URL });
api.use(authMiddleware);

type FetchResult<T> = { data?: T; error?: unknown; response: Response };

/**
 * Awaits an openapi-fetch call and returns the data, or throws one ApiError.
 * Also returns the raw response for callers that need the status (202) or headers (Idempotent-Replayed).
 */
export async function call<T>(request: Promise<FetchResult<T>>): Promise<{ data: T; response: Response }> {
  let result: FetchResult<T>;
  try {
    result = await request;
  } catch (e) {
    throw networkError(e);
  }
  if (!result.response.ok || result.error !== undefined) {
    throw toApiError(result.response, result.error);
  }
  return { data: result.data as T, response: result.response };
}

export async function data<T>(request: Promise<FetchResult<T>>): Promise<T> {
  return (await call(request)).data;
}

export { ApiError };
