import { useQuery } from '@tanstack/react-query';
import { api, data } from './client';
import { ApiError } from './errors';
import type { Complaint, MenuItem, Order, PickupPass, Profile, Slot, Vendor } from './adapters';

export const keys = {
  vendors: ['vendors'] as const,
  vendor: (id: number) => ['vendors', id] as const,
  menu: (vendorId: number) => ['vendors', vendorId, 'menu'] as const,
  slots: (vendorId: number, date: string) => ['vendors', vendorId, 'slots', date] as const,
  myOrders: ['orders', 'me'] as const,
  order: (id: number) => ['orders', id] as const,
  pickup: (id: number) => ['orders', id, 'pickup'] as const,
  me: ['users', 'me'] as const,
  myComplaints: ['complaints', 'me'] as const,
};

/** Retry transient failures only; business errors (4xx) are shown straight away. */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false;
  return failureCount < 2;
}

export function useVendors() {
  return useQuery({ queryKey: keys.vendors, queryFn: (): Promise<Vendor[]> => data(api.GET('/vendors')) });
}

export function useVendor(id: number) {
  return useQuery({
    queryKey: keys.vendor(id),
    queryFn: (): Promise<Vendor> => data(api.GET('/vendors/{id}', { params: { path: { id } } })),
  });
}

export function useMenu(vendorId: number | null) {
  return useQuery({
    queryKey: keys.menu(vendorId ?? 0),
    enabled: vendorId != null,
    queryFn: (): Promise<MenuItem[]> => data(api.GET('/vendors/{id}/menu', { params: { path: { id: vendorId! } } })),
  });
}

export function useSlots(vendorId: number | null, date: string) {
  return useQuery({
    queryKey: keys.slots(vendorId ?? 0, date),
    enabled: vendorId != null,
    queryFn: (): Promise<Slot[]> =>
      data(api.GET('/vendors/{id}/slots', { params: { path: { id: vendorId! }, query: { date } } })),
  });
}

export function useMyOrders() {
  return useQuery({ queryKey: keys.myOrders, queryFn: (): Promise<Order[]> => data(api.GET('/orders/me')) });
}

export function useOrder(id: number, refetchInterval?: number | false) {
  return useQuery({
    queryKey: keys.order(id),
    queryFn: (): Promise<Order> => data(api.GET('/orders/{id}', { params: { path: { id } } })),
    refetchInterval,
  });
}

export function usePickupPass(id: number, enabled: boolean) {
  return useQuery({
    queryKey: keys.pickup(id),
    enabled,
    queryFn: (): Promise<PickupPass> => data(api.GET('/orders/{id}/pickup', { params: { path: { id } } })),
  });
}

export function useProfile() {
  return useQuery({ queryKey: keys.me, queryFn: (): Promise<Profile> => data(api.GET('/users/me')) });
}

export function useMyComplaints() {
  return useQuery({ queryKey: keys.myComplaints, queryFn: (): Promise<Complaint[]> => data(api.GET('/complaints/me')) });
}
