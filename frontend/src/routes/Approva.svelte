<script lang="ts">
  import { api, messaggioErrore, TIPI_RICHIESTA, type Candidato, type Richiesta } from '../lib/api';
  import { descrivi } from '../lib/richieste';

  /**
   * Le richieste per il coordinatore (ADR 0022). Scambi e cambi d'orario si approvano o si respingono. Una sostituzione
   * no: chi la chiede ha avvisato che sarà assente. Si conferma chi l'ha presa, la si propone a un collega preciso
   * (accettando è già approvata) o, se nessuno può, si lascia il turno scoperto.
   */
  let { cambiate }: { cambiate?: (n: number) => void } = $props();
  let richieste = $state<Richiesta[]>([]);
  let note = $state<Record<string, string>>({});
  let caricamento = $state(true);
  let errore = $state('');
  let messaggio = $state('');
  let lavoro = $state<string | null>(null);
  // la scelta del collega a cui proporre una sostituzione, una richiesta alla volta
  let proposta = $state<{ id: string; candidati: Candidato[] | null; scelto: string; occupati: boolean } | null>(null);

  const daDecidere = $derived(richieste.filter((r) => r.stato === 'IN_ATTESA_APPROVAZIONE'));
  const daCoprire = $derived(richieste.filter((r) => r.stato === 'IN_ATTESA_COLLEGA' && r.tipo === 'SOSTITUZIONE')
    .sort((a, b) => a.turno.data.localeCompare(b.turno.data) || a.turno.inizio.localeCompare(b.turno.inizio)));

  async function carica() {
    caricamento = true;
    try {
      richieste = await api.richiesteDaGestire();
      cambiate?.(richieste.filter((r) => r.stato === 'IN_ATTESA_APPROVAZIONE').length);
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      caricamento = false;
    }
  }
  carica();

  async function esegui(r: Richiesta, fai: () => Promise<unknown>, ok: string) {
    errore = messaggio = '';
    lavoro = r.id;
    try {
      await fai();
      messaggio = ok;
      proposta = null;
      await carica();
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      lavoro = null;
    }
  }

  async function apriProposta(r: Richiesta) {
    if (proposta?.id === r.id) { proposta = null; return; }
    proposta = { id: r.id, candidati: null, scelto: '', occupati: false };
    try {
      const c = await api.candidati(r.id);
      if (proposta?.id === r.id) proposta.candidati = c.filter((x) => x.persona.id !== r.collega?.id);
    } catch (e) {
      errore = messaggioErrore(e);
      proposta = null;
    }
  }

  function statoSostituzione(r: Richiesta): string {
    if (!r.collega) return 'Aperta a tutti: nessuno l’ha ancora presa.';
    return r.propostaDa ? `Proposta a ${r.collega.nome}: aspetta la risposta.` : `Chiesta a ${r.collega.nome}: aspetta la risposta.`;
  }
</script>

