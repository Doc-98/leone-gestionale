<script lang="ts">
  import { api, messaggioErrore, STATI_RICHIESTA, TIPI_RICHIESTA, type Richiesta, type Turno } from '../lib/api';
  import { sessione } from '../lib/sessione.svelte';
  import { descrivi } from '../lib/richieste';

  let mie = $state<Richiesta[]>([]);
  let aperte = $state<Richiesta[]>([]);
  let caricamento = $state(true);
  let errore = $state('');
  let messaggio = $state('');
  let lavoro = $state<string | null>(null);
  /** I miei turni nei giorni delle sostituzioni aperte: per dire subito «sei già in turno» */
  let mieiTurni = $state<Turno[]>([]);

  const io = $derived(sessione.utente?.personaId);
  const rivolteAMe = $derived(mie.filter((r) => r.stato === 'IN_ATTESA_COLLEGA' && r.collega?.id === io));
  const mieRichieste = $derived(mie.filter((r) => r.richiedente.id === io));
  const altre = $derived(mie.filter((r) => r.richiedente.id !== io && !(r.stato === 'IN_ATTESA_COLLEGA' && r.collega?.id === io)));

  async function carica() {
    caricamento = true;
    try {
      [mie, aperte] = await Promise.all([api.richiesteMie(), api.richiesteAperte()]);
      const date = aperte.map((r) => r.turno.data).sort();
      mieiTurni = date.length ? await api.turniMiei(date[0], date.at(-1)!).catch(() => []) : [];
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a caricare le richieste.');
    } finally {
      caricamento = false;
    }
  }
  carica();

  async function azione(id: string, fn: () => Promise<unknown>, ok: string) {
    errore = messaggio = '';
    lavoro = id;
    try {
      await fn();
      messaggio = ok;
      await carica();
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      lavoro = null;
    }
  }
  /** Ho già un turno che si accavalla con quello da coprire? */
  const occupato = (r: Richiesta) =>
    mieiTurni.some((t) => t.stato === 'PIANIFICATO' && t.data === r.turno.data && t.inizio < r.turno.fine && t.fine > r.turno.inizio);
  const aperta = (r: Richiesta) => r.stato === 'IN_ATTESA_COLLEGA' || r.stato === 'IN_ATTESA_APPROVAZIONE';
</script>

{#snippet scheda(r: Richiesta)}
  {@const d = descrivi(r, io)}
  <div class="card richiesta">
    <div class="testa">
      <span class="tag t-soft">{TIPI_RICHIESTA[r.tipo]}</span>
      <span class="tag" class:t-warn={aperta(r)}>{STATI_RICHIESTA[r.stato]}</span>
    </div>
    <div class="titolo">{d.titolo}</div>
    <div class="muted small">{d.dettaglio}</div>
    {#if r.messaggio}<div class="small citazione">«{r.messaggio}»</div>{/if}
    {#if r.notaDecisione}<div class="small muted">Nota: {r.notaDecisione}</div>{/if}
  </div>
{/snippet}

<section class="page">
  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

  {#if rivolteAMe.length}
    <h2 class="lbl">Rivolte a te</h2>
    {#each rivolteAMe as r (r.id)}
      <div class="gruppo-azioni">
        {@render scheda(r)}
        <div class="row">
          <button class="btn btn-primary grow" disabled={lavoro === r.id} onclick={() => azione(r.id, () => api.accetta(r.id), 'Accettata: ora la deve approvare il coordinatore.')}>Accetto</button>
          <button class="btn btn-secondary grow" disabled={lavoro === r.id} onclick={() => azione(r.id, () => api.rifiuta(r.id), 'Richiesta rifiutata.')}>Non posso</button>
        </div>
      </div>
    {/each}
  {/if}

  <h2 class="lbl">Turni da coprire</h2>
  {#each aperte as r (r.id)}
    <div class="gruppo-azioni">
      {@render scheda(r)}
      {#if occupato(r)}
        <button class="btn btn-secondary" disabled>Sei già in turno in quell'orario</button>
      {:else}
        <button class="btn btn-accent" disabled={lavoro === r.id} onclick={() => azione(r.id, () => api.accetta(r.id), 'Grazie! Ora la deve approvare il coordinatore.')}>Lo copro io</button>
      {/if}
    </div>
  {:else}
    <p class="muted small nomargin">{caricamento ? '…' : 'Nessun collega cerca un sostituto.'}</p>
  {/each}

  <h2 class="lbl">Le tue richieste</h2>
  {#each mieRichieste as r (r.id)}
    <div class="gruppo-azioni">
      {@render scheda(r)}
      {#if aperta(r)}
        <button class="btn btn-secondary" disabled={lavoro === r.id} onclick={() => azione(r.id, () => api.annullaRichiesta(r.id), 'Richiesta annullata.')}>Annulla richiesta</button>
      {/if}
    </div>
  {:else}
    <p class="muted small nomargin">{caricamento ? '…' : 'Nessuna richiesta. Per farne una, tocca un tuo turno.'}</p>
  {/each}

  {#if altre.length}
    <h2 class="lbl">Dove sei coinvolto</h2>
    {#each altre as r (r.id)}{@render scheda(r)}{/each}
  {/if}
</section>

<style>
  h2 { margin: var(--space-8) 0 calc(-1 * var(--space-6)); }
  .gruppo-azioni { display: flex; flex-direction: column; gap: var(--space-8); }
  .richiesta { padding: var(--space-12) var(--space-14); display: flex; flex-direction: column; gap: var(--space-4); }
  .testa { display: flex; gap: var(--space-6); flex-wrap: wrap; margin-bottom: var(--space-2); }
  .titolo { font-weight: 600; }
  .citazione { font-style: italic; }
</style>
