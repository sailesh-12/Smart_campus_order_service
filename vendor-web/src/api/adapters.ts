import type { OrderStatus } from '@/lib/status';
import type { components } from './schema';

/** Shapes the OpenAPI document does not pin down. Each gap is marked TODO(contract). */

export type Schemas = components['schemas'];
export type Vendor = Schemas['VendorResponse'];
export type Order = Schemas['OrderResponse'];
export type MenuItem = Schemas['MenuItemVendorResponse'];
export type VendorAnalytics = Schemas['VendorAnalyticsResponse'];

export type Role = 'STUDENT' | 'VENDOR' | 'ADMIN';
export type SessionUser = { id: number; name: string; email: string; role: Role };
export type Session = { token: string; user: SessionUser };

/** POST /auth/login: every OpenAPI field is optional; require the ones we rely on. */
export function toSession(response: Schemas['AuthResponse']): Session {
  const u = response.user;
  if (!response.token || !u?.id || !u.email || !u.role) throw new Error('Unexpected login response from the server');
  return { token: response.token, user: { id: u.id, name: u.name ?? u.email, email: u.email, role: u.role } };
}

/** order-status on /vendor/orders/stream: confirmed shape. It is the only event the vendor stream sends. */
export type OrderStatusEvent = { orderId: number; vendorId: number; status: OrderStatus; at: string };

export function parseEvent<T>(raw: string | null | undefined): T | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}
