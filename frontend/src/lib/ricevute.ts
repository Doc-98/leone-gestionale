import type { ImpostazioniRicevute, Ricevuta, RiparteSerie } from './api';
import { dataBreve, dataDi } from './date';
import { euro } from './listino';

/** Ricevute (ADR 0018, parte 10): i testi e le scelte condivise dal pannello dopo il pagamento, dalla scheda e dalle impostazioni. */

/** Il numero come uscirà: il formato con {stagione}, {anno} e {numero} (con le cifre, es. 0143). */
export function anteprimaNumero(formato: string, cifre: number, stagione: string, anno: number, numero: number): string {
  return formato.replaceAll('{stagione}', stagione).replaceAll('{anno}', String(anno)).replaceAll('{numero}', String(numero).padStart(Math.max(1, cifre), '0'));
}

/** I formati pronti tra cui scegliere (si può anche scriverne uno). */
export const FORMATI_PRONTI = ['{stagione}-{numero}', '{anno}/{numero}', '{numero}'];

export const RIPARTE: { id: RiparteSerie; nome: string }[] = [
  { id: 'STAGIONE', nome: 'Ogni stagione' },
  { id: 'ANNO', nome: 'Ogni anno' },
  { id: 'MAI', nome: 'Mai' },
];

/** Un genitore paga per più figli: si chiede se fare una ricevuta o una per figlio (solo se le impostazioni dicono «chiedi»). */
export function serveScelta(i: Pick<ImpostazioniRicevute, 'famiglia' | 'intestata'>, beneficiari: number): boolean {
  return beneficiari > 1 && i.famiglia === 'CHIEDI' && i.intestata === 'PAGANTE';
}

/** Per chi è e quanto: «Sofia Bianchi · 520,00 €», o «Sofia e Luca Bianchi · 2 righe · …». */
export function perChi(r: Pick<Ricevuta, 'beneficiari' | 'righe' | 'totale'>): string {
  const b = r.beneficiari;
  const nomi = b.length === 2 && b[0].cognome === b[1].cognome ? `${b[0].nome} e ${b[1].nome} ${b[0].cognome}`
    : b.map((x) => `${x.nome} ${x.cognome}`).join(', ');
  return [nomi, r.righe.length > 1 ? `${r.righe.length} righe` : null, euro(r.totale)].filter(Boolean).join(' · ');
}

/** Cosa è successo a una ricevuta, dal più importante: annullata (e rifatta), inviata, stampata, ancora da consegnare. */
export function statoRicevuta(r: Pick<Ricevuta, 'annullataIl' | 'motivoAnnullamento' | 'sostituitaDaNumero' | 'inviataIl' | 'inviataA' | 'stampataIl'>): {
  testo: string;
  tono: 'annullata' | 'fatto' | 'da';
} {
  if (r.annullataIl) {
    const rifatta = r.sostituitaDaNumero ? ` · rifatta come ${r.sostituitaDaNumero}` : '';
    return { testo: `Annullata il ${dataBreve(dataDi(r.annullataIl))}${r.motivoAnnullamento ? `: ${r.motivoAnnullamento}` : ''}${rifatta}`, tono: 'annullata' };
  }
  if (r.inviataIl) return { testo: `Inviata a ${r.inviataA} il ${dataBreve(dataDi(r.inviataIl))}`, tono: 'fatto' };
  if (r.stampataIl) return { testo: `Stampata il ${dataBreve(dataDi(r.stampataIl))}`, tono: 'fatto' };
  return { testo: 'Da consegnare', tono: 'da' };
}

/** Il nome di una ricevuta o fattura, con il numero: «Ricevuta 2026/27-0142». */
export const nomeRicevuta = (r: Pick<Ricevuta, 'tipo' | 'numero'>) => `${r.tipo === 'FATTURA' ? 'Fattura' : 'Ricevuta'} ${r.numero}`;
