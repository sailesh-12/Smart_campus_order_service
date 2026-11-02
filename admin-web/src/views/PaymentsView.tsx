'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import type { ManualRefundReason, Order, Payment, PaymentIssue } from '@/api/adapters';
import { api, data } from '@/api/client';
import { friendlyMessage } from '@/api/errors';
import { MoneyConfirm, RefundResult } from '@/components/MoneyConfirm';
import { Banner, Button, Card, Empty, ErrorBox, Field, Loading, StatusBadge } from '@/components/ui';
import { UNCONFIRMED_MAX_MINUTES, canResolve, canRetryRefund, manualRefundProblem } from '@/lib/admin';
import { formatINR } from '@/lib/money';
import { formatDateTime } from '@/lib/time';

const ISSUES = ['admin', 'issues'] as const;

function ResolveDialog({ issue, onClose }: { issue: PaymentIssue; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [note, setNote] = useState('');
  const resolve = useMutation({
    mutationFn: () => data(api.POST('/admin/payments/{id}/resolve', { params: { path: { id: issue.id! } }, body: { note: note.trim() } })),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ISSUES });
      onClose();
    },
  });
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-navy/50 p-4" role="dialog" aria-modal="true" aria-label="Resolve payment issue">
      <form
        className="w-full max-w-md space-y-4 rounded-card bg-white p-6"
        onSubmit={(e) => {
          e.preventDefault();
          if (note.trim()) resolve.mutate();
        }}
      >
        <h2 className="text-xl font-extrabold">Resolve payment #{issue.id}</h2>
        <p className="text-muted">Use this when the issue was handled outside the system (for example refunded by hand in the gateway dashboard). The payment status does not change.</p>
        {resolve.isError ? <Banner tone="danger" title={friendlyMessage(resolve.error)} /> : null}
        <label className="block">
          <span className="mb-1 block font-semibold">What was done? (required)</span>
          <textarea value={note} onChange={(e) => setNote(e.target.value.slice(0, 300))} rows={3} className="w-full rounded-card border border-line p-3" required />
        </label>
        <div className="flex justify-end gap-3">
          <Button type="button" tone="secondary" onClick={onClose} disabled={resolve.isPending}>Cancel</Button>
          <Button type="submit" loading={resolve.isPending} disabled={!note.trim()}>Mark resolved</Button>
        </div>
      </form>
    </div>
  );
}

