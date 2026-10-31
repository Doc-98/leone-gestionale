<script lang="ts">
  import { untrack } from 'svelte';
  import { api, messaggioErrore, type PaginaAttesa, type TurnoPosti, type VoceAttesa } from '../../lib/api';
  import { testoAvviso } from '../../lib/attesa';

  /**
   * L'avviso a chi tocca un posto: l'email già scritta, da cambiare se serve, oppure «Avvisato a voce» per chi telefona.
   * In tutti e due i casi parte il conto dei giorni per rispondere (impostazione della società).
   */
  let { voce, turno, pagina, onfatto, onchiudi }: {
    voce: VoceAttesa;
    turno: TurnoPosti;
    pagina: PaginaAttesa;
    onfatto: (messaggio: string) => void;
    onchiudi: () => void;
  } = $props();

  // il testo di partenza si scrive una volta: poi è di chi lo modifica (il modulo si ricrea per ogni persona)
  const iniziale = untrack(() => testoAvviso(voce, turno, pagina));
  let oggetto = $state(iniziale.oggetto);
  let testo = $state(iniziale.testo);
  let errore = $state('');
  let invio = $state(false);
  const chi = $derived(`${voce.nome} ${voce.cognome}`);

  async function invia() {
    errore = '';
    invio = true;
    try {
      const r = await api.avvisaAttesa(voce.id, { oggetto, testo });
      onfatto(r.stato === 'INVIATA' ? `Avviso inviato a ${voce.contatti.join(', ')}.`
        : r.stato === 'NON_INVIATA' ? 'Avviso registrato, ma non partito: il server di posta non è configurato. Telefona alla famiglia.'
        : r.stato === 'SENZA_EMAIL' ? `${chi} non ha un'email (né un tutore che riceve le comunicazioni): telefona e segna «Avvisato a voce».`
        : `Invio non riuscito: ${r.errore ?? 'errore del server di posta'}.`);
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      invio = false;
    }
  }

  async function aVoce() {
    errore = '';
    invio = true;
    try {
      await api.avvisataAVoce(voce.id);
      onfatto(`${chi}: avviso a voce segnato, ha ${pagina.giorniRisposta} giorni per rispondere.`);
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      invio = false;
    }
  }
</script>

<form class="card avviso" onsubmit={(e) => { e.preventDefault(); invia(); }}>
  <div class="testa"><h2>Avviso a {chi}</h2><span class="grow"></span><button type="button" class="btn btn-quieto btn-sm" onclick={onchiudi}>Chiudi</button></div>
  <span class="muted small">{voce.contatti.length ? `a ${voce.contatti.join(', ')}` : 'Nessun indirizzo email: telefona e segna «Avvisato a voce».'}</span>
  <label class="field"><span class="lbl">Oggetto</span><input class="inp" bind:value={oggetto} /></label>
  <label class="field"><span class="lbl">Testo</span><textarea class="inp testo" rows="9" bind:value={testo}></textarea></label>
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
  <div class="bottoni">
    <button type="button" class="btn btn-secondary" disabled={invio} onclick={aVoce}>Avvisato a voce</button>
    <button class="btn btn-primary" disabled={invio || !voce.contatti.length || !oggetto.trim() || !testo.trim()}>Invia l'avviso</button>
  </div>
</form>

<style>
  .avviso { padding: var(--space-16) var(--space-16); display: flex; flex-direction: column; gap: var(--space-12); }
  .testa { display: flex; align-items: center; }
  h2 { margin: 0; font-size: var(--text-lg); }
  .testo { min-height: 190px; font-family: var(--font-body); line-height: 1.45; resize: vertical; }
  .bottoni { display: flex; gap: var(--space-8); }
  .bottoni .btn { flex: 1; }
  .bottoni .btn-primary { flex: 1.4; }
</style>
