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

/** Friendly wording for the business errors canteen staff can hit. Falls back to the server message. */
const FRIENDLY: Record<string, string> = {
  OUT_OF_STOCK: 'That item is out of stock.',
  VENDOR_CLOSED: 'The shop is closed.',
  VENDOR_SUSPENDED: 'This shop is suspended by the campus admin.',
  SLOT_FULL: 'That pickup slot is full.',
  HOLD_EXPIRED: 'The order hold ran out before payment.',
  PAYMENT_DECLINED: 'The payment was declined.',
  PAYMENT_UNCONFIRMED: 'The payment for this order is still being confirmed.',
  TOO_MANY_UNPAID_ORDERS: 'The student has too many unpaid orders.',
  INVALID_TRANSITION: 'This order changed in the meantime. The board has been refreshed.',
  REFUND_NOT_ALLOWED: 'A refund is not possible for this payment.',
  ITEM_IN_USE: 'This item appears in past orders, so it can’t be deleted. Mark it unavailable instead.',
  FORBIDDEN: 'That belongs to another shop.',
  RATE_LIMITED: 'Too many attempts. Please wait a moment.',
  NETWORK_ERROR: 'Can’t reach the server. Check the connection and try again.',
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
