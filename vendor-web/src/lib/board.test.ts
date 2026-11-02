import { describe, expect, it } from 'vitest';
import type { Order } from '@/api/adapters';
import { actionsFor, groupByColumn, isToday, newPaidOrderIds, salesPerHour } from './board';
import { toPaise } from './money';

const order = (id: number, status: Order['status'], createdAt = '2026-10-23T06:00:00Z', total = 100): Order => ({
  id, status, createdAt, total, items: [],
});

describe('board actions', () => {
  it('offers accept or reject for new orders', () => {
    expect(actionsFor('PAID').map((a) => a.target)).toEqual(['ACCEPTED', 'REJECTED']);
  });

  it('asks for confirmation before rejecting, mentioning the refund', () => {
    const reject = actionsFor('ACCEPTED').find((a) => a.target === 'REJECTED');
    expect(reject?.confirm).toMatch(/refund/i);
  });

  it('only moves forward from preparing', () => {
    expect(actionsFor('PREPARING').map((a) => a.target)).toEqual(['READY']);
  });

  it('never offers COLLECTED on the board (pickup verification does that)', () => {
    for (const s of ['PAID', 'ACCEPTED', 'PREPARING', 'READY', 'UNCOLLECTED'] as const) {
      expect(actionsFor(s).some((a) => a.target === 'COLLECTED')).toBe(false);
    }
  });
});

describe('columns', () => {
  it('groups active orders and keeps the oldest first', () => {
    const groups = groupByColumn([
      order(2, 'PAID', '2026-10-23T06:05:00Z'),
      order(1, 'PAID', '2026-10-23T06:01:00Z'),
      order(3, 'READY'),
      order(4, 'COLLECTED'),
    ]);
    expect(groups.PAID.map((o) => o.id)).toEqual([1, 2]);
    expect(groups.READY.map((o) => o.id)).toEqual([3]);
    expect(Object.keys(groups)).toEqual(['PAID', 'ACCEPTED', 'PREPARING', 'READY']);
  });
});

describe('new order chime', () => {
  it('does not chime on the first load', () => {
    expect(newPaidOrderIds(null, [order(1, 'PAID')])).toEqual([]);
  });

  it('chimes only for paid orders not seen before', () => {
    const known = new Set([1]);
    expect(newPaidOrderIds(known, [order(1, 'PAID'), order(2, 'PAID'), order(3, 'ACCEPTED')])).toEqual([2]);
  });
});

describe('dates and sales', () => {
  it('uses campus time for "today"', () => {
    // 20:00 UTC on the 22nd is 01:30 on the 23rd in Kolkata
    expect(isToday('2026-10-22T20:00:00Z', '2026-10-23')).toBe(true);
    expect(isToday('2026-10-22T18:00:00Z', '2026-10-23')).toBe(false);
  });

  it('buckets sales per campus hour in paise, filling gaps', () => {
    const rows = salesPerHour(
      [
        order(1, 'COLLECTED', '2026-10-23T06:40:00Z', 120.5), // 12:10 IST
        order(2, 'COLLECTED', '2026-10-23T06:50:00Z', 30), // 12:20 IST
        order(3, 'COLLECTED', '2026-10-23T08:35:00Z', 15.25), // 14:05 IST
      ],
      (v) => toPaise(v),
    );
    expect(rows).toEqual([
      { hour: '12:00', paise: 15050, orders: 2 },
      { hour: '13:00', paise: 0, orders: 0 },
      { hour: '14:00', paise: 1525, orders: 1 },
    ]);
  });
});
