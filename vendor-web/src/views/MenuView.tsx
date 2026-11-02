'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import type { MenuItem } from '@/api/adapters';
import { api, data } from '@/api/client';
import { ApiError, friendlyMessage } from '@/api/errors';
import { keys, useVendorMenu } from '@/api/queries';
import { Banner, Button, Card, ConfirmDialog, Empty, ErrorBox, Field, Loading, Switch } from '@/components/ui';
import { formatINR } from '@/lib/money';

type Draft = {
  name: string;
  price: string;
  stockQty: string;
  isAvailable: boolean;
  description: string;
  category: string;
  imageUrl: string;
  prepMinutes: string;
};

const EMPTY: Draft = { name: '', price: '', stockQty: '0', isAvailable: true, description: '', category: '', imageUrl: '', prepMinutes: '10' };
const PRICE = /^\d{1,8}(\.\d{1,2})?$/;

function toDraft(item: MenuItem): Draft {
  return {
    name: item.name ?? '',
    price: item.price != null ? item.price.toFixed(2) : '',
    stockQty: String(item.stockQty ?? 0),
    isAvailable: !!item.isAvailable,
    description: item.description ?? '',
    category: item.category ?? '',
    imageUrl: item.imageUrl ?? '',
    prepMinutes: String(item.prepMinutes ?? 10),
  };
}

function validate(d: Draft): Record<string, string> {
  const e: Record<string, string> = {};
  if (!d.name.trim()) e.name = 'Name is required';
  if (!PRICE.test(d.price.trim())) e.price = 'Price in rupees, up to 2 decimals (e.g. 45 or 45.50)';
  if (!/^\d+$/.test(d.stockQty)) e.stockQty = 'Whole number, 0 or more';
  if (!/^\d+$/.test(d.prepMinutes) || Number(d.prepMinutes) < 1) e.prepMinutes = 'At least 1 minute';
  return e;
}

function toBody(d: Draft) {
  return {
    name: d.name.trim(),
    // The validated decimal string is sent as a JSON number; the server stores it as an exact decimal
    price: Number(d.price.trim()),
    stockQty: Number(d.stockQty),
    isAvailable: d.isAvailable,
    description: d.description.trim() || undefined,
    category: d.category.trim() || undefined,
    imageUrl: d.imageUrl.trim() || undefined,
    prepMinutes: Number(d.prepMinutes),
  };
}

function ItemForm({ item, onDone }: { item: MenuItem | null; onDone: () => void }) {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<Draft>(item ? toDraft(item) : EMPTY);
  const [local, setLocal] = useState<Record<string, string>>({});
  const save = useMutation({
    mutationFn: () =>
      item
        ? data(api.PUT('/menu-items/{id}', { params: { path: { id: item.id! } }, body: toBody(draft) }))
        : data(api.POST('/menu-items', { body: toBody(draft) })),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: keys.menu });
      onDone();
    },
  });
  const server = save.error instanceof ApiError ? save.error.fieldErrors : {};
  const errors = { ...server, ...local };
  const set = (k: keyof Draft) => (e: { target: { value: string } }) => setDraft({ ...draft, [k]: e.target.value });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const v = validate(draft);
    setLocal(v);
    if (Object.keys(v).length === 0) save.mutate();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-navy/50 p-4" role="dialog" aria-modal="true" aria-label={item ? 'Edit item' : 'New item'}>
      <form onSubmit={submit} noValidate className="max-h-[90vh] w-full max-w-lg space-y-3 overflow-y-auto rounded-card bg-white p-6">
        <h2 className="text-xl font-extrabold">{item ? `Edit ${item.name}` : 'New menu item'}</h2>
        {save.isError && !Object.keys(server).length ? <Banner tone="danger" title={friendlyMessage(save.error)} /> : null}
        <Field label="Name" name="name" value={draft.name} onChange={set('name')} error={errors.name} maxLength={100} />
        <div className="grid grid-cols-2 gap-3">
          <Field label="Price (₹)" name="price" inputMode="decimal" value={draft.price} onChange={set('price')} error={errors.price} />
          <Field label="Stock" name="stockQty" inputMode="numeric" value={draft.stockQty} onChange={set('stockQty')} error={errors.stockQty} />
          <Field label="Prep minutes" name="prepMinutes" inputMode="numeric" value={draft.prepMinutes} onChange={set('prepMinutes')} error={errors.prepMinutes} hint="Used for the wait estimate" />
          <Field label="Category" name="category" value={draft.category} onChange={set('category')} error={errors.category} maxLength={50} />
        </div>
        <Field label="Description" name="description" value={draft.description} onChange={set('description')} error={errors.description} maxLength={255} />
        <Field label="Image URL" name="imageUrl" type="url" value={draft.imageUrl} onChange={set('imageUrl')} error={errors.imageUrl} maxLength={255} />
        <Switch label="Available" checked={draft.isAvailable} onChange={(isAvailable) => setDraft({ ...draft, isAvailable })} />
        <div className="flex justify-end gap-3 pt-2">
          <Button type="button" tone="secondary" onClick={onDone} disabled={save.isPending}>Cancel</Button>
          <Button type="submit" loading={save.isPending}>{item ? 'Save' : 'Add item'}</Button>
        </div>
      </form>
    </div>
  );
}

