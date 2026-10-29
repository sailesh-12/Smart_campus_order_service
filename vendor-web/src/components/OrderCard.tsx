'use client';

import type { ReactNode } from 'react';
import type { Order } from '@/api/adapters';
import { formatINR } from '@/lib/money';
import { formatTime } from '@/lib/time';
import { StatusBadge } from './ui';

export function OrderCard({ order, flashing, children }: { order: Order; flashing?: boolean; children?: ReactNode }) {
  return (
    <article
      className={`rounded-card border bg-white p-4 shadow-sm ${flashing ? 'flash-new border-primary' : 'border-line'}`}
      aria-label={`Order ${order.id}`}
    >
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="text-lg font-extrabold">#{order.id}</p>
          <p className="text-sm text-muted">
            Placed {formatTime(order.createdAt)}
            {order.pickupSlotStart ? ` · pickup ${formatTime(order.pickupSlotStart)}` : ''}
          </p>
        </div>
        <StatusBadge status={order.status} />
      </div>
      {flashing ? <p className="mt-2 font-bold text-primary">New order</p> : null}
      <ul className="mt-3 space-y-1">
        {(order.items ?? []).map((i) => (
          <li key={i.menuItemId} className="flex justify-between gap-2 text-base">
            <span>
              <strong>{i.qty} ×</strong> {i.itemName}
            </span>
          </li>
        ))}
      </ul>
      <p className="mt-2 text-sm text-muted">Total {formatINR(order.total)}</p>
      {children ? <div className="mt-3 flex flex-wrap gap-2">{children}</div> : null}
    </article>
  );
}
