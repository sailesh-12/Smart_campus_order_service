'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import type { Vendor } from '@/api/adapters';
import { api, data } from '@/api/client';
import { ApiError, friendlyMessage } from '@/api/errors';
import { keys } from '@/api/queries';
import { Banner, Button, Card, Field } from '@/components/ui';
import { useVendorContext } from '@/components/VendorContext';

const FIELDS = [
  { key: 'avgPrepMinutes', label: 'Average prep time (minutes)', hint: 'Fallback when a dish has no prep time; also used for pickup slots.' },
  { key: 'parallelCapacity', label: 'Orders cooked at the same time', hint: 'Higher means shorter queue estimates for students.' },
  { key: 'slotMaxOrders', label: 'Orders per 5-minute pickup slot', hint: 'Applies to slots that have no bookings yet.' },
] as const;

type Key = (typeof FIELDS)[number]['key'];

function SettingsForm({ vendor }: { vendor: Vendor }) {
  const queryClient = useQueryClient();
  const [values, setValues] = useState<Record<Key, string>>({
    avgPrepMinutes: String(vendor.avgPrepMinutes ?? 10),
    parallelCapacity: String(vendor.parallelCapacity ?? 2),
    slotMaxOrders: String(vendor.slotMaxOrders ?? 10),
  });
  const [local, setLocal] = useState<Record<string, string>>({});

  const save = useMutation({
    mutationFn: () =>
      data(
        api.PATCH('/vendors/{id}', {
          params: { path: { id: vendor.id! } },
          body: {
            avgPrepMinutes: Number(values.avgPrepMinutes),
            parallelCapacity: Number(values.parallelCapacity),
            slotMaxOrders: Number(values.slotMaxOrders),
          },
        }),
      ),
    onSuccess: (v) => queryClient.setQueryData(keys.me, v),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const errors: Record<string, string> = {};
    for (const f of FIELDS) if (!/^\d+$/.test(values[f.key]) || Number(values[f.key]) < 1) errors[f.key] = 'Whole number, at least 1';
    setLocal(errors);
    if (!Object.keys(errors).length) save.mutate();
  };

  const server = save.error instanceof ApiError ? save.error.fieldErrors : {};

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      {save.isSuccess ? <Banner tone="success" title="Settings saved" /> : null}
      {save.isError && !Object.keys(server).length ? <Banner tone="danger" title={friendlyMessage(save.error)} /> : null}
      {FIELDS.map((f) => (
        <Field
          key={f.key}
          label={f.label}
          name={f.key}
          inputMode="numeric"
          hint={f.hint}
          value={values[f.key]}
          onChange={(e) => setValues({ ...values, [f.key]: e.target.value })}
          error={local[f.key] ?? server[f.key]}
        />
      ))}
      <Button type="submit" loading={save.isPending}>Save settings</Button>
    </form>
  );
}

export function SettingsView() {
  const { vendor } = useVendorContext();
  return (
    <div className="mx-auto max-w-xl space-y-4">
      <h1 className="text-2xl font-extrabold">Shop settings</h1>
      <Card>
        <p className="text-muted">Shop name</p>
        <p className="text-lg font-bold">{vendor.name}</p>
        <p className="mt-2 text-sm text-muted">Open/closed and rush mode are in the header. Renaming or suspending a shop is done by the campus admin.</p>
      </Card>
      <SettingsForm key={vendor.id} vendor={vendor} />
    </div>
  );
}
