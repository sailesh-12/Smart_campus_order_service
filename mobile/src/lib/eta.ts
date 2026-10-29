/**
 * GET /vendors gives a single estimatedWaitMinutes; shown as a range with the same spread the server
 * uses for per-item ranges (high = ceil(low x 1.3) + 1). TODO(contract): a server-side range would be better.
 */
export function waitRange(minutes: number | null | undefined): string {
  if (minutes == null) return '';
  const low = Math.max(1, Math.ceil(minutes));
  return `${low}–${Math.ceil(low * 1.3) + 1} min`;
}

export function etaRange(low: number | null | undefined, high: number | null | undefined): string {
  if (low == null || high == null) return '';
  return `${low}–${high} min`;
}
