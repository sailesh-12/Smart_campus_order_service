'use client';

import { useQuery } from '@tanstack/react-query';
import type { AdminAction } from '@/api/adapters';
import { api, data } from '@/api/client';
import { Banner, Empty, ErrorBox, Loading } from '@/components/ui';
import { isEndpointMissing } from '@/lib/admin';
import { formatDateTime } from '@/lib/time';

export function AuditView() {
  // GET /admin/actions is optional in the brief; this backend provides it (newest first, not paged)
  const actions = useQuery({ queryKey: ['admin', 'actions'], queryFn: (): Promise<AdminAction[]> => data(api.GET('/admin/actions')) });

  if (actions.isPending) return <Loading label="Loading audit log…" />;
  if (actions.isError) {
    return isEndpointMissing(actions.error) ? (
      <Banner tone="info" title="Audit log not available">This backend has no GET /admin/actions.</Banner>
    ) : (
      <ErrorBox error={actions.error} onRetry={() => actions.refetch()} />
    );
  }

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-extrabold">Audit log</h1>
      <p className="text-sm text-muted">Every admin action on money, canteens and complaints.</p>
      {actions.data.length === 0 ? <Empty title="No admin actions yet" /> : (
        <div className="overflow-x-auto rounded-card border border-line bg-white">
          <table className="w-full min-w-[720px] text-left">
            <thead className="bg-page text-sm text-muted">
              <tr>
                <th className="p-3">When</th>
                <th className="p-3">Admin</th>
                <th className="p-3">Action</th>
                <th className="p-3">Target</th>
                <th className="p-3">Note</th>
              </tr>
            </thead>
            <tbody>
              {actions.data.map((a) => (
                <tr key={a.id} className="border-t border-line">
                  <td className="p-3 text-sm">{formatDateTime(a.createdAt)}</td>
                  <td className="p-3">#{a.adminId}</td>
                  <td className="p-3 font-bold">{a.action}</td>
                  <td className="p-3">{a.targetType} #{a.targetId}</td>
                  <td className="p-3 text-sm">{a.note ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
