import type { components } from './schema';
import type { OrderStatus } from '@/lib/status';

/**
 * Typed shapes the OpenAPI document does not describe. Each is a TODO(contract): the backend should
 * publish these schemas so they can be generated like everything else.
 */

export type Schemas = components['schemas'];
export type Vendor = Schemas['VendorResponse'];
export type MenuItem = Schemas['MenuItemStudentResponse'];
export type Order = Schemas['OrderResponse'];
export type Slot = Schemas['SlotResponse'];
export type PayResult = Schemas['PayOrderResponse'];
export type PickupPass = Schemas['PickupCredentialsResponse'];
export type Profile = Schemas['UserProfileResponse'];
export type Complaint = Schemas['ComplaintResponse'];

export type Role = 'STUDENT' | 'VENDOR' | 'ADMIN';

/**
 * POST /auth/login response. The prompt confirms { token, user: { id, name, email, role } };
 * the OpenAPI AuthResponse marks every field optional, so this adapter requires the fields we rely on.
 */
export type SessionUser = { id: number; name: string; email: string; role: Role };
export type Session = { token: string; user: SessionUser };

export function toSession(response: Schemas['AuthResponse']): Session {
  const u = response.user;
  if (!response.token || !u?.id || !u.email || !u.role) {
    throw new Error('Unexpected login response from the server');
  }
  return { token: response.token, user: { id: u.id, name: u.name ?? u.email, email: u.email, role: u.role } };
}

// ---------- SSE event payloads (SseEmitter is untyped in OpenAPI) ----------

/** order-status: confirmed shape. */
export type OrderStatusEvent = { orderId: number; vendorId: number; status: OrderStatus; at: string };

/** TODO(contract) order-eta: taken from the backend's OrderEtaUpdated record, not from the OpenAPI document. */
export type OrderEtaEvent = {
  orderId: number; vendorId: number; estMinutesLow: number; estMinutesHigh: number; estReadyAt: string; at: string;
};

/** TODO(contract) payment-refunded: from the backend's PaymentRefunded record (no student id, has amount and reason). */
export type PaymentRefundedEvent = { paymentId: number; orderId: number; amount: number; reason: string; at: string };

/** TODO(contract) order-reminder: from the backend's OrderReminder record. */
export type OrderReminderEvent = { orderId: number; vendorId: number; at: string };

/** TODO(contract) menu-item-update: from the backend's MenuItemUpdated record. */
export type MenuItemUpdateEvent = { vendorId: number; itemId: number; available: boolean; lowStock: boolean };

export function parseEvent<T>(raw: string | null | undefined): T | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}
