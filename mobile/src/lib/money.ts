/**
 * Money is handled as integer paise. Server amounts arrive as JSON numbers with at most two decimals
 * (BigDecimal on the server); they are converted through their decimal string, never multiplied as floats.
 * The server's order total is always the amount of record; client sums are previews only.
 */
const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 });

export function toPaise(value: number | string | null | undefined): number {
  if (value === null || value === undefined || value === '') return 0;
  const text = typeof value === 'number' ? value.toFixed(2) : value.trim();
  const match = /^(-)?(\d+)(?:\.(\d{0,2})\d*)?$/.exec(text);
  if (!match) return 0;
  const [, sign, rupees, fraction = ''] = match;
  const paise = Number(rupees) * 100 + Number(fraction.padEnd(2, '0'));
  return sign ? -paise : paise;
}

export function formatPaise(paise: number): string {
  const negative = paise < 0;
  const abs = Math.abs(paise);
  // Build the decimal string from integers so formatting never depends on float division
  const text = `${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, '0')}`;
  const formatted = inr.format(Number(text));
  return negative ? `-${formatted}` : formatted;
}

/** Formats a server amount (rupees, e.g. 120.5) as ₹120.50. */
export function formatINR(value: number | string | null | undefined): string {
  return formatPaise(toPaise(value));
}

export function lineTotalPaise(unitPrice: number | string, qty: number): number {
  return toPaise(unitPrice) * qty;
}
