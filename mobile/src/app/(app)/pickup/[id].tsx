import { useLocalSearchParams } from 'expo-router';
import { ScrollView, Text, View } from 'react-native';
import QRCode from 'react-native-qrcode-svg';
import { useOrder, usePickupPass } from '@/api/queries';
import { Card, ErrorState, LoadingState, StatusBadge, styles } from '@/components/ui';
import { colors, spacing } from '@/theme';

/** Big code for the counter, QR (the random secret) for the vendor's scanner. */
export default function PickupPassScreen() {
  const orderId = Number(useLocalSearchParams<{ id: string }>().id);
  const order = useOrder(orderId);
  const pass = usePickupPass(orderId, true);

  if (pass.isPending) return <LoadingState label="Loading pickup pass…" />;
  if (pass.isError) return <ErrorState error={pass.error} onRetry={() => pass.refetch()} />;

  return (
    <ScrollView contentContainerStyle={[styles.content, { alignItems: 'center' }]}>
      {order.data ? <StatusBadge status={order.data.status} /> : null}
      <Card style={{ alignItems: 'center', width: '100%', gap: spacing.md }}>
        <Text style={styles.muted}>Show this at the counter</Text>
        <Text
          accessibilityLabel={`Pickup code ${pass.data.pickupCode?.split('').join(' ')}`}
          style={{ fontSize: 64, fontWeight: '900', color: colors.navy, letterSpacing: 6 }}
        >
          {pass.data.pickupCode}
        </Text>
        {pass.data.pickupSecret ? (
          <View style={{ padding: spacing.md, backgroundColor: '#fff' }}>
            <QRCode value={pass.data.pickupSecret} size={220} color={colors.navy} backgroundColor="#fff" />
          </View>
        ) : null}
        <Text style={styles.muted}>Order #{pass.data.orderId}</Text>
      </Card>
      <Text style={[styles.muted, { textAlign: 'center' }]}>Keep this private: anyone with the code can collect the order.</Text>
    </ScrollView>
  );
}
