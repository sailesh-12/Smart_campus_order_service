import type { ReactNode } from 'react';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
  type TextInputProps,
  type ViewStyle,
} from 'react-native';
import { friendlyMessage } from '@/api/errors';
import { statusInfo } from '@/lib/status';
import { colors, radius, spacing } from '@/theme';

type ButtonProps = {
  title: string;
  onPress: () => void;
  variant?: 'primary' | 'secondary' | 'danger';
  disabled?: boolean;
  loading?: boolean;
  style?: ViewStyle;
  accessibilityHint?: string;
};

export function Button({ title, onPress, variant = 'primary', disabled, loading, style, accessibilityHint }: ButtonProps) {
  const inactive = disabled || loading;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: !!inactive, busy: !!loading }}
      accessibilityHint={accessibilityHint}
      disabled={inactive}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        variant === 'primary' && { backgroundColor: pressed ? colors.primaryPressed : colors.primary },
        variant === 'secondary' && styles.buttonSecondary,
        variant === 'danger' && { backgroundColor: colors.danger },
        inactive && { opacity: 0.55 },
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator color={variant === 'secondary' ? colors.navy : '#fff'} />
      ) : (
        <Text style={[styles.buttonText, variant === 'secondary' && { color: colors.navy }]}>{title}</Text>
      )}
    </Pressable>
  );
}

export function Card({ children, style, muted }: { children: ReactNode; style?: ViewStyle; muted?: boolean }) {
  return <View style={[styles.card, muted && { opacity: 0.55 }, style]}>{children}</View>;
}

export function TextField({ label, error, ...props }: TextInputProps & { label: string; error?: string }) {
  return (
    <View style={{ marginBottom: spacing.md }}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        accessibilityLabel={label}
        placeholderTextColor={colors.textMuted}
        style={[styles.input, error ? { borderColor: colors.danger } : null]}
        {...props}
      />
      {error ? <Text style={styles.fieldError}>{error}</Text> : null}
    </View>
  );
}

export function StatusBadge({ status }: { status: string | null | undefined }) {
  const info = statusInfo(status);
  return (
    <View style={[styles.badge, { backgroundColor: info.background }]} accessibilityLabel={`Status: ${info.label}`}>
      <Text style={[styles.badgeText, { color: info.color }]}>{info.label}</Text>
    </View>
  );
}

export function Banner({ tone = 'info', title, children }: { tone?: 'info' | 'success' | 'warning' | 'danger'; title: string; children?: ReactNode }) {
  const palette = {
    info: [colors.infoBg, colors.info],
    success: [colors.successBg, colors.success],
    warning: [colors.warningBg, colors.warning],
    danger: [colors.dangerBg, colors.danger],
  }[tone];
  return (
    <View style={[styles.banner, { backgroundColor: palette[0], borderLeftColor: palette[1] }]} accessibilityRole="alert">
      <Text style={[styles.bannerTitle, { color: palette[1] }]}>{title}</Text>
      {children ? <Text style={styles.bannerBody}>{children}</Text> : null}
    </View>
  );
}

export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <View style={styles.center}>
      <ActivityIndicator color={colors.primary} size="large" />
      <Text style={styles.muted}>{label}</Text>
    </View>
  );
}

export function EmptyState({ title, hint, action }: { title: string; hint?: string; action?: ReactNode }) {
  return (
    <View style={styles.center}>
      <Text style={styles.emptyTitle}>{title}</Text>
      {hint ? <Text style={[styles.muted, { textAlign: 'center' }]}>{hint}</Text> : null}
      {action}
    </View>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <View style={styles.center}>
      <Text style={styles.emptyTitle}>Couldn’t load this</Text>
      <Text style={[styles.muted, { textAlign: 'center' }]}>{friendlyMessage(error)}</Text>
      <Button title="Try again" variant="secondary" onPress={onRetry} style={{ marginTop: spacing.md, minWidth: 160 }} />
    </View>
  );
}

export function SectionTitle({ children }: { children: ReactNode }) {
  return <Text style={styles.section}>{children}</Text>;
}

export const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.lg, gap: spacing.md },
  button: { minHeight: 48, borderRadius: radius, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing.lg },
  buttonSecondary: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  card: { backgroundColor: colors.surface, borderRadius: radius, padding: spacing.lg, borderWidth: 1, borderColor: colors.border },
  label: { color: colors.text, fontWeight: '600', marginBottom: spacing.xs },
  input: {
    minHeight: 48, borderRadius: radius, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.surface,
    paddingHorizontal: spacing.md, color: colors.text, fontSize: 16,
  },
  fieldError: { color: colors.danger, marginTop: spacing.xs },
  badge: { alignSelf: 'flex-start', borderRadius: 999, paddingHorizontal: 10, paddingVertical: 4 },
  badgeText: { fontWeight: '700', fontSize: 13 },
  banner: { borderRadius: radius, padding: spacing.md, borderLeftWidth: 4 },
  bannerTitle: { fontWeight: '700', fontSize: 15 },
  bannerBody: { color: colors.text, marginTop: 2 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl, gap: spacing.sm },
  muted: { color: colors.textMuted },
  emptyTitle: { color: colors.text, fontSize: 18, fontWeight: '700', textAlign: 'center' },
  section: { color: colors.navy, fontSize: 18, fontWeight: '800', marginTop: spacing.sm },
  h1: { color: colors.navy, fontSize: 24, fontWeight: '800' },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
});
