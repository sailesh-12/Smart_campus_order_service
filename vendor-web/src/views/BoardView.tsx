'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useState } from 'react';
import type { Order } from '@/api/adapters';
import { api, call } from '@/api/client';
import { ApiError, friendlyMessage } from '@/api/errors';
import { keys, useBoard, useFlash, useOrdersByStatus } from '@/api/queries';
import { OrderCard } from '@/components/OrderCard';
import { Banner, Button, ConfirmDialog, Empty, ErrorBox, Loading } from '@/components/ui';
import { useVendorContext } from '@/components/VendorContext';
import { COLUMNS, actionsFor, groupByColumn, isToday, type BoardAction } from '@/lib/board';

type Tab = 'live' | 'today' | 'uncollected';

function useStatusChange() {
  const queryClient = useQueryClient();
  const [error, setError] = useState<unknown>(null);
  const mutation = useMutation({
    mutationFn: ({ order, action }: { order: Order; action: BoardAction }) =>
      call(api.PATCH('/orders/{id}/status', { params: { path: { id: order.id! } }, body: { status: action.target } })),
    onMutate: () => setError(null),
    // No optimistic moves: the card moves when the server says so
    onSettled: () => void queryClient.invalidateQueries({ queryKey: keys.allOrders }),
    onError: (e) => setError(e),
  });
  return { mutation, error, clearError: () => setError(null) };
}

function LiveBoard() {
  const { stream } = useVendorContext();
  const board = useBoard(true, stream === 'live');
  const flashing = useFlash((s) => s.flashing);
  const { mutation, error, clearError } = useStatusChange();
  const [confirm, setConfirm] = useState<{ order: Order; action: BoardAction } | null>(null);

  if (board.isPending) return <Loading label="Loading orders…" />;
  if (board.isError) return <ErrorBox error={board.error} onRetry={() => board.refetch()} />;

  const groups = groupByColumn(board.data);
  const busyId = mutation.isPending ? mutation.variables?.order.id : null;

  const run = (order: Order, action: BoardAction) => {
    if (action.confirm) setConfirm({ order, action });
    else mutation.mutate({ order, action });
  };

  return (
    <div className="space-y-4">
      {error ? (
        <Banner tone={error instanceof ApiError && error.code === 'INVALID_TRANSITION' ? 'warning' : 'danger'} title={friendlyMessage(error)}>
          <button className="underline" onClick={clearError}>Dismiss</button>
        </Banner>
      ) : null}
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {COLUMNS.map((col) => (
          <section key={col.status} aria-label={col.title} className="rounded-card bg-white/60 p-3">
            <h2 className="mb-3 flex items-center justify-between text-lg font-extrabold">
              {col.title}
              <span className="rounded-full bg-navy px-3 py-0.5 text-sm text-white">{groups[col.status].length}</span>
            </h2>
            <div className="space-y-3">
              {groups[col.status].length === 0 ? <Empty title="Nothing here" /> : null}
              {groups[col.status].map((order) => (
                <OrderCard key={order.id} order={order} flashing={flashing.has(order.id!)}>
                  {actionsFor(order.status).map((action) => (
                    <Button
                      key={action.target}
                      tone={action.tone}
                      className="flex-1"
                      disabled={mutation.isPending}
                      loading={busyId === order.id && mutation.variables?.action.target === action.target}
                      onClick={() => run(order, action)}
                    >
                      {action.label}
                    </Button>
                  ))}
                  {order.status === 'READY' ? (
                    <Link href="/pickup" className="flex min-h-12 flex-1 items-center justify-center rounded-card border border-line font-bold">
                      Verify pickup
                    </Link>
                  ) : null}
                </OrderCard>
              ))}
            </div>
          </section>
        ))}
      </div>
      <ConfirmDialog
        open={!!confirm}
        title={`Reject order #${confirm?.order.id}?`}
        body={confirm?.action.confirm}
        confirmLabel="Reject and refund"
        busy={mutation.isPending}
        onCancel={() => setConfirm(null)}
        onConfirm={() => {
          if (confirm) mutation.mutate(confirm, { onSettled: () => setConfirm(null) });
        }}
      />
    </div>
  );
}

function TodayTab() {
  // TODO(contract): /vendor/orders has no date filter, so finished orders are filtered to today in the browser
  const collected = useOrdersByStatus('COLLECTED');
  const rejected = useOrdersByStatus('REJECTED');
  const cancelled = useOrdersByStatus('CANCELLED');
  const all = [collected, rejected, cancelled];
  if (all.some((q) => q.isPending)) return <Loading label="Loading today’s orders…" />;
  const failed = all.find((q) => q.isError);
  if (failed) return <ErrorBox error={failed.error} onRetry={() => all.forEach((q) => void q.refetch())} />;
  const today = all
    .flatMap((q) => q.data ?? [])
    .filter((o) => isToday(o.createdAt))
    .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));
  if (today.length === 0) return <Empty title="No finished orders today yet" hint="Collected, rejected and cancelled orders appear here." />;
  return (
    <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
      {today.map((o) => <OrderCard key={o.id} order={o} />)}
    </div>
  );
}

function UncollectedTab() {
  const uncollected = useOrdersByStatus('UNCOLLECTED');
  if (uncollected.isPending) return <Loading />;
  if (uncollected.isError) return <ErrorBox error={uncollected.error} onRetry={() => uncollected.refetch()} />;
  if (uncollected.data.length === 0) return <Empty title="No uncollected orders" hint="Orders left at the counter too long show up here." />;
  return (
    <div className="space-y-3">
      <p className="text-muted">These were ready but not picked up. The student can still collect them with their code or QR; there is no automatic refund.</p>
      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {uncollected.data.map((o) => (
          <OrderCard key={o.id} order={o}>
            <Link href="/pickup" className="flex min-h-12 flex-1 items-center justify-center rounded-card border border-line font-bold">
              Verify pickup
            </Link>
          </OrderCard>
        ))}
      </div>
    </div>
  );
}

export function BoardView() {
  const [tab, setTab] = useState<Tab>('live');
  const tabs: { id: Tab; label: string }[] = [
    { id: 'live', label: 'Live board' },
    { id: 'today', label: 'Today' },
    { id: 'uncollected', label: 'Uncollected' },
  ];
  return (
    <div className="space-y-4">
      <div role="tablist" aria-label="Order views" className="flex gap-2">
        {tabs.map((t) => (
          <button
            key={t.id}
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => setTab(t.id)}
            className={`min-h-12 rounded-card px-5 font-bold ${tab === t.id ? 'bg-navy text-white' : 'border border-line bg-white'}`}
          >
            {t.label}
          </button>
        ))}
      </div>
      {tab === 'live' ? <LiveBoard /> : tab === 'today' ? <TodayTab /> : <UncollectedTab />}
    </div>
  );
}
