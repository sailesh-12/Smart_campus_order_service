import { Link } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { toSession } from '@/api/adapters';
import { api, data } from '@/api/client';
import { friendlyMessage } from '@/api/errors';
import { Banner, Button, TextField, styles } from '@/components/ui';
import { fieldErrorsOf, useCooldown } from '@/lib/hooks';
import { useAuth } from '@/store/auth';
import { colors, spacing } from '@/theme';

export default function LoginScreen() {
  const signIn = useAuth((s) => s.signIn);
  const notice = useAuth((s) => s.notice);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [cooldown, startCooldown] = useCooldown();
  const fields = fieldErrorsOf(error);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      const response = await data(api.POST('/auth/login', { body: { email: email.trim(), password } }));
      await signIn(toSession(response)); // refuses non-student accounts
    } catch (e) {
      setError(e);
      startCooldown(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={styles.screen}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={[styles.content, { paddingTop: spacing.xl * 2 }]} keyboardShouldPersistTaps="handled">
          <View style={{ marginBottom: spacing.lg }}>
            <Text style={[styles.h1, { fontSize: 30 }]}>Smart Campus</Text>
            <Text style={{ color: colors.primary, fontWeight: '700', fontSize: 16 }}>Order ahead, skip the queue</Text>
          </View>
          {notice ? <Banner tone="warning" title={notice} /> : null}
          {error && !Object.keys(fields).length ? <Banner tone="danger" title={friendlyMessage(error)} /> : null}
          <TextField
            label="College email"
            value={email}
            onChangeText={setEmail}
            autoCapitalize="none"
            autoComplete="email"
            keyboardType="email-address"
            error={fields.email}
          />
          <TextField
            label="Password"
            value={password}
            onChangeText={setPassword}
            secureTextEntry
            autoComplete="password"
            error={fields.password}
          />
          <Button
            title={cooldown ? `Try again in ${cooldown}s` : 'Log in'}
            onPress={submit}
            loading={busy}
            disabled={!email || !password || cooldown > 0}
          />
          <View style={[styles.rowBetween, { marginTop: spacing.md }]}>
            <Link href="/register" style={{ color: colors.navy, fontWeight: '700', padding: spacing.sm }}>
              Create account
            </Link>
            <Link href="/forgot-password" style={{ color: colors.navy, fontWeight: '700', padding: spacing.sm }}>
              Forgot password?
            </Link>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
