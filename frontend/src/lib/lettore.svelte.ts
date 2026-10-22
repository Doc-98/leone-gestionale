import { api, ErroreApi, type AbbinamentoLettore } from './api';

/**
 * Il telefono come lettore NFC di questo PC (ADR 0017). L'abbinamento resta attivo cambiando pagina (e ricaricando,
 * finché la scheda del browser è aperta); i codici letti dal telefono vanno alla pagina che li sta ascoltando
 * (il tornello, oppure «Assegna un braccialetto» nella scheda del cliente).
 */
class LettoreTelefono {
  abbinamento = $state<AbbinamentoLettore | null>(null);
  /** quando il telefono si è fatto vivo l'ultima volta */
  telefonoVisto = $state<string | null>(null);
  ultimo = $state<{ codice: string; quando: number } | null>(null);
  errore = $state('');

  private dopo = 0;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private gestori: ((codice: string) => void)[] = [];

  constructor() {
    try {
      const salvato = sessionStorage.getItem('lettore-telefono');
      if (salvato) {
        const a = JSON.parse(salvato) as AbbinamentoLettore & { dopo?: number };
        this.abbinamento = a;
        this.dopo = a.dopo ?? 0;
        this.avvia();
      }
    } catch {
      /* niente memoria della scheda: si rifà l'abbinamento */
    }
  }

  /** Il telefono è collegato se si è fatto vivo nell'ultimo minuto (si presenta ogni 20 secondi). */
  get collegato(): boolean {
    return !!this.telefonoVisto && Date.now() - Date.parse(this.telefonoVisto) < 60_000;
  }

  /** La pagina riceve i codici letti finché non chiama la funzione restituita. Vince l'ultima che si è messa in ascolto. */
  ascolta(gestore: (codice: string) => void): () => void {
    this.gestori.push(gestore);
    return () => {
      this.gestori = this.gestori.filter((g) => g !== gestore);
    };
  }

  async abbina() {
    this.errore = '';
    try {
      this.abbinamento = await api.abbinaLettore();
      this.dopo = 0;
      this.telefonoVisto = null;
      this.ultimo = null;
      this.salva();
      this.avvia();
    } catch (e) {
      this.errore = e instanceof ErroreApi ? e.message : 'Non riesco a preparare l\'abbinamento.';
    }
  }

  async scollega() {
    const a = this.abbinamento;
    this.ferma();
    if (a) await api.scollegaLettore(a.id).catch(() => {});
  }

  private ferma() {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    this.abbinamento = null;
    this.telefonoVisto = null;
    try {
      sessionStorage.removeItem('lettore-telefono');
    } catch {
      /* nessuna memoria */
    }
  }

  private salva() {
    try {
      if (this.abbinamento) sessionStorage.setItem('lettore-telefono', JSON.stringify({ ...this.abbinamento, dopo: this.dopo }));
    } catch {
      /* nessuna memoria */
    }
  }

  private avvia() {
    if (this.timer) clearTimeout(this.timer);
    const giro = async () => {
      const a = this.abbinamento;
      if (!a) return;
      try {
        const r = await api.lettureLettore(a.id, this.dopo);
        if (this.abbinamento?.id !== a.id) return;
        this.telefonoVisto = r.telefonoVistoIl;
        this.errore = '';
        for (const l of r.letture) {
          this.dopo = Math.max(this.dopo, l.numero);
          this.ultimo = { codice: l.codice, quando: Date.now() };
          this.gestori.at(-1)?.(l.codice);
        }
        if (r.letture.length) this.salva();
      } catch (e) {
        if (e instanceof ErroreApi && e.status === 404) {
          this.ferma();
          this.errore = 'L\'abbinamento con il telefono è scaduto: rifallo.';
          return;
        }
        this.errore = 'Non riesco a sentire il server: riprovo.';
      }
      this.timer = setTimeout(giro, document.hidden ? 3000 : 700);
    };
    giro();
  }
}

export const lettoreTelefono = new LettoreTelefono();

/**
 * L'indirizzo da aprire sul telefono. Se il PC usa «localhost» (sviluppo, o il programma per Windows collegato a
 * localhost) il telefono non lo raggiungerebbe: si usa l'indirizzo del PC in rete, che Vite conosce.
 */
export function indirizzoPerTelefono(origine = location.origin, rete: string[] = __INDIRIZZI_RETE__): { url: string; localhost: boolean; sviluppo: boolean } {
  const u = new URL(origine);
  const locale = ['localhost', '127.0.0.1', '[::1]'].includes(u.hostname);
  if (locale && rete.length) u.hostname = rete[0];
  return { url: u.origin, localhost: locale && !rete.length, sviluppo: locale };
}
