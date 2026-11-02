import { useRouter } from 'expo-router';
import { FlatList, Pressable, RefreshControl, Text, View } from 'react-native';
import { useMyOrders } from '@/api/queries';
import { Button, Card, EmptyState, ErrorState, LoadingState, StatusBadge, styles } from '@/components/ui';
import { formatINR } from '@/lib/money';
import { formatDateTime } from '@/lib/time';
import { colors, spacing } from '@/theme';

export default function OrderHistoryScreen() {
  const router = useRouter();
  const orders = useMyOrders();

  if (orders.isPending) return <LoadingState label="Loading your orders…" />;
  if (orders.isError) return <ErrorState error={orders.error} onRetry={() => orders.refetch()} />;

  return (
    <FlatList
      style={styles.screen}
      data={orders.data}
      keyExtractor={(o) => String(o.id)}
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={orders.isRefetching} onRefresh={() => void orders.refetch()} />}
      ListEmptyComponent={
        <EmptyState
          title="No orders yet"
          hint="Your orders will show up here."
          action={<Button title="Browse canteens" onPress={() => router.replace('/')} style={{ marginTop: spacing.md }} />}
        />
      }
      renderItem={({ item }) => (
        <Pressable accessibilityRole="button" onPress={() => router.push(`/order/${item.id}`)}>
          <Card>
            <View style={styles.rowBetween}>
              <Text style={{ fontWeight: '800', color: colors.navy }}>Order #{item.id}</Text>
              <StatusBadge status={item.status} />
            </View>
            <Text style={[styles.muted, { marginTop: 4 }]} numberOfLines={1}>
              {(item.items ?? []).map((i) => `${i.qty}× ${i.itemName}`).join(', ')}
            </Text>
            <View style={[styles.rowBetween, { marginTop: 4 }]}>
              <Text style={styles.muted}>{formatDateTime(item.createdAt)}</Text>
              <Text style={{ fontWeight: '700', color: colors.text }}>{formatINR(item.total)}</Text>
            </View>
          </Card>
        </Pressable>
      )}
    />
  );
}
