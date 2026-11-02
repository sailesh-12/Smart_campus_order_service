'use client';

import { useQuery } from '@tanstack/react-query';
import { create } from 'zustand';
import { newPaidOrderIds } from '@/lib/board';
import { playChime } from '@/lib/chime';
import type { OrderStatus } from '@/lib/status';
import type { MenuItem, Order, Vendor } from './adapters';
import { api, data } from './client';

export const keys = {
  me: ['vendor', 'me'] as const,
  board: ['vendor', 'orders', 'active'] as const,
  ordersByStatus: (status: OrderStatus) => ['vendor', 'orders', status] as const,
  allOrders: ['vendor', 'orders'] as const,
  menu: ['vendor', 'menu'] as const,
  analytics: (from: string, to: string) => ['vendor', 'analytics', from, to] as const,
};

export const FALLBACK_POLL_MS = 20_000;

/** New-order highlight + chime, kept outside React state so it can be updated from the fetch itself. */
type FlashState = { known: Set<number> | null; flashing: Set<number>; soundOn: boolean; setSoundOn: (on: boolean) => void };
export const useFlash = create<FlashState>((set) => ({
  known: null,
  flashing: new Set(),
  soundOn: false,
  setSoundOn: (soundOn) => set({ soundOn }),
}));

function noticeNewOrders(orders: Order[]) {
  const { known, soundOn } = useFlash.getState();
  const fresh = newPaidOrderIds(known, orders);
  const allPaid = new Set(orders.filter((o) => o.status === 'PAID').map((o) => o.id!));
  if (known) for (const id of known) allPaid.add(id); // never re-chime an order seen earlier
  useFlash.setState((s) => ({ known: allPaid, flashing: new Set([...s.flashing, ...fresh]) }));
  if (fresh.length) {
    if (soundOn) playChime();
    setTimeout(() => {
      useFlash.setState((s) => ({ flashing: new Set([...s.flashing].filter((id) => !fresh.includes(id))) }));
    }, 4000);
  }
}

export function useVendorMe(enabled: boolean) {
  return useQuery({ queryKey: keys.me, enabled, queryFn: (): Promise<Vendor> => data(api.GET('/vendor/me')) });
}

/** The live board: GET /vendor/orders without status = PAID, ACCEPTED, PREPARING, READY. */
export function useBoard(enabled: boolean, streamLive: boolean) {
  return useQuery({
    queryKey: keys.board,
    enabled,
    // While the stream is down, poll so nothing is missed
    refetchInterval: streamLive ? false : FALLBACK_POLL_MS,
    queryFn: async (): Promise<Order[]> => {
      const orders = await data(api.GET('/vendor/orders'));
      noticeNewOrders(orders);
      return orders;
    },
  });
}

export function useOrdersByStatus(status: OrderStatus, enabled = true) {
  return useQuery({
    queryKey: keys.ordersByStatus(status),
    enabled,
    queryFn: (): Promise<Order[]> => data(api.GET('/vendor/orders', { params: { query: { status } } })),
  });
}

export function useVendorMenu() {
  return useQuery({ queryKey: keys.menu, queryFn: (): Promise<MenuItem[]> => data(api.GET('/vendor/menu')) });
}
