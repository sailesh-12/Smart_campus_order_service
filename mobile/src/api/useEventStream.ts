import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import EventSource from 'react-native-sse';
import { API_BASE_URL } from '@/config';
import { currentToken } from './client';

export type StreamState = 'connecting' | 'live' | 'reconnecting' | 'off';

export function backoffDelay(attempt: number): number {
  const base = Math.min(30_000, 1000 * 2 ** attempt);
  return base / 2 + Math.random() * (base / 2);
}

type Handlers = Record<string, (data: string | null) => void>;

/**
 * Subscribes to a server-sent event stream with the Authorization header (plain EventSource cannot send it).
 * Reconnects with exponential backoff, and calls onReconnect after every re-open so screens refetch over REST:
 * events can be missed while disconnected, and REST is the source of truth.
 * The stream is closed while the app is in the background and reopened when it returns.
 */
export function useEventStream(path: string | null, handlers: Handlers, onReconnect?: () => void): StreamState {
  const [state, setState] = useState<StreamState>('connecting');
  const handlersRef = useRef(handlers);
  const reconnectRef = useRef(onReconnect);
  // Keep the latest callbacks without re-subscribing to the stream
  useLayoutEffect(() => {
    handlersRef.current = handlers;
    reconnectRef.current = onReconnect;
  });

  useEffect(() => {
    if (!path) return;
    let source: EventSource<string> | null = null;
    let timer: ReturnType<typeof setTimeout> | null = null;
    let attempt = 0;
    let everOpened = false;
    let disposed = false;

    const cleanupSource = () => {
      source?.removeAllEventListeners();
      source?.close();
      source = null;
    };

    const scheduleReconnect = () => {
      if (disposed || timer) return;
      cleanupSource();
      setState('reconnecting');
      timer = setTimeout(() => {
        timer = null;
        connect();
      }, backoffDelay(attempt++));
    };

    const connect = () => {
      if (disposed) return;
      const token = currentToken();
      source = new EventSource<string>(`${API_BASE_URL}${path}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        pollingInterval: 0, // reconnection is handled here, with backoff
      });
      source.addEventListener('open', () => {
        attempt = 0;
        setState('live');
        if (everOpened) reconnectRef.current?.();
        everOpened = true;
      });
      for (const name of Object.keys(handlersRef.current)) {
        source.addEventListener(name, (event) => {
          handlersRef.current[name]?.((event as { data: string | null }).data);
        });
      }
      source.addEventListener('error', scheduleReconnect);
      source.addEventListener('close', scheduleReconnect);
    };

    connect();

    const sub = AppState.addEventListener('change', (next) => {
      if (next === 'active' && !source && !timer) {
        attempt = 0;
        connect();
        reconnectRef.current?.();
      } else if (next !== 'active') {
        if (timer) clearTimeout(timer);
        timer = null;
        cleanupSource();
      }
    });

    return () => {
      disposed = true;
      sub.remove();
      if (timer) clearTimeout(timer);
      cleanupSource();
    };
  }, [path]);

  return path ? state : 'off';
}
