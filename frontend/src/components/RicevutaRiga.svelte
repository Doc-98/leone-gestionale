<script lang="ts">
  import Icona from './Icona.svelte';
  import { api, messaggioErrore, type Ricevuta } from '../lib/api';
  import { nomeRicevuta, perChi, statoRicevuta } from '../lib/ricevute';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Una ricevuta con quello che ci si fa: stampa (apre il PDF), PDF da scaricare, email a chi paga (o a un altro
   * indirizzo) e, con il permesso, annullare (e rifarla subito). «cambiata» avvisa chi la mostra.
   */
  let { ricevuta, cambiata, compatta = false }: { ricevuta: Ricevuta; cambiata?: (nuove?: Ricevuta[]) => void; compatta?: boolean } = $props();

  let errore = $state('');
  let lavoro = $state(false);
  let altraEmail = $state<string | null>(null);
  let annulla = $state<{ motivo: string; rifai: boolean } | null>(null);
  const r = $derived(ricevuta);
  const stato = $derived(statoRicevuta(r));
  const attiva = $derived(!r.annullataIl);
  const puoPagare = $derived(sessione.puo('PAGAMENTI'));
  const puoAnnullare = $derived(sessione.puo('PAGAMENTI_ANNULLA'));

  async function fai(azione: () => Promise<Ricevuta[] | Ricevuta | void>) {
    errore = '';
    lavoro = true;
    try {
      const esito = await azione();
      cambiata?.(Array.isArray(esito) ? esito : undefined);
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      lavoro = false;
    }
  }

  function stampata() {
    // il PDF si apre in un'altra scheda (lì si stampa); qui resta segnato
    if (attiva && puoPagare && !r.stampataIl) void fai(() => api.ricevutaStampata(r.id));
  }

  async function invia(email?: string) {
    await fai(async () => {
      const e = await api.inviaRicevuta(r.id, email);
      if (e.stato !== 'INVIATA') throw new Error(e.errore ?? 'Email non partita.');
      altraEmail = null;
    });
  }
</script>

<div class="ric" class:annullata={!attiva} class:compatta>
  <span class="ico-r" aria-hidden="true"><Icona nome="ricevuta" /></span>
  <span class="t">
    <b class="m num">{nomeRicevuta(r)}</b>
    <span>{perChi(r)}</span>
    <span class="stato {stato.tono}">{stato.testo}</span>
  </span>
  <span class="azioni-r">
    <a class="btn btn-secondary btn-sm" href={api.urlPdfRicevuta(r.id)} target="_blank" rel="noopener" onclick={stampata}><Icona nome="stampa" />Stampa</a>
    <a class="btn btn-secondary btn-sm" href={api.urlPdfRicevuta(r.id, true)} download><Icona nome="scarica" />PDF</a>
    {#if attiva && puoPagare}
      {#if r.intestatario.email}
        <button type="button" class="btn btn-sm {r.inviataIl ? 'btn-secondary' : 'btn-primary'}" disabled={lavoro} onclick={() => invia()}
          title={`Manda a ${r.intestatario.email}`}><Icona nome="promemoria" />{r.inviataIl ? 'Rimanda' : 'Manda per email'}</button>
      {:else if altraEmail === null}
        <button type="button" class="btn btn-secondary btn-sm" onclick={() => (altraEmail = '')} title="Nella scheda di chi paga non c'è l'email"><Icona nome="promemoria" />Email…</button>
      {/if}
      {#if puoAnnullare && !annulla}
        <button type="button" class="mini-x" aria-label={`Annulla la ${nomeRicevuta(r).toLowerCase()}`} onclick={() => (annulla = { motivo: '', rifai: true })}><Icona nome="annulla" /></button>
      {/if}
    {/if}
  </span>
  {#if altraEmail !== null}
    <form class="sotto" onsubmit={(e) => { e.preventDefault(); invia(altraEmail!.trim()); }}>
      <input class="inp" type="email" bind:value={altraEmail} placeholder="Indirizzo email" aria-label="Indirizzo email a cui mandarla" required />
      <button class="btn btn-primary btn-sm" disabled={lavoro || !altraEmail.includes('@')}>Manda</button>
      <button type="button" class="btn btn-secondary btn-sm" onclick={() => (altraEmail = null)}>Annulla</button>
    </form>
  {/if}
  {#if annulla}
    <form class="sotto annulla" onsubmit={(e) => { e.preventDefault(); fai(() => api.annullaRicevuta(r.id, annulla!.motivo.trim(), annulla!.rifai)).then(() => (annulla = null)); }}>
      <b>Annullare la {nomeRicevuta(r).toLowerCase()}?</b>
      <span class="small">Il numero resta usato e la ricevuta resta, barrata, con il motivo. Il pagamento non cambia.</span>
      <input class="inp" bind:value={annulla.motivo} placeholder="Perché (es. intestata al genitore sbagliato)" maxlength="300" aria-label="Motivo dell'annullamento" />
      <label class="small spunta"><input type="checkbox" bind:checked={annulla.rifai} /> Rifalla subito con i dati di adesso (prende il numero successivo)</label>
      <span class="riga">
        <button type="button" class="btn btn-secondary btn-sm" onclick={() => (annulla = null)}>Lascia com'è</button>
        <button class="btn btn-danger btn-sm" disabled={lavoro || !annulla.motivo.trim()}>{annulla.rifai ? 'Annulla e rifai' : 'Annulla'}</button>
      </span>
    </form>
  {/if}
  {#if errore}<p class="msg err nomargin sotto" role="alert">{errore}</p>{/if}
</div>

<style>
  .ric { display: flex; align-items: center; gap: var(--space-12); flex-wrap: wrap; padding: var(--space-12) 0; }
  .ico-r { width: 36px; height: 44px; border-radius: 6px; border: 1.5px solid var(--line-strong); display: grid; place-items: center; color: var(--seg-fg); flex-shrink: 0; background: var(--surface); }
  .compatta .ico-r { display: none; }
  .t { flex: 1 1 240px; min-width: 0; display: flex; flex-direction: column; gap: 1px; font-size: var(--text-sm); }
  .t > span { color: var(--muted); }
  .num { font-size: var(--text-base); font-weight: 500; color: var(--ink); }
  .annullata .num { text-decoration: line-through; color: var(--muted); }
  .stato.fatto { color: var(--passa-fg); font-weight: 600; }
  .stato.annullata { color: var(--ferma-fg); }
  .azioni-r { display: flex; gap: var(--space-6); flex-wrap: wrap; align-items: center; }
  .azioni-r a.btn { text-decoration: none; display: inline-flex; align-items: center; }
  .mini-x { position: relative; width: 36px; height: 36px; border-radius: 50%; border: none; background: none; color: var(--muted); cursor: pointer; display: grid; place-items: center; }
  .mini-x::after { content: ''; position: absolute; inset: -4px; }
  .mini-x:hover { background: var(--surface-sunken); color: var(--danger, var(--ferma)); }
  .sotto { flex-basis: 100%; display: flex; gap: var(--space-8); flex-wrap: wrap; align-items: center; }
  .sotto .inp { flex: 1 1 240px; height: 40px; }
  .annulla { flex-direction: column; align-items: stretch; padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); background: var(--ferma-bg); color: var(--ferma-fg); }
  .annulla .inp { flex: none; }
  .riga { display: flex; gap: var(--space-8); justify-content: flex-end; flex-wrap: wrap; }
  .spunta { display: flex; align-items: center; gap: var(--space-6); color: var(--ink); }
</style>
