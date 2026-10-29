import { useEffect, useState } from 'react';
import { ApiError } from '@/api/errors';

/** Seconds left of a Retry-After cooldown (429), counting down to 0. */
export function useCooldown(): [number, (error: unknown) => void] {
  const [until, setUntil] = useState(0);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (until <= Date.now()) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [until]);

  const start = (error: unknown) => {
    if (error instanceof ApiError && error.status === 429) {
      const seconds = error.retryAfterSeconds ?? 30;
      setUntil(Date.now() + seconds * 1000);
      setNow(Date.now());
    }
  };
  return [Math.max(0, Math.ceil((until - now) / 1000)), start];
}

/** Ticks every second; used for countdowns. */
export function useNow(intervalMs = 1000): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);
  return now;
}

export function fieldErrorsOf(error: unknown): Record<string, string> {
  return error instanceof ApiError ? error.fieldErrors : {};
}
