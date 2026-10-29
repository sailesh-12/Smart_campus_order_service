import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import type { Order } from '@/api/adapters';
import { api, call } from '@/api/client';
import { ApiError, friendlyMessage } from '@/api/errors';
import { keys, useMenu, useSlots } from '@/api/queries';
import { Banner, Button, Card, EmptyState, SectionTitle, styles } from '@/components/ui';
import { useCooldown } from '@/lib/hooks';
import { formatINR, formatPaise, lineTotalPaise } from '@/lib/money';
import { campusToday, formatTime } from '@/lib/time';
import { MAX_QTY, cartSubtotalPaise, useCart } from '@/store/cart';
import { colors, radius, spacing } from '@/theme';

const SLOT_CHIPS = 12;

function Chip({ label, selected, onPress, disabled }: { label: string; selected: boolean; onPress: () => void; disabled?: boolean }) {
  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityState={{ selected, disabled }}
      disabled={disabled}
      onPress={onPress}
      style={{
        paddingHorizontal: spacing.md, minHeight: 40, justifyContent: 'center', borderRadius: radius,
        borderWidth: 1, borderColor: selected ? colors.primary : colors.border,
        backgroundColor: selected ? colors.primary : colors.surface, opacity: disabled ? 0.4 : 1,
      }}
    >
      <Text style={{ color: selected ? '#fff' : colors.navy, fontWeight: '700' }}>{label}</Text>
    </Pressable>
  );
}

export default function CartScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const cart = useCart();
  const vendorId = cart.vendor?.id ?? null;
  const menu = useMenu(vendorId);
  const slots = useSlots(vendorId, campusToday());
  const [error, setError] = useState<unknown>(null);
  const [cooldown, startCooldown] = useCooldown();

  const place = useMutation({
    mutationFn: async () => {
      // Same key for retries of this exact cart; a new one after success or any cart/slot change
      const key = cart.checkoutKeyForAttempt();
      const { data } = await call(
        api.POST('/orders', {
          params: { header: { 'Idempotency-Key': key } },
          body: {
            vendorId: vendorId!,
            items: cart.lines.map((l) => ({ menuItemId: l.itemId, qty: l.qty })),
            pickupSlotStart: cart.slot ?? undefined,
          },
        }),
      );
      return data as Order;
    },
    onMutate: () => setError(null),
    onSuccess: (order) => {
      cart.markOrderCreated(order.id!);
      queryClient.setQueryData(keys.order(order.id!), order);
      void queryClient.invalidateQueries({ queryKey: keys.myOrders });
      router.replace(`/order/${order.id}`);
    },
    onError: (e) => {
      setError(e);
      startCooldown(e);
      if (e instanceof ApiError) {
        if (e.code === 'OUT_OF_STOCK' || e.code === 'ITEM_UNAVAILABLE') void menu.refetch();
        if (e.code === 'SLOT_FULL') {
          void slots.refetch();
          cart.setSlot(null);
        }
      }
    },
  });

  if (!cart.vendor || cart.lines.length === 0) {
    return (
      <EmptyState
        title="Your cart is empty"
        hint="Pick a canteen and add something tasty."
        action={<Button title="Browse canteens" onPress={() => router.replace('/')} style={{ marginTop: spacing.md }} />}
      />
    );
  }

  const menuById = new Map((menu.data ?? []).map((i) => [i.id, i]));
  const unavailable = cart.lines.filter((l) => menu.data && !menuById.get(l.itemId)?.available);
  const openSlots = (slots.data ?? []).filter((s) => s.available).slice(0, SLOT_CHIPS);
  const code = error instanceof ApiError ? error.code : null;

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <Text style={styles.h1}>{cart.vendor.name}</Text>

      {cart.pendingOrderId ? (
        <Banner tone="info" title="You have an order waiting for payment">
          Open it from My orders to pay, or place this cart again if it was cancelled.
        </Banner>
      ) : null}

      {error ? (
        <Banner tone={code === 'OUT_OF_STOCK' ? 'warning' : 'danger'} title={friendlyMessage(error)}>
          {code === 'TOO_MANY_UNPAID_ORDERS' ? 'Go to My orders to pay or cancel an unpaid order.' : undefined}
        </Banner>
      ) : null}
      {unavailable.length > 0 ? (
        <Banner tone="warning" title="Some items are sold out">
          Remove {unavailable.map((l) => l.name).join(', ')} to continue.
        </Banner>
      ) : null}

      <Card>
        {cart.lines.map((line) => {
          const soldOut = unavailable.includes(line);
          return (
            <View key={line.itemId} style={[styles.rowBetween, { paddingVertical: spacing.sm }]}>
              <View style={{ flex: 1 }}>
                <Text style={{ fontWeight: '700', color: soldOut ? colors.textMuted : colors.navy }}>
                  {line.name}
                  {soldOut ? ' · Sold out' : ''}
                </Text>
                <Text style={styles.muted}>{formatINR(line.price)} each · {formatPaise(lineTotalPaise(line.price, line.qty))}</Text>
              </View>
              <View style={{ flexDirection: 'row', gap: spacing.sm, alignItems: 'center' }}>
                <Button title="−" variant="secondary" onPress={() => cart.setQty(line.itemId, line.qty - 1)} style={{ minWidth: 44 }} />
                <Text style={{ fontWeight: '800', minWidth: 18, textAlign: 'center' }}>{line.qty}</Text>
                <Button
                  title="+"
                  variant="secondary"
                  disabled={line.qty >= MAX_QTY || soldOut}
                  onPress={() => cart.setQty(line.itemId, line.qty + 1)}
                  style={{ minWidth: 44 }}
                />
              </View>
            </View>
          );
        })}
        <View style={[styles.rowBetween, { borderTopWidth: 1, borderTopColor: colors.border, paddingTop: spacing.sm, marginTop: spacing.sm }]}>
          <Text style={{ fontWeight: '800', color: colors.navy }}>Estimated total</Text>
          <Text style={{ fontWeight: '800', color: colors.navy }}>{formatPaise(cartSubtotalPaise(cart.lines))}</Text>
        </View>
        <Text style={[styles.muted, { marginTop: 4 }]}>The final amount is confirmed when you place the order.</Text>
      </Card>

      <SectionTitle>Pickup time</SectionTitle>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm }}>
        <Chip label="As soon as possible" selected={!cart.slot} onPress={() => cart.setSlot(null)} />
        {openSlots.map((s) => (
          <Chip key={s.slotStart} label={formatTime(s.slotStart)} selected={cart.slot === s.slotStart} onPress={() => cart.setSlot(s.slotStart!)} />
        ))}
      </View>
      {slots.isError ? <Text style={styles.muted}>Pickup times couldn’t be loaded; “As soon as possible” still works.</Text> : null}
      {slots.data && openSlots.length === 0 ? <Text style={styles.muted}>No later pickup times left today.</Text> : null}

      <Button
        title={cooldown ? `Try again in ${cooldown}s` : 'Place order'}
        onPress={() => place.mutate()}
        loading={place.isPending}
        disabled={place.isPending || unavailable.length > 0 || cooldown > 0}
        style={{ marginTop: spacing.md }}
        accessibilityHint="Reserves your items for a few minutes while you pay"
      />
      <Text style={[styles.muted, { textAlign: 'center' }]}>Your items are held for a few minutes while you pay.</Text>
    </ScrollView>
  );
}
