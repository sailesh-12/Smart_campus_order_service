import { Redirect, Stack } from 'expo-router';
import { useEffect } from 'react';
import { registerForPush } from '@/lib/push';
import { useAuth } from '@/store/auth';
import { colors } from '@/theme';

export default function AppLayout() {
  const status = useAuth((s) => s.status);

  useEffect(() => {
    if (status === 'signedIn') void registerForPush();
  }, [status]);

  if (status !== 'signedIn') return <Redirect href="/login" />;

  return (
    <Stack
      screenOptions={{
        headerStyle: { backgroundColor: colors.navy },
        headerTintColor: '#fff',
        headerTitleStyle: { fontWeight: '700' },
        contentStyle: { backgroundColor: colors.background },
      }}
    >
      <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
      <Stack.Screen name="vendor/[id]" options={{ title: 'Menu' }} />
      <Stack.Screen name="cart" options={{ title: 'Your cart' }} />
      <Stack.Screen name="order/[id]" options={{ title: 'Your order' }} />
      <Stack.Screen name="pickup/[id]" options={{ title: 'Pickup pass' }} />
      <Stack.Screen name="complaint/[orderId]" options={{ title: 'Report a problem' }} />
    </Stack>
  );
}
