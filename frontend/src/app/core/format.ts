const FR = 'fr-FR';

export function pct(v: number | null | undefined, digits = 1): string {
  return v === null || v === undefined ? '—' : `${(v * 100).toLocaleString(FR, { minimumFractionDigits: digits, maximumFractionDigits: digits })} %`;
}

/** Probabilité arrondie, affichée comme une approximation (« ≈ 35 % »). */
export function approxPct(v: number | null | undefined): string {
  if (v === null || v === undefined) {
    return '—';
  }
  const p = v * 100;
  const rounded = p < 10 ? Math.round(p) : Math.round(p / 5) * 5;
  return `≈ ${Math.max(rounded, 1)} %`;
}

export function num(v: number | null | undefined, digits = 0): string {
  return v === null || v === undefined ? '—' : v.toLocaleString(FR, { minimumFractionDigits: digits, maximumFractionDigits: digits });
}

export function hours(v: number | null | undefined): string {
  if (v === null || v === undefined) {
    return '—';
  }
  const h = Math.floor(v);
  const m = Math.round((v - h) * 60);
  return `${h} h ${String(m).padStart(2, '0')}`;
}

export function minutesToHm(m: number | null | undefined): string {
  if (m === null || m === undefined) {
    return '—';
  }
  const h = Math.floor(m / 60);
  return `${h}h${String(Math.round(m % 60)).padStart(2, '0')}`;
}

export function hm(t: string | null | undefined): string {
  return t ? t.slice(0, 5).replace(':', 'h') : '—';
}

export function isoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function parseIso(s: string): Date {
  const [y, m, d] = s.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function frDate(s: string | null | undefined, opts: Intl.DateTimeFormatOptions = { day: '2-digit', month: '2-digit', year: 'numeric' }): string {
  return s ? parseIso(s.slice(0, 10)).toLocaleDateString(FR, opts) : '—';
}

export function longDate(s: string | null | undefined): string {
  return frDate(s, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
}

export function dateTime(s: string | null | undefined): string {
  return s ? new Date(s).toLocaleString(FR, { dateStyle: 'short', timeStyle: 'short' }) : '—';
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter((w) => /^\p{L}/u.test(w))
    .filter((w, i, all) => i === 0 || i === all.length - 1)
    .map((w) => w[0]?.toUpperCase() ?? '')
    .join('');
}
