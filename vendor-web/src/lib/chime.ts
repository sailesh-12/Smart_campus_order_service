'use client';

/**
 * New-order chime made with Web Audio (no sound file). Browsers block audio until the user interacts
 * with the page, so the context is created when "Sound on" is clicked, and only then can it play.
 */
let ctx: AudioContext | null = null;

export async function enableSound(): Promise<boolean> {
  try {
    ctx ??= new AudioContext();
    if (ctx.state === 'suspended') await ctx.resume();
    return ctx.state === 'running';
  } catch {
    return false;
  }
}

export function disableSound() {
  void ctx?.suspend();
}

export function playChime() {
  if (!ctx || ctx.state !== 'running') return;
  const now = ctx.currentTime;
  for (const [i, freq] of [880, 1320].entries()) {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'sine';
    osc.frequency.value = freq;
    gain.gain.setValueAtTime(0.0001, now + i * 0.18);
    gain.gain.exponentialRampToValueAtTime(0.4, now + i * 0.18 + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, now + i * 0.18 + 0.35);
    osc.connect(gain).connect(ctx.destination);
    osc.start(now + i * 0.18);
    osc.stop(now + i * 0.18 + 0.4);
  }
}
