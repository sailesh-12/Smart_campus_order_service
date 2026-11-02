import type { Metadata, Viewport } from 'next';
import type { ReactNode } from 'react';
import { Geist } from 'next/font/google';
import { Providers } from './providers';
import './globals.css';

const geistSans = Geist({ variable: '--font-geist-sans', subsets: ['latin'] });

export const metadata: Metadata = {
  title: 'Smart Campus · Admin',
  description: 'Campus admin dashboard: canteens, complaints, payment issues',
};

export const viewport: Viewport = { width: 'device-width', initialScale: 1, themeColor: '#0B1F3A' };

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en-IN" className={`${geistSans.variable} h-full antialiased`}>
      <body className="min-h-full">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
