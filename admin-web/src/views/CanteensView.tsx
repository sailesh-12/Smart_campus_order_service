'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { VENDOR_TYPES, type Vendor, type VendorType } from '@/api/adapters';
import { api, data } from '@/api/client';
import { ApiError, friendlyMessage } from '@/api/errors';
import { Banner, Button, Card, ConfirmDialog, Empty, ErrorBox, Field, Loading } from '@/components/ui';
import { generateTempPassword } from '@/lib/admin';

const KEY = ['admin', 'vendors'] as const;

function AddCanteenForm({ onDone }: { onDone: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [type, setType] = useState<VendorType>('CANTEEN');
  const [ownerUserId, setOwner] = useState('');
  const [parallelCapacity, setCapacity] = useState('2');
  const [slotMaxOrders, setSlot] = useState('10');
  const [local, setLocal] = useState<Record<string, string>>({});

  const create = useMutation({
    mutationFn: () =>
      data(
        api.POST('/admin/vendors', {
          body: { name: name.trim(), type, ownerUserId: Number(ownerUserId), parallelCapacity: Number(parallelCapacity), slotMaxOrders: Number(slotMaxOrders) },
        }),
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: KEY });
      onDone();
    },
  });
  const server = create.error instanceof ApiError ? create.error.fieldErrors : {};
  const errors = { ...server, ...local };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const v: Record<string, string> = {};
    if (!name.trim()) v.name = 'Name is required';
    if (!/^\d+$/.test(ownerUserId)) v.ownerUserId = 'User id of the canteen’s VENDOR account';
    if (!/^\d+$/.test(parallelCapacity) || Number(parallelCapacity) < 1) v.parallelCapacity = 'At least 1';
    if (!/^\d+$/.test(slotMaxOrders) || Number(slotMaxOrders) < 1) v.slotMaxOrders = 'At least 1';
    setLocal(v);
    if (!Object.keys(v).length) create.mutate();
  };

  return (
    <Card>
      <form onSubmit={submit} className="space-y-3" noValidate>
        <h2 className="text-lg font-extrabold">Onboard a canteen</h2>
        {create.isError && !Object.keys(server).length ? <Banner tone="danger" title={friendlyMessage(create.error)} /> : null}
        <div className="grid gap-3 md:grid-cols-2">
          <Field label="Name" name="name" value={name} onChange={(e) => setName(e.target.value)} error={errors.name} maxLength={100} />
          <label className="block">
            <span className="mb-1 block font-semibold">Type</span>
            <select value={type} onChange={(e) => setType(e.target.value as VendorType)} className="min-h-12 w-full rounded-card border border-line bg-white px-3">
              {VENDOR_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
          </label>
          <Field
            label="Owner user id"
            name="ownerUserId"
            inputMode="numeric"
            value={ownerUserId}
            onChange={(e) => setOwner(e.target.value)}
            error={errors.ownerUserId}
            hint="An existing, active user with role VENDOR who does not own a canteen yet."
          />
          <Field label="Orders cooked at once" name="parallelCapacity" inputMode="numeric" value={parallelCapacity} onChange={(e) => setCapacity(e.target.value)} error={errors.parallelCapacity} />
          <Field label="Orders per 5-min slot" name="slotMaxOrders" inputMode="numeric" value={slotMaxOrders} onChange={(e) => setSlot(e.target.value)} error={errors.slotMaxOrders} />
        </div>
        <div className="flex justify-end gap-3">
          <Button type="button" tone="secondary" onClick={onDone} disabled={create.isPending}>Cancel</Button>
          <Button type="submit" loading={create.isPending}>Create canteen</Button>
        </div>
      </form>
    </Card>
  );
}

/** Shows the generated password once; nothing keeps it after the dialog closes. */
function TempPasswordDialog({ vendor, password, onClose }: { vendor: Vendor; password: string; onClose: () => void }) {
  const [copied, setCopied] = useState(false);
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-navy/50 p-4" role="dialog" aria-modal="true" aria-label="Temporary password">
      <div className="w-full max-w-md space-y-4 rounded-card bg-white p-6">
        <h2 className="text-xl font-extrabold">New password for {vendor.name}</h2>
        <p className="text-muted">Give this to the canteen owner now. It is shown only once; old sessions were logged out.</p>
        <p className="select-all rounded-card bg-page p-4 text-center font-mono text-2xl font-bold tracking-wider">{password}</p>
        <div className="flex justify-end gap-3">
          <Button
            tone="secondary"
            onClick={() => void navigator.clipboard?.writeText(password).then(() => setCopied(true), () => setCopied(false))}
          >
            {copied ? 'Copied' : 'Copy'}
          </Button>
          <Button onClick={onClose}>Done</Button>
        </div>
      </div>
    </div>
  );
}

