import { ApiError } from '@/api/errors';
import type { Order, PayResult } from '@/api/adapters';

/**
 * What the UI does after POST /orders/{id}/pay:
 * - paid        200: go to tracking
 * - declined    402 PAYMENT_DECLINED: "Payment failed, items released", back to the cart
 * - confirming  202 PAYMENT_UNCONFIRMED: "Confirming your payment, don't pay again"; poll the order, no Pay, no Cancel
 * - expired     409 HOLD_EXPIRED: back to the cart
 * - error       anything else: show the message, allow retry
 */
export type PayOutcome =
  | { kind: 'paid' }
  | { kind: 'confirming' }
  | { kind: 'declined' }
  | { kind: 'expired' }
  | { kind: 'error'; error: ApiError };

export function outcomeFromResponse(status: number, body: PayResult | undefined): PayOutcome {
  if (status === 202 || body?.paymentStatus === 'UNCONFIRMED' || body?.paymentStatus === 'INITIATED') {
    return { kind: 'confirming' };
  }
  return { kind: 'paid' };
}

export function outcomeFromError(error: unknown): PayOutcome {
  if (error instanceof ApiError) {
    if (error.status === 402 || error.code === 'PAYMENT_DECLINED') return { kind: 'declined' };
    if (error.code === 'HOLD_EXPIRED') return { kind: 'expired' };
    if (error.code === 'PAYMENT_UNCONFIRMED') return { kind: 'confirming' };
    return { kind: 'error', error };
  }
  return { kind: 'error', error: new ApiError({ status: 0, code: 'UNKNOWN', message: String(error) }) };
}

/**
 * While confirming, GET /orders/{id} is polled every 3 s and the order status decides when it is settled.
 * Returns null while the payment is still unknown.
 */
export function outcomeFromPolledOrder(order: Pick<Order, 'status'>): PayOutcome | null {
  switch (order.status) {
    case 'PENDING_PAYMENT':
      return null;
    case 'CANCELLED':
      return { kind: 'declined' };
    case 'EXPIRED':
      return { kind: 'expired' };
    default:
      return { kind: 'paid' };
  }
}

export const CONFIRM_POLL_MS = 3000;
