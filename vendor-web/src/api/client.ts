import createClient, { type Middleware } from 'openapi-fetch';
import { clearSession, getToken } from '@/auth/session';
import { API_BASE_URL } from '@/config';
import { ApiError, networkError, toApiError } from './errors';
import type { paths } from './schema';

const authMiddleware: Middleware = {
  onRequest({ request }) {
    const token = getToken();
    if (token) request.headers.set('Authorization', `Bearer ${token}`);
    return request;
  },
  onResponse({ request, response }) {
    // 401 on an authenticated call: session expired or the password was reset; log out
    if (response.status === 401 && request.headers.has('Authorization')) {
      clearSession('Your session ended. Please log in again.');
    }
    return response;
  },
};

export const api = createClient<paths>({ baseUrl: API_BASE_URL });
api.use(authMiddleware);

type FetchResult<T> = { data?: T; error?: unknown; response: Response };

/** Awaits an openapi-fetch call and returns data + response, or throws one ApiError. */
export async function call<T>(request: Promise<FetchResult<T>>): Promise<{ data: T; response: Response }> {
  let result: FetchResult<T>;
  try {
    result = await request;
  } catch (e) {
    throw networkError(e);
  }
  if (!result.response.ok || result.error !== undefined) throw toApiError(result.response, result.error);
  return { data: result.data as T, response: result.response };
}

export async function data<T>(request: Promise<FetchResult<T>>): Promise<T> {
  return (await call(request)).data;
}

export { ApiError };
