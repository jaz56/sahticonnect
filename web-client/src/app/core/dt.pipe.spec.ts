import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { registerLocaleData } from '@angular/common';
import localeFrTn from '@angular/common/locales/fr-TN';
import { DtPipe } from './dt.pipe';

describe('DtPipe (montants en dinars, 3 décimales)', () => {
  let pipe: DtPipe;

  beforeEach(() => {
    registerLocaleData(localeFrTn);
    TestBed.configureTestingModule({ providers: [DtPipe, { provide: LOCALE_ID, useValue: 'fr-TN' }] });
    pipe = TestBed.inject(DtPipe);
  });

  it('affiche toujours 3 décimales, virgule décimale', () => {
    expect(pipe.transform(145)).toBe('145,000 DT');
    expect(pipe.transform(0.3)).toBe('0,300 DT');
    expect(pipe.transform(48.0004)).toBe('48,000 DT');
  });

  it('sépare les milliers (espace insécable de la locale)', () => {
    expect(pipe.transform(1234.5).replace(/\s/g, ' ')).toBe('1 234,500 DT');
  });

  it('accepte une chaîne (montant SOAP xs:decimal)', () => {
    expect(pipe.transform('72.000')).toBe('72,000 DT');
  });

  it('montant absent ou illisible → tiret', () => {
    expect(pipe.transform(null)).toBe('—');
    expect(pipe.transform(undefined)).toBe('—');
    expect(pipe.transform('abc')).toBe('—');
  });
});
