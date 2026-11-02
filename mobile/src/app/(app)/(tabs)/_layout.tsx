import { Tabs } from 'expo-router';
import { Text, type ColorValue } from 'react-native';
import { cartCount, useCart } from '@/store/cart';
import { colors } from '@/theme';

function TabGlyph({ glyph, color }: { glyph: string; color: ColorValue }) {
  return <Text style={{ color, fontSize: 18 }} accessibilityElementsHidden>{glyph}</Text>;
}

export default function TabsLayout() {
  const count = useCart((s) => cartCount(s.lines));
  return (
    <Tabs
      screenOptions={{
        headerStyle: { backgroundColor: colors.navy },
        headerTintColor: '#fff',
        headerTitleStyle: { fontWeight: '700' },
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textMuted,
        tabBarLabelStyle: { fontWeight: '700' },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: 'Canteens',
          tabBarIcon: ({ color }) => <TabGlyph glyph="🍽" color={color} />,
          tabBarBadge: count > 0 ? count : undefined,
        }}
      />
      <Tabs.Screen
        name="orders"
        options={{ title: 'My orders', tabBarIcon: ({ color }) => <TabGlyph glyph="🧾" color={color} /> }}
      />
      <Tabs.Screen
        name="profile"
        options={{ title: 'Profile', tabBarIcon: ({ color }) => <TabGlyph glyph="👤" color={color} /> }}
      />
    </Tabs>
  );
}
