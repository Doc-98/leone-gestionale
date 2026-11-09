<script lang="ts">
  import Icona from '../Icona.svelte';
  import { api, messaggioErrore, type SessioneCassa } from '../../lib/api';
  import { conSegno, esitoDifferenza, MOVIMENTI, statoChiusura } from '../../lib/cassa';
  import { dataBreve, dataDi, oraDi } from '../../lib/date';
  import { euro } from '../../lib/listino';
  import { sessione } from '../../lib/sessione.svelte';

  /**
   * Una chiusura di cassa nello storico: quando, chi, attesi, contati, differenza e stato (da controllare, controllata,
   * torna, non torna). Si apre per il dettaglio (primo conteggio, riscontri, movimenti, note), il PDF e, per un
   * responsabile, «Segna come controllata». «cambiata» avvisa chi la mostra.
   */
  let { chiusura, conCassa = false, cambiata }: { chiusura: SessioneCassa; conCassa?: boolean; cambiata?: (s: SessioneCassa) => void } = $props();
  let aperta = $state(false);
  let dettaglio = $state<SessioneCassa | null>(null);
  let errore = $state('');
  let nota = $state('');
  let lavoro = $state(false);
  const s = $derived(dettaglio ?? chiusura);
  const e = $derived(esitoDifferenza(s.differenza ?? 0));
  const st = $derived(statoChiusura(s));
  const puoControllare = $derived(sessione.puo('PAGAMENTI_ANNULLA') && !s.controllataIl);
  const quando = (i: string) => `${dataBreve(dataDi(i))} ${oraDi(i)}`;
  const nomeTipo = (t: string) => MOVIMENTI.find((m) => m.id === t)?.nome ?? t;

  async function apri() {
    aperta = !aperta;
    if (aperta && !dettaglio)
      try {
        dettaglio = await api.chiusuraCassa(chiusura.id!);
      } catch (x) {
        errore = messaggioErrore(x);
      }
  }

  async function controlla() {
    errore = '';
    lavoro = true;
    try {
      dettaglio = await api.controllaChiusura(s.id!, nota.trim());
      nota = '';
      cambiata?.(dettaglio);
    } catch (x) {
      errore = messaggioErrore(x);
    } finally {
      lavoro = false;
    }
  }
</script>

