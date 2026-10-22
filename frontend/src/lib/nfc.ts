/**
 * Lettura dei braccialetti. Sul telefono Android con Chrome: Web NFC (ADR 0004). Sul PC: i lettori da scrivania
 * più comuni «scrivono» il codice come una tastiera seguito da Invio, quindi basta un campo di testo.
 */

/**
 * Il codice come lo salva il server: esadecimale maiuscolo senza separatori («04:a1:b2:c3» → «04A1B2C3»). Dieci
 * cifre sono il formato decimale del gestionale attuale e dei lettori fissi: «0209345710» → «0C7A5CAE».
 */
export function normalizzaCodice(grezzo: string): string {
  const c = grezzo.replace(/[^0-9a-z]/gi, '').toUpperCase();
  if (/^[0-9]{10}$/.test(c) && Number(c) <= 0xffffffff) return Number(c).toString(16).toUpperCase().padStart(8, '0');
  return c;
}

/** Lo stesso codice in decimale a 10 cifre, come nel gestionale attuale (solo per i seriali di 4 byte). */
export function codiceDecimale(codice: string): string | null {
  return /^[0-9A-F]{8}$/.test(codice) ? String(parseInt(codice, 16)).padStart(10, '0') : null;
}

export function codiceValido(codice: string): boolean {
  return /^[0-9A-Z]{4,32}$/.test(codice);
}

interface LetturaNfc {
  serialNumber: string;
}
interface LettoreNfc {
  scan(o?: { signal?: AbortSignal }): Promise<void>;
  addEventListener(tipo: 'reading', f: (e: LetturaNfc) => void): void;
  addEventListener(tipo: 'readingerror', f: () => void): void;
}

export function nfcDisponibile(): boolean {
  return typeof window !== 'undefined' && 'NDEFReader' in window;
}

/**
 * Perché questo dispositivo non legge i braccialetti dal browser, in parole semplici (null se li legge). La Web NFC c'è
 * solo in Chrome per Android, e solo su pagine sicure: https:// oppure localhost. In sviluppo, dal telefono si apre
 * l'indirizzo del PC in rete (http://192.168…): Chrome lo accetta solo se lo si segna come sicuro (vedi README).
 */
export function motivoNfcAssente(ua = navigator.userAgent, sicura = window.isSecureContext): string | null {
  if (nfcDisponibile()) return null;
  if (!/Android/i.test(ua)) return "Questo dispositivo non legge l'NFC dal browser (serve Chrome su Android): scrivi il codice o usa il lettore.";
  if (!/Chrome\//.test(ua) || /SamsungBrowser|Firefox|EdgA|OPR\//.test(ua)) return "Per leggere i braccialetti apri il gestionale con Chrome: questo browser non legge l'NFC.";
  if (!sicura) return "Chrome legge l'NFC solo su indirizzi sicuri (https://): questo indirizzo non lo è. Vedi «Provare l'NFC dal telefono» nel README.";
  return "Chrome non dà accesso all'NFC: aggiornalo e controlla che l'NFC del telefono sia acceso.";
}

/**
 * Legge i braccialetti finché non si ferma con il segnale. Lo stesso codice letto di nuovo entro {@link pausaMs} si
 * ignora (il braccialetto appoggiato al telefono viene letto più volte di seguito).
 */
export async function leggiConNfc(suCodice: (codice: string) => void, segnale: AbortSignal, pausaMs = 2500): Promise<void> {
  const Lettore = (window as unknown as { NDEFReader: new () => LettoreNfc }).NDEFReader;
  const lettore = new Lettore();
  let ultimo = '';
  let quando = 0;
  lettore.addEventListener('reading', (e) => {
    const c = normalizzaCodice(e.serialNumber ?? '');
    if (!c) return;
    const ora = Date.now();
    if (c === ultimo && ora - quando < pausaMs) return;
    ultimo = c;
    quando = ora;
    suCodice(c);
  });
  await lettore.scan({ signal: segnale });
}
