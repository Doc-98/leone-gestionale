<script lang="ts">
  import AzioneIcona from './Azione.svelte';
  import Icona from './Icona.svelte';
  import { untrack } from 'svelte';
  import { api, messaggioErrore, type MetodoPagamento, type SchedaBorsellino, type Utilizzo } from '../lib/api';
  import { dataBreve, dataDi, oraDi } from '../lib/date';
  import { euro } from '../lib/listino';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Il borsellino di un cliente (ADR 0018, parti 7–9): credito in euro (rimborsi, si usa per pagare, può scadere, si
   * trasferisce ai familiari) e crediti di phon e docce, che si ricaricano qui senza bisogno del braccialetto in mano.
   */
  let { personaId, versione = 0, cambiato }: { personaId: string; versione?: number; cambiato?: () => void } = $props();
  const id = untrack(() => personaId);

  let dati = $state<SchedaBorsellino | null>(null);
  let utilizzi = $state<Utilizzo[]>([]);
  let metodi = $state<MetodoPagamento[]>([]);
  let errore = $state('');
  let messaggio = $state('');
  let tutti = $state(false);

  const puoRicaricare = $derived(sessione.puo('BORSELLINO'));
  const puoRimborsare = $derived(sessione.puo('BORSELLINO_RIMBORSI'));

  async function carica() {
    try {
      [dati, utilizzi] = await Promise.all([api.borsellino(id), api.utilizzi({ personaId: id, quanti: 10 }).catch(() => [])]);
      if (daAprire) {
        daAprire = false;
        apriRicarica();
      }
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a caricare il borsellino.');
    }
  }
  $effect(() => {
    void versione;
    untrack(carica);
  });
  api.metodiPagamento().then((m) => (metodi = m.filter((x) => x.attivo))).catch(() => {});
  const perPagare = $derived(metodi.filter((m) => !m.daIncassare));
  const perRestituire = $derived(metodi.filter((m) => !m.borsellino && !m.daIncassare));

  async function esegui(azione: () => Promise<unknown>, fatto: string) {
    errore = '';
    messaggio = '';
    try {
      await azione();
      await carica();
      messaggio = fatto;
      cambiato?.();
      return true;
    } catch (e) {
      errore = messaggioErrore(e);
      return false;
    }
  }

  type Azione =
    | { tipo: 'ricarica'; importo: string; metodoId: string; paganteId: string }
    | { tipo: 'trasferisci'; a: string; importo: string }
    | { tipo: 'restituisci'; importo: string; metodoId: string }
    | { tipo: 'correggi'; saldo: 'EURO' | 'CREDITI'; quantita: string; motivo: string };
  let azione = $state<Azione | null>(null);

  const crediti = (imp: string) => (dati && Number(imp) > 0 ? Math.floor(Math.round(Number(imp) * 100) / Math.round(dati.regole.valoreCredito * 100)) : 0);
  const multiplo = (imp: string) => !!dati && Math.round(Number(imp) * 100) % Math.round(dati.regole.valoreCredito * 100) === 0;

  async function conferma() {
    const a = azione!;
    let ok = false;
    if (a.tipo === 'ricarica')
      ok = await esegui(() => api.ricarica(id, { paganteId: a.paganteId, importo: Number(a.importo), metodi: [{ metodoId: a.metodoId, importo: Number(a.importo) }] }),
        `Ricarica di ${euro(Number(a.importo))}: ${crediti(a.importo)} crediti.`);
    else if (a.tipo === 'trasferisci')
      ok = await esegui(() => api.trasferisci(id, a.a, Number(a.importo)), `${euro(Number(a.importo))} trasferiti a ${dati!.trasferibileA.find((f) => f.personaId === a.a)?.nome}.`);
    else if (a.tipo === 'restituisci')
      ok = await esegui(() => api.restituisci(id, Number(a.importo), a.metodoId || null), `${euro(Number(a.importo))} restituiti.`);
    else ok = await esegui(() => api.correggi(id, a.saldo, Number(a.quantita), a.motivo.trim()), 'Saldo corretto.');
    if (ok) azione = null;
  }

  const ESITI: Record<string, string> = { OK: 'Usato', GRATIS: 'Gratis', CREDITI_FINITI: 'Crediti finiti', SCONOSCIUTO: 'Sconosciuto', NON_ATTIVO: 'Non attivo', SERVIZIO_SPENTO: 'Spento' };
  const movimenti = $derived(tutti ? dati?.movimenti ?? [] : (dati?.movimenti ?? []).slice(0, 8));
  const segno = (n: number) => (n > 0 ? '+' : '−');

  function apriRicarica() {
    if (!dati) return;
    azione = { tipo: 'ricarica', importo: String(Math.max(dati.regole.ricaricaMinima, 5)), metodoId: perPagare.find((m) => !m.borsellino)?.id ?? '', paganteId: id };
  }
  /** Dal riassunto della scheda: apre la ricarica appena il borsellino è caricato. */
  let daAprire = false;
  export function avviaRicarica() {
    if (dati) apriRicarica();
    else daAprire = true;
  }
