import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { Alert, RefreshControl, ScrollView, Text, View } from 'react-native';
import {
  parseEvent,
  type Order,
  type OrderEtaEvent,
  type OrderStatusEvent,
  type PayResult,
} from '@/api/adapters';
import { api, call, data } from '@/api/client';
import { ApiError, friendlyMessage } from '@/api/errors';
import { keys, useOrder } from '@/api/queries';
import { useEventStream } from '@/api/useEventStream';
import { Banner, Button, Card, ErrorState, LoadingState, SectionTitle, StatusBadge, styles } from '@/components/ui';
import { etaRange } from '@/lib/eta';
import { useNow } from '@/lib/hooks';
import { formatINR } from '@/lib/money';
import {
  CONFIRM_POLL_MS,
  outcomeFromError,
  outcomeFromPolledOrder,
  outcomeFromResponse,
  type PayOutcome,
} from '@/lib/payOutcome';
import { CLOSED_STATUSES, ORDER_STATUS, TRACKING_STEPS, stepIndex, type OrderStatus } from '@/lib/status';
import { formatCountdown, formatTime, secondsUntil } from '@/lib/time';
import { useCart } from '@/store/cart';
import { colors, spacing } from '@/theme';

const HAS_PICKUP_PASS: ReadonlySet<OrderStatus> = new Set(['PAID', 'ACCEPTED', 'PREPARING', 'READY', 'UNCOLLECTED']);

function Stepper({ status }: { status: OrderStatus }) {
  const current = stepIndex(status);
  return (
    <View style={{ gap: spacing.sm }}>
      {TRACKING_STEPS.map((step, i) => {
        const done = current >= 0 && i <= current;
        return (
          <View key={step} style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
            <View
              style={{
                width: 28, height: 28, borderRadius: 14, alignItems: 'center', justifyContent: 'center',
                backgroundColor: done ? colors.primary : colors.border,
              }}
            >
              <Text style={{ color: done ? '#fff' : colors.textMuted, fontWeight: '800' }}>{done ? '✓' : i + 1}</Text>
            </View>
            <Text style={{ fontWeight: i === current ? '800' : '600', color: done ? colors.navy : colors.textMuted }}>
              {ORDER_STATUS[step].label}
            </Text>
          </View>
        );
      })}
    </View>
  );
}

