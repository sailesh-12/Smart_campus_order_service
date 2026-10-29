import type { components } from './schema';

/** Shapes the OpenAPI document does not pin down. Each gap is marked TODO(contract). */

export type Schemas = components['schemas'];
export type Vendor = Schemas['VendorResponse'];
export type VendorType = NonNullable<Vendor['type']>;
export type Order = Schemas['OrderResponse'];
export type Complaint = Schemas['ComplaintResponse'];
export type ComplaintStatus = NonNullable<Complaint['status']>;
export type PaymentIssue = Schemas['PaymentIssueResponse'];
export type Payment = Schemas['PaymentResponse'];
export type AdminAnalytics = Schemas['AdminAnalyticsResponse'];
export type AdminAction = Schemas['AdminAction'];
export type ManualRefundReason = 'COMPLAINT' | 'UNCOLLECTED_OVERRIDE';

export type Role = 'STUDENT' | 'VENDOR' | 'ADMIN';
export type SessionUser = { id: number; name: string; email: string; role: Role };
export type Session = { token: string; user: SessionUser };

/** POST /auth/login: every OpenAPI field is optional; require the ones we rely on. */
export function toSession(response: Schemas['AuthResponse']): Session {
  const u = response.user;
  if (!response.token || !u?.id || !u.email || !u.role) throw new Error('Unexpected login response from the server');
  return { token: response.token, user: { id: u.id, name: u.name ?? u.email, email: u.email, role: u.role } };
}

export const VENDOR_TYPES: VendorType[] = ['CANTEEN', 'CAFE', 'JUICE', 'TEA', 'BAKERY'];
export const COMPLAINT_STATUSES: ComplaintStatus[] = ['OPEN', 'IN_REVIEW', 'RESOLVED', 'REJECTED'];
