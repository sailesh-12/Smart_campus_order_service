import type { ReactNode } from 'react';
import { DashboardShell } from '@/components/DashboardShell';

/**
 * Authentication happens in the browser (the JWT lives in sessionStorage, which the server never sees),
 * so nothing below this layout can be prerendered: the shell shows a loading state until the session is read.
 * Opt this segment out of instant-navigation validation (Cache Components), as the Next.js docs suggest.
 */
export const instant = false;

export default function DashboardLayout({ children }: { children: ReactNode }) {
  return <DashboardShell>{children}</DashboardShell>;
}
