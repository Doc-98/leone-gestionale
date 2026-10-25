import { sessione } from './sessione.svelte';

/**
 * Le scelte di vista (quale vista del piano vasca, quale vasca…) restano su questo dispositivo, per ogni account:
 * chi cambia vista la ritrova la volta dopo. Senza memoria del browser (finestra privata) si riparte dal predefinito.
 */
const chiaveDi = (nome: string) => `leone:${sessione.utente?.accountId ?? 'anonimo'}:${nome}`;

export function leggiPreferenza<T extends string>(nome: string, predefinito: T, ammessi?: readonly T[]): T {
  try {
    const v = localStorage.getItem(chiaveDi(nome));
    if (v != null && (!ammessi || ammessi.includes(v as T))) return v as T;
  } catch {
    /* niente memoria: il predefinito */
  }
  return predefinito;
}

export function salvaPreferenza(nome: string, valore: string) {
  try {
    localStorage.setItem(chiaveDi(nome), valore);
  } catch {
    /* niente memoria: vale solo per questa volta */
  }
}
