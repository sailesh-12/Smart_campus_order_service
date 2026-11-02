'use client';

import { createContext, useContext } from 'react';
import type { Vendor } from '@/api/adapters';
import type { StreamState } from '@/api/useEventStream';

export type VendorContextValue = { vendor: Vendor; stream: StreamState };

export const VendorContext = createContext<VendorContextValue | null>(null);

export function useVendorContext(): VendorContextValue {
  const ctx = useContext(VendorContext);
  if (!ctx) throw new Error('useVendorContext must be used inside the dashboard layout');
  return ctx;
}