</script>

<section class="card blocco largo">
  <div class="testa">
    <h3>Borsellino</h3>
    {#if dati}
      {@const s = dati.saldo}
      <div class="totali">
        <span class="tot"><span class="lbl">Credito</span><strong class="m">{euro(s.euro)}</strong>
          {#if s.prossimaScadenza && s.inScadenza > 0}<span class="small muted">{euro(s.inScadenza)} scadono il {dataBreve(s.prossimaScadenza)}</span>{/if}</span>
        <span class="tot"><span class="lbl">Phon e docce</span><strong class="m">{s.crediti} crediti</strong><span class="small muted">= {euro(s.valoreCrediti)}</span></span>
      </div>
    {/if}
  </div>
  {#if messaggio}<p class="msg ok nomargin" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  {#if dati}
    {@const r = dati.regole}
    {#if !azione}
      <div class="azioni">
        {#if puoRicaricare}
          <button class="btn btn-primary" onclick={apriRicarica}><Icona nome="macchine" />Ricarica crediti…</button>
          {#if r.trasferibile && dati.trasferibileA.length && dati.saldo.euro > 0}
            <button class="btn btn-secondary" onclick={() => (azione = { tipo: 'trasferisci', a: dati!.trasferibileA[0].personaId, importo: '' })}>Trasferisci a un familiare…</button>
          {/if}
        {/if}
        {#if puoRimborsare && r.restituibile && dati.saldo.euro > 0}
          <button class="btn btn-secondary" onclick={() => (azione = { tipo: 'restituisci', importo: String(dati!.saldo.euro), metodoId: perRestituire[0]?.id ?? '' })}>Restituisci in denaro…</button>
        {/if}
        {#if puoRimborsare}<AzioneIcona icona="matita" etichetta="Correggi un saldo" onclick={() => (azione = { tipo: 'correggi', saldo: 'CREDITI', quantita: '', motivo: '' })} />{/if}
      </div>
    {:else}
      <form class="modulo" onsubmit={(e) => { e.preventDefault(); conferma(); }}>
        {#if azione.tipo === 'ricarica'}
          <div class="field">
            <span class="lbl">Quanto (un credito = {euro(r.valoreCredito)}, minimo {euro(r.ricaricaMinima)})</span>
            <div class="riga">
              <div class="chips">
                {#each [...new Set([Math.max(r.ricaricaMinima, 5), 10, 20])] as v (v)}
                  <button type="button" class="chip" aria-pressed={Number(azione.importo) === v} onclick={() => azione!.tipo === 'ricarica' && (azione!.importo = String(v))}>{euro(v)}</button>
                {/each}
              </div>
              <input class="inp m importo" type="number" min={r.ricaricaMinima} step={r.valoreCredito} bind:value={azione.importo} aria-label="Importo della ricarica" />
              <span class="small">= <strong>{crediti(azione.importo)} crediti</strong></span>
            </div>
            {#if Number(azione.importo) > 0 && !multiplo(azione.importo)}<span class="small rosso">Deve essere un multiplo di {euro(r.valoreCredito)}.</span>{/if}
          </div>
          <div class="due">
            <label class="field"><span class="lbl">Come si paga</span>
              <select class="inp" bind:value={azione.metodoId}>{#each perPagare as m (m.id)}<option value={m.id}>{m.borsellino ? `Borsellino (credito: ${euro(dati.saldo.euro)})` : m.nome}</option>{/each}</select>
            </label>
            <label class="field"><span class="lbl">Chi paga</span>
              <select class="inp" bind:value={azione.paganteId}>
                <option value={id}>Lui o lei</option>
                {#each dati.trasferibileA as f (f.personaId)}<option value={f.personaId}>{f.nome}{f.relazione ? ` (${f.relazione.toLowerCase()})` : ''}</option>{/each}
              </select>
            </label>
          </div>
        {:else if azione.tipo === 'trasferisci'}
          <div class="due">
            <label class="field"><span class="lbl">A chi</span>
              <select class="inp" bind:value={azione.a}>{#each dati.trasferibileA as f (f.personaId)}<option value={f.personaId}>{f.nome}{f.relazione ? ` (${f.relazione.toLowerCase()})` : ''}</option>{/each}</select>
            </label>
            <label class="field"><span class="lbl">Quanto (disponibili {euro(dati.saldo.euro)})</span>
              <input class="inp m" type="number" min="0.01" max={dati.saldo.euro} step="0.01" bind:value={azione.importo} /></label>
          </div>
          <p class="muted small nomargin">Le scadenze passano con il credito.</p>
        {:else if azione.tipo === 'restituisci'}
          <div class="due">
            <label class="field"><span class="lbl">Quanto (disponibili {euro(dati.saldo.euro)})</span>
              <input class="inp m" type="number" min="0.01" max={dati.saldo.euro} step="0.01" bind:value={azione.importo} /></label>
            <label class="field"><span class="lbl">Con</span>
              <select class="inp" bind:value={azione.metodoId}>{#each perRestituire as m (m.id)}<option value={m.id}>{m.nome}</option>{/each}</select>
            </label>
          </div>
        {:else}
          <div class="seg" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Quale saldo">
            <button type="button" aria-pressed={azione.saldo === 'CREDITI'} onclick={() => azione!.tipo === 'correggi' && (azione!.saldo = 'CREDITI')}>Crediti</button>
            <button type="button" aria-pressed={azione.saldo === 'EURO'} onclick={() => azione!.tipo === 'correggi' && (azione!.saldo = 'EURO')}>Credito in euro</button>
          </div>
          <div class="due">
            <label class="field"><span class="lbl">Di quanto (negativo per togliere)</span>
              <input class="inp m" type="number" step={azione.saldo === 'CREDITI' ? 1 : 0.01} bind:value={azione.quantita} /></label>
            <label class="field"><span class="lbl">Perché</span><input class="inp" bind:value={azione.motivo} maxlength="150" placeholder="Es. la macchina ha scalato due volte" /></label>
          </div>
        {/if}
        <div class="row">
          <button type="button" class="btn btn-secondary grow" onclick={() => (azione = null)}>Annulla</button>
          <button class="btn btn-primary grow" disabled={azione.tipo === 'ricarica' ? !(Number(azione.importo) >= r.ricaricaMinima && Number(azione.importo) > 0 && multiplo(azione.importo) && azione.metodoId)
            : azione.tipo === 'correggi' ? !(Number(azione.quantita) !== 0 && azione.motivo.trim()) : !(Number(azione.importo) > 0 && Number(azione.importo) <= dati.saldo.euro)}>
            {azione.tipo === 'ricarica' ? `Ricarica ${euro(Number(azione.importo) || 0)}` : azione.tipo === 'trasferisci' ? 'Trasferisci' : azione.tipo === 'restituisci' ? 'Restituisci' : 'Correggi'}
          </button>
        </div>
      </form>
    {/if}

    <div class="due-col">
      <div class="col">
        <div class="lbl">Movimenti</div>
        <ul class="righe">
          {#each movimenti as m (m.id)}
            <li>
              <span class="m imp" class:piu={m.quantita > 0}>{segno(m.quantita)}{m.saldo === 'EURO' ? euro(Math.abs(m.quantita)) : `${Math.abs(m.quantita)} cr.`}</span>
              <span class="grow small">{m.descrizione}{m.scadenza ? ` · fino al ${dataBreve(m.scadenza)}` : ''}<span class="muted"> · {dataBreve(dataDi(m.creatoIl))}{m.creatoDa ? ` · ${m.creatoDa}` : ''}</span></span>
            </li>
          {:else}
            <li class="muted small">Nessun movimento.</li>
          {/each}
        </ul>
        {#if dati.movimenti.length > 8}<button class="btn btn-quieto btn-sm sx" aria-expanded={tutti} onclick={() => (tutti = !tutti)}><Icona nome={tutti ? 'su' : 'giu'} />{tutti ? 'Solo gli ultimi' : `Tutti i movimenti (${dati.movimenti.length})`}</button>{/if}
      </div>
      <div class="col">
        <div class="lbl">Ultimi utilizzi di phon e docce</div>
        <ul class="righe">
          {#each utilizzi as u (u.id)}
            <li>
              <span class="small grow">{u.servizio ?? u.dispositivo}{u.postazione ? ` ${u.postazione}` : ''} <span class="muted">· {dataBreve(dataDi(u.istante))} {oraDi(u.istante)}{u.simulato ? ' · simulato' : ''}</span></span>
              <span class="tag {u.esito === 'OK' || u.esito === 'GRATIS' ? 't-soft' : 't-warn'}">{ESITI[u.esito] ?? u.esito}{u.crediti ? ` · ${u.crediti} cr.` : ''}</span>
            </li>
          {:else}
            <li class="muted small">Nessun utilizzo.</li>
          {/each}
        </ul>
      </div>
    </div>
  {:else if !errore}
    <p class="muted small">Caricamento…</p>
  {/if}
</section>

<style>
  .blocco { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16); }
  h3 { margin: 0; font-size: var(--text-xl); }
  .testa { display: flex; align-items: center; justify-content: space-between; gap: var(--space-12); flex-wrap: wrap; }
  .totali { display: flex; gap: var(--space-8); flex-wrap: wrap; }
  .tot { display: flex; flex-direction: column; padding: var(--space-4) var(--space-10); border-radius: var(--radius-md); background: var(--surface-sunken); }
  .tot strong { font-size: var(--text-lg); }
  .azioni { display: flex; gap: var(--space-10); flex-wrap: wrap; align-items: center; }
  .modulo { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-12); border-radius: var(--radius-md); background: var(--surface-sunken); }
  .riga { display: flex; gap: var(--space-8); align-items: center; flex-wrap: wrap; }
  .importo { width: 110px; }
  select.inp { appearance: auto; }
  .due-col { display: grid; grid-template-columns: 1fr; gap: var(--space-14); padding-top: var(--space-8); border-top: 1px solid var(--line); }
  @media (min-width: 900px) { .due-col { grid-template-columns: 1fr 1fr; } }
  .col { display: flex; flex-direction: column; gap: var(--space-4); }
  .righe { list-style: none; margin: 0; padding: 0; }
  .righe li { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-6) 0; border-top: 1px solid var(--line-soft); }
  .righe li:first-child { border-top: none; }
  .imp { min-width: 72px; font-weight: 700; color: var(--muted); }
  .imp.piu { color: var(--cloro-ink); }
  .rosso { color: var(--danger); }
  .sx { align-self: flex-start; min-height: 32px; }
</style>
