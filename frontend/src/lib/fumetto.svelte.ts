/**
 * Quale fumetto «i» è aperto. Ne sta aperto uno solo: aprirne uno chiude quello di prima.
 * Ogni `Fumetto` ha un id suo; qui si tiene solo l'id di quello aperto.
 */
class Fumetti {
  aperto = $state<string | null>(null);

  apri(id: string): void {
    this.aperto = id;
  }

  /** Chiude il fumetto `id`; senza `id` chiude quello aperto, qualunque sia. Un altro fumetto non si tocca. */
  chiudi(id?: string): void {
    if (id === undefined || this.aperto === id) this.aperto = null;
  }

  commuta(id: string): void {
    this.aperto = this.aperto === id ? null : id;
  }
}

export const fumetti = new Fumetti();
