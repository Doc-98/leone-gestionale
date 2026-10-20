<script lang="ts">
  import { api, messaggioErrore, type Scadenze, type VoceScadenza } from '../lib/api';
  import { dataBreve, dataDi } from '../lib/date';
  import { schermo } from '../lib/schermo.svelte';

  /**
   * Le scadenze per la segreteria: chi ha il certificato, l'abbonamento o il tesseramento in scadenza o mancante, per
   * chi frequenta. Da qui si apre la scheda o si manda un promemoria; i promemoria automatici partono ogni mattina.
   */
  type Scheda = 'certificati' | 'abbonamenti' | 'tesseramento';
  const SCHEDE: { id: Scheda; nome: string; vuoto: string }[] = [
    { id: 'certificati', nome: 'Certificati', vuoto: 'Nessun certificato in scadenza.' },
    { id: 'abbonamenti', nome: 'Abbonamenti e carnet', vuoto: 'Nessun abbonamento in scadenza.' },
    { id: 'tesseramento', nome: 'Tesseramento', vuoto: 'Tutti in regola con il tesseramento.' },
  ];

  let entro = $state(30);
  let dati = $state<Scadenze | null>(null);
  let scheda = $state<Scheda>('certificati');
  let cerca = $state('');
  let errore = $state('');
  let messaggio = $state('');
  let inviando = $state('');

  async function carica() {
    errore = '';
    try {
      dati = await api.scadenze(entro);
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a caricare le scadenze.');
    }
  }
  $effect(() => {
    void entro;
    carica();
  });

  const voci = $derived.by(() => {
    if (!dati) return [];
    const q = cerca.trim().toLowerCase();
    return dati[scheda].filter((v) => !q || `${v.nome} ${v.cognome}`.toLowerCase().includes(q) || `${v.cognome} ${v.nome}`.toLowerCase().includes(q));
  });
  const conta = (s: Scheda) => dati?.[s].length ?? 0;
  const urgenti = (s: Scheda) => dati?.[s].filter((v) => v.giorni == null || v.giorni <= 7).length ?? 0;

  function quando(v: VoceScadenza): { testo: string; classe: string } {
    if (v.giorni == null) return { testo: v.tipo === 'CARNET' ? 'Da rinnovare' : 'Manca', classe: 'rosso' };
    if (v.giorni < 0) return { testo: v.giorni === -1 ? 'Scaduto ieri' : `Scaduto da ${-v.giorni} giorni`, classe: 'rosso' };
    if (v.giorni === 0) return { testo: 'Scade oggi', classe: 'rosso' };
    return { testo: v.giorni === 1 ? 'Scade domani' : `Tra ${v.giorni} giorni`, classe: v.giorni <= 7 ? 'arancio' : 'neutro' };
  }

  async function invia(v: VoceScadenza) {
    const chiave = v.personaId + v.riferimento;
    inviando = chiave;
    errore = '';
    messaggio = '';
    try {
      const r = await api.inviaPromemoria({ personaId: v.personaId, tipo: v.tipo, riferimento: v.riferimento });
      messaggio = r.stato === 'INVIATA' ? `Promemoria inviato a ${v.email}.`
        : r.stato === 'NON_INVIATA' ? 'Promemoria registrato, ma non partito: il server di posta non è configurato.'
        : r.stato === 'SENZA_EMAIL' ? `${v.nome} ${v.cognome} non ha un'email nella scheda.`
        : `Invio non riuscito: ${r.errore ?? 'errore del server di posta'}.`;
      await carica();
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      inviando = '';
    }
  }

  /** L'elenco della scheda corrente in CSV (per le telefonate, o per Excel). */
  function scarica() {
    const righe = [['Cognome', 'Nome', 'Età', 'Cosa', 'Scadenza', 'Email', 'Ultimo promemoria']];
    for (const v of voci)
      righe.push([v.cognome, v.nome, v.eta == null ? '' : String(v.eta), v.descrizione, v.scadenza ? dataBreve(v.scadenza) : '', v.email ?? '',
        v.ultimoPromemoria ? dataBreve(dataDi(v.ultimoPromemoria)) : '']);
    const csv = righe.map((r) => r.map((c) => `"${c.replaceAll('"', '""')}"`).join(';')).join('\r\n');
    const url = URL.createObjectURL(new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = `scadenze-${scheda}-${dati?.oggi ?? ''}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }
</script>

<div class="barra">
  <div class="seg entro" role="group" aria-label="Entro quanti giorni">
    {#each [7, 15, 30, 60] as g (g)}<button aria-pressed={entro === g} onclick={() => (entro = g)}>{g} giorni</button>{/each}
  </div>
  <input class="inp cerca" type="search" bind:value={cerca} placeholder="Cerca per nome" aria-label="Cerca per nome" />
  <span class="grow"></span>
  <button class="btn btn-secondary" onclick={scarica} disabled={!voci.length}>Scarica elenco</button>
  <a class="btn btn-secondary" href="#/promemoria">Promemoria email…</a>
</div>

{#if dati && !dati.postaConfigurata}
  <p class="hint nomargin">Il server di posta non è ancora configurato: i promemoria si registrano (si vedono nella scheda e nei promemoria) ma non partono.</p>
{/if}
{#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

<div class="schede" role="tablist">
  {#each SCHEDE as s (s.id)}
    <button class="scheda card" role="tab" aria-selected={scheda === s.id} onclick={() => (scheda = s.id)}>
      <span class="numero d">{conta(s.id)}</span>
      <span class="nome-s">{s.nome}</span>
      <span class="small" class:urg={urgenti(s.id) > 0}>{urgenti(s.id) ? `${urgenti(s.id)} urgenti` : 'niente di urgente'}</span>
    </button>
  {/each}
</div>

{#if dati}
  <ul class="card elenco" aria-label="Elenco delle scadenze">
    {#each voci as v (v.tipo + v.personaId + v.riferimento)}
      {@const q = quando(v)}
      <li class="voce" class:largo={schermo.larga}>
        <span class="badge-s {q.classe}">{q.testo}</span>
        <span class="chi">
          <a href={`#/clienti/${v.personaId}`} class="nome">{v.cognome} {v.nome}</a>
          <span class="muted small">{v.eta != null ? `${v.eta} anni · ` : ''}{v.descrizione}{v.scadenza ? ` · ${dataBreve(v.scadenza)}` : ''}</span>
        </span>
        <span class="contatto small">
          {#if v.email}<span class="muted">{v.email}</span>{:else}<span class="manca">Nessuna email</span>{/if}
          {#if v.ultimoPromemoria}<span class="muted">Promemoria il {dataBreve(dataDi(v.ultimoPromemoria))}</span>{/if}
        </span>
        <button class="btn btn-secondary piccolo" disabled={!v.email || inviando === v.personaId + v.riferimento || v.tipo === 'CERTIFICATO_MANCANTE'}
                title={!v.email ? "Aggiungi l'email nella scheda" : ''} onclick={() => invia(v)}>
          {inviando === v.personaId + v.riferimento ? 'Invio…' : 'Invia promemoria'}
        </button>
      </li>
    {:else}
      <li class="muted vuoto">{cerca ? 'Nessuno con questo nome.' : SCHEDE.find((s) => s.id === scheda)?.vuoto}</li>
    {/each}
  </ul>
{:else if !errore}
  <p class="muted">Caricamento…</p>
{/if}

<style>
  .barra a.btn { text-decoration: none; display: inline-flex; align-items: center; }
  .barra { display: flex; flex-wrap: wrap; gap: var(--space-10); align-items: center; }
  .entro { grid-template-columns: repeat(4, auto); }
  .entro button { padding: 0 var(--space-12); }
  .cerca { width: 220px; }
  .schede { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-10); }
  .scheda { display: flex; flex-direction: column; align-items: flex-start; gap: 2px; padding: var(--space-12) var(--space-14); cursor: pointer; font: inherit;
    color: var(--ink); text-align: left; }
  .scheda[aria-selected='true'] { border-color: var(--cloro); box-shadow: inset 0 0 0 1px var(--cloro); background: var(--cloro-soft); }
  .numero { font-size: 30px; font-weight: 800; line-height: 1; }
  .nome-s { font-weight: 600; }
  .urg { color: var(--ferma); font-weight: 600; }
  .elenco { list-style: none; margin: 0; padding: 0; overflow: hidden; }
  .voce { display: grid; grid-template-columns: 1fr auto; gap: var(--space-6) var(--space-12); align-items: center; padding: var(--space-10) var(--space-14);
    border-top: 1px solid var(--line-soft); }
  .voce.largo { grid-template-columns: 150px minmax(0, 1.4fr) minmax(0, 1fr) auto; }
  .voce:first-child { border-top: none; }
  .voce:not(.largo) .badge-s { grid-column: 1 / -1; justify-self: start; }
  .voce:not(.largo) .contatto { grid-column: 1; }
  .badge-s { display: inline-flex; align-items: center; height: 26px; padding: 0 var(--space-10); border-radius: var(--radius-pill); font-size: var(--text-sm);
    font-weight: 600; justify-self: start; }
  .rosso { background: var(--ferma-bg); color: var(--ferma-fg); }
  .arancio { background: var(--oro-bg); color: var(--oro-fg); }
  .neutro { background: var(--surface-sunken); color: var(--ink); }
  .chi { display: flex; flex-direction: column; min-width: 0; }
  .nome { font-weight: 600; color: var(--ink); text-decoration: none; }
  .nome:hover { text-decoration: underline; }
  .contatto { display: flex; flex-direction: column; min-width: 0; overflow: hidden; text-overflow: ellipsis; }
  .manca { color: var(--warn); font-weight: 600; }
  .piccolo { min-height: 36px; padding: 0 var(--space-12); font-size: var(--text-sm); }
  .vuoto { padding: var(--space-20); text-align: center; }
  @media (max-width: 700px) { .schede { grid-template-columns: 1fr; } .cerca { width: 100%; } }
</style>
