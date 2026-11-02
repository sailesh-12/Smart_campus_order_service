import { useState } from 'react';
import { ScrollView, Text } from 'react-native';
import { toSession } from '@/api/adapters';
import { api, data } from '@/api/client';
import { friendlyMessage } from '@/api/errors';
import { Banner, Button, TextField, styles } from '@/components/ui';
import { fieldErrorsOf } from '@/lib/hooks';
import { useAuth } from '@/store/auth';

export default function RegisterScreen() {
  const signIn = useAuth((s) => s.signIn);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const fields = fieldErrorsOf(error);
  const tooShort = password.length > 0 && password.length < 8;

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      // This app only ever creates student accounts
      const response = await data(
        api.POST('/auth/register', { body: { name: name.trim(), email: email.trim(), password, role: 'STUDENT' } }),
      );
      await signIn(toSession(response));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <Text style={styles.muted}>Use your college email. You can order as soon as the account is created.</Text>
      {error && !Object.keys(fields).length ? <Banner tone="danger" title={friendlyMessage(error)} /> : null}
      <TextField label="Full name" value={name} onChangeText={setName} autoComplete="name" error={fields.name} />
      <TextField
        label="College email"
        value={email}
        onChangeText={setEmail}
        autoCapitalize="none"
        keyboardType="email-address"
        autoComplete="email"
        error={fields.email}
      />
      <TextField
        label="Password (at least 8 characters)"
        value={password}
        onChangeText={setPassword}
        secureTextEntry
        autoComplete="new-password"
        error={fields.password ?? (tooShort ? 'Use at least 8 characters' : undefined)}
      />
      <Button title="Create account" onPress={submit} loading={busy} disabled={!name || !email || password.length < 8} />
    </ScrollView>
  );
}
