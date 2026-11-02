'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import type { AdminAnalytics } from '@/api/adapters';
import { api, data } from '@/api/client';
import { Card, ErrorBox, Loading } from '@/components/ui';
import { formatINR } from '@/lib/money';

function Stat({ label, value, href, alert }: { label: string; value: string; href?: string; alert?: boolean }) {
  const body = (
    <Card className={alert ? 'border-amber-400 bg-amber-50' : ''}>
      <p className="text-sm text-muted">{label}</p>
      <p className="text-3xl font-extrabold">{value}</p>
      {href ? <p className="mt-1 text-sm font-bold text-primary">Open →</p> : null}
    </Card>
  );
  return href ? <Link href={href} className="block rounded-card focus:outline-none focus:ring-2 focus:ring-primary">{body}</Link> : body;
}

export function OverviewView() {
  const analytics = useQuery({
    queryKey: ['admin', 'analytics'],
    queryFn: (): Promise<AdminAnalytics> => data(api.GET('/admin/analytics')),
    refetchInterval: 60_000,
  });

  if (analytics.isPending) return <Loading label="Loading campus figures…" />;
  if (analytics.isError) return <ErrorBox error={analytics.error} onRetry={() => analytics.refetch()} />;
  const a = analytics.data;

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-extrabold">Overview</h1>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <Stat label="Campus revenue (paid orders)" value={formatINR(a.totalCampusRevenue)} />
        <Stat label="Orders" value={String(a.totalOrders ?? 0)} href="/orders" />
        <Stat label="Active canteens" value={String(a.activeVendors ?? 0)} href="/canteens" />
        <Stat label="Active students" value={String(a.activeStudents ?? 0)} />
        <Stat label="Open complaints" value={String(a.unresolvedComplaints ?? 0)} href="/complaints" alert={(a.unresolvedComplaints ?? 0) > 0} />
        <Stat label="Payment issues" value={String(a.unresolvedPaymentIssues ?? 0)} href="/payments" alert={(a.unresolvedPaymentIssues ?? 0) > 0} />
      </div>
    </div>
  );
}
