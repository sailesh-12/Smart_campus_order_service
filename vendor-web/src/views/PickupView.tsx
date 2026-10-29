'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import type { Order } from '@/api/adapters';
import { api, data } from '@/api/client';
import { ApiError, friendlyMessage } from '@/api/errors';
import { keys } from '@/api/queries';
import { OrderCard } from '@/components/OrderCard';
import { Banner, Button, Field } from '@/components/ui';

const SCANNER_ID = 'pickup-qr-scanner';

function pickupError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'NOT_FOUND') return 'No order of your shop matches this code. Check the code, or scan the QR.';
    if (error.code === 'INVALID_TRANSITION') return `${error.message}. Only READY or UNCOLLECTED orders can be handed over.`;
  }
  return friendlyMessage(error);
}

/** Camera QR scanner; the library is loaded only in the browser, and only while scanning. */
function QrScanner({ onScan, onClose }: { onScan: (secret: string) => void; onClose: () => void }) {
  const [problem, setProblem] = useState<string | null>(null);
  const scanned = useRef(false);

  useEffect(() => {
    let scanner: import('html5-qrcode').Html5Qrcode | null = null;
    let stopped = false;
    void (async () => {
      try {
        const { Html5Qrcode } = await import('html5-qrcode');
        if (stopped) return;
        scanner = new Html5Qrcode(SCANNER_ID);
        await scanner.start(
          { facingMode: 'environment' },
          { fps: 10, qrbox: { width: 260, height: 260 } },
          (text) => {
            if (scanned.current) return;
            scanned.current = true;
            onScan(text.trim());
          },
          () => undefined,
        );
      } catch (e) {
        setProblem(e instanceof Error ? e.message : 'Camera not available');
      }
    })();
    return () => {
      stopped = true;
      if (scanner?.isScanning) void scanner.stop().catch(() => undefined);
    };
  }, [onScan]);

  return (
    <div className="space-y-3">
      <div id={SCANNER_ID} className="mx-auto w-full max-w-md overflow-hidden rounded-card bg-black" />
      {problem ? <Banner tone="warning" title="Can’t use the camera">{problem}. Type the code instead.</Banner> : null}
      <Button tone="secondary" onClick={onClose}>Stop scanning</Button>
    </div>
  );
}

export function PickupView() {
  const queryClient = useQueryClient();
  const [code, setCode] = useState('');
  const [scanning, setScanning] = useState(false);

  const verify = useMutation({
    mutationFn: (body: { pickupCode?: string; pickupSecret?: string }) =>
      data(api.POST('/orders/pickup/verify', { body })) as Promise<Order>,
    onSuccess: () => {
      setCode('');
      void queryClient.invalidateQueries({ queryKey: keys.allOrders });
    },
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (code.trim()) verify.mutate({ pickupCode: code.trim().toUpperCase() });
  };

  const { mutate } = verify;
  // Stable callback, so the camera is not restarted on every render
  const onScan = useCallback(
    (secret: string) => {
      setScanning(false);
      mutate({ pickupSecret: secret });
    },
    [mutate],
  );

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <h1 className="text-2xl font-extrabold">Hand over an order</h1>
      <p className="text-muted">Ask for the pickup code, or scan the QR on the student’s phone. Uncollected orders can be handed over too.</p>

      <form onSubmit={submit} className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <div className="flex-1">
          <Field
            label="Pickup code"
            name="pickupCode"
            placeholder="e.g. A47"
            autoComplete="off"
            autoCapitalize="characters"
            value={code}
            onChange={(e) => setCode(e.target.value)}
          />
        </div>
        <Button type="submit" loading={verify.isPending} disabled={!code.trim()}>Verify</Button>
        <Button type="button" tone="secondary" onClick={() => setScanning((s) => !s)} disabled={verify.isPending}>
          {scanning ? 'Close camera' : 'Scan QR'}
        </Button>
      </form>

      {scanning ? <QrScanner onScan={onScan} onClose={() => setScanning(false)} /> : null}

      {verify.isError ? <Banner tone="danger" title={pickupError(verify.error)} /> : null}
      {verify.isSuccess ? (
        <div className="space-y-2">
          <Banner tone="success" title={`Order #${verify.data.id} collected`}>Hand over these items:</Banner>
          <OrderCard order={verify.data} />
        </div>
      ) : null}
    </div>
  );
}
