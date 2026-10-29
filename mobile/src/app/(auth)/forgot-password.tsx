import { useRouter } from 'expo-router';
import { useState } from 'react';
import { ScrollView, Text } from 'react-native';
import { api, data } from '@/api/client';
import { friendlyMessage } from '@/api/errors';
import { Banner, Button, TextField, styles } from '@/components/ui';
import { fieldErrorsOf, useCooldown } from '@/lib/hooks';

export default function ForgotPasswordScreen() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [cooldown, startCooldown] = useCooldown();
  const fields = fieldErrorsOf(error);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      // The server answers the same way whether or not the account exists
      const response = await data(api.POST('/auth/forgot-password', { body: { email: email.trim() } }));
      setSent(response.message ?? 'If the email is registered, a reset link has been sent.');
    } catch (e) {
      setError(e);
      startCooldown(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <Text style={styles.muted}>Enter your email and we’ll send a link to choose a new password.</Text>
      {sent ? <Banner tone="success" title={sent} /> : null}
      {error && !Object.keys(fields).length ? <Banner tone="danger" title={friendlyMessage(error)} /> : null}
      <TextField
        label="College email"
        value={email}
        onChangeText={setEmail}
        autoCapitalize="none"
        keyboardType="email-address"
        error={fields.email}
      />
      <Button
        title={cooldown ? `Try again in ${cooldown}s` : 'Send reset link'}
        onPress={submit}
        loading={busy}
        disabled={!email || cooldown > 0}
      />
      <Button title="I have a reset code" variant="secondary" onPress={() => router.push('/reset-password')} />
    </ScrollView>
  );
}