function StockEditor({ item }: { item: MenuItem }) {
  const queryClient = useQueryClient();
  const [value, setValue] = useState(String(item.stockQty ?? 0));
  const update = useMutation({
    mutationFn: (stockQty: number) => data(api.PATCH('/menu-items/{id}/stock', { params: { path: { id: item.id! } }, body: { stockQty } })),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: keys.menu }),
  });
  const valid = /^\d+$/.test(value);
  const dirty = valid && Number(value) !== item.stockQty;
  return (
    <form
      className="flex items-center gap-2"
      onSubmit={(e) => {
        e.preventDefault();
        if (dirty) update.mutate(Number(value));
      }}
    >
      <label className="sr-only" htmlFor={`stock-${item.id}`}>Stock for {item.name}</label>
      <input
        id={`stock-${item.id}`}
        inputMode="numeric"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        className={`min-h-12 w-20 rounded-card border px-2 text-center text-lg font-bold ${valid ? 'border-line' : 'border-red-700'}`}
      />
      <Button type="submit" tone="secondary" disabled={!dirty} loading={update.isPending}>Set</Button>
      {update.isError ? <span className="text-sm text-red-700">{friendlyMessage(update.error)}</span> : null}
    </form>
  );
}

export function MenuView() {
  const queryClient = useQueryClient();
  const menu = useVendorMenu();
  const [editing, setEditing] = useState<MenuItem | null | 'new'>(null);
  const [deleting, setDeleting] = useState<MenuItem | null>(null);

  const availability = useMutation({
    mutationFn: ({ id, isAvailable }: { id: number; isAvailable: boolean }) =>
      data(api.PATCH('/menu-items/{id}/availability', { params: { path: { id } }, body: { isAvailable } })),
    onSettled: () => void queryClient.invalidateQueries({ queryKey: keys.menu }),
  });
  const remove = useMutation({
    mutationFn: (id: number) => data(api.DELETE('/menu-items/{id}', { params: { path: { id } } })),
    // Close the dialog either way; an error (e.g. ITEM_IN_USE) is shown above the list
    onSettled: () => {
      setDeleting(null);
      void queryClient.invalidateQueries({ queryKey: keys.menu });
    },
  });

  if (menu.isPending) return <Loading label="Loading menu…" />;
  if (menu.isError) return <ErrorBox error={menu.error} onRetry={() => menu.refetch()} />;

  const lowCount = menu.data.filter((i) => i.lowStock || i.stockQty === 0).length;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-extrabold">Menu</h1>
        <Button onClick={() => setEditing('new')}>+ Add item</Button>
      </div>
      {lowCount ? <Banner tone="warning" title={`${lowCount} item${lowCount > 1 ? 's are' : ' is'} low or out of stock`} /> : null}
      {availability.isError ? <Banner tone="danger" title={friendlyMessage(availability.error)} /> : null}
      {remove.isError ? <Banner tone="danger" title={friendlyMessage(remove.error)} /> : null}

      {menu.data.length === 0 ? <Empty title="Your menu is empty" hint="Add your first dish to start taking orders." /> : null}
      <div className="grid gap-3 lg:grid-cols-2">
        {menu.data.map((item) => {
          const out = item.stockQty === 0;
          return (
            <Card key={item.id} className={out ? 'border-red-300 bg-red-50' : item.lowStock ? 'border-amber-400 bg-amber-50' : ''}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-lg font-extrabold">{item.name}</p>
                  <p className="text-muted">
                    {formatINR(item.price)} · {item.prepMinutes} min{item.category ? ` · ${item.category}` : ''}
                  </p>
                  <p className={`mt-1 font-bold ${out ? 'text-red-800' : item.lowStock ? 'text-amber-800' : 'text-green-800'}`}>
                    {out ? 'Out of stock' : item.lowStock ? `Low stock: ${item.stockQty} left` : `${item.stockQty} in stock`}
                  </p>
                </div>
                <StockEditor key={`${item.id}-${item.stockQty}`} item={item} />
              </div>
              <div className="mt-3 flex flex-wrap items-center gap-2">
                <Switch
                  label="Available"
                  checked={!!item.isAvailable}
                  disabled={availability.isPending}
                  onChange={(isAvailable) => availability.mutate({ id: item.id!, isAvailable })}
                />
                <Button tone="secondary" onClick={() => setEditing(item)}>Edit</Button>
                <Button tone="secondary" onClick={() => setDeleting(item)}>Delete</Button>
              </div>
            </Card>
          );
        })}
      </div>

      {editing ? <ItemForm item={editing === 'new' ? null : editing} onDone={() => setEditing(null)} /> : null}
      <ConfirmDialog
        open={!!deleting}
        title={`Delete ${deleting?.name}?`}
        body="This removes the dish from your menu. Dishes that were ordered before can’t be deleted; mark them unavailable instead."
        confirmLabel="Delete"
        busy={remove.isPending}
        onCancel={() => setDeleting(null)}
        onConfirm={() => deleting && remove.mutate(deleting.id!)}
      />
    </div>
  );
}