function IssuesTable() {
  const queryClient = useQueryClient();
  const issues = useQuery({ queryKey: ISSUES, queryFn: (): Promise<PaymentIssue[]> => data(api.GET('/admin/payments/issues')) });
  const [retryTarget, setRetryTarget] = useState<PaymentIssue | null>(null);
  const [resolveTarget, setResolveTarget] = useState<PaymentIssue | null>(null);

  const retry = useMutation({
    mutationFn: (issue: PaymentIssue): Promise<Payment> =>
      data(api.POST('/admin/payments/{id}/retry-refund', { params: { path: { id: issue.id! } } })),
    onSuccess: () => {
      setRetryTarget(null);
      void queryClient.invalidateQueries({ queryKey: ISSUES });
    },
  });

  if (issues.isPending) return <Loading label="Loading payment issues…" />;
  if (issues.isError) return <ErrorBox error={issues.error} onRetry={() => issues.refetch()} />;

  return (
    <div className="space-y-3">
      {retry.data ? <RefundResult status={retry.data.status} /> : null}
      {issues.data.length === 0 ? <Empty title="No open payment issues" hint="Unconfirmed payments and failed refunds show up here." /> : null}
      {issues.data.length > 0 ? (
        <div className="overflow-x-auto rounded-card border border-line bg-white">
          <table className="w-full min-w-[900px] text-left">
            <thead className="bg-page text-sm text-muted">
              <tr>
                <th className="p-3">Payment</th>
                <th className="p-3">Status</th>
                <th className="p-3">Amount</th>
                <th className="p-3">Age</th>
                <th className="p-3">Checks / refund attempts</th>
                <th className="p-3">Reason</th>
                <th className="p-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {issues.data.map((i) => (
                <tr key={i.id} className="border-t border-line align-top">
                  <td className="p-3 font-bold">#{i.id}<span className="block text-sm font-normal text-muted">Order #{i.orderId}</span></td>
                  <td className="p-3">
                    <span className={`rounded-full px-3 py-1 text-sm font-bold ${i.status === 'REFUND_FAILED' ? 'bg-red-100 text-red-800' : 'bg-amber-100 text-amber-900'}`}>
                      {i.status === 'REFUND_FAILED' ? 'Refund failed' : 'Unconfirmed'}
                    </span>
                  </td>
                  <td className="p-3 font-bold">{formatINR(i.amount)}</td>
                  <td className="p-3 text-sm">{i.ageMinutes} min<span className="block text-muted">{formatDateTime(i.createdAt)}</span></td>
                  <td className="p-3 text-sm">{i.checkCount} / {i.refundAttempts}</td>
                  <td className="p-3 text-sm">{i.refundReason ?? '—'}{i.failureReason ? <span className="block text-muted">{i.failureReason}</span> : null}</td>
                  <td className="p-3">
                    <div className="flex justify-end gap-2">
                      {canRetryRefund(i) ? (
                        <Button tone="danger" onClick={() => { retry.reset(); setRetryTarget(i); }}>Retry refund</Button>
                      ) : null}
                      <Button
                        tone="secondary"
                        disabled={!canResolve(i)}
                        title={canResolve(i) ? undefined : `Unconfirmed payments can be resolved after ${UNCONFIRMED_MAX_MINUTES} minutes`}
                        onClick={() => setResolveTarget(i)}
                      >
                        Resolve
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}

      <MoneyConfirm
        open={!!retryTarget}
        title={`Retry the refund of payment #${retryTarget?.id}?`}
        amount={retryTarget?.amount}
        confirmLabel="Retry refund"
        busy={retry.isPending}
        error={retry.error}
        onCancel={() => setRetryTarget(null)}
        onConfirm={() => retryTarget && retry.mutate(retryTarget)}
      >
        One more full refund attempt at the gateway (reason {retryTarget?.refundReason ?? 'unchanged'}). The gateway reference is fixed, so it can never refund twice.
      </MoneyConfirm>
      {resolveTarget ? <ResolveDialog issue={resolveTarget} onClose={() => setResolveTarget(null)} /> : null}
    </div>
  );
}

function ManualRefund() {
  const queryClient = useQueryClient();
  const [orderId, setOrderId] = useState('');
  const [loadedId, setLoadedId] = useState<number | null>(null);
  const [reason, setReason] = useState<ManualRefundReason>('COMPLAINT');
  const [note, setNote] = useState('');
  const [confirming, setConfirming] = useState(false);

  const order = useQuery({
    queryKey: ['admin', 'order', loadedId],
    enabled: loadedId != null,
    queryFn: (): Promise<Order> => data(api.GET('/orders/{id}', { params: { path: { id: loadedId! } } })),
  });
  const refund = useMutation({
    mutationFn: (): Promise<Payment> =>
      data(
        api.POST('/admin/payments/{id}/refund', {
          params: { path: { id: order.data!.paymentId! } },
          body: { reason, note: note.trim() || undefined },
        }),
      ),
    onSuccess: () => {
      setConfirming(false);
      void queryClient.invalidateQueries({ queryKey: ISSUES });
    },
  });

  const load = (e: FormEvent) => {
    e.preventDefault();
    if (/^\d+$/.test(orderId)) {
      refund.reset();
      setLoadedId(Number(orderId));
    }
  };
  const problem = order.data ? manualRefundProblem(order.data, reason) : null;

  return (
    <Card>
      <h2 className="text-lg font-extrabold">Manual full refund</h2>
      <p className="mb-3 text-sm text-muted">
        For a paid order: reason COMPLAINT, or UNCOLLECTED_OVERRIDE for an order that was never collected. Full refunds only; stock is not restored.
        Refunds for a specific complaint are easier from the complaint itself (it gets linked and resolved).
      </p>
      <form onSubmit={load} className="flex flex-wrap items-end gap-3">
        <div className="w-48">
          <Field label="Order id" name="orderId" inputMode="numeric" value={orderId} onChange={(e) => setOrderId(e.target.value)} />
        </div>
        <Button type="submit" tone="secondary" disabled={!/^\d+$/.test(orderId)}>Load order</Button>
      </form>

      {order.isFetching ? <Loading /> : null}
      {order.isError ? <div className="mt-3"><ErrorBox error={order.error} onRetry={() => order.refetch()} /></div> : null}
      {order.data && !order.isFetching ? (
        <div className="mt-4 space-y-3">
          <div className="flex flex-wrap items-center gap-3">
            <span className="font-bold">Order #{order.data.id}</span>
            <StatusBadge status={order.data.status} />
            <span className="font-bold">{formatINR(order.data.total)}</span>
            <span className="text-sm text-muted">payment #{order.data.paymentId ?? '—'}</span>
          </div>
          <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Refund reason">
            {(['COMPLAINT', 'UNCOLLECTED_OVERRIDE'] as const).map((r) => (
              <button
                key={r}
                type="button"
                role="radio"
                aria-checked={reason === r}
                onClick={() => setReason(r)}
                className={`min-h-12 rounded-card border px-4 font-bold ${reason === r ? 'border-primary bg-primary text-white' : 'border-line bg-white'}`}
              >
                {r === 'COMPLAINT' ? 'Complaint' : 'Uncollected override'}
              </button>
            ))}
          </div>
          <input
            aria-label="Note"
            placeholder="Note (optional)"
            value={note}
            onChange={(e) => setNote(e.target.value.slice(0, 300))}
            className="min-h-12 w-full rounded-card border border-line px-3"
          />
          {problem ? <p className="text-sm text-red-800">{problem}</p> : null}
          {refund.data ? <RefundResult status={refund.data.status} /> : null}
          <Button tone="danger" disabled={!!problem} onClick={() => { refund.reset(); setConfirming(true); }}>Refund in full</Button>
        </div>
      ) : null}

      <MoneyConfirm
        open={confirming}
        title={`Refund order #${order.data?.id}?`}
        amount={order.data?.total}
        confirmLabel="Refund in full"
        busy={refund.isPending}
        error={refund.error}
        onCancel={() => setConfirming(false)}
        onConfirm={() => refund.mutate()}
      >
        Reason {reason === 'COMPLAINT' ? 'COMPLAINT' : 'UNCOLLECTED_OVERRIDE'}. The full amount goes back to the student’s original payment method.
      </MoneyConfirm>
    </Card>
  );
}

export function PaymentsView() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-extrabold">Payment issues</h1>
      <IssuesTable />
      <ManualRefund />
    </div>
  );
}
