import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert, ScrollView, Text } from 'react-native';
import { api, data } from '@/api/client';
import { friendlyMessage } from '@/api/errors';
import { keys, useMyComplaints, useProfile } from '@/api/queries';
import { Banner, Button, Card, ErrorState, LoadingState, SectionTitle, TextField, styles } from '@/components/ui';
import { fieldErrorsOf } from '@/lib/hooks';
import { formatDateTime } from '@/lib/time';
import { useAuth } from '@/store/auth';
import { colors } from '@/theme';

export default function ProfileScreen() {
  const queryClient = useQueryClient();
  const profile = useProfile();
  const complaints = useMyComplaints();
  const signOut = useAuth((s) => s.signOut);
  const setUserName = useAuth((s) => s.setUserName);
  // The field shows the saved name until the student starts typing
  const [draft, setName] = useState<string | null>(null);
  const name = draft ?? profile.data?.name ?? '';
  const [saved, setSaved] = useState(false);

  const save = useMutation({
    mutationFn: () => data(api.PATCH('/users/me', { body: { name: name.trim() } })),
    onMutate: () => setSaved(false),
    onSuccess: (p) => {
      queryClient.setQueryData(keys.me, p);
      if (p.name) setUserName(p.name);
      setSaved(true);
    },
  });

  if (profile.isPending) return <LoadingState />;
  if (profile.isError) return <ErrorState error={profile.error} onRetry={() => profile.refetch()} />;

  const fields = fieldErrorsOf(save.error);

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <Card>
        <Text style={styles.muted}>Signed in as</Text>
        <Text style={{ fontSize: 18, fontWeight: '800', color: colors.navy }}>{profile.data.email}</Text>
      </Card>
      {saved ? <Banner tone="success" title="Saved" /> : null}
      {save.isError && !Object.keys(fields).length ? <Banner tone="danger" title={friendlyMessage(save.error)} /> : null}
      <TextField label="Name" value={name} onChangeText={setName} error={fields.name} />
      <Button
        title="Save"
        onPress={() => save.mutate()}
        loading={save.isPending}
        disabled={!name.trim() || name.trim() === profile.data.name}
      />

      <SectionTitle>My complaints</SectionTitle>
      {complaints.isPending ? <Text style={styles.muted}>Loading…</Text> : null}
      {complaints.isError ? <Button title="Retry loading complaints" variant="secondary" onPress={() => complaints.refetch()} /> : null}
      {complaints.data?.length === 0 ? <Text style={styles.muted}>No complaints. Good!</Text> : null}
      {complaints.data?.map((c) => (
        <Card key={c.id}>
          <Text style={{ fontWeight: '700', color: colors.navy }}>Order #{c.orderId} · {c.status?.replace('_', ' ')}</Text>
          <Text style={styles.muted}>{formatDateTime(c.createdAt)}</Text>
          <Text style={{ color: colors.text, marginTop: 4 }}>{c.text}</Text>
          {c.adminNote ? <Text style={{ color: colors.info, marginTop: 4 }}>Reply: {c.adminNote}</Text> : null}
        </Card>
      ))}

      <Button
        title="Log out"
        variant="danger"
        onPress={() =>
          Alert.alert('Log out?', 'Your cart will be cleared on this phone.', [
            { text: 'Stay', style: 'cancel' },
            { text: 'Log out', style: 'destructive', onPress: () => void signOut() },
          ])
        }
      />
    </ScrollView>
  );
}
