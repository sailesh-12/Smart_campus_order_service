'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useState, type FormEvent } from 'react';
import { toSession } from '@/api/adapters';
import { api, data } from '@/api/client';
import { ApiError, friendlyMessage } from '@/api/errors';
import { saveSession, useLogoutNotice, useSession } from '@/auth/session';
import { Banner, Button, Field } from '@/components/ui';

export default function LoginPage() {
  const router = useRouter();
  const session = useSession();
  const notice = useLogoutNotice();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [cooldownUntil, setCooldownUntil] = useState(0);
  const [now, setNow] = useState(0);

  useEffect(() => {
    if (session) router.replace('/');
  }, [session, router]);

  useEffect(() => {
    if (cooldownUntil <= now) return;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [cooldownUntil, now]);

  const cooldown = Math.max(0, Math.ceil((cooldownUntil - now) / 1000));
  const fields = error instanceof ApiError ? error.fieldErrors : {};

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const response = await data(api.POST('/auth/login', { body: { email: email.trim(), password } }));
      saveSession(toSession(response)); // throws for non-vendor accounts
      router.replace('/');
    } catch (err) {
      setError(err);
      if (err instanceof ApiError && err.status === 429) {
        setNow(Date.now());
        setCooldownUntil(Date.now() + (err.retryAfterSeconds ?? 30) * 1000);
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="flex min-h-screen items-center justify-center p-4">
      <form onSubmit={submit} className="w-full max-w-md space-y-4 rounded-card bg-white p-8 shadow-sm" noValidate>
        <div>
          <h1 className="text-3xl font-extrabold">Smart Campus</h1>
          <p className="font-bold text-primary">Canteen dashboard</p>
        </div>
        {notice ? <Banner tone="warning" title={notice} /> : null}
        {error && !Object.keys(fields).length ? <Banner tone="danger" title={friendlyMessage(error)} /> : null}
        <Field label="Email" name="email" type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} error={fields.email} />
        <Field
          label="Password"
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fields.password}
        />
        <Button type="submit" className="w-full" loading={busy} disabled={!email || !password || cooldown > 0}>
          {cooldown ? `Try again in ${cooldown}s` : 'Log in'}
        </Button>
      </form>
    </main>
  );
}
