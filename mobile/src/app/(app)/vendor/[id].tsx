import { useQueryClient } from '@tanstack/react-query';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback } from 'react';
import { Alert, FlatList, Pressable, RefreshControl, Text, View } from 'react-native';
import { parseEvent, type MenuItem, type MenuItemUpdateEvent } from '@/api/adapters';
import { keys, useMenu, useVendor } from '@/api/queries';
import { useEventStream } from '@/api/useEventStream';
import { Banner, Button, Card, EmptyState, ErrorState, LoadingState, styles } from '@/components/ui';
import { etaRange } from '@/lib/eta';
import { formatINR, formatPaise } from '@/lib/money';
import { MAX_QTY, cartCount, cartSubtotalPaise, useCart } from '@/store/cart';
import { colors, radius, spacing } from '@/theme';

function QtyStepper({ qty, onChange, max }: { qty: number; onChange: (q: number) => void; max: boolean }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm }}>
      <Pressable accessibilityLabel="Remove one" onPress={() => onChange(qty - 1)} style={stepBtn}>
        <Text style={stepText}>−</Text>
      </Pressable>
      <Text style={{ minWidth: 20, textAlign: 'center', fontWeight: '800', color: colors.navy }}>{qty}</Text>
      <Pressable accessibilityLabel="Add one" disabled={max} onPress={() => onChange(qty + 1)} style={[stepBtn, max && { opacity: 0.4 }]}>
        <Text style={stepText}>+</Text>
      </Pressable>
    </View>
  );
}
const stepBtn = { width: 40, height: 40, borderRadius: radius, backgroundColor: colors.navy, alignItems: 'center', justifyContent: 'center' } as const;
const stepText = { color: '#fff', fontSize: 20, fontWeight: '800' } as const;

export default function MenuScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const vendorId = Number(useLocalSearchParams<{ id: string }>().id);
  const vendor = useVendor(vendorId);
  const menu = useMenu(vendorId);
  const cart = useCart();

  // Live stock/availability: patch the cached menu, so sold-out items grey out without a reload
  const stream = useEventStream(
    `/vendors/${vendorId}/menu/events`,
    {
      'menu-item-update': (raw) => {
        const event = parseEvent<MenuItemUpdateEvent>(raw);
        if (!event) return;
        queryClient.setQueryData<MenuItem[]>(keys.menu(vendorId), (items) =>
          items?.map((i) => (i.id === event.itemId ? { ...i, available: event.available, lowStock: event.lowStock } : i)),
        );
      },
    },
    useCallback(() => void queryClient.invalidateQueries({ queryKey: keys.menu(vendorId) }), [queryClient, vendorId]),
  );

  const open = !!vendor.data?.isOpen && !vendor.data?.isSuspended;
  const cartVendor = { id: vendorId, name: vendor.data?.name ?? 'Canteen' };

  const add = (item: MenuItem) => {
    const entry = { id: item.id!, name: item.name ?? 'Item', price: item.price ?? 0 };
    const result = cart.add(cartVendor, entry);
    if (result === 'other-vendor') {
      Alert.alert(
        'Start a new cart?',
        `Your cart has items from ${cart.vendor?.name}. You can order from one canteen at a time.`,
        [
          { text: 'Keep my cart', style: 'cancel' },
          { text: 'Start new cart', style: 'destructive', onPress: () => cart.replaceVendorAndAdd(cartVendor, entry) },
        ],
      );
    } else if (result === 'max-reached') {
      Alert.alert('Limit reached', `You can order at most ${MAX_QTY} of each item.`);
    }
  };

  if (menu.isPending || vendor.isPending) return <LoadingState label="Loading menu…" />;
  if (vendor.isError) return <ErrorState error={vendor.error} onRetry={() => vendor.refetch()} />;
  if (menu.isError) return <ErrorState error={menu.error} onRetry={() => menu.refetch()} />;

  const inThisCart = cart.vendor?.id === vendorId ? cart.lines : [];

  return (
    <View style={styles.screen}>
      <Stack.Screen options={{ title: vendor.data.name ?? 'Menu' }} />
      <FlatList
        data={menu.data}
        keyExtractor={(i) => String(i.id)}
        contentContainerStyle={[styles.content, { paddingBottom: inThisCart.length ? 96 : spacing.lg }]}
        refreshControl={<RefreshControl refreshing={menu.isRefetching} onRefresh={() => void menu.refetch()} />}
        ListHeaderComponent={
          <View style={{ gap: spacing.sm }}>
            {!open ? <Banner tone="warning" title="This canteen is closed right now">You can look at the menu, but ordering is paused.</Banner> : null}
            {stream === 'reconnecting' ? <Text style={styles.muted}>Reconnecting for live stock…</Text> : null}
          </View>
        }
        ListEmptyComponent={<EmptyState title="No dishes yet" hint="This canteen hasn't added its menu." />}
        renderItem={({ item }) => {
          const line = inThisCart.find((l) => l.itemId === item.id);
          const soldOut = !item.available;
          return (
            <Card muted={soldOut}>
              <View style={styles.rowBetween}>
                <View style={{ flex: 1, paddingRight: spacing.md }}>
                  <Text style={{ fontSize: 17, fontWeight: '800', color: colors.navy }}>{item.name}</Text>
                  {item.description ? <Text style={styles.muted}>{item.description}</Text> : null}
                  <Text style={{ fontWeight: '700', color: colors.text, marginTop: 4 }}>{formatINR(item.price)}</Text>
                  <View style={{ flexDirection: 'row', gap: spacing.sm, marginTop: 4, flexWrap: 'wrap' }}>
                    {soldOut ? <Text style={{ color: colors.danger, fontWeight: '700' }}>Sold out</Text> : null}
                    {!soldOut && item.lowStock ? <Text style={{ color: colors.warning, fontWeight: '700' }}>Only a few left</Text> : null}
                    {!soldOut && item.estimatedTime ? (
                      <Text style={styles.muted}>Ready in {etaRange(item.estimatedTime.lowMinutes, item.estimatedTime.highMinutes)}</Text>
                    ) : null}
                  </View>
                </View>
                {soldOut || !open ? null : line ? (
                  <QtyStepper qty={line.qty} max={line.qty >= MAX_QTY} onChange={(q) => cart.setQty(item.id!, q)} />
                ) : (
                  <Button title="Add" onPress={() => add(item)} style={{ minWidth: 80 }} />
                )}
              </View>
            </Card>
          );
        }}
      />
      {inThisCart.length > 0 ? (
        <View style={{ position: 'absolute', left: spacing.lg, right: spacing.lg, bottom: spacing.lg }}>
          <Button
            title={`View cart · ${cartCount(inThisCart)} · ${formatPaise(cartSubtotalPaise(inThisCart))}`}
            onPress={() => router.push('/cart')}
          />
        </View>
      ) : null}
    </View>
  );
}
