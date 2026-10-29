import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { ScrollView, Text } from 'react-native';
import { api, data } from '@/api/client';
import { friendlyMessage } from '@/api/errors';
import { keys } from '@/api/queries';
import { Banner, Button, TextField, styles } from '@/components/ui';
import { fieldErrorsOf } from '@/lib/hooks';

const MAX = 1000;

export default function ComplaintScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const orderId = Number(useLocalSearchParams<{ orderId: string }>().orderId);
  const [text, setText] = useState('');

  const submit = useMutation({
    mutationFn: () => data(api.POST('/complaints', { body: { orderId, text: text.trim() } })),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: keys.myComplaints }),
  });
  const fields = fieldErrorsOf(submit.error);

  if (submit.isSuccess) {
    return (
      <ScrollView contentContainerStyle={styles.content}>
        <Banner tone="success" title="Thanks, we’ve got it">The campus team will look into it. You can follow it in Profile.</Banner>
        <Button title="Back to order" onPress={() => router.back()} />
      </ScrollView>
    );
  }

  return (
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <Text style={styles.muted}>Tell us what went wrong with order #{orderId}.</Text>
      {submit.isError && !Object.keys(fields).length ? <Banner tone="danger" title={friendlyMessage(submit.error)} /> : null}
      <TextField
        label={`What happened? (${text.length}/${MAX})`}
        value={text}
        onChangeText={(t) => setText(t.slice(0, MAX))}
        multiline
        style={[styles.input, { minHeight: 140, textAlignVertical: 'top', paddingTop: 12 }]}
        error={fields.text}
      />
      <Button title="Send" onPress={() => submit.mutate()} loading={submit.isPending} disabled={!text.trim() || submit.isPending} />
    </ScrollView>
  );
}