export default function TrackingScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const orderId = Number(useLocalSearchParams<{ id: string }>().id);
  const onOrderPaid = useCart((s) => s.onOrderPaid);

  const [confirming, setConfirming] = useState(false);
  const [eta, setEta] = useState<OrderEtaEvent | null>(null);
  const [refunded, setRefunded] = useState(false);
  const [reminder, setReminder] = useState(false);
  const [actionError, setActionError] = useState<unknown>(null);
  const now = useNow();

  const order = useOrder(orderId);

  const refetch = useCallback(() => void queryClient.invalidateQueries({ queryKey: keys.order(orderId) }), [queryClient, orderId]);

  const stream = useEventStream(
    `/orders/${orderId}/events`,
    {
      'order-status': (raw) => {
        const event = parseEvent<OrderStatusEvent>(raw);
        if (!event) return;
        queryClient.setQueryData<Order>(keys.order(orderId), (o) => (o ? { ...o, status: event.status } : o));
        refetch();
      },
      'order-eta': (raw) => setEta(parseEvent<OrderEtaEvent>(raw)),
      'payment-refunded': () => setRefunded(true),
      'order-reminder': () => setReminder(true),
    },
    refetch,
  );

  const settle = useCallback(
    (outcome: PayOutcome) => {
      switch (outcome.kind) {
        case 'paid':
          setConfirming(false);
          onOrderPaid(orderId);
          refetch();
          void queryClient.invalidateQueries({ queryKey: keys.myOrders });
          break;
        case 'confirming':
          setConfirming(true);
          break;
        case 'declined':
          setConfirming(false);
          refetch();
          Alert.alert('Payment failed, items released', 'Nothing was charged. Your cart is still there if you want to try again.', [
            { text: 'Back to cart', onPress: () => router.replace('/cart') },
          ]);
          break;
        case 'expired':
          setConfirming(false);
          refetch();
          Alert.alert('Hold expired', 'The items were released before payment. Place the order again from your cart.', [
            { text: 'Back to cart', onPress: () => router.replace('/cart') },
          ]);
          break;
        case 'error':
          setActionError(outcome.error);
          if (outcome.error.code === 'INVALID_TRANSITION') refetch();
          break;
      }
    },
    [onOrderPaid, orderId, queryClient, refetch, router],
  );

  // While confirming: poll GET /orders/{id} every 3 s; the polled status settles the payment
  useQuery({
    queryKey: [...keys.order(orderId), 'confirming'],
    enabled: confirming,
    refetchInterval: CONFIRM_POLL_MS,
    gcTime: 0,
    queryFn: async () => {
      const latest = await data(api.GET('/orders/{id}', { params: { path: { id: orderId } } }));
      queryClient.setQueryData(keys.order(orderId), latest);
      const outcome = outcomeFromPolledOrder(latest);
      if (outcome) settle(outcome);
      return latest;
    },
  });

  const pay = useMutation({
    mutationFn: async () => {
      // PAYMENT SDK: a real gateway's checkout sheet would open here and hand back a payment reference.
      // The backend's mock gateway decides the result from the order total, so the app just asks it to charge.
      const { data, response } = await call(api.POST('/orders/{id}/pay', { params: { path: { id: orderId } } }));
      return { status: response.status, body: data as PayResult };
    },
    onMutate: () => setActionError(null),
    onSuccess: ({ status, body }) => settle(outcomeFromResponse(status, body)),
    onError: (e) => settle(outcomeFromError(e)),
  });

  const cancel = useMutation({
    mutationFn: () => call(api.POST('/orders/{id}/cancel', { params: { path: { id: orderId } } })),
    onMutate: () => setActionError(null),
    onSuccess: () => {
      refetch();
      void queryClient.invalidateQueries({ queryKey: keys.myOrders });
    },
    onError: (e) => {
      setActionError(e);
      if (e instanceof ApiError && e.code === 'PAYMENT_UNCONFIRMED') setConfirming(true);
      if (e instanceof ApiError && e.code === 'INVALID_TRANSITION') refetch();
    },
  });

  if (order.isPending) return <LoadingState label="Loading your order…" />;
  if (order.isError) return <ErrorState error={order.error} onRetry={() => order.refetch()} />;

  const o = order.data;
  const status = o.status as OrderStatus;
  const pending = status === 'PENDING_PAYMENT';
  const holdLeft = pending ? secondsUntil(o.holdExpiresAt, now) : 0;
  const holdOver = pending && !!o.holdExpiresAt && holdLeft === 0;
  const canCancel = (pending && !confirming) || status === 'PAID';
  const showRefund = refunded || status === 'REJECTED';
  const readyAt = eta?.estReadyAt ?? o.estReadyAt;
  const range = eta ? etaRange(eta.estMinutesLow, eta.estMinutesHigh) : etaRange(o.estMinutesLow, o.estMinutesHigh);

  const confirmCancel = () =>
    Alert.alert(
      'Cancel this order?',
      status === 'PAID' ? 'You will get a full refund to your original payment method.' : 'Your items will be released.',
      [
        { text: 'Keep order', style: 'cancel' },
        { text: 'Cancel order', style: 'destructive', onPress: () => cancel.mutate() },
      ],
    );

  return (
    <ScrollView
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={order.isRefetching && !confirming} onRefresh={() => void order.refetch()} />}
    >
      <View style={styles.rowBetween}>
        <Text style={styles.h1}>Order #{o.id}</Text>
        <StatusBadge status={status} />
      </View>
      {stream === 'reconnecting' ? <Text style={styles.muted}>Reconnecting for live updates…</Text> : null}

      {actionError ? <Banner tone="danger" title={friendlyMessage(actionError)} /> : null}
      {showRefund ? <Banner tone="info" title="Refund initiated">The refund has been sent to your payment method. Banks can take a few days to show it.</Banner> : null}
      {reminder && (status === 'READY' || status === 'UNCOLLECTED') ? (
        <Banner tone="warning" title="Your food is waiting">Please collect it soon.</Banner>
      ) : null}
      {status === 'UNCOLLECTED' ? (
        <Banner tone="warning" title="Not collected yet">You can still pick it up: show your pickup pass at the counter.</Banner>
      ) : null}

      {pending && confirming ? (
        <Banner tone="info" title="Confirming your payment, don't pay again">
          We’re checking with the bank. This page updates by itself, usually within a minute.
        </Banner>
      ) : null}

      {pending && !confirming ? (
        <Card>
          <Text style={{ fontSize: 16, fontWeight: '800', color: colors.navy }}>Pay to confirm your order</Text>
          {holdOver ? (
            <Text style={[styles.muted, { marginTop: 4 }]}>The hold has run out. Your items may have been released.</Text>
          ) : (
            <Text style={[styles.muted, { marginTop: 4 }]}>
              Items held for <Text style={{ fontWeight: '800', color: colors.primary }}>{formatCountdown(holdLeft)}</Text>
            </Text>
          )}
          <Button
            title={`Pay now · ${formatINR(o.total)}`}
            onPress={() => pay.mutate()}
            loading={pay.isPending}
            disabled={pay.isPending || holdOver}
            style={{ marginTop: spacing.md }}
          />
          {holdOver ? (
            <Button title="Back to cart" variant="secondary" onPress={() => router.replace('/cart')} style={{ marginTop: spacing.sm }} />
          ) : null}
        </Card>
      ) : null}

      {!pending && !CLOSED_STATUSES.has(status) && readyAt ? (
        <Card>
          <Text style={styles.muted}>Estimated ready</Text>
          <Text style={{ fontSize: 22, fontWeight: '800', color: colors.navy }}>~{formatTime(readyAt)}</Text>
          {range ? <Text style={styles.muted}>About {range} from when it was updated</Text> : null}
          {o.pickupSlotStart ? <Text style={styles.muted}>Your pickup slot: {formatTime(o.pickupSlotStart)}</Text> : null}
        </Card>
      ) : null}

      {HAS_PICKUP_PASS.has(status) ? (
        <Button title="Show pickup pass" onPress={() => router.push(`/pickup/${o.id}`)} />
      ) : null}

      {!pending && !['CANCELLED', 'EXPIRED', 'REJECTED'].includes(status) ? (
        <Card>
          <Stepper status={status} />
        </Card>
      ) : null}

      <SectionTitle>Items</SectionTitle>
      <Card>
        {(o.items ?? []).map((i) => (
          <View key={i.menuItemId} style={[styles.rowBetween, { paddingVertical: 4 }]}>
            <Text style={{ color: colors.text, flex: 1 }}>{i.qty} × {i.itemName}</Text>
            <Text style={{ color: colors.text }}>{formatINR(i.unitPrice)}</Text>
          </View>
        ))}
        <View style={[styles.rowBetween, { borderTopWidth: 1, borderTopColor: colors.border, marginTop: spacing.sm, paddingTop: spacing.sm }]}>
          <Text style={{ fontWeight: '800', color: colors.navy }}>Total</Text>
          <Text style={{ fontWeight: '800', color: colors.navy }}>{formatINR(o.total)}</Text>
        </View>
      </Card>

      {canCancel ? (
        <Button title="Cancel order" variant="secondary" onPress={confirmCancel} loading={cancel.isPending} disabled={cancel.isPending} />
      ) : null}
      {!pending ? (
        <Button title="Report a problem" variant="secondary" onPress={() => router.push(`/complaint/${o.id}`)} />
      ) : null}
    </ScrollView>
  );
}
