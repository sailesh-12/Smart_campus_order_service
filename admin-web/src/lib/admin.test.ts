import { describe, expect, it } from 'vitest';
import { ApiError, friendlyMessage } from '@/api/errors';
import { WrongRoleError, assertAppRole } from '@/auth/session';
import { canResolve, canRetryRefund, generateTempPassword, isEndpointMissing, manualRefundProblem } from './admin';
import { formatINR } from './money';
import { ORDER_STATUS } from './status';

const session = (role: 'STUDENT' | 'VENDOR' | 'ADMIN') => ({ token: 't', user: { id: 1, name: 'A', email: 'a@x.edu', role } });

describe('role guard', () => {
  it('accepts admins only', () => {
    expect(assertAppRole(session('ADMIN')).user.role).toBe('ADMIN');
    expect(() => assertAppRole(session('VENDOR'))).toThrow(WrongRoleError);
    expect(() => assertAppRole(session('STUDENT'))).toThrow(WrongRoleError);
  });
});

describe('payment issue actions', () => {
  it('retry refund only for REFUND_FAILED', () => {
    expect(canRetryRefund({ status: 'REFUND_FAILED' })).toBe(true);
    expect(canRetryRefund({ status: 'UNCONFIRMED' })).toBe(false);
  });

  it('resolve: REFUND_FAILED always, UNCONFIRMED only after the cap', () => {
    expect(canResolve({ status: 'REFUND_FAILED', ageMinutes: 1 })).toBe(true);
    expect(canResolve({ status: 'UNCONFIRMED', ageMinutes: 5 })).toBe(false);
    expect(canResolve({ status: 'UNCONFIRMED', ageMinutes: 30 })).toBe(true);
  });
});

describe('manual refund', () => {
  it('needs a paid order with a payment', () => {
    expect(manualRefundProblem(undefined, 'COMPLAINT')).toMatch(/load/i);
    expect(manualRefundProblem({ status: 'COLLECTED', paymentId: undefined }, 'COMPLAINT')).toMatch(/no payment/);
    expect(manualRefundProblem({ status: 'PENDING_PAYMENT', paymentId: 3 }, 'COMPLAINT')).toMatch(/never paid/);
    expect(manualRefundProblem({ status: 'COLLECTED', paymentId: 3 }, 'COMPLAINT')).toBeNull();
  });

  it('UNCOLLECTED_OVERRIDE only for uncollected orders', () => {
    expect(manualRefundProblem({ status: 'COLLECTED', paymentId: 3 }, 'UNCOLLECTED_OVERRIDE')).not.toBeNull();
    expect(manualRefundProblem({ status: 'UNCOLLECTED', paymentId: 3 }, 'UNCOLLECTED_OVERRIDE')).toBeNull();
  });
});

describe('temporary password', () => {
  it('is long enough and mixes letters and digits', () => {
    for (let i = 0; i < 50; i++) {
      const p = generateTempPassword();
      expect(p).toHaveLength(12);
      expect(p).toMatch(/[A-Z]/);
      expect(p).toMatch(/[a-z]/);
      expect(p).toMatch(/\d/);
    }
  });

  it('refuses short passwords (the backend needs 8)', () => {
    expect(() => generateTempPassword(6)).toThrow();
  });
});

describe('optional endpoints, money and statuses', () => {
  it('detects a missing endpoint', () => {
    expect(isEndpointMissing(new ApiError({ status: 404, code: 'NOT_FOUND', message: 'No endpoint GET /admin/orders.' }))).toBe(true);
    expect(isEndpointMissing(new ApiError({ status: 500, code: 'INTERNAL_ERROR', message: 'x' }))).toBe(false);
  });

  it('mentions CORS when the server cannot be reached', () => {
    expect(friendlyMessage(new ApiError({ status: 0, code: 'NETWORK_ERROR', message: 'Failed to fetch' }))).toMatch(/3001/);
  });

  it('formats INR and shares the status table', () => {
    expect(formatINR(1500)).toBe('₹1,500.00');
    expect(ORDER_STATUS.UNCOLLECTED.label).toBe('Not collected');
  });
});
