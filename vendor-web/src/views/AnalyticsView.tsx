'use client';

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { VendorAnalytics } from '@/api/adapters';
import { api, data } from '@/api/client';
import { keys, useOrdersByStatus } from '@/api/queries';
import { Banner, Card, Empty, ErrorBox, Field, Loading } from '@/components/ui';
import { isToday, salesPerHour } from '@/lib/board';
import { formatINR, formatPaise, toPaise } from '@/lib/money';
import { campusToday } from '@/lib/time';

function daysAgo(n: number): string {
  return campusToday(new Date(Date.now() - n * 86_400_000));
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <Card>
      <p className="text-sm text-muted">{label}</p>
      <p className="text-2xl font-extrabold">{value}</p>
    </Card>
  );
}

function inRange(iso: string | undefined, from: string, to: string): boolean {
  if (!iso) return false;
  const day = campusToday(new Date(iso));
  return day >= from && day <= to;
}

export function AnalyticsView() {
  const [from, setFrom] = useState(() => daysAgo(6));
  const [to, setTo] = useState(() => campusToday());
  const valid = from <= to;

  const analytics = useQuery({
    queryKey: keys.analytics(from, to),
    enabled: valid,
    queryFn: (): Promise<VendorAnalytics> => data(api.GET('/vendor/analytics', { params: { query: { from, to } } })),
  });
  const collected = useOrdersByStatus('COLLECTED');

  const hourly = salesPerHour((collected.data ?? []).filter((o) => inRange(o.createdAt, from, to)), (v) => toPaise(v));
  const a = analytics.data;
  const other = a ? Math.max(0, (a.totalOrders ?? 0) - (a.completedOrders ?? 0) - (a.cancelledOrders ?? 0)) : 0;
  const statusBars = a
    ? [
        { name: 'Collected', count: a.completedOrders ?? 0, fill: '#374151' },
        { name: 'Cancelled / rejected / expired', count: a.cancelledOrders ?? 0, fill: '#B91C1C' },
        { name: 'Open or unpaid', count: other, fill: '#1D4ED8' },
      ]
    : [];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h1 className="text-2xl font-extrabold">Analytics</h1>
        <div className="flex gap-3">
          <Field label="From" name="from" type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
          <Field label="To" name="to" type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} error={valid ? undefined : 'Must be after From'} />
        </div>
      </div>

      {analytics.isPending && valid ? <Loading label="Crunching numbers…" /> : null}
      {analytics.isError ? <ErrorBox error={analytics.error} onRetry={() => analytics.refetch()} /> : null}

      {a ? (
        <>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Stat label="Revenue" value={formatINR(a.totalRevenue)} />
            <Stat label="Orders" value={String(a.totalOrders ?? 0)} />
            <Stat label="Collected" value={String(a.completedOrders ?? 0)} />
            <Stat label="Cancelled / rejected" value={String(a.cancelledOrders ?? 0)} />
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <h2 className="mb-3 text-lg font-extrabold">Top items</h2>
              {(a.topItems ?? []).length === 0 ? (
                <Empty title="No sales in this period" />
              ) : (
                <div className="h-72">
                  <ResponsiveContainer>
                    <BarChart data={a.topItems} layout="vertical" margin={{ left: 24 }}>
                      <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                      <XAxis type="number" allowDecimals={false} />
                      <YAxis type="category" dataKey="itemName" width={120} />
                      <Tooltip formatter={(v) => [`${v} sold`, 'Quantity']} />
                      <Bar dataKey="totalSold" fill="#FF7A1A" radius={[0, 6, 6, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </Card>

            <Card>
              <h2 className="mb-3 text-lg font-extrabold">Orders by outcome</h2>
              <div className="h-72">
                <ResponsiveContainer>
                  <BarChart data={statusBars}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} />
                    <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                    <YAxis allowDecimals={false} />
                    <Tooltip />
                    <Bar dataKey="count" radius={[6, 6, 0, 0]}>
                      {statusBars.map((s) => <Cell key={s.name} fill={s.fill} />)}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </Card>
          </div>
        </>
      ) : null}

      <Card>
        <h2 className="mb-1 text-lg font-extrabold">Sales per hour</h2>
        <p className="mb-3 text-sm text-muted">
          From collected orders in the selected dates (computed in the browser{collected.data ? `; ${collected.data.filter((o) => isToday(o.createdAt)).length} collected today` : ''}).
        </p>
        {collected.isError ? <Banner tone="danger" title="Couldn’t load collected orders" /> : null}
        {collected.isPending ? <Loading /> : hourly.length === 0 ? (
          <Empty title="No collected orders in this period" />
        ) : (
          <div className="h-72">
            <ResponsiveContainer>
              <BarChart data={hourly}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="hour" />
                <YAxis width={90} tickFormatter={(v: number) => formatPaise(v)} />
                <Tooltip formatter={(v) => [formatPaise(Number(v)), 'Sales']} />
                <Bar dataKey="paise" fill="#0B1F3A" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </Card>
    </div>
  );
}
