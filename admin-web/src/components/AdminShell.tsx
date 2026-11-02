'use client';

import { useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, type ReactNode } from 'react';
import { clearSession, useSession } from '@/auth/session';
import { Button, Loading } from './ui';

const NAV = [
  { href: '/', label: 'Overview' },
  { href: '/canteens', label: 'Canteens' },
  { href: '/complaints', label: 'Complaints' },
  { href: '/payments', label: 'Payment issues' },
  { href: '/orders', label: 'Orders' },
  { href: '/audit', label: 'Audit log' },
] as const;

export function AdminShell({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const queryClient = useQueryClient();
  const session = useSession();

  useEffect(() => {
    if (session === null) {
      queryClient.clear();
      router.replace('/login');
    }
  }, [session, router, queryClient]);

  if (!session) return <Loading label={session === undefined ? 'Opening dashboard…' : 'Redirecting to login…'} />;

  return (
    <div className="min-h-screen">
      <header className="bg-navy text-white">
        <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
          <div>
            <p className="text-xl font-extrabold">Smart Campus admin</p>
            <p className="text-sm text-white/80">{session.user.name}</p>
          </div>
          <Button tone="secondary" onClick={() => clearSession()}>Log out</Button>
        </div>
        <nav className="flex gap-1 overflow-x-auto px-2" aria-label="Main">
          {NAV.map((item) => {
            const active = item.href === '/' ? pathname === '/' : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? 'page' : undefined}
                className={`flex min-h-12 items-center whitespace-nowrap rounded-t-card px-5 font-bold ${active ? 'bg-page text-navy' : 'text-white/85 hover:text-white'}`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
      </header>
      <main className="mx-auto max-w-7xl p-4">{children}</main>
    </div>
  );
}
