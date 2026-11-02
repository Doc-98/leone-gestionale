import type { AccessoChip, MemoriaChip, StatoCreditiBraccialetto } from './api';
import { normalizzaCodice } from './nfc';

/**
 * Il lettore che legge e scrive la memoria dei braccialetti (ADR 0025), per i crediti di phon e docce quando le macchine
 * non sono collegate. Legge e scrive solo blocchi di byte: cosa vogliono dire lo sa il server (il formato della società).
 *
 * Oggi c'è soltanto il lettore simulato, con il formato di prova della demo: tiene la memoria dei braccialetti su questo
 * dispositivo. Il lettore vero del PC (PC/SC, nel programma per Windows) si aggiunge qui quando sapremo che chip sono e
 * con quali chiavi; dal telefono la Web NFC legge solo il seriale, non questa memoria.
 */
export interface LettoreChip {
  tipo: 'simulato' | 'pc';
  nome: string;
  /** Legge i blocchi che chiede il formato dal braccialetto appoggiato. */
  leggi(accesso: AccessoChip): Promise<MemoriaChip>;
  /** Scrive i blocchi sul braccialetto appoggiato, che deve essere quello col seriale indicato. */
  scrivi(uid: string, blocchi: Record<number, string>, accesso: AccessoChip): Promise<void>;
}

const ZERO = '0'.repeat(32);

/** La memoria di un braccialetto mai scritto: i blocchi richiesti, tutti a zero. */
export function memoriaVuota(uid: string, blocchi: number[]): MemoriaChip {
  return { uid, blocchi: Object.fromEntries(blocchi.map((b) => [b, ZERO])) };
}

/** La memoria dopo una scrittura: i blocchi scritti prendono il posto di quelli vecchi. */
export function applicaScrittura(m: MemoriaChip, scrivi: Record<number, string>): MemoriaChip {
  return { uid: m.uid, blocchi: { ...m.blocchi, ...scrivi } };
}

/** Dove il lettore simulato tiene le memorie: nel browser, o solo finché la pagina è aperta. */
export interface Deposito {
  leggi(uid: string): Record<number, string> | null;
  scrivi(uid: string, blocchi: Record<number, string>): void;
}

const inMemoria = new Map<string, Record<number, string>>();
export const depositoLocale: Deposito = {
  leggi(uid) {
    try {
      const v = localStorage.getItem(`leone:chip-simulato:${uid}`);
      if (v) return JSON.parse(v) as Record<number, string>;
    } catch {
      /* niente memoria del browser: quella della pagina */
    }
    return inMemoria.get(uid) ?? null;
  },
  scrivi(uid, blocchi) {
    inMemoria.set(uid, blocchi);
    try {
      localStorage.setItem(`leone:chip-simulato:${uid}`, JSON.stringify(blocchi));
    } catch {
      /* resta nella pagina */
    }
  },
};

/** Il lettore simulato: un braccialetto «appoggiato» alla volta, scelto a mano; la memoria resta nel deposito. */
export class LettoreSimulato implements LettoreChip {
  readonly tipo = 'simulato' as const;
  readonly nome = 'Lettore simulato';
  appoggiato = $state<string | null>(null);

  constructor(private deposito: Deposito = depositoLocale) {}

  appoggia(codice: string | null) {
    this.appoggiato = codice ? normalizzaCodice(codice) : null;
  }

  async leggi(accesso: AccessoChip): Promise<MemoriaChip> {
    const uid = this.appoggiato;
    if (!uid) throw new Error('Non c\'è nessun braccialetto sul lettore.');
    const salvati = this.deposito.leggi(uid) ?? {};
    return { uid, blocchi: Object.fromEntries(accesso.blocchi.map((b) => [b, salvati[b] ?? ZERO])) };
  }

  async scrivi(uid: string, blocchi: Record<number, string>, _accesso?: AccessoChip): Promise<void> {
    if (this.appoggiato !== uid) throw new Error('Sul lettore non c\'è più lo stesso braccialetto: la scrittura non è partita.');
    this.deposito.scrivi(uid, { ...(this.deposito.leggi(uid) ?? {}), ...blocchi });
  }
}

export const lettoreSimulato = new LettoreSimulato();

/**
 * Il lettore da usare, o perché non ce n'è uno: con il formato di prova quello simulato; con un formato vero servirà il
 * lettore del PC, non ancora collegato.
 */
export function lettoreChip(stato: StatoCreditiBraccialetto | null): { lettore: LettoreChip | null; perche: string } {
  if (!stato || !stato.sulBraccialetto) return { lettore: null, perche: 'I crediti stanno nel gestionale: non serve leggere il braccialetto.' };
  if (!stato.pronto) return { lettore: null, perche: stato.nota };
  if (!stato.accesso) return { lettore: null, perche: 'Per leggere la memoria del braccialetto serve il permesso «Borsellino e ricariche».' };
  if (stato.formato === 'PROVA') return { lettore: lettoreSimulato, perche: '' };
  return { lettore: null, perche: 'Il lettore del PC per la memoria dei braccialetti non è ancora collegato: si aggiunge nel programma per Windows.' };
}
