import type { Timer } from '@/modules/timer-engine';

export function remainingMs(timer: Timer, now: number): number {
  if (timer.state === 'running') return Math.max(0, timer.endAt - now);
  if (timer.state === 'paused') return timer.remainingMs;
  return 0;
}

const pad = (n: number) => String(n).padStart(2, '0');

/** mm:ss, or h:mm:ss from one hour. Rounds up so a timer only reads 00:00 once it has ended. */
export function formatClock(ms: number): string {
  const total = Math.ceil(ms / 1000);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}
