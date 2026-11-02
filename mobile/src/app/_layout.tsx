import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { shouldRetry } from '@/api/queries';
import { LoadingState } from '@/components/ui';
import { unregisterPush } from '@/lib/push';
import { onSignOut, useAuth } from '@/store/auth';
import { useCart } from '@/store/cart';
import { colors } from '@/theme';

export default function RootLayout() {
  const [queryClient] = useState(
    () => new QueryClient({ defaultOptions: { queries: { retry: shouldRetry, staleTime: 10_000 }, mutations: { retry: false } } }),
  );
  const status = useAuth((s) => s.status);

  useEffect(() => {
    void useAuth.getState().hydrate();
    // Logout: forget the device token while the session is still valid, then drop cached data and the cart
    return onSignOut(async () => {
      await unregisterPush();
      queryClient.clear();
      useCart.getState().clear();
    });
  }, [queryClient]);

  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>
        <StatusBar style="light" />
        {status === 'loading' ? (
          <LoadingState label="Starting…" />
        ) : (
          <Stack
            screenOptions={{
              headerStyle: { backgroundColor: colors.navy },
              headerTintColor: '#fff',
              headerTitleStyle: { fontWeight: '700' },
              contentStyle: { backgroundColor: colors.background },
            }}
          >
            <Stack.Screen name="(auth)" options={{ headerShown: false }} />
            <Stack.Screen name="(app)" options={{ headerShown: false }} />
          </Stack>
        )}
      </QueryClientProvider>
    </SafeAreaProvider>
  );
}