export function CanteensView() {
  const queryClient = useQueryClient();
  const vendors = useQuery({ queryKey: KEY, queryFn: (): Promise<Vendor[]> => data(api.GET('/admin/vendors')) });
  const [adding, setAdding] = useState(false);
  const [suspendTarget, setSuspendTarget] = useState<Vendor | null>(null);
  const [resetTarget, setResetTarget] = useState<Vendor | null>(null);
  const [shownPassword, setShownPassword] = useState<{ vendor: Vendor; password: string } | null>(null);

  const suspend = useMutation({
    mutationFn: (v: Vendor) => data(api.PATCH('/admin/vendors/{id}', { params: { path: { id: v.id! } }, body: { isSuspended: !v.isSuspended } })),
    onSettled: () => {
      setSuspendTarget(null);
      void queryClient.invalidateQueries({ queryKey: KEY });
    },
  });

  const reset = useMutation({
    mutationFn: async (v: Vendor) => {
      const password = generateTempPassword();
      await data(api.POST('/admin/vendors/{id}/reset-password', { params: { path: { id: v.id! } }, body: { newPassword: password } }));
      return { vendor: v, password };
    },
    onSuccess: (result) => {
      setResetTarget(null);
      setShownPassword(result);
    },
  });

  if (vendors.isPending) return <Loading label="Loading canteens…" />;
  if (vendors.isError) return <ErrorBox error={vendors.error} onRetry={() => vendors.refetch()} />;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-extrabold">Canteens</h1>
        {!adding ? <Button onClick={() => setAdding(true)}>+ Onboard canteen</Button> : null}
      </div>
      {adding ? <AddCanteenForm onDone={() => setAdding(false)} /> : null}
      {suspend.isError ? <Banner tone="danger" title={friendlyMessage(suspend.error)} /> : null}

      {vendors.data.length === 0 ? <Empty title="No canteens yet" hint="Onboard the first one above." /> : null}
      <div className="overflow-x-auto rounded-card border border-line bg-white">
        <table className="w-full min-w-[720px] text-left">
          <thead className="bg-page text-sm text-muted">
            <tr>
              <th className="p-3">Canteen</th>
              <th className="p-3">Type</th>
              <th className="p-3">State</th>
              <th className="p-3">Load</th>
              <th className="p-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {vendors.data.map((v) => (
              <tr key={v.id} className="border-t border-line">
                <td className="p-3 font-bold">{v.name}<span className="block text-sm font-normal text-muted">#{v.id}</span></td>
                <td className="p-3">{v.type}</td>
                <td className="p-3">
                  {v.isSuspended ? (
                    <span className="rounded-full bg-red-100 px-3 py-1 text-sm font-bold text-red-800">Suspended</span>
                  ) : v.isOpen ? (
                    <span className="rounded-full bg-green-100 px-3 py-1 text-sm font-bold text-green-800">Open</span>
                  ) : (
                    <span className="rounded-full bg-gray-200 px-3 py-1 text-sm font-bold text-gray-700">Closed</span>
                  )}
                </td>
                <td className="p-3 text-sm">{v.busyLevel} · ~{v.estimatedWaitMinutes} min</td>
                <td className="p-3">
                  <div className="flex justify-end gap-2">
                    <Button tone="secondary" onClick={() => setResetTarget(v)}>Reset password</Button>
                    <Button tone={v.isSuspended ? 'primary' : 'danger'} onClick={() => setSuspendTarget(v)}>
                      {v.isSuspended ? 'Reactivate' : 'Suspend'}
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <ConfirmDialog
        open={!!suspendTarget}
        title={suspendTarget?.isSuspended ? `Reactivate ${suspendTarget?.name}?` : `Suspend ${suspendTarget?.name}?`}
        body={
          suspendTarget?.isSuspended
            ? 'Students will be able to order from this canteen again.'
            : 'Students won’t be able to place new orders here. Orders already in progress are not affected.'
        }
        confirmLabel={suspendTarget?.isSuspended ? 'Reactivate' : 'Suspend'}
        tone={suspendTarget?.isSuspended ? 'primary' : 'danger'}
        busy={suspend.isPending}
        onCancel={() => setSuspendTarget(null)}
        onConfirm={() => suspendTarget && suspend.mutate(suspendTarget)}
      />
      <ConfirmDialog
        open={!!resetTarget}
        title={`Reset the password for ${resetTarget?.name}?`}
        body={
          <>
            A new temporary password is generated and shown once. The owner is logged out everywhere.
            {reset.isError ? <span className="mt-2 block text-red-800">{friendlyMessage(reset.error)}</span> : null}
          </>
        }
        confirmLabel="Reset password"
        busy={reset.isPending}
        onCancel={() => {
          setResetTarget(null);
          reset.reset();
        }}
        onConfirm={() => resetTarget && reset.mutate(resetTarget)}
      />
      {shownPassword ? <TempPasswordDialog vendor={shownPassword.vendor} password={shownPassword.password} onClose={() => setShownPassword(null)} /> : null}
    </div>
  );
}
