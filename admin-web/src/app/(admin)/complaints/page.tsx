import { ComplaintsView } from '@/views/ComplaintsView';

// Client-only page (browser session); see the admin layout
export const instant = false;

export default function Page() {
  return <ComplaintsView />;
}
