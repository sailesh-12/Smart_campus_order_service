import { useRouter } from 'expo-router';
import { FlatList, Pressable, RefreshControl, Text, View } from 'react-native';
import type { Vendor } from '@/api/adapters';
import { useVendors } from '@/api/queries';
import { Button, Card, EmptyState, ErrorState, LoadingState, styles } from '@/components/ui';
import { waitRange } from '@/lib/eta';
import { formatPaise } from '@/lib/money';
import { cartCount, cartSubtotalPaise, useCart } from '@/store/cart';
import { colors, spacing } from '@/theme';

const BUSY = {
  CALM: { label: 'Calm', color: colors.success, bg: colors.successBg },
  BUSY: { label: 'Busy', color: colors.warning, bg: colors.warningBg },
  PACKED: { label: 'Packed', color: colors.danger, bg: colors.dangerBg },
} as const;

function VendorCard({ vendor, onPress }: { vendor: Vendor; onPress: () => void }) {
  const open = !!vendor.isOpen && !vendor.isSuspended;
  const busy = BUSY[vendor.busyLevel ?? 'CALM'];
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${vendor.name}, ${open ? 'open' : 'closed'}`}
    >
      <Card muted={!open}>
        <View style={styles.rowBetween}>
          <Text style={{ fontSize: 18, fontWeight: '800', color: colors.navy, flexShrink: 1 }}>{vendor.name}</Text>
          <Text style={{ fontWeight: '700', color: open ? colors.success : colors.textMuted }}>{open ? '● Open' : '○ Closed'}</Text>
        </View>
        {open ? (
          <View style={{ flexDirection: 'row', gap: spacing.sm, marginTop: spacing.sm, alignItems: 'center' }}>
            <View style={[styles.badge, { backgroundColor: busy.bg }]}>
              <Text style={[styles.badgeText, { color: busy.color }]}>{busy.label}</Text>
            </View>
            <Text style={styles.muted}>Wait about {waitRange(vendor.estimatedWaitMinutes)}</Text>
          </View>
        ) : (
          <Text style={[styles.muted, { marginTop: spacing.sm }]}>Not taking orders right now</Text>
        )}
      </Card>
    </Pressable>
  );
}

export default function HomeScreen() {
  const router = useRouter();
  const vendors = useVendors();
  const lines = useCart((s) => s.lines);

  if (vendors.isPending) return <LoadingState label="Finding canteens…" />;
  if (vendors.isError) return <ErrorState error={vendors.error} onRetry={() => vendors.refetch()} />;

  // Open canteens first; closed ones stay visible but greyed out
  const list = [...vendors.data].sort((a, b) => Number(!!b.isOpen) - Number(!!a.isOpen));

  return (
    <View style={styles.screen}>
      <FlatList
        data={list}
        keyExtractor={(v) => String(v.id)}
        contentContainerStyle={[styles.content, { paddingBottom: lines.length ? 96 : spacing.lg }]}
        refreshControl={<RefreshControl refreshing={vendors.isRefetching} onRefresh={() => void vendors.refetch()} />}
        ListEmptyComponent={<EmptyState title="No canteens yet" hint="Pull down to refresh." />}
        renderItem={({ item }) => <VendorCard vendor={item} onPress={() => router.push(`/vendor/${item.id}`)} />}
      />
      {lines.length > 0 ? (
        <View style={{ position: 'absolute', left: spacing.lg, right: spacing.lg, bottom: spacing.lg }}>
          <Button
            title={`View cart · ${cartCount(lines)} item${cartCount(lines) > 1 ? 's' : ''} · ${formatPaise(cartSubtotalPaise(lines))}`}
            onPress={() => router.push('/cart')}
          />
        </View>
      ) : null}
    </View>
  );
}
