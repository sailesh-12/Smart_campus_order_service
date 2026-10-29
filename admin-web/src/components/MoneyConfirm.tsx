'use client';

import type { ReactNode } from 'react';
import { friendlyMessage } from '@/api/errors';
import { formatINR } from '@/lib/money';
import { Banner, Button } from './ui';

/**
 * Confirmation for any action that moves money: shows the amount, says it cannot be undone,
 * stays disabled while the request runs, and shows the server error if it fails.
 */
export function MoneyConfirm({
  open,
  title,
  amount,
  children,
  confirmLabel,
  busy,
  error,
  onConfirm,
  onCancel,
}: {
  open: boolean;
  title: string;
  amount: number | undefined;
  children?: ReactNode;
  confirmLabel: string;
  busy: boolean;
  error: unknown;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-navy/50 p-4" role="dialog" aria-modal="true" aria-label={title}>
      <div className="w-full max-w-md space-y-4 rounded-card bg-white p-6 shadow-xl">
        <h2 className="text-xl font-extrabold">{title}</h2>
        <p className="text-3xl font-extrabold text-navy">{formatINR(amount)}</p>
        {children ? <div className="text-muted">{children}</div> : null}
        <p className="font-bold text-red-800">This cannot be undone.</p>
        {error ? <Banner tone="danger" title={friendlyMessage(error)} /> : null}
        <div className="flex justify-end gap-3">
          <Button tone="secondary" onClick={onCancel} disabled={busy}>Cancel</Button>
          <Button tone="danger" onClick={onConfirm} loading={busy}>{confirmLabel}</Button>
        </div>
      </div>
    </div>
  );
}

/** After a refund call: the payment's new status decides the wording. Never claims the money is back. */
export function RefundResult({ status }: { status: string | undefined }) {
  if (status === 'REFUNDED') return <Banner tone="success" title="Refund initiated">The gateway accepted the refund. The student’s bank may take a few days to show it.</Banner>;
  if (status === 'REFUND_FAILED') return <Banner tone="warning" title="Refund failed at the gateway">It is listed under Payment issues and will be retried.</Banner>;
  return <Banner tone="info" title={`Payment is now ${status ?? 'unknown'}`} />;
}
