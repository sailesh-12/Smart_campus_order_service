import { PaymentsView } from '@/views/PaymentsView';

// Client-only page (browser session); see the admin layout
export const instant = false;

export default function Page() {
  return <PaymentsView />;
}
