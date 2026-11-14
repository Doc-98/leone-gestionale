/**
 * La conferma in due tocchi (design system, «Azioni»): il primo tocco arma il pulsante, che mostra una domanda corta
 * («Elimina?»); il secondo esegue. Si disarma da solo dopo 5 secondi o quando il pulsante perde il focus.
 * La usano `Azione.svelte` (pulsanti-icona) e `Conferma.svelte` (pulsanti con testo).
 */
export const SCADENZA_CONFERMA_MS = 5000;

export interface ConfermaDueTocchi {
  /** Un tocco: restituisce `true` quando è il secondo e l'azione va eseguita, `false` quando ha solo armato. */
  tocca(): boolean;
  /** Torna a riposo (uscita dal pulsante, scadenza, azione eseguita). */
  disarma(): void;
}

/** `cambia` riceve ogni cambio di stato (armata sì/no): lo stato visibile sta nel componente. */
export function creaConferma(cambia: (armata: boolean) => void, ms = SCADENZA_CONFERMA_MS): ConfermaDueTocchi {
  let armata = false;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const imposta = (v: boolean) => {
    if (armata !== v) {
      armata = v;
      cambia(v);
    }
  };
  const disarma = () => {
    clearTimeout(timer);
    timer = undefined;
    imposta(false);
  };
  return {
    tocca() {
      if (!armata) {
        imposta(true);
        clearTimeout(timer);
        timer = setTimeout(disarma, ms);
        return false;
      }
      disarma();
      return true;
    },
    disarma,
  };
}

/** Il nome per lo screen reader del pulsante armato (uguale in `Azione` e `Conferma`). */
export const etichettaArmata = (domanda: string) => `${domanda} (tocca di nuovo per confermare)`;
