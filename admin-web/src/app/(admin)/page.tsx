import { OverviewView } from '@/views/OverviewView';

// Client-only page (browser session); see the admin layout
export const instant = false;

export default function Page() {
  return <OverviewView />;
}
