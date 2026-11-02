import type { Order } from '@/api/adapters';
import type { OrderStatus } from './status';
import { campusToday } from './time';

/** Live board columns, left to right. READY orders leave the board when collected (pickup verify). */
export const COLUMNS: { status: OrderStatus; title: string }[] = [
  { status: 'PAID', title: 'New' },
  { status: 'ACCEPTED', title: 'Accepted' },
  { status: 'PREPARING', title: 'Preparing' },
  { status: 'READY', title: 'Ready' },
];

export type BoardAction = { label: string; target: OrderStatus; tone: 'primary' | 'danger'; confirm?: string };

/**
 * Buttons per column. Only transitions the backend allows a vendor to make (PATCH /orders/{id}/status);
 * COLLECTED happens through pickup verification, never from the board.
 */
export function actionsFor(status: OrderStatus | undefined): BoardAction[] {
  const reject: BoardAction = {
    label: 'Reject',
    target: 'REJECTED',
    tone: 'danger',
    confirm: 'Reject this order? The student is refunded in full and the items go back into stock.',
  };
  switch (status) {
    case 'PAID':
      return [{ label: 'Accept', target: 'ACCEPTED', tone: 'primary' }, reject];
    case 'ACCEPTED':
      return [{ label: 'Start preparing', target: 'PREPARING', tone: 'primary' }, reject];
    case 'PREPARING':
      return [{ label: 'Mark ready', target: 'READY', tone: 'primary' }];
    default:
      return [];
  }
}

export function groupByColumn(orders: Order[]): Record<string, Order[]> {
  const groups: Record<string, Order[]> = Object.fromEntries(COLUMNS.map((c) => [c.status, []]));
  for (const o of orders) if (o.status && groups[o.status]) groups[o.status].push(o);
  // Oldest first, so the kitchen works in order
  for (const list of Object.values(groups)) list.sort((a, b) => (a.createdAt ?? '').localeCompare(b.createdAt ?? ''));
  return groups;
}

/**
 * Which paid orders are new since the last look (chime + flash). The first load seeds the set silently,
 * so opening the dashboard does not chime for orders that were already waiting.
 */
export function newPaidOrderIds(known: ReadonlySet<number> | null, orders: Order[]): number[] {
  const paid = orders.filter((o) => o.status === 'PAID' && o.id != null).map((o) => o.id!);
  if (known === null) return [];
  return paid.filter((id) => !known.has(id));
}

/** Orders created today in campus time. */
export function isToday(iso: string | undefined, today: string = campusToday()): boolean {
  return !!iso && campusToday(new Date(iso)) === today;
}

const hourFmt = new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Kolkata', hour: '2-digit', hour12: false });

/**
 * Sales per hour of day (campus time), in paise, from a list of orders.
 * TODO(contract): /vendor/analytics has no hourly breakdown, so this is computed in the browser
 * from the shop's collected orders.
 */
export function salesPerHour(orders: Order[], toPaise: (v: number | undefined) => number): { hour: string; paise: number; orders: number }[] {
  const buckets = new Map<number, { paise: number; orders: number }>();
  for (const o of orders) {
    if (!o.createdAt) continue;
    const hour = Number(hourFmt.format(new Date(o.createdAt))) % 24;
    const b = buckets.get(hour) ?? { paise: 0, orders: 0 };
    b.paise += toPaise(o.total);
    b.orders += 1;
    buckets.set(hour, b);
  }
  if (buckets.size === 0) return [];
  const hours = [...buckets.keys()];
  const from = Math.min(...hours);
  const to = Math.max(...hours);
  const out = [];
  for (let h = from; h <= to; h++) {
    const b = buckets.get(h) ?? { paise: 0, orders: 0 };
    out.push({ hour: `${String(h).padStart(2, '0')}:00`, ...b });
  }
  return out;
}
