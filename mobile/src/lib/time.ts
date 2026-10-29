/** All times are shown in campus time (Asia/Kolkata), whatever the phone's zone is. */
export const CAMPUS_TZ = 'Asia/Kolkata';

const timeFmt = new Intl.DateTimeFormat('en-IN', { timeZone: CAMPUS_TZ, hour: 'numeric', minute: '2-digit', hour12: true });
const dateTimeFmt = new Intl.DateTimeFormat('en-IN', {
  timeZone: CAMPUS_TZ, day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit', hour12: true,
});
const dateFmt = new Intl.DateTimeFormat('en-CA', { timeZone: CAMPUS_TZ, year: 'numeric', month: '2-digit', day: '2-digit' });

export function formatTime(iso: string | null | undefined): string {
  return iso ? timeFmt.format(new Date(iso)) : '';
}

export function formatDateTime(iso: string | null | undefined): string {
  return iso ? dateTimeFmt.format(new Date(iso)) : '';
}

/** Today's date in campus time as YYYY-MM-DD (for GET /vendors/{id}/slots?date=). */
export function campusToday(now: Date = new Date()): string {
  return dateFmt.format(now);
}

export function secondsUntil(iso: string | null | undefined, now: number = Date.now()): number {
  if (!iso) return 0;
  return Math.max(0, Math.floor((new Date(iso).getTime() - now) / 1000));
}

export function formatCountdown(totalSeconds: number): string {
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}
