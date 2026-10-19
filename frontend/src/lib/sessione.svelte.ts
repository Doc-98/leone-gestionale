import { api, NonAutenticato, type Me, type Permesso, type Ruolo } from './api';

/** Chi è collegato. «pronto» diventa vero dopo il primo controllo della sessione. */
class Sessione {
  utente = $state<Me | null>(null);
  pronto = $state(false);

  async carica() {
    try {
      this.utente = await api.me();
    } catch (e) {
      if (!(e instanceof NonAutenticato)) console.error(e);
      this.utente = null;
    } finally {
      this.pronto = true;
    }
  }

  async entra(email: string, password: string) {
    this.utente = await api.login(email, password);
  }

  async esci() {
    try {
      await api.logout();
    } finally {
      this.utente = null;
    }
  }

  ha(...ruoli: Ruolo[]): boolean {
    return !!this.utente?.ruoli.some((r) => ruoli.includes(r.ruolo));
  }

  /** Ha almeno uno dei permessi (in almeno un impianto; il server controlla l'impianto). */
  puo(...permessi: Permesso[]): boolean {
    return !!this.utente?.permessi?.some((p) => permessi.includes(p));
  }

  /** Aggiorna ruoli e permessi senza rifare il login (es. dopo averli cambiati). */
  async aggiorna() {
    try {
      this.utente = await api.me();
    } catch {
      /* resta com'è */
    }
  }
}

export const sessione = new Sessione();
