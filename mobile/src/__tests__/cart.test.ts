// eslint-disable-next-line @typescript-eslint/no-require-imports -- jest.mock factories must use require
jest.mock('@react-native-async-storage/async-storage', () => require('@react-native-async-storage/async-storage/jest/async-storage-mock'));
let mockUuidCounter = 0;
jest.mock('expo-crypto', () => ({ randomUUID: () => `uuid-${++mockUuidCounter}` }));

// eslint-disable-next-line import/first -- mocks above must be registered before the store is imported
import { MAX_QTY, cartCount, cartSubtotalPaise, useCart } from '@/store/cart';

const canteen = { id: 1, name: 'Main Canteen' };
const cafe = { id: 3, name: 'Metro Cafe' };
const biryani = { id: 9, name: 'Chicken Biryani', price: 120 };
const tea = { id: 20, name: 'Masala Tea', price: 15.5 };

beforeEach(() => {
  useCart.getState().clear();
});

describe('cart', () => {
  it('adds items and merges repeats into one line', () => {
    useCart.getState().add(canteen, biryani);
    useCart.getState().add(canteen, biryani);
    expect(useCart.getState().lines).toEqual([{ itemId: 9, name: 'Chicken Biryani', price: 120, qty: 2 }]);
  });

  it('caps each item at 10', () => {
    for (let i = 0; i < MAX_QTY; i++) useCart.getState().add(canteen, biryani);
    expect(useCart.getState().add(canteen, biryani)).toBe('max-reached');
    useCart.getState().setQty(9, 50);
    expect(useCart.getState().lines[0].qty).toBe(MAX_QTY);
  });

  it('allows only one canteen per cart', () => {
    useCart.getState().add(canteen, biryani);
    expect(useCart.getState().add(cafe, tea)).toBe('other-vendor');
    expect(useCart.getState().vendor).toEqual(canteen);
    useCart.getState().replaceVendorAndAdd(cafe, tea);
    expect(useCart.getState().vendor).toEqual(cafe);
    expect(useCart.getState().lines.map((l) => l.itemId)).toEqual([20]);
  });

  it('removing the last line empties the cart and forgets the canteen', () => {
    useCart.getState().add(canteen, biryani);
    useCart.getState().setQty(9, 0);
    expect(useCart.getState().lines).toEqual([]);
    expect(useCart.getState().vendor).toBeNull();
  });

  it('sums in paise without float drift', () => {
    useCart.getState().add(cafe, tea);
    useCart.getState().setQty(20, 3);
    expect(cartSubtotalPaise(useCart.getState().lines)).toBe(4650);
    expect(cartCount(useCart.getState().lines)).toBe(3);
  });
});

describe('idempotency key', () => {
  it('reuses the same key for retries of the same cart', () => {
    useCart.getState().add(canteen, biryani);
    const first = useCart.getState().checkoutKeyForAttempt();
    expect(useCart.getState().checkoutKeyForAttempt()).toBe(first);
  });

  it('gets a new key after the cart changes', () => {
    useCart.getState().add(canteen, biryani);
    const first = useCart.getState().checkoutKeyForAttempt();
    useCart.getState().setQty(9, 2);
    expect(useCart.getState().checkoutKeyForAttempt()).not.toBe(first);
  });

  it('gets a new key after the pickup slot changes', () => {
    useCart.getState().add(canteen, biryani);
    const first = useCart.getState().checkoutKeyForAttempt();
    useCart.getState().setSlot('2026-10-23T07:30:00Z');
    expect(useCart.getState().checkoutKeyForAttempt()).not.toBe(first);
  });

  it('is reset once the order is created, and the cart empties when that order is paid', () => {
    useCart.getState().add(canteen, biryani);
    const first = useCart.getState().checkoutKeyForAttempt();
    useCart.getState().markOrderCreated(55);
    expect(useCart.getState().checkoutKey).toBeNull();
    expect(useCart.getState().checkoutKeyForAttempt()).not.toBe(first);

    useCart.getState().onOrderPaid(99); // some other order
    expect(useCart.getState().lines).toHaveLength(1);
    useCart.getState().onOrderPaid(55);
    expect(useCart.getState().lines).toHaveLength(0);
  });
});
