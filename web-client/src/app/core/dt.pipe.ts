import { LOCALE_ID, Pipe, PipeTransform, inject } from '@angular/core';
import { formatNumber } from '@angular/common';

/**
 * Montant en dinars tunisiens, toujours avec 3 décimales (millimes) : 145 → « 145,000 DT ».
 * Format de la locale fr-TN (virgule décimale, espace pour les milliers).
 */
@Pipe({ name: 'dt' })
export class DtPipe implements PipeTransform {
  private readonly locale = inject(LOCALE_ID);

  transform(montant: number | string | null | undefined): string {
    if (montant === null || montant === undefined || montant === '') {
      return '—';
    }
    const valeur = typeof montant === 'string' ? Number(montant) : montant;
    if (Number.isNaN(valeur)) {
      return '—';
    }
    return `${formatNumber(valeur, this.locale, '1.3-3')} DT`;
  }
}
