import { describe, expect, it } from 'vitest';
import { ApiError, friendlyMessage, toApiError } from '@/api/errors';
import { WrongRoleError, assertAppRole } from '@/auth/session';
import { formatINR, toPaise } from './money';
import { ORDER_STATUS, statusInfo } from './status';

const session = (role: 'STUDENT' | 'VENDOR' | 'ADMIN') => ({ token: 't', user: { id: 1, name: 'A', email: 'a@x.edu', role } });

describe('role guard', () => {
  it('accepts vendor accounts', () => {
    expect(assertAppRole(session('VENDOR')).user.role).toBe('VENDOR');
  });

  it.each(['STUDENT', 'ADMIN'] as const)('refuses %s accounts', (role) => {
    expect(() => assertAppRole(session(role))).toThrow(WrongRoleError);
  });
});

describe('status mapping', () => {
  it('covers every status with the agreed labels', () => {
    expect(Object.keys(ORDER_STATUS)).toHaveLength(10);
    expect(ORDER_STATUS.PAID.label).toBe('Confirmed');
    expect(ORDER_STATUS.READY.label).toBe('Ready for pickup');
    expect(statusInfo('NEW_THING').label).toBe('NEW_THING');
  });
});

describe('money and errors', () => {
  it('formats INR without float drift', () => {
    expect(toPaise(0.1 + 0.2)).toBe(30);
    expect(formatINR(1234.5)).toBe('₹1,234.50');
  });

  it('honours Retry-After on 429', () => {
    const e = toApiError(new Response(null, { status: 429, headers: { 'Retry-After': '15' } }), { code: 'RATE_LIMITED', message: 'x' });
    expect(e.retryAfterSeconds).toBe(15);
    expect(friendlyMessage(e)).toContain('15 seconds');
  });

  it('explains INVALID_TRANSITION', () => {
    expect(friendlyMessage(new ApiError({ status: 409, code: 'INVALID_TRANSITION', message: 'm' }))).toMatch(/refreshed/);
  });
});