<li class="ch" class:aperta>
  <button type="button" class="riga" aria-expanded={aperta} onclick={apri}>
    <span class="quando"><b class="m">{quando(s.chiusaIl!)}</b><span class="muted small">{conCassa ? `${s.cassa} · ` : ''}{s.chiusaDa ?? ''}</span></span>
    <span class="num"><span class="lbl">Attesi</span><span class="m">{euro(s.attesi ?? 0)}</span></span>
    <span class="num"><span class="lbl">Contati</span><span class="m">{euro(s.contati ?? 0)}</span></span>
    <span class="num diff {e.tono}"><span class="lbl">Differenza</span><span class="m">{conSegno(s.differenza ?? 0, euro)}</span></span>
    <span class="stato"><span class="pill {st.tono}">{st.testo}</span></span>
    <Icona nome={aperta ? 'su' : 'giu'} />
  </button>
  {#if aperta}
    <div class="dett">
      {#if errore}<p class="msg err nomargin">{errore}</p>{/if}
      <span class="small muted">Dal {quando(s.dal)}{s.esplicita && s.apertaDa ? ` · aperta da ${s.apertaDa} alle ${oraDi(s.apertaIl!)}` : ''}</span>
      <div class="fatti">
        <span class="fatto">Fondo · lasciato<b class="m">{euro(s.fondo)} · {euro(s.lasciato ?? 0)}</b></span>
        <span class="fatto">Versato{s.perCassaforte != null && s.perCassaforte !== s.versato ? ' (con quelli del turno)' : ''}<b class="m">{euro(s.perCassaforte ?? s.versato ?? 0)}{s.inBanca ? ' · in banca' : ''}</b></span>
        {#if s.primoConteggio != null && Math.round(s.primoConteggio * 100) !== Math.round((s.contati ?? 0) * 100)}
          <span class="fatto">Primo conteggio · riconteggio<b class="m">{euro(s.primoConteggio)} · {euro(s.contati ?? 0)}</b></span>
        {/if}
        {#each s.riscontri ?? [] as r (r.metodo)}
          <span class="fatto">POS {r.metodo}<b class="m">{r.dichiarato == null ? 'non scritto' : Math.round(r.differenza * 100) === 0 ? `torna (${euro(r.atteso)})` : `${conSegno(r.differenza, euro)} su ${euro(r.atteso)}`}</b></span>
        {/each}
      </div>
      {#if s.conti.perMetodo.length}
        <span class="metodi">{#each s.conti.perMetodo as m (m.metodo)}<span class="tag-m">{m.metodo} <b class="m">{euro(m.entrate)}</b>{m.uscite ? ` · −${euro(m.uscite)}` : ''}</span>{/each}</span>
      {/if}
      {#if dettaglio?.movimenti?.some((m) => !m.annullato)}
        <span class="small">{#each dettaglio.movimenti.filter((m) => !m.annullato) as m, i (m.id)}{i ? ' · ' : ''}{nomeTipo(m.tipo)} {euro(m.importo)}{m.descrizione !== nomeTipo(m.tipo) ? ` (${m.descrizione})` : ''}{/each}</span>
      {/if}
      {#if s.note}<span class="note small" class:avv={st.tono === 'warn' || st.tono === 'ferma'}>{s.note}</span>{/if}
      {#if s.controllataIl}
        <span class="ok-c small"><Icona nome="check" />Controllata da {s.controllataDa ?? '—'} il {quando(s.controllataIl)}{s.notaControllo ? `: ${s.notaControllo}` : ''}</span>
      {/if}
      {#if puoControllare}
        <form class="controlla" onsubmit={(ev) => { ev.preventDefault(); controlla(); }}>
          <input class="inp" bind:value={nota} maxlength="500" placeholder="Cosa hai verificato (facoltativo)" aria-label="Nota del controllo" />
          <button class="btn btn-primary btn-sm" disabled={lavoro}><Icona nome="check" />Segna come controllata</button>
        </form>
      {/if}
      <span class="bottoni">
        <a class="btn btn-secondary btn-sm" href={api.urlPdfChiusura(s.id!)} target="_blank" rel="noopener"><Icona nome="stampa" />Stampa il riepilogo</a>
        <a class="btn btn-secondary btn-sm" href={api.urlPdfChiusura(s.id!, true)} download><Icona nome="scarica" />PDF</a>
      </span>
    </div>
  {/if}
</li>

<style>
  .ch { border-top: 1px solid var(--line-soft); container-type: inline-size; }
  .ch:first-child { border-top: none; }
  .riga { width: 100%; display: grid; grid-template-columns: minmax(150px, 1.6fr) repeat(3, minmax(80px, 1fr)) 120px 24px; gap: var(--space-10); align-items: center;
    padding: var(--space-10) var(--space-16); border: none; background: none; font: inherit; color: var(--ink); text-align: left; cursor: pointer; }
  .riga:hover { background: var(--surface-sunken); }
  .quando { display: flex; flex-direction: column; min-width: 0; }
  .num { display: flex; flex-direction: column; align-items: flex-end; font-size: var(--text-md); }
  .num .lbl { font-size: 10px; }
  .num .m { font-weight: 700; white-space: nowrap; }
  .diff.ok .m { color: var(--passa-fg); }
  .diff.piu .m { color: var(--warn); }
  .diff.meno .m { color: var(--ferma-fg); }
  .stato { justify-self: end; }
  .pill { display: inline-flex; align-items: center; height: 24px; padding: 0 10px; border-radius: var(--radius-pill); font-size: var(--text-xs); font-weight: 700; white-space: nowrap; }
  .pill.warn { background: var(--warn-bg); color: var(--warn); }
  .pill.cloro { background: var(--cloro-soft); color: var(--cloro-ink); }
  .pill.ferma { background: var(--ferma-bg); color: var(--ferma-fg); }
  .pill.passa { background: var(--passa-bg); color: var(--passa-fg); }
  .dett { display: flex; flex-direction: column; gap: var(--space-8); padding: 0 var(--space-16) var(--space-14); }
  .fatti { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: var(--space-8); }
  .fatto { display: flex; flex-direction: column; gap: 2px; padding: var(--space-8) var(--space-10); border-radius: var(--radius-md); background: var(--carta); font-size: var(--text-sm); color: var(--muted); }
  .fatto b { font-size: var(--text-md); color: var(--ink); font-weight: 500; }
  .metodi { display: flex; gap: var(--space-6); flex-wrap: wrap; }
  .tag-m { display: inline-flex; align-items: center; gap: var(--space-4); height: 24px; padding: 0 9px; border-radius: var(--radius-pill); background: var(--surface-sunken); font-size: var(--text-xs); }
  .note { white-space: pre-line; padding: var(--space-8) var(--space-10); border-radius: var(--radius-sm); background: var(--carta); }
  .note.avv { background: var(--warn-bg); color: var(--warn); }
  .ok-c { display: flex; gap: var(--space-6); align-items: center; padding: var(--space-8) var(--space-10); border-radius: var(--radius-sm); background: var(--passa-bg); color: var(--passa-fg); font-weight: 600; }
  .ok-c :global(.ic) { width: 16px; height: 16px; }
  .controlla { display: flex; gap: var(--space-8); flex-wrap: wrap; align-items: center; padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); border: 1px solid var(--line); }
  .controlla .inp { flex: 1 1 240px; height: 40px; }
  .bottoni { display: flex; gap: var(--space-8); flex-wrap: wrap; }
  .bottoni a.btn { text-decoration: none; display: inline-flex; align-items: center; }
  /* stretta (nella colonna di lato o al telefono): quando e stato sopra, i numeri tre per riga */
  @container (max-width: 620px) {
    .riga { position: relative; grid-template-columns: repeat(3, minmax(0, 1fr)); row-gap: var(--space-6); padding-right: 48px; }
    .quando { grid-column: 1 / 3; }
    .stato { grid-column: 3; grid-row: 1; }
    .riga > :global(.ic) { position: absolute; right: var(--space-16); top: var(--space-12); }
    .num { align-items: flex-start; }
  }
</style>
