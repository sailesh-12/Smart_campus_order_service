'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { parseEvent, type OrderStatusEvent, type Vendor } from '@/api/adapters';
import { api, data } from '@/api/client';
import { friendlyMessage } from '@/api/errors';
import { keys, useBoard, useFlash, useVendorMe } from '@/api/queries';
import { useEventStream, type StreamState } from '@/api/useEventStream';
import { clearSession, useSession } from '@/auth/session';
import { Banner, Button, ErrorBox, Loading, Switch } from '@/components/ui';
import { VendorContext } from '@/components/VendorContext';
import { disableSound, enableSound } from '@/lib/chime';

const NAV = [
  { href: '/', label: 'Live board' },
  { href: '/pickup', label: 'Pickup' },
  { href: '/menu', label: 'Menu' },
  { href: '/analytics', label: 'Analytics' },
  { href: '/settings', label: 'Settings' },
] as const;

function StreamIndicator({ state }: { state: StreamState }) {
  const map = {
    live: ['bg-green-500', 'Live'],
    connecting: ['bg-amber-400', 'Connecting…'],
    reconnecting: ['bg-amber-400', 'Reconnecting (checking every 20 s)'],
    off: ['bg-red-500', 'Offline'],
  } as const;
  const [dot, label] = map[state];
  return (
    <span className="flex items-center gap-2 text-sm font-semibold" role="status" aria-live="polite">
      <span className={`h-3 w-3 rounded-full ${dot}`} aria-hidden />
      {label}
    </span>
  );
}

function ShopSwitches({ vendor }: { vendor: Vendor }) {
  const queryClient = useQueryClient();
  const update = useMutation({
    mutationFn: (body: { isOpen?: boolean; rushMode?: boolean }) =>
      data(api.PATCH('/vendors/{id}', { params: { path: { id: vendor.id! } }, body })),
    onSuccess: (v) => queryClient.setQueryData(keys.me, v),
  });
  return (
    <div className="flex flex-wrap items-center gap-2">
      <Switch label="Open" checked={!!vendor.isOpen} disabled={update.isPending} onChange={(isOpen) => update.mutate({ isOpen })} />
      <Switch label="Rush mode" checked={!!vendor.rushMode} disabled={update.isPending} onChange={(rushMode) => update.mutate({ rushMode })} />
      {update.isError ? <span className="text-sm text-red-700">{friendlyMessage(update.error)}</span> : null}
    </div>
  );
}

function SoundToggle() {
  const soundOn = useFlash((s) => s.soundOn);
  const setSoundOn = useFlash((s) => s.setSoundOn);
  const [blocked, setBlocked] = useState(false);
  const toggle = async () => {
    if (soundOn) {
      disableSound();
      setSoundOn(false);
      return;
    }
    // The click itself is the user gesture browsers require before audio can play
    const ok = await enableSound();
    setBlocked(!ok);
    setSoundOn(ok);
  };
  return (
    <button
      type="button"
      onClick={toggle}
      aria-pressed={soundOn}
      className={`min-h-12 rounded-card border px-4 font-bold ${soundOn ? 'border-primary bg-primary text-white' : 'border-line bg-white text-navy'}`}
      title={blocked ? 'The browser blocked audio' : undefined}
    >
      {soundOn ? '🔔 Sound on' : '🔕 Sound off'}
    </button>
  );
}

export function DashboardShell({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const queryClient = useQueryClient();
  const session = useSession();
  const signedIn = !!session;

  useEffect(() => {
    if (session === null) {
      // Logged out (or 401): drop this shop's cached data before anyone else logs in
      queryClient.clear();
      useFlash.setState({ known: null, flashing: new Set() });
      router.replace('/login');
    }
  }, [session, router, queryClient]);

  const me = useVendorMe(signedIn);

  // One stream for the whole dashboard: any order event refreshes the order lists from REST
  const refreshOrders = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: keys.allOrders });
  }, [queryClient]);
  const stream = useEventStream(
    signedIn ? '/vendor/orders/stream' : null,
    (event, raw) => {
      if (event === 'order-status' && parseEvent<OrderStatusEvent>(raw)) refreshOrders();
    },
    refreshOrders,
  );
  useBoard(signedIn, stream === 'live');

  if (session === undefined || (signedIn && me.isPending)) return <Loading label="Opening dashboard…" />;
  if (!session) return <Loading label="Redirecting to login…" />;
  if (me.isError) {
    return (
      <main className="mx-auto max-w-xl p-6">
        <ErrorBox error={me.error} onRetry={() => me.refetch()} />
        <Button tone="secondary" className="mt-4" onClick={() => clearSession()}>Log out</Button>
      </main>
    );
  }

  const vendor = me.data!;

  return (
    <VendorContext.Provider value={{ vendor, stream }}>
      <div className="min-h-screen">
        <header className="bg-navy text-white">
          <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
            <div>
              <p className="text-xl font-extrabold">{vendor.name}</p>
              <StreamIndicator state={stream} />
            </div>
            <div className="flex flex-wrap items-center gap-3 rounded-card bg-white px-2 text-navy">
              <ShopSwitches vendor={vendor} />
            </div>
            <div className="flex items-center gap-3">
              <SoundToggle />
              <Button tone="secondary" onClick={() => { queryClient.clear(); clearSession(); }}>Log out</Button>
            </div>
          </div>
          <nav className="flex gap-1 overflow-x-auto px-2" aria-label="Main">
            {NAV.map((item) => {
              const active = item.href === '/' ? pathname === '/' : pathname.startsWith(item.href);
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  aria-current={active ? 'page' : undefined}
                  className={`flex min-h-12 items-center rounded-t-card px-5 font-bold ${active ? 'bg-page text-navy' : 'text-white/85 hover:text-white'}`}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>
        </header>
        {vendor.isSuspended ? (
          <div className="p-4"><Banner tone="danger" title="This shop is suspended by the campus admin">Students can’t order until it is reactivated.</Banner></div>
        ) : null}
        <main className="p-4">{children}</main>
      </div>
    </VendorContext.Provider>
  );
}
