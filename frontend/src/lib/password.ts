/** I requisiti della password nuova, da spuntare mentre si scrive (il server chiede almeno 10 caratteri). */
export function requisitiPassword(attuale: string, nuova: string, ripeti: string, temporanea: boolean): { testo: string; ok: boolean }[] {
  return [
    { testo: 'Almeno 10 caratteri', ok: nuova.length >= 10 },
    { testo: temporanea ? 'Diversa da quella temporanea' : 'Diversa da quella attuale', ok: nuova.length > 0 && nuova !== attuale },
    { testo: 'Le due password sono uguali', ok: ripeti.length > 0 && nuova === ripeti },
  ];
}

/** Quanto è robusta, in tre livelli, solo dalla lunghezza: una frase lunga batte un codice corto e complicato. */
export function forzaPassword(p: string): { livello: 0 | 1 | 2 | 3; testo: string } {
  const n = p.length;
  if (n === 0) return { livello: 0, testo: 'Meglio una frase che ricordi facilmente, ad esempio «tuffo del martedì alle sette».' };
  if (n < 10) return { livello: 1, testo: 'Ancora corta: servono almeno 10 caratteri.' };
  if (n < 16) return { livello: 2, testo: 'Va bene. Una frase più lunga è ancora meglio.' };
  return { livello: 3, testo: 'Ottima: lunga e facile da ricordare.' };
}
