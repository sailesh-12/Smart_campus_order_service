'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { COMPLAINT_STATUSES, type Complaint, type ComplaintStatus, type Order, type Payment } from '@/api/adapters';
import { api, data } from '@/api/client';
import { friendlyMessage } from '@/api/errors';
import { MoneyConfirm, RefundResult } from '@/components/MoneyConfirm';
import { Banner, Button, Empty, ErrorBox, Loading, StatusBadge } from '@/components/ui';
import { manualRefundProblem } from '@/lib/admin';
import { formatINR } from '@/lib/money';
import { formatDateTime } from '@/lib/time';

const LABEL: Record<ComplaintStatus, string> = { OPEN: 'Open', IN_REVIEW: 'In review', RESOLVED: 'Resolved', REJECTED: 'Rejected' };
const KEY = ['admin', 'complaints'] as const;

function ComplaintBadge({ status }: { status: ComplaintStatus | undefined }) {
  const cls = {
    OPEN: 'bg-amber-100 text-amber-900',
    IN_REVIEW: 'bg-blue-100 text-blue-900',
    RESOLVED: 'bg-green-100 text-green-900',
    REJECTED: 'bg-gray-200 text-gray-800',
  }[status ?? 'OPEN'];
  return <span className={`rounded-full px-3 py-1 text-sm font-bold ${cls}`}>{LABEL[status ?? 'OPEN']}</span>;
}

function Drawer({ complaint, onClose }: { complaint: Complaint; onClose: () => void }) {
  const queryClient = useQueryClient();
  const closed = complaint.status === 'RESOLVED' || complaint.status === 'REJECTED';
  const [status, setStatus] = useState<ComplaintStatus>(complaint.status === 'OPEN' ? 'IN_REVIEW' : complaint.status ?? 'IN_REVIEW');
  const [note, setNote] = useState(complaint.adminNote ?? '');
  const [refundOpen, setRefundOpen] = useState(false);
  const [refundNote, setRefundNote] = useState('');

  const order = useQuery({
    queryKey: ['admin', 'order', complaint.orderId],
    queryFn: (): Promise<Order> => data(api.GET('/orders/{id}', { params: { path: { id: complaint.orderId! } } })),
  });

  const update = useMutation({
    mutationFn: () =>
      data(api.PATCH('/admin/complaints/{id}', { params: { path: { id: complaint.id! } }, body: { status, adminNote: note.trim() || undefined } })),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: KEY });
      onClose();
    },
  });

  const refund = useMutation({
    mutationFn: (): Promise<Payment> =>
      data(
        api.POST('/admin/payments/{id}/refund', {
          params: { path: { id: order.data!.paymentId! } },
          body: { reason: 'COMPLAINT', complaintId: complaint.id, note: refundNote.trim() || undefined },
        }),
      ),
    onSuccess: () => {
      setRefundOpen(false);
      void queryClient.invalidateQueries({ queryKey: KEY });
      void queryClient.invalidateQueries({ queryKey: ['admin', 'issues'] });
    },
  });

  const problem = manualRefundProblem(order.data, 'COMPLAINT');
  const alreadyRefunded = complaint.refundPaymentId != null || refund.data?.status === 'REFUNDED';

  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-navy/40" role="dialog" aria-modal="true" aria-label={`Complaint ${complaint.id}`}>
      <aside className="h-full w-full max-w-xl space-y-4 overflow-y-auto bg-white p-6 shadow-xl">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-xl font-extrabold">Complaint #{complaint.id}</h2>
            <p className="text-sm text-muted">Order #{complaint.orderId} · {formatDateTime(complaint.createdAt)}</p>
          </div>
          <Button tone="secondary" onClick={onClose}>Close</Button>
        </div>
        <ComplaintBadge status={complaint.status} />
        <p className="whitespace-pre-wrap rounded-card bg-page p-4">{complaint.text}</p>

        <section className="space-y-2">
          <h3 className="font-extrabold">Order</h3>
          {order.isPending ? <Loading label="Loading order…" /> : null}
          {order.isError ? <ErrorBox error={order.error} onRetry={() => order.refetch()} /> : null}
          {order.data ? (
            <div className="rounded-card border border-line p-4">
              <div className="flex items-center justify-between">
                <StatusBadge status={order.data.status} />
                <span className="font-bold">{formatINR(order.data.total)}</span>
              </div>
              <ul className="mt-2 space-y-1">
                {(order.data.items ?? []).map((i) => (
                  <li key={i.menuItemId}>{i.qty} × {i.itemName} · {formatINR(i.unitPrice)}</li>
                ))}
              </ul>
              <p className="mt-2 text-sm text-muted">Canteen #{order.data.vendorId} · placed {formatDateTime(order.data.createdAt)}</p>
            </div>
          ) : null}
        </section>

        {refund.data ? <RefundResult status={refund.data.status} /> : null}
        {alreadyRefunded && !refund.data ? <Banner tone="info" title="A refund is linked to this complaint" /> : null}
        {!alreadyRefunded ? (
          <div>
            <Button tone="danger" disabled={!!problem || closed} onClick={() => { refund.reset(); setRefundOpen(true); }}>
              Refund the full order
            </Button>
            {problem && order.data ? <p className="mt-1 text-sm text-muted">{problem}</p> : null}
            {closed ? <p className="mt-1 text-sm text-muted">The complaint is closed.</p> : null}
          </div>
        ) : null}

        {!closed ? (
          <section className="space-y-3 border-t border-line pt-4">
            <h3 className="font-extrabold">Update status</h3>
            {update.isError ? <Banner tone="danger" title={friendlyMessage(update.error)} /> : null}
            <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="New status">
              {COMPLAINT_STATUSES.filter((s) => s !== 'OPEN').map((s) => (
                <button
                  key={s}
                  role="radio"
                  aria-checked={status === s}
                  onClick={() => setStatus(s)}
                  className={`min-h-12 rounded-card border px-4 font-bold ${status === s ? 'border-primary bg-primary text-white' : 'border-line bg-white'}`}
                >
                  {LABEL[s]}
                </button>
              ))}
            </div>
            <label className="block">
              <span className="mb-1 block font-semibold">Note to the student</span>
              <textarea
                value={note}
                onChange={(e) => setNote(e.target.value.slice(0, 1000))}
                rows={4}
                className="w-full rounded-card border border-line p-3"
              />
            </label>
            <Button onClick={() => update.mutate()} loading={update.isPending}>Save</Button>
          </section>
        ) : complaint.adminNote ? (
          <p className="text-muted">Note: {complaint.adminNote}</p>
        ) : null}

        <MoneyConfirm
          open={refundOpen}
          title={`Refund order #${complaint.orderId}?`}
          amount={order.data?.total}
          confirmLabel="Refund in full"
          busy={refund.isPending}
          error={refund.error}
          onCancel={() => setRefundOpen(false)}
          onConfirm={() => refund.mutate()}
        >
          <p>Full refund to the student’s original payment method, reason COMPLAINT. The complaint is marked resolved. Stock is not restored.</p>
          <label className="mt-3 block">
            <span className="mb-1 block font-semibold text-navy">Note (optional)</span>
            <input value={refundNote} onChange={(e) => setRefundNote(e.target.value.slice(0, 300))} className="min-h-12 w-full rounded-card border border-line px-3" />
          </label>
        </MoneyConfirm>
      </aside>
    </div>
  );
}

