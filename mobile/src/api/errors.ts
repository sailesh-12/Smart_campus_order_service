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

/** Friendly wording for the business errors a student can hit. Falls back to the server message. */
const FRIENDLY: Record<string, string> = {
  OUT_OF_STOCK: 'Sorry, something in your cart just sold out.',
  VENDOR_CLOSED: 'This canteen is closed right now.',
  VENDOR_SUSPENDED: 'This canteen is not taking orders at the moment.',
  SLOT_FULL: 'That pickup time just filled up. Please choose another one.',
  HOLD_EXPIRED: 'Your order hold ran out before payment. Your cart is still here, place the order again.',
  PAYMENT_DECLINED: 'Payment failed and the items were released. Nothing was charged.',
  PAYMENT_UNCONFIRMED: "We're still confirming your payment. Please don't pay again.",
  TOO_MANY_UNPAID_ORDERS: 'You already have unpaid orders. Pay for or cancel one before ordering again.',
  INVALID_TRANSITION: 'This order changed in the meantime. Showing the latest status.',
  REFUND_NOT_ALLOWED: 'A refund is not possible for this payment.',
  ITEM_UNAVAILABLE: 'One of the items is no longer available.',
  IDEMPOTENCY_KEY_REUSED: 'Your cart changed while ordering. Please try again.',
  RATE_LIMITED: 'Too many attempts. Please wait a moment.',
  INVALID_RESET_TOKEN: 'This reset link is invalid or has expired. Request a new one.',
  NETWORK_ERROR: "Can't reach the server. Check your connection and try again.",
};

export function friendlyMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'RATE_LIMITED' && error.retryAfterSeconds) {
      return `Too many attempts. Try again in ${error.retryAfterSeconds} seconds.`;
    }
    // OUT_OF_STOCK carries the item name in the server message ("Chicken 65 just sold out")
    if (error.code === 'OUT_OF_STOCK' && error.message) {
      return `Sorry, ${error.message.replace(/\.$/, '')}. We've refreshed the menu.`;
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
