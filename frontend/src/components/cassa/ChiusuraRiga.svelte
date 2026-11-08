<script lang="ts">
  import Icona from '../Icona.svelte';
  import { api, messaggioErrore, type SessioneCassa } from '../../lib/api';
  import { conSegno, esitoDifferenza, MOVIMENTI } from '../../lib/cassa';
  import { dataBreve, dataDi, oraDi } from '../../lib/date';
  import { euro } from '../../lib/listino';

  /** Una chiusura di cassa nello storico: quando, chi, attesi, contati e differenza; si apre per il dettaglio e il PDF. */
  let { chiusura, conCassa = false }: { chiusura: SessioneCassa; conCassa?: boolean } = $props();
  let aperta = $state(false);
  let dettaglio = $state<SessioneCassa | null>(null);
  let errore = $state('');
  const s = $derived(chiusura);
  const e = $derived(esitoDifferenza(s.differenza ?? 0));
  const quando = (i: string) => `${dataBreve(dataDi(i))} ${oraDi(i)}`;

  async function apri() {
    aperta = !aperta;
    if (aperta && !dettaglio)
      try {
        dettaglio = await api.chiusuraCassa(s.id!);
      } catch (x) {
        errore = messaggioErrore(x);
      }
  }
</script>

<li class="ch" class:aperta>
  <button type="button" class="riga" aria-expanded={aperta} onclick={apri}>
    <span class="quando"><b class="m">{quando(s.chiusaIl!)}</b><span class="muted small">{conCassa ? `${s.cassa} · ` : ''}{s.chiusaDa ?? ''}</span></span>
    <span class="num"><span class="lbl">Attesi</span><span class="m">{euro(s.attesi ?? 0)}</span></span>
    <span class="num"><span class="lbl">Contati</span><span class="m">{euro(s.contati ?? 0)}</span></span>
    <span class="num diff {e.tono}"><span class="lbl">Differenza</span><span class="m">{conSegno(s.differenza ?? 0, euro)}</span></span>
    <span class="num"><span class="lbl">Versato</span><span class="m">{euro(s.versato ?? 0)}</span></span>
    <Icona nome={aperta ? 'su' : 'giu'} />
  </button>
  {#if aperta}
    <div class="dett">
      {#if errore}<p class="msg err nomargin">{errore}</p>{/if}
      <span class="small muted">Dal {quando(s.dal)}{s.esplicita && s.apertaDa ? ` · aperta da ${s.apertaDa} alle ${oraDi(s.apertaIl!)}` : ''} · fondo {euro(s.fondo)} · lasciati {euro(s.lasciato ?? 0)}</span>
      {#if s.conti.perMetodo.length}
        <span class="metodi">{#each s.conti.perMetodo as m (m.metodo)}<span class="tag-m">{m.metodo} <b class="m">{euro(m.entrate)}</b>{m.uscite ? ` · −${euro(m.uscite)}` : ''}</span>{/each}</span>
      {/if}
      {#if dettaglio?.movimenti?.some((m) => !m.annullato)}
        <span class="small">{#each dettaglio.movimenti.filter((m) => !m.annullato) as m, i (m.id)}{i ? ' · ' : ''}{MOVIMENTI.find((t) => t.id === m.tipo)?.nome} {euro(m.importo)}{m.descrizione !== MOVIMENTI.find((t) => t.id === m.tipo)?.nome ? ` (${m.descrizione})` : ''}{/each}</span>
      {/if}
      {#if s.note}<span class="note small">{s.note}</span>{/if}
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
  .riga { width: 100%; display: grid; grid-template-columns: minmax(150px, 1.6fr) repeat(4, minmax(80px, 1fr)) 24px; gap: var(--space-10); align-items: center;
    padding: var(--space-10) var(--space-16); border: none; background: none; font: inherit; color: var(--ink); text-align: left; cursor: pointer; }
  .riga:hover { background: var(--surface-sunken); }
  .quando { display: flex; flex-direction: column; min-width: 0; }
  .num { display: flex; flex-direction: column; align-items: flex-end; font-size: var(--text-md); }
  .num .lbl { font-size: 10px; }
  .num .m { font-weight: 700; white-space: nowrap; }
  .diff.ok .m { color: var(--passa-fg); }
  .diff.piu .m { color: var(--warn); }
  .diff.meno .m { color: var(--ferma-fg); }
  .dett { display: flex; flex-direction: column; gap: var(--space-8); padding: 0 var(--space-16) var(--space-14); }
  .metodi { display: flex; gap: var(--space-6); flex-wrap: wrap; }
  .tag-m { display: inline-flex; align-items: center; gap: var(--space-4); height: 24px; padding: 0 9px; border-radius: var(--radius-pill); background: var(--surface-sunken); font-size: var(--text-xs); }
  .note { white-space: pre-line; padding: var(--space-8) var(--space-10); border-radius: var(--radius-sm); background: var(--carta); }
  .bottoni { display: flex; gap: var(--space-8); flex-wrap: wrap; }
  .bottoni a.btn { text-decoration: none; display: inline-flex; align-items: center; }
  /* stretta (nella colonna di lato o al telefono): quando sopra, i numeri due per riga */
  @container (max-width: 620px) {
    .riga { position: relative; grid-template-columns: repeat(2, minmax(0, 1fr)); row-gap: var(--space-6); padding-right: 48px; }
    .quando { grid-column: 1 / -1; }
    .riga > :global(.ic) { position: absolute; right: var(--space-16); top: var(--space-12); }
    .num { align-items: flex-start; }
  }
</style>
