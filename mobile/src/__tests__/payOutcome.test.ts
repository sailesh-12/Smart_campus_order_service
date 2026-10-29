import { ApiError } from '@/api/errors';
import { outcomeFromError, outcomeFromPolledOrder, outcomeFromResponse } from '@/lib/payOutcome';

const err = (status: number, code: string) => new ApiError({ status, code, message: code });

describe('pay outcomes', () => {
  it('200 SUCCESS goes to tracking', () => {
    expect(outcomeFromResponse(200, { orderId: 1, paymentStatus: 'SUCCESS', orderStatus: 'PAID' })).toEqual({ kind: 'paid' });
  });

  it('202 means confirming (no second Pay)', () => {
    expect(outcomeFromResponse(202, { orderId: 1, paymentStatus: 'UNCONFIRMED' })).toEqual({ kind: 'confirming' });
  });

  it('402 PAYMENT_DECLINED goes back to the cart', () => {
    expect(outcomeFromError(err(402, 'PAYMENT_DECLINED'))).toEqual({ kind: 'declined' });
  });

  it('409 HOLD_EXPIRED goes back to the cart', () => {
    expect(outcomeFromError(err(409, 'HOLD_EXPIRED'))).toEqual({ kind: 'expired' });
  });

  it('other errors are shown and can be retried', () => {
    expect(outcomeFromError(err(409, 'INVALID_TRANSITION')).kind).toBe('error');
  });

  it('polling settles a confirming payment from the order status', () => {
    expect(outcomeFromPolledOrder({ status: 'PENDING_PAYMENT' })).toBeNull();
    expect(outcomeFromPolledOrder({ status: 'PAID' })).toEqual({ kind: 'paid' });
    expect(outcomeFromPolledOrder({ status: 'CANCELLED' })).toEqual({ kind: 'declined' });
    expect(outcomeFromPolledOrder({ status: 'EXPIRED' })).toEqual({ kind: 'expired' });
  });
});
