<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import { api, messaggioErrore, type DaIncassare, type Incassi } from '../lib/api';
  import { aggiungiGiorni, dataBreve, iso, lunedi } from '../lib/date';
  import { euro } from '../lib/listino';

  /**
   * Gli incassi di un giorno o di un periodo, con i totali per metodo (per la chiusura di cassa, che arriverà con la
   * parte 11), e i pagamenti ancora da incassare (bonifici) da segnare quando arrivano sul conto.
   */
  const oggi = iso(new Date());
  type Vista = 'incassi' | 'daIncassare';
  let vista = $state<Vista>('incassi');
  let da = $state(oggi);
  let a = $state(oggi);
  let dati = $state<Incassi | null>(null);
  let aperti = $state<DaIncassare[]>([]);
  let errore = $state('');
  let messaggio = $state('');

  const PERIODI = [
    { nome: 'Oggi', da: () => oggi, a: () => oggi },
    { nome: 'Ieri', da: () => aggiungiGiorni(oggi, -1), a: () => aggiungiGiorni(oggi, -1) },
    { nome: 'Questa settimana', da: () => lunedi(oggi), a: () => oggi },
    { nome: 'Questo mese', da: () => oggi.slice(0, 8) + '01', a: () => oggi },
  ];

  async function carica() {
    errore = '';
    try {
      [dati, aperti] = await Promise.all([api.incassi(da, a), api.daIncassare()]);
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a caricare gli incassi.');
    }
  }
  $effect(() => {
    if (da && a && a >= da) carica();
  });

  async function incassa(x: DaIncassare) {
    messaggio = '';
    try {
      await api.incassa(x.id);
      messaggio = `${x.metodo} di ${euro(x.importo)} da ${x.pagante} segnato incassato.`;
      await carica();
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  const totaleDaIncassare = $derived(aperti.reduce((t, x) => t + Math.round(x.importo * 100), 0) / 100);

  /** CSV per l'amministrazione: un pagamento per riga, con i metodi e per chi. */
  function esporta() {
    const d = dati!;
    const q = (v: string | number | null) => `"${String(v ?? '').replaceAll('"', '""')}"`;
    const righe = [['Data', 'Importo', 'Chi paga', 'Metodi', 'Per', 'Note', 'Annullato'].map(q).join(';')];
    for (const p of d.pagamenti)
      righe.push([p.data, p.importo.toFixed(2).replace('.', ','), p.pagante, p.metodi.map((m) => `${m.metodo} ${m.importo.toFixed(2).replace('.', ',')}${m.daIncassare && !m.incassatoIl ? ' (da incassare)' : ''}`).join(' + '),
        p.voci.map((v) => `${v.persona} · ${v.descrizione}`).join(' + '), p.note, p.annullatoIl ? (p.motivoAnnullamento ?? 'sì') : ''].map(q).join(';'));
    const url = URL.createObjectURL(new Blob(['﻿' + righe.join('\r\n')], { type: 'text/csv;charset=utf-8' }));
    const link = Object.assign(document.createElement('a'), { href: url, download: `incassi-${d.da}${d.a !== d.da ? '-' + d.a : ''}.csv` });
    link.click();
    URL.revokeObjectURL(url);
  }
</script>

<div class="pagina">
  <div class="seg viste" role="group" aria-label="Vista">
    <button aria-pressed={vista === 'incassi'} onclick={() => (vista = 'incassi')}>Incassi</button>
    <button aria-pressed={vista === 'daIncassare'} onclick={() => (vista = 'daIncassare')}>Da incassare{aperti.length ? ` (${aperti.length})` : ''}</button>
  </div>
  {#if messaggio}<p class="msg ok nomargin" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  {#if vista === 'incassi'}
    <div class="filtri">
      <div class="chips">
        {#each PERIODI as p (p.nome)}
          <button class="chip" aria-pressed={da === p.da() && a === p.a()} onclick={() => { da = p.da(); a = p.a(); }}>{p.nome}</button>
        {/each}
      </div>
      <label class="field data"><span class="lbl">Dal</span><input class="inp" type="date" bind:value={da} max={oggi} /></label>
      <label class="field data"><span class="lbl">Al</span><input class="inp" type="date" bind:value={a} min={da} max={oggi} /></label>
      {#if dati?.pagamenti.length}<button class="btn btn-secondary" onclick={esporta}><Icona nome="scarica" />Esporta (CSV)</button>{/if}
    </div>

    {#if dati}
      <div class="totali">
        <div class="card tot grande"><span class="lbl">Totale {da === a ? dataBreve(da) : `${dataBreve(da)} – ${dataBreve(a)}`}</span><strong class="m">{euro(dati.totale)}</strong></div>
        {#each dati.perMetodo as m (m.metodoId)}
          <div class="card tot"><span class="lbl">{m.metodo}</span><strong class="m">{euro(m.importo)}</strong>
            {#if m.daIncassare}<span class="small avviso">{euro(m.daIncassare)} da incassare</span>{/if}
            {#if m.uscite}<span class="small avviso">−{euro(m.uscite)} restituiti</span>{/if}</div>
        {/each}
      </div>

      <section class="card elenco">
        <ul class="righe">
          {#each dati.pagamenti as p (p.id)}
            <li class:spento={p.annullatoIl}>
              <span class="m imp">{euro(p.importo)}</span>
              <span class="grow">
                <strong>{p.pagante}</strong>{da !== a ? ` · ${dataBreve(p.data)}` : ''}
                <span class="small muted riga">{p.voci.map((v) => `${v.persona !== p.pagante ? v.persona + ' · ' : ''}${v.descrizione}`).join(' · ')}{p.note ? ` · ${p.note}` : ''}</span>
              </span>
              {#each p.metodi as m (m.id)}<span class="tag {m.daIncassare && !m.incassatoIl ? 't-warn' : 't-altro'}">{m.metodo}{p.metodi.length > 1 ? ` ${euro(m.importo)}` : ''}</span>{/each}
              {#if p.annullatoIl}<span class="tag t-altro" title={p.motivoAnnullamento ?? ''}>Annullato</span>{/if}
            </li>
          {:else}
            <li class="muted small">Nessun pagamento in questo periodo.</li>
          {/each}
        </ul>
      </section>
      {#if dati.uscite.length}
        <section class="card elenco">
          <div class="lbl sotto">Soldi restituiti</div>
          <ul class="righe">
            {#each dati.uscite as u (u.id)}
              <li>
                <span class="m imp">−{euro(u.importo)}</span>
                <span class="grow"><strong>{u.persona}</strong>{da !== a ? ` · ${dataBreve(u.data)}` : ''}<span class="small muted riga">{u.descrizione}</span></span>
                <span class="tag t-altro">{u.metodo}</span>
              </li>
            {/each}
          </ul>
        </section>
      {/if}
    {/if}
  {:else}
    <p class="muted small nomargin">I pagamenti con un metodo «da incassare» (es. bonifico) restano qui finché non li segni arrivati sul conto.
      {#if aperti.length}Totale: <strong>{euro(totaleDaIncassare)}</strong>.{/if}</p>
    <section class="card elenco">
      <ul class="righe">
        {#each aperti as x (x.id)}
          <li>
            <span class="m imp">{euro(x.importo)}</span>
            <span class="grow"><strong>{x.pagante}</strong> · {x.metodo} del {dataBreve(x.data)}{x.riferimento ? ` · ${x.riferimento}` : ''}
              <span class="small muted riga">{x.per.join(' · ')}</span></span>
            <button class="btn btn-secondary" onclick={() => incassa(x)}>Segna incassato</button>
          </li>
        {:else}
          <li class="muted small">Niente da incassare.</li>
        {/each}
      </ul>
    </section>
  {/if}
</div>

<style>
  .pagina { display: flex; flex-direction: column; gap: var(--space-14); }
  .viste { grid-template-columns: 1fr 1fr; max-width: 420px; }
  .filtri { display: flex; gap: var(--space-10); align-items: flex-end; flex-wrap: wrap; }
  .data .inp { width: 160px; }
  .totali { display: flex; gap: var(--space-10); flex-wrap: wrap; }
  .tot { display: flex; flex-direction: column; gap: 2px; padding: var(--space-10) var(--space-14); min-width: 150px; }
  .tot strong { font-size: var(--text-xl); }
  .tot.grande strong { font-size: 26px; }
  .avviso { color: var(--warn); }
  .elenco { padding: var(--space-6) var(--space-16); }
  .righe { list-style: none; margin: 0; padding: 0; }
  .righe li { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; padding: var(--space-10) 0; border-top: 1px solid var(--line-soft); }
  .righe li:first-child { border-top: none; }
  .imp { min-width: 90px; font-weight: 700; }
  .riga { display: block; }
  .spento { opacity: 0.55; }
  .sotto { padding-top: var(--space-10); }
</style>
