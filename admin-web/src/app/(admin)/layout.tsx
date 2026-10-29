import type { ReactNode } from 'react';
import { AdminShell } from '@/components/AdminShell';

/**
 * Authentication happens in the browser (JWT in sessionStorage), so nothing below this layout can be
 * prerendered; opt out of Cache Components' instant-navigation validation.
 */
export const instant = false;

export default function AdminLayout({ children }: { children: ReactNode }) {
  return <AdminShell>{children}</AdminShell>;
}
