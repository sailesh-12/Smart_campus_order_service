import { useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { ScrollView, Text } from 'react-native';
import { api, data } from '@/api/client';
import { friendlyMessage } from '@/api/errors';
import { Banner, Button, TextField, styles } from '@/components/ui';
import { fieldErrorsOf } from '@/lib/hooks';

/**
 * Opened from the emailed link (smartcampus://reset-password?token=...) or by pasting the token.
 * TODO(contract): app.auth.reset-link-base points at a web URL by default; set it to this app's scheme
 * (or a web page that redirects here) for the link to open the app.
 */
export default function ResetPasswordScreen() {
  const router = useRouter();
  const params = useLocalSearchParams<{ token?: string }>();
  const [token, setToken] = useState(params.token ?? '');
  const [password, setPassword] = useState('');
  const [done, setDone] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const fields = fieldErrorsOf(error);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await data(api.POST('/auth/reset-password', { body: { token: token.trim(), newPassword: password } }));
      setDone(true);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  if (done) {
    return (
      <ScrollView contentContainerStyle={styles.content}>
        <Banner tone="success" title="Password changed">
          You were logged out everywhere. Log in with your new password.
        </Banner>
        <Button title="Go to login" onPress={() => router.replace('/login')} />
      </ScrollView>
    );
  }

  return (
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <Text style={styles.muted}>Choose a new password of at least 8 characters.</Text>
      {error && !Object.keys(fields).length ? <Banner tone="danger" title={friendlyMessage(error)} /> : null}
      <TextField label="Reset code" value={token} onChangeText={setToken} autoCapitalize="none" error={fields.token} />
      <TextField
        label="New password"
        value={password}
        onChangeText={setPassword}
        secureTextEntry
        autoComplete="new-password"
        error={fields.newPassword ?? (password && password.length < 8 ? 'Use at least 8 characters' : undefined)}
      />
      <Button title="Set new password" onPress={submit} loading={busy} disabled={!token || password.length < 8} />
    </ScrollView>
  );
}
