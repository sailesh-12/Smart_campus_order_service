'use client';

import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react';
import { friendlyMessage } from '@/api/errors';
import { statusInfo } from '@/lib/status';

type Tone = 'primary' | 'secondary' | 'danger';

const toneClass: Record<Tone, string> = {
  primary: 'bg-primary text-white hover:bg-primary-dark',
  secondary: 'bg-white text-navy border border-line hover:bg-page',
  danger: 'bg-red-700 text-white hover:bg-red-800',
};

export function Button({
  tone = 'primary',
  loading,
  className = '',
  children,
  disabled,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { tone?: Tone; loading?: boolean }) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`min-h-12 rounded-card px-5 font-bold transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${toneClass[tone]} ${className}`}
    >
      {loading ? 'Working…' : children}
    </button>
  );
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`rounded-card border border-line bg-white p-4 ${className}`}>{children}</div>;
}

export function Field({
  label,
  error,
  hint,
  ...rest
}: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string; hint?: string }) {
  const id = rest.id ?? rest.name ?? label;
  return (
    <label htmlFor={id} className="block">
      <span className="mb-1 block font-semibold">{label}</span>
      <input
        id={id}
        {...rest}
        aria-invalid={!!error || undefined}
        className={`min-h-12 w-full rounded-card border bg-white px-3 text-base outline-none focus:ring-2 focus:ring-primary ${
          error ? 'border-red-700' : 'border-line'
        }`}
      />
      {hint && !error ? <span className="mt-1 block text-sm text-muted">{hint}</span> : null}
      {error ? <span className="mt-1 block text-sm text-red-700">{error}</span> : null}
    </label>
  );
}

export function StatusBadge({ status }: { status: string | null | undefined }) {
  const info = statusInfo(status);
  return (
    <span
      className="inline-block rounded-full px-3 py-1 text-sm font-bold"
      style={{ color: info.color, backgroundColor: info.background }}
    >
      {info.label}
    </span>
  );
}

export function Banner({ tone = 'info', title, children }: { tone?: 'info' | 'success' | 'warning' | 'danger'; title: string; children?: ReactNode }) {
  const cls = {
    info: 'border-blue-700 bg-blue-50 text-blue-900',
    success: 'border-green-700 bg-green-50 text-green-900',
    warning: 'border-amber-600 bg-amber-50 text-amber-900',
    danger: 'border-red-700 bg-red-50 text-red-900',
  }[tone];
  return (
    <div role={tone === 'danger' ? 'alert' : 'status'} className={`rounded-card border-l-4 p-3 ${cls}`}>
      <p className="font-bold">{title}</p>
      {children ? <div className="mt-1 text-navy">{children}</div> : null}
    </div>
  );
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex min-h-40 items-center justify-center gap-3 text-muted" role="status">
      <span className="h-5 w-5 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      {label}
    </div>
  );
}

export function Empty({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="flex min-h-32 flex-col items-center justify-center gap-1 rounded-card border border-dashed border-line p-6 text-center">
      <p className="font-bold">{title}</p>
      {hint ? <p className="text-muted">{hint}</p> : null}
    </div>
  );
}

export function ErrorBox({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <div className="flex min-h-32 flex-col items-center justify-center gap-3 rounded-card border border-red-200 bg-red-50 p-6 text-center">
      <p className="font-bold text-red-900">Couldn’t load this</p>
      <p className="text-red-900">{friendlyMessage(error)}</p>
      <Button tone="secondary" onClick={onRetry}>Try again</Button>
    </div>
  );
}

export function Switch({ checked, onChange, label, disabled }: { checked: boolean; onChange: (v: boolean) => void; label: string; disabled?: boolean }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className="flex min-h-12 items-center gap-3 rounded-card px-2 font-semibold disabled:opacity-50"
    >
      <span className={`relative inline-block h-7 w-12 rounded-full transition-colors ${checked ? 'bg-primary' : 'bg-gray-400'}`}>
        <span className={`absolute top-1 h-5 w-5 rounded-full bg-white transition-all ${checked ? 'left-6' : 'left-1'}`} />
      </span>
      {label}: {checked ? 'On' : 'Off'}
    </button>
  );
}

/** Modal confirmation used for destructive actions. */
export function ConfirmDialog({
  open,
  title,
  body,
  confirmLabel,
  tone = 'danger',
  busy,
  onConfirm,
  onCancel,
}: {
  open: boolean;
  title: string;
  body: ReactNode;
  confirmLabel: string;
  tone?: Tone;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-navy/50 p-4" role="dialog" aria-modal="true" aria-label={title}>
      <div className="w-full max-w-md rounded-card bg-white p-6 shadow-xl">
        <h2 className="text-xl font-extrabold">{title}</h2>
        <div className="mt-2 text-muted">{body}</div>
        <div className="mt-6 flex justify-end gap-3">
          <Button tone="secondary" onClick={onCancel} disabled={busy}>Cancel</Button>
          <Button tone={tone} onClick={onConfirm} loading={busy}>{confirmLabel}</Button>
        </div>
      </div>
    </div>
  );
}
