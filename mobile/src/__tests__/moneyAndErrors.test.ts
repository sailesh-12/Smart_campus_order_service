import { ApiError, friendlyMessage, toApiError } from '@/api/errors';
import { formatINR, formatPaise, toPaise } from '@/lib/money';

describe('money', () => {
  it('converts server amounts to paise exactly', () => {
    expect(toPaise(120)).toBe(12000);
    expect(toPaise(0.1)).toBe(10);
    expect(toPaise('15.5')).toBe(1550);
    expect(toPaise(99.99)).toBe(9999);
  });

  it('formats INR the Indian way', () => {
    expect(formatINR(150000)).toBe('₹1,50,000.00');
    expect(formatPaise(4650)).toBe('₹46.50');
  });
});

describe('errors', () => {
  it('reads the error body and Retry-After', () => {
    const response = new Response(null, { status: 429, headers: { 'Retry-After': '42' } });
    const e = toApiError(response, { status: 429, code: 'RATE_LIMITED', message: 'slow down', fieldErrors: null });
    expect(e.retryAfterSeconds).toBe(42);
    expect(friendlyMessage(e)).toContain('42 seconds');
  });

  it('names the sold out item', () => {
    const e = new ApiError({ status: 409, code: 'OUT_OF_STOCK', message: 'Chicken 65 just sold out' });
    expect(friendlyMessage(e)).toContain('Chicken 65 just sold out');
  });

  it('keeps field errors for forms', () => {
    const e = toApiError(new Response(null, { status: 400 }), {
      status: 400,
      code: 'VALIDATION_ERROR',
      message: 'Input validation failed',
      fieldErrors: { email: 'must be a well-formed email address' },
    });
    expect(e.fieldErrors.email).toMatch(/email/);
  });
});
