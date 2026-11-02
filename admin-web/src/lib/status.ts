import type { components } from '@/api/schema';

export type OrderStatus = NonNullable<components['schemas']['OrderResponse']['status']>;

/**
 * Order status presentation, identical in the student app, vendor dashboard and admin dashboard.
 * Every badge shows the text label as well as the colour, so status is never conveyed by colour alone.
 */
export const ORDER_STATUS: Record<OrderStatus, { label: string; color: string; background: string }> = {
  PENDING_PAYMENT: { label: 'Waiting for payment', color: '#4B5563', background: '#E5E7EB' },
  PAID: { label: 'Confirmed', color: '#1D4ED8', background: '#DBEAFE' },
  ACCEPTED: { label: 'Accepted', color: '#6D28D9', background: '#EDE9FE' },
  PREPARING: { label: 'Preparing', color: '#C2410C', background: '#FFEDD5' },
  READY: { label: 'Ready for pickup', color: '#15803D', background: '#DCFCE7' },
  COLLECTED: { label: 'Collected', color: '#F9FAFB', background: '#374151' },
  UNCOLLECTED: { label: 'Not collected', color: '#92400E', background: '#FEF3C7' },
  REJECTED: { label: 'Rejected', color: '#B91C1C', background: '#FEE2E2' },
  CANCELLED: { label: 'Cancelled', color: '#B91C1C', background: '#FEE2E2' },
  EXPIRED: { label: 'Expired', color: '#B91C1C', background: '#FEE2E2' },
};

export function statusInfo(status: string | null | undefined) {
  return ORDER_STATUS[status as OrderStatus] ?? { label: status ?? 'Unknown', color: '#4B5563', background: '#E5E7EB' };
}

/** Progress steps shown on the tracking screen. */
export const TRACKING_STEPS: OrderStatus[] = ['PAID', 'ACCEPTED', 'PREPARING', 'READY', 'COLLECTED'];

export const CLOSED_STATUSES: ReadonlySet<OrderStatus> = new Set(['COLLECTED', 'REJECTED', 'CANCELLED', 'EXPIRED']);

export function stepIndex(status: OrderStatus): number {
  if (status === 'UNCOLLECTED') return TRACKING_STEPS.indexOf('READY');
  return TRACKING_STEPS.indexOf(status);
}
