import { api, NonAutenticato, type Me, type Ruolo } from './api';

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
}

export const sessione = new Sessione();
