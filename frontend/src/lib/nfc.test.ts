import { describe, expect, it } from 'vitest';
import { codiceDecimale, codiceValido, motivoNfcAssente, normalizzaCodice } from './nfc';

describe('codici dei braccialetti', () => {
  it('toglie i separatori e mette in maiuscolo', () => {
    expect(normalizzaCodice('04:a1:b2:c3')).toBe('04A1B2C3');
    expect(normalizzaCodice(' d000-0001 ')).toBe('D0000001');
  });
  it('accetta da 4 a 32 caratteri', () => {
    expect(codiceValido('D0000001')).toBe(true);
    expect(codiceValido('ABC')).toBe(false);
    expect(codiceValido('')).toBe(false);
  });
  it('converte il decimale a 10 cifre del gestionale attuale', () => {
    expect(normalizzaCodice('0209345710')).toBe('0C7A5CAE');
    expect(normalizzaCodice('0c:7a:5c:ae')).toBe('0C7A5CAE');
    expect(codiceDecimale('0C7A5CAE')).toBe('0209345710');
    expect(normalizzaCodice('12345678')).toBe('12345678'); // otto cifre: già esadecimale
  });
});

describe('perché il dispositivo non legge l\'NFC', () => {
  const chromeAndroid = 'Mozilla/5.0 (Linux; Android 15; A065) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36';
  it('sul PC serve il lettore o il codice', () => {
    expect(motivoNfcAssente('Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/141.0.0.0', true)).toContain('Chrome su Android');
  });
  it('su Android serve Chrome', () => {
    expect(motivoNfcAssente(chromeAndroid.replace('Mobile Safari', 'SamsungBrowser/28.0 Mobile Safari'), true)).toContain('con Chrome');
  });
  it('su un indirizzo non sicuro lo dice', () => {
    expect(motivoNfcAssente(chromeAndroid, false)).toContain('https');
  });
});

describe('tag NFC comuni', () => {
  it('accetta i seriali di 7 byte (adesivi NTAG)', () => {
    expect(normalizzaCodice('04:5a:3b:c2:91:6e:80')).toBe('045A3BC2916E80');
    expect(codiceValido('045A3BC2916E80')).toBe(true);
    expect(codiceDecimale('045A3BC2916E80')).toBeNull();
  });
});
