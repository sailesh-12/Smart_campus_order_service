import { WrongRoleError, assertAppRole } from '@/lib/roleGuard';
import { ORDER_STATUS, statusInfo } from '@/lib/status';

const session = (role: 'STUDENT' | 'VENDOR' | 'ADMIN') => ({
  token: 't',
  user: { id: 1, name: 'A', email: 'a@x.edu', role },
});

describe('role guard', () => {
  it('accepts students', () => {
    expect(assertAppRole(session('STUDENT')).user.role).toBe('STUDENT');
  });

  it.each(['VENDOR', 'ADMIN'] as const)('refuses %s accounts', (role) => {
    expect(() => assertAppRole(session(role))).toThrow(WrongRoleError);
  });
});

describe('status mapping', () => {
  const all = ['PENDING_PAYMENT', 'PAID', 'ACCEPTED', 'PREPARING', 'READY', 'COLLECTED', 'UNCOLLECTED', 'REJECTED', 'CANCELLED', 'EXPIRED'];

  it('covers every backend status with a label', () => {
    expect(Object.keys(ORDER_STATUS).sort()).toEqual([...all].sort());
    for (const s of all) expect(statusInfo(s).label.length).toBeGreaterThan(0);
  });

  it('uses the agreed labels', () => {
    expect(ORDER_STATUS.PENDING_PAYMENT.label).toBe('Waiting for payment');
    expect(ORDER_STATUS.PAID.label).toBe('Confirmed');
    expect(ORDER_STATUS.READY.label).toBe('Ready for pickup');
  });

  it('colours the closed failures the same red', () => {
    expect(new Set([ORDER_STATUS.REJECTED.color, ORDER_STATUS.CANCELLED.color, ORDER_STATUS.EXPIRED.color]).size).toBe(1);
  });

  it('falls back to the raw value for unknown statuses', () => {
    expect(statusInfo('SOMETHING_NEW').label).toBe('SOMETHING_NEW');
  });
});
