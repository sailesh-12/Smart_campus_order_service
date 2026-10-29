import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import { toPaise } from '@/lib/money';
import { newUuid } from '@/lib/uuid';

export const MAX_QTY = 10;

export type CartLine = { itemId: number; name: string; price: number; qty: number };
export type CartVendor = { id: number; name: string };

export type AddResult = 'added' | 'max-reached' | 'other-vendor';

type CartState = {
  vendor: CartVendor | null;
  lines: CartLine[];
  /** Chosen pickup slot (ISO) or null for "as soon as possible". Part of the order payload. */
  slot: string | null;
  /**
   * Idempotency-Key of the current checkout attempt (rule 5.7 on the server).
   * Created on the first "Place order" tap, reused for retries of the same cart,
   * cleared when the order is created or when anything in the payload (lines, slot) changes.
   */
  checkoutKey: string | null;
  /** The order created from this cart that is waiting for payment; the cart is emptied once it is paid. */
  pendingOrderId: number | null;

  add: (vendor: CartVendor, item: { id: number; name: string; price: number }) => AddResult;
  replaceVendorAndAdd: (vendor: CartVendor, item: { id: number; name: string; price: number }) => void;
  setQty: (itemId: number, qty: number) => void;
  remove: (itemId: number) => void;
  setSlot: (slot: string | null) => void;
  clear: () => void;
  checkoutKeyForAttempt: () => string;
  markOrderCreated: (orderId: number) => void;
  onOrderPaid: (orderId: number) => void;
};

export const useCart = create<CartState>()(
  persist(
    (set, get) => ({
      vendor: null,
      lines: [],
      slot: null,
      checkoutKey: null,
      pendingOrderId: null,

      add: (vendor, item) => {
        const state = get();
        // One canteen per cart
        if (state.vendor && state.vendor.id !== vendor.id && state.lines.length > 0) return 'other-vendor';
        const existing = state.lines.find((l) => l.itemId === item.id);
        if (existing && existing.qty >= MAX_QTY) return 'max-reached';
        const lines = existing
          ? state.lines.map((l) => (l.itemId === item.id ? { ...l, qty: l.qty + 1 } : l))
          : [...state.lines, { itemId: item.id, name: item.name, price: item.price, qty: 1 }];
        set({ vendor, lines, checkoutKey: null });
        return 'added';
      },

      replaceVendorAndAdd: (vendor, item) => {
        set({ vendor, lines: [{ itemId: item.id, name: item.name, price: item.price, qty: 1 }], slot: null, checkoutKey: null });
      },

      setQty: (itemId, qty) => {
        const clamped = Math.max(0, Math.min(MAX_QTY, Math.floor(qty)));
        const lines = clamped === 0
          ? get().lines.filter((l) => l.itemId !== itemId)
          : get().lines.map((l) => (l.itemId === itemId ? { ...l, qty: clamped } : l));
        set({ lines, checkoutKey: null, ...(lines.length === 0 ? { vendor: null, slot: null } : {}) });
      },

      remove: (itemId) => get().setQty(itemId, 0),

      setSlot: (slot) => {
        if (slot !== get().slot) set({ slot, checkoutKey: null });
      },

      clear: () => set({ vendor: null, lines: [], slot: null, checkoutKey: null, pendingOrderId: null }),

      checkoutKeyForAttempt: () => {
        const current = get().checkoutKey;
        if (current) return current;
        const key = newUuid();
        set({ checkoutKey: key });
        return key;
      },

      markOrderCreated: (orderId) => set({ checkoutKey: null, pendingOrderId: orderId }),

      onOrderPaid: (orderId) => {
        if (get().pendingOrderId === orderId) get().clear();
      },
    }),
    {
      name: 'sc.cart',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: (s) => ({ vendor: s.vendor, lines: s.lines, slot: s.slot, checkoutKey: s.checkoutKey, pendingOrderId: s.pendingOrderId }),
    },
  ),
);

export function cartSubtotalPaise(lines: CartLine[]): number {
  return lines.reduce((sum, l) => sum + toPaise(l.price) * l.qty, 0);
}

export function cartCount(lines: CartLine[]): number {
  return lines.reduce((sum, l) => sum + l.qty, 0);
}
