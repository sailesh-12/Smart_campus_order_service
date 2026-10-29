import { AnalyticsView } from '@/views/AnalyticsView';

// Client-only page (browser session); see the dashboard layout for why instant validation is off
export const instant = false;

export default function Page() {
  return <AnalyticsView />;
}
