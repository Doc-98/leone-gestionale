import { afterEach, describe, expect, it, vi } from 'vitest';
import { codiceDecimale, codiceValido, leggiConNfc, motivoNfcAssente, normalizzaCodice } from './nfc';

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
    expect(normalizzaCodice('0983503556')).toBe('3A9F12C4');
    expect(normalizzaCodice('3a:9f:12:c4')).toBe('3A9F12C4');
    expect(codiceDecimale('3A9F12C4')).toBe('0983503556');
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

describe('lettura con la Web NFC', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  /** Un NDEFReader finto: il test «appoggia» i braccialetti chiamando leggi(). */
  function lettoreFinto() {
    let suLettura: (e: { serialNumber?: string }) => void = () => {};
    const scan = vi.fn(async () => {});
    class NDEFReader {
      addEventListener(_tipo: string, f: (e: { serialNumber?: string }) => void) { suLettura = f; }
      scan = scan;
    }
    vi.stubGlobal('window', { NDEFReader });
    return { scan, leggi: (serialNumber?: string) => suLettura({ serialNumber }) };
  }

  it('normalizza i codici e ignora lo stesso braccialetto letto di nuovo subito dopo', async () => {
    vi.useFakeTimers();
    const finto = lettoreFinto();
    const letti: string[] = [];
    const segnale = new AbortController().signal;
    await leggiConNfc((c) => letti.push(c), segnale, 2500);
    expect(finto.scan).toHaveBeenCalledWith({ signal: segnale });
    finto.leggi('04:a1:b2:c3');
    finto.leggi('04:A1:B2:C3'); // ancora appoggiato
    finto.leggi(undefined); // un tag senza seriale
    finto.leggi('d0:00:00:01');
    vi.advanceTimersByTime(1000);
    finto.leggi('d0:00:00:01');
    vi.advanceTimersByTime(3000);
    finto.leggi('d0:00:00:01'); // ripassato dopo la pausa: vale
    expect(letti).toEqual(['04A1B2C3', 'D0000001', 'D0000001']);
  });
});
