'use client';

import { fetchEventSource } from '@microsoft/fetch-event-source';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { getToken } from '@/auth/session';
import { API_BASE_URL } from '@/config';

export type StreamState = 'connecting' | 'live' | 'reconnecting' | 'off';

export function backoffDelay(attempt: number): number {
  const base = Math.min(30_000, 1000 * 2 ** attempt);
  return base / 2 + Math.random() * (base / 2);
}

class FatalStreamError extends Error {}

/**
 * Server-sent events with the Authorization header (the browser EventSource cannot send it).
 * Reconnects with exponential backoff and calls onReconnect after every re-open so the page refetches over REST.
 * Keeps running in background tabs (openWhenHidden), so a kitchen tablet never misses a new order chime.
 */
export function useEventStream(
  path: string | null,
  onEvent: (event: string, data: string) => void,
  onReconnect?: () => void,
): StreamState {
  const [state, setState] = useState<StreamState>('connecting');
  const eventRef = useRef(onEvent);
  const reconnectRef = useRef(onReconnect);
  useLayoutEffect(() => {
    eventRef.current = onEvent;
    reconnectRef.current = onReconnect;
  });

  useEffect(() => {
    if (!path) return;
    const controller = new AbortController();
    let attempt = 0;
    let everOpened = false;

    void fetchEventSource(`${API_BASE_URL}${path}`, {
      signal: controller.signal,
      openWhenHidden: true,
      headers: { Authorization: `Bearer ${getToken() ?? ''}`, Accept: 'text/event-stream' },
      async onopen(response) {
        if (response.status === 401 || response.status === 403) throw new FatalStreamError(`HTTP ${response.status}`);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        attempt = 0;
        setState('live');
        if (everOpened) reconnectRef.current?.();
        everOpened = true;
      },
      onmessage(message) {
        if (message.event) eventRef.current(message.event, message.data);
      },
      onclose() {
        // Server closed the stream (timeout): reconnect through onerror
        throw new Error('closed');
      },
      onerror(error) {
        if (error instanceof FatalStreamError) {
          setState('off');
          throw error; // stop retrying; the REST 401 handler logs out
        }
        setState('reconnecting');
        return backoffDelay(attempt++);
      },
    }).catch(() => undefined);

    return () => controller.abort();
  }, [path]);

  return path ? state : 'off';
}
