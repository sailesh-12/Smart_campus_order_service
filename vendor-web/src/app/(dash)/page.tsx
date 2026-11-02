import { BoardView } from '@/views/BoardView';

// Client-only page (browser session); see the dashboard layout for why instant validation is off
export const instant = false;

export default function Page() {
  return <BoardView />;
}
