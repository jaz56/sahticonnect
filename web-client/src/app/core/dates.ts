/** Date du jour (ou d'un objet Date) au format ISO attendu par le portail : 2026-11-26. */
export function dateIso(date: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}`;
}