{#snippet scelta(r: Richiesta)}
  {#if proposta?.id === r.id}
    <div class="proposta">
      {#if !proposta.candidati}
        <span class="muted small">…</span>
      {:else if !proposta.candidati.length}
        <span class="muted small">Nessun altro collega in questo impianto.</span>
      {:else}
        <span class="lbl">A chi la proponi?</span>
        <div class="candidati">
          {#each proposta.candidati.filter((c) => proposta!.occupati || !c.occupato) as c (c.persona.id)}
            <label class="candidato" class:occupato={!!c.occupato} title={c.occupato ? `In turno ${c.occupato}` : undefined}>
              <input type="radio" name="collega-{r.id}" value={c.persona.id} bind:group={proposta.scelto} disabled={!!c.occupato} />
              <span class="nome">{c.persona.nome}</span>
              <span class="small {c.occupato ? 'muted' : 'ok'}">{c.occupato ? `in turno ${c.occupato.split(' · ')[0]}` : 'libero'}</span>
            </label>
          {/each}
        </div>
        {@const occupati = proposta.candidati.filter((c) => c.occupato).length}
        {#if occupati}
          <button type="button" class="btn btn-quieto btn-sm sx" onclick={() => (proposta!.occupati = !proposta!.occupati)}>
            {proposta.occupati ? 'Nascondi chi è già in turno' : `Mostra anche chi è già in turno (${occupati})`}</button>
        {/if}
        <span class="muted small">Se accetta, la sostituzione è già confermata. Se non può, torna aperta a tutti.</span>
        <div class="row">
          <button class="btn btn-secondary grow" onclick={() => (proposta = null)}>Lascia stare</button>
          <button class="btn btn-primary grow" disabled={!proposta.scelto || lavoro === r.id}
            onclick={() => esegui(r, () => api.proponi(r.id, proposta!.scelto), 'Proposta inviata: aspetta la sua risposta.')}>Proponi</button>
        </div>
      {/if}
    </div>
  {/if}
{/snippet}

<section class="page">
  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

  <h2 class="lbl">Da decidere</h2>
  {#each daDecidere as r (r.id)}
    {@const d = descrivi(r)}
    <div class="card richiesta">
      <span class="tag t-soft">{TIPI_RICHIESTA[r.tipo]}</span>
      <div class="titolo">{d.titolo}</div>
      <div class="muted small">{d.dettaglio}</div>
      {#if r.messaggio}<div class="small citazione">«{r.messaggio}»</div>{/if}
      <input class="inp" placeholder="Nota per lo staff (facoltativa)" bind:value={note[r.id]} maxlength="500" />
      {#if r.tipo === 'SOSTITUZIONE'}
        <div class="row">
          <button class="btn btn-primary grow" disabled={lavoro === r.id}
            onclick={() => esegui(r, () => api.approva(r.id, note[r.id]), 'Confermata: il turno è passato al sostituto.')}>Conferma {r.collega?.nome.split(' ')[0] ?? ''}</button>
          <button class="btn btn-secondary grow" disabled={lavoro === r.id} aria-expanded={proposta?.id === r.id} onclick={() => apriProposta(r)}>Proponi a un altro</button>
        </div>
        {@render scelta(r)}
      {:else}
        <div class="row">
          <button class="btn btn-primary grow" disabled={lavoro === r.id}
            onclick={() => esegui(r, () => api.approva(r.id, note[r.id]), 'Approvata: i turni sono aggiornati.')}>Approva</button>
          <button class="btn btn-secondary grow" disabled={lavoro === r.id}
            onclick={() => esegui(r, () => api.respingi(r.id, note[r.id]), 'Richiesta respinta.')}>Respingi</button>
        </div>
      {/if}
    </div>
  {:else}
    <p class="muted vuoto">{caricamento ? '…' : 'Nessuna richiesta da decidere.'}</p>
  {/each}

  {#if daCoprire.length}
    <h2 class="lbl">Sostituzioni ancora da coprire</h2>
    <p class="muted small spiega">Chi le ha chieste sarà assente: non si respingono. Proponile a un collega libero o, se nessuno può, lascia il turno scoperto.</p>
    {#each daCoprire as r (r.id)}
      {@const d = descrivi(r)}
      <div class="card richiesta">
        <div class="titolo">{r.richiedente.nome} sarà assente</div>
        <div class="muted small">{d.dettaglio}</div>
        {#if r.messaggio}<div class="small citazione">«{r.messaggio}»</div>{/if}
        <div class="small warn">{statoSostituzione(r)}</div>
        <div class="row">
          <button class="btn btn-primary grow" disabled={lavoro === r.id} aria-expanded={proposta?.id === r.id}
            onclick={() => apriProposta(r)}>{r.collega ? 'Proponi a un altro' : 'Proponi a…'}</button>
          <button class="btn btn-secondary grow" disabled={lavoro === r.id}
            onclick={() => esegui(r, () => api.senzaSostituto(r.id), 'Il turno resta da assegnare: lo trovi scoperto nella pianificazione.')}>Lascia scoperto</button>
        </div>
        {@render scelta(r)}
      </div>
    {/each}
  {/if}
</section>

<style>
  h2.lbl { margin: var(--space-8) 0 0; }
  .richiesta { padding: var(--space-14); display: flex; flex-direction: column; gap: var(--space-8); align-items: flex-start; }
  .richiesta .inp, .richiesta .row, .proposta { align-self: stretch; }
  .titolo { font-weight: 600; }
  .citazione { font-style: italic; }
  .warn { color: var(--warn); font-weight: 600; }
  .ok { color: var(--ok); }
  .spiega { margin: 0; }
  .vuoto { margin: var(--space-4) 0 var(--space-10); }
  .proposta { display: flex; flex-direction: column; gap: var(--space-8); padding-top: var(--space-10); border-top: 1px solid var(--line-soft); }
  .candidati { display: flex; flex-direction: column; border: 1px solid var(--line); border-radius: var(--radius-md); max-height: 360px; overflow-y: auto; }
  .sx { align-self: flex-start; }
  .nome { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .candidato { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-10) var(--space-12); min-height: 44px; box-sizing: border-box;
    border-top: 1px solid var(--line-soft); cursor: pointer; font-size: var(--text-md); }
  .candidato:first-child { border-top: none; }
  .candidato.occupato { cursor: default; opacity: 0.6; }
  .candidato span:last-child { white-space: nowrap; }
</style>
