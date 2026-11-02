'use client';

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import type { Order } from '@/api/adapters';
import { api, data } from '@/api/client';
import { Banner, Empty, ErrorBox, Loading, StatusBadge } from '@/components/ui';
import { isEndpointMissing } from '@/lib/admin';
import { formatINR } from '@/lib/money';
import { ORDER_STATUS, type OrderStatus } from '@/lib/status';
import { formatDateTime } from '@/lib/time';

const LIMIT = 200;

export function OrdersView() {
  const [status, setStatus] = useState<OrderStatus | 'ALL'>('ALL');
  // GET /admin/orders is optional in the brief; this backend provides it (?limit, most recent first)
  const orders = useQuery({
    queryKey: ['admin', 'orders', LIMIT],
    queryFn: (): Promise<Order[]> => data(api.GET('/admin/orders', { params: { query: { limit: LIMIT } } })),
  });

  if (orders.isPending) return <Loading label="Loading orders…" />;
  if (orders.isError) {
    return isEndpointMissing(orders.error) ? (
      <Banner tone="info" title="Orders list not available">This backend has no GET /admin/orders.</Banner>
    ) : (
      <ErrorBox error={orders.error} onRetry={() => orders.refetch()} />
    );
  }

  const shown = status === 'ALL' ? orders.data : orders.data.filter((o) => o.status === status);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h1 className="text-2xl font-extrabold">Orders</h1>
        <label>
          <span className="mb-1 block font-semibold">Status</span>
          <select value={status} onChange={(e) => setStatus(e.target.value as OrderStatus | 'ALL')} className="min-h-12 rounded-card border border-line bg-white px-3">
            <option value="ALL">All</option>
            {Object.entries(ORDER_STATUS).map(([s, info]) => <option key={s} value={s}>{info.label}</option>)}
          </select>
        </label>
      </div>
      <p className="text-sm text-muted">The latest {LIMIT} orders across campus.</p>
      {shown.length === 0 ? <Empty title="No orders" /> : (
        <div className="overflow-x-auto rounded-card border border-line bg-white">
          <table className="w-full min-w-[760px] text-left">
            <thead className="bg-page text-sm text-muted">
              <tr>
                <th className="p-3">Order</th>
                <th className="p-3">Placed</th>
                <th className="p-3">Canteen</th>
                <th className="p-3">Student</th>
                <th className="p-3">Items</th>
                <th className="p-3">Total</th>
                <th className="p-3">Status</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((o) => (
                <tr key={o.id} className="border-t border-line">
                  <td className="p-3 font-bold">#{o.id}<span className="block text-sm font-normal text-muted">payment #{o.paymentId ?? '—'}</span></td>
                  <td className="p-3 text-sm">{formatDateTime(o.createdAt)}</td>
                  <td className="p-3">#{o.vendorId}</td>
                  <td className="p-3">#{o.studentId}</td>
                  <td className="p-3 text-sm">{(o.items ?? []).map((i) => `${i.qty}× ${i.itemName}`).join(', ')}</td>
                  <td className="p-3 font-bold">{formatINR(o.total)}</td>
                  <td className="p-3"><StatusBadge status={o.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
