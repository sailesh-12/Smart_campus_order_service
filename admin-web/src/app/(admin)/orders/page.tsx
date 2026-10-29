import { OrdersView } from '@/views/OrdersView';

// Client-only page (browser session); see the admin layout
export const instant = false;

export default function Page() {
  return <OrdersView />;
}