export function ComplaintsView() {
  const [filter, setFilter] = useState<ComplaintStatus | 'ALL'>('OPEN');
  const [selected, setSelected] = useState<Complaint | null>(null);
  const complaints = useQuery({
    queryKey: [...KEY, filter],
    queryFn: (): Promise<Complaint[]> =>
      data(api.GET('/admin/complaints', { params: { query: filter === 'ALL' ? {} : { status: filter } } })),
  });

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-extrabold">Complaints</h1>
      <div role="tablist" aria-label="Filter by status" className="flex flex-wrap gap-2">
        {(['ALL', ...COMPLAINT_STATUSES] as const).map((s) => (
          <button
            key={s}
            role="tab"
            aria-selected={filter === s}
            onClick={() => setFilter(s)}
            className={`min-h-12 rounded-card px-4 font-bold ${filter === s ? 'bg-navy text-white' : 'border border-line bg-white'}`}
          >
            {s === 'ALL' ? 'All' : LABEL[s]}
          </button>
        ))}
      </div>
      {complaints.isPending ? <Loading label="Loading complaints…" /> : null}
      {complaints.isError ? <ErrorBox error={complaints.error} onRetry={() => complaints.refetch()} /> : null}
      {complaints.data?.length === 0 ? <Empty title="No complaints here" /> : null}
      <div className="space-y-2">
        {complaints.data?.map((c) => (
          <button
            key={c.id}
            onClick={() => setSelected(c)}
            className="block w-full rounded-card border border-line bg-white p-4 text-left hover:border-primary"
          >
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="font-extrabold">#{c.id} · Order #{c.orderId}</span>
              <ComplaintBadge status={c.status} />
            </div>
            <p className="mt-1 line-clamp-2 text-muted">{c.text}</p>
            <p className="mt-1 text-sm text-muted">{formatDateTime(c.createdAt)}{c.refundPaymentId ? ' · refunded' : ''}</p>
          </button>
        ))}
      </div>
      {selected ? <Drawer key={selected.id} complaint={selected} onClose={() => setSelected(null)} /> : null}
    </div>
  );
}
