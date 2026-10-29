/**
 * One error type for every failed API call. The backend error body is
 * { timestamp, status, code, message, fieldErrors } (GlobalExceptionHandler); it is not described in the
 * OpenAPI document, so it is typed here. TODO(contract): ask for the error schema in /v3/api-docs.
 */
export type FieldErrors = Record<string, string>;

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldErrors;
  readonly retryAfterSeconds: number | null;

  constructor(opts: { status: number; code: string; message: string; fieldErrors?: FieldErrors | null; retryAfterSeconds?: number | null }) {
    super(opts.message);
    this.name = 'ApiError';
    this.status = opts.status;
    this.code = opts.code;
    this.fieldErrors = opts.fieldErrors ?? {};
    this.retryAfterSeconds = opts.retryAfterSeconds ?? null;
  }
}

/** Friendly wording for the errors an admin can hit. Falls back to the server message. */
const FRIENDLY: Record<string, string> = {
  OUT_OF_STOCK: 'That item is out of stock.',
  VENDOR_CLOSED: 'The canteen is closed.',
  VENDOR_SUSPENDED: 'This canteen is suspended.',
  SLOT_FULL: 'That pickup slot is full.',
  HOLD_EXPIRED: 'The order hold ran out before payment.',
  PAYMENT_DECLINED: 'The payment was declined.',
  PAYMENT_UNCONFIRMED: 'The payment is still being confirmed with the gateway.',
  TOO_MANY_UNPAID_ORDERS: 'The student has too many unpaid orders.',
  INVALID_TRANSITION: 'This changed in the meantime (or is not allowed in its current state). The list has been refreshed.',
  REFUND_NOT_ALLOWED: 'A refund is not possible for this payment in its current state.',
  VENDOR_EXISTS: 'That user already owns a canteen.',
  FORBIDDEN: 'Only admins can do this.',
  RATE_LIMITED: 'Too many attempts. Please wait a moment.',
  NETWORK_ERROR:
    'Can’t reach the server. If the backend is running, it may be blocking this site: its CORS settings must allow http://localhost:3001.',
};

export function friendlyMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'RATE_LIMITED' && error.retryAfterSeconds) {
      return `Too many attempts. Try again in ${error.retryAfterSeconds} seconds.`;
    }
    return FRIENDLY[error.code] ?? (error.message || 'Something went wrong.');
  }
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

function parseRetryAfter(value: string | null): number | null {
  if (!value) return null;
  const seconds = Number(value);
  if (Number.isFinite(seconds)) return Math.max(0, Math.ceil(seconds));
  const date = Date.parse(value);
  return Number.isNaN(date) ? null : Math.max(0, Math.ceil((date - Date.now()) / 1000));
}

export function toApiError(response: Response | undefined, body: unknown): ApiError {
  const b = (body ?? {}) as Partial<{ status: number; code: string; message: string; fieldErrors: FieldErrors | null }>;
  const status = response?.status ?? b.status ?? 0;
  return new ApiError({
    status,
    code: b.code ?? (status === 401 ? 'UNAUTHORIZED' : status === 403 ? 'FORBIDDEN' : `HTTP_${status}`),
    message: b.message ?? (response?.statusText || 'Request failed'),
    fieldErrors: b.fieldErrors ?? null,
    retryAfterSeconds: parseRetryAfter(response?.headers.get('Retry-After') ?? null),
  });
}

export function networkError(cause: unknown): ApiError {
  return new ApiError({ status: 0, code: 'NETWORK_ERROR', message: cause instanceof Error ? cause.message : 'Network error' });
}
