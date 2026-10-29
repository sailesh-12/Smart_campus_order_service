import type { ManualRefundReason, Order, PaymentIssue } from '@/api/adapters';
import { ApiError } from '@/api/errors';

/**
 * Backend default for app.payment.unconfirmed-max-minutes. Only used to explain why "Resolve" is disabled;
 * the server makes the real decision and answers 409 if it disagrees.
 * TODO(contract): not exposed by the API.
 */
export const UNCONFIRMED_MAX_MINUTES = 30;

/** Retry refund is only for REFUND_FAILED (anything else is 409 REFUND_NOT_ALLOWED). */
export function canRetryRefund(issue: Pick<PaymentIssue, 'status'>): boolean {
  return issue.status === 'REFUND_FAILED';
}

/** Resolve: REFUND_FAILED, or UNCONFIRMED once it is older than the cap. */
export function canResolve(issue: Pick<PaymentIssue, 'status' | 'ageMinutes'>, cap = UNCONFIRMED_MAX_MINUTES): boolean {
  if (issue.status === 'REFUND_FAILED') return true;
  return issue.status === 'UNCONFIRMED' && (issue.ageMinutes ?? 0) >= cap;
}

/**
 * Manual full refund (POST /admin/payments/{id}/refund): reason COMPLAINT or UNCOLLECTED_OVERRIDE;
 * UNCOLLECTED_OVERRIDE needs an order in UNCOLLECTED. Returns why it is not possible, or null.
 */
export function manualRefundProblem(order: Pick<Order, 'status' | 'paymentId'> | undefined, reason: ManualRefundReason): string | null {
  if (!order) return 'Load the order first.';
  if (!order.paymentId) return 'This order has no payment.';
  if (order.status === 'PENDING_PAYMENT') return 'This order was never paid.';
  if (reason === 'UNCOLLECTED_OVERRIDE' && order.status !== 'UNCOLLECTED') {
    return 'UNCOLLECTED_OVERRIDE is only for orders in Not collected (UNCOLLECTED).';
  }
  return null;
}

const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789';

/**
 * Temporary password for a canteen account reset, generated in the browser with crypto.getRandomValues.
 * Shown to the admin once; it is never stored here. Always contains an upper case letter, a lower case letter and a digit.
 */
export function generateTempPassword(length = 12, random: (n: number) => Uint8Array = (n) => crypto.getRandomValues(new Uint8Array(n))): string {
  if (length < 8) throw new Error('Password must be at least 8 characters');
  for (;;) {
    const bytes = random(length);
    const value = Array.from(bytes, (b) => ALPHABET[b % ALPHABET.length]).join('');
    if (/[A-Z]/.test(value) && /[a-z]/.test(value) && /\d/.test(value)) return value;
  }
}

/** Optional admin endpoints (GET /admin/orders, GET /admin/actions) may be missing on some backends. */
export function isEndpointMissing(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 404 || error.status === 405) && /no endpoint|not found/i.test(error.message);
}
