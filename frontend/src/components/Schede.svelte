<script lang="ts" generics="S extends string">
  import { onMount, tick, type Snippet } from 'svelte';
  import Azione from './Azione.svelte';
  import { frecceVisibili, schedaDopoTasto, scorriPerVedere, type Scheda } from '../lib/schede';

  /**
   * Le schede di una pagina (design system, «Schede lunghe»): un `role="tablist"` con il suo `role="tabpanel"`.
   *   <Schede schede={SCHEDE} bind:attiva={scheda} etichetta="Sezioni della scheda" id="cliente">
   *     {#snippet children(s)}{#if s === 'corsi'}…{/if}{/snippet}
   *   </Schede>
   * - Tastiera: frecce sinistra e destra a giro, Inizio e Fine; la scheda si apre spostandosi e prende il focus. Nel giro del
   *   Tab c'è solo la scheda attiva.
   * - Se le schede non stanno nella larghezza scorrono di lato, senza barra di scorrimento: ai lati compaiono due pulsanti-icona
   *   «Schede precedenti» e «Schede successive», solo dove c'è ancora qualcosa. Le frecce stanno sopra i bordi (non spostano
   *   le schede) e la scheda attiva si porta sempre in vista, a distanza dalle frecce.
   * - `azioni`: un pezzo a destra della riga delle schede (il pulsante «Nuovo ruolo…» di Account e ruoli).
   * - Gli id: `${id}-tab-${scheda}` e `${id}-pannello-${scheda}`.
   */
  let { schede, attiva = $bindable(), etichetta, id: idBase, children, azioni }: {
    schede: Scheda<S>[];
    attiva: S;
    etichetta: string;
    id: string;
    children: Snippet<[S]>;
    azioni?: Snippet;
  } = $props();

  /** Lo spazio che una freccia (pulsante-icona con cornice da 44 px più la sfumatura) copre ai lati: la scheda attiva non ci va sotto. */
  const FRECCIA = 52;

  let lista = $state<HTMLElement>();
  let sx = $state(false);
  let dx = $state(false);

  const idTab = (s: string) => `${idBase}-tab-${s}`;
  const indice = $derived(Math.max(0, schede.findIndex((x) => x.id === attiva)));

  function misura() {
    if (!lista) return;
    const f = frecceVisibili(lista.scrollLeft, lista.clientWidth, lista.scrollWidth);
    sx = f.sx;
    dx = f.dx;
  }
  /** Porta in vista la scheda attiva (senza animazione al primo disegno). */
  function mostraAttiva(liscio: boolean) {
    const el = lista?.querySelector<HTMLElement>('[role="tab"][aria-selected="true"]');
    if (!lista || !el) return;
    // le frecce stanno sopra i bordi solo se le schede non ci stanno: se ci stanno non serve margine
    const scorre = lista.scrollWidth > lista.clientWidth + 1;
    const r = el.getBoundingClientRect();
    const inizio = r.left - lista.getBoundingClientRect().left + lista.scrollLeft;
    const a = scorriPerVedere(inizio, r.width, lista.scrollLeft, lista.clientWidth, scorre ? FRECCIA : 0);
    if (Math.abs(a - lista.scrollLeft) > 0.5) lista.scrollTo?.({ left: a, behavior: liscio ? 'smooth' : 'auto' });
  }
  function scorri(verso: 1 | -1) {
    if (!lista) return;
    lista.scrollBy?.({ left: verso * Math.max(FRECCIA, lista.clientWidth - 2 * FRECCIA), behavior: 'smooth' });
  }
  async function tasto(e: KeyboardEvent) {
    const j = schedaDopoTasto(e.key, indice, schede.length);
    if (j < 0) return;
    e.preventDefault();
    attiva = schede[j].id;
    await tick();
    // dentro la propria lista: due `Schede` con lo stesso `id` nella pagina non si scambiano il focus
    lista?.querySelector<HTMLElement>(`[role="tab"][data-scheda="${schede[j].id}"]`)?.focus({ preventScroll: true });
  }

  onMount(() => {
    mostraAttiva(false);
    misura();
    if (typeof ResizeObserver === 'undefined') return;
    const ro = new ResizeObserver(() => {
      misura();
      mostraAttiva(false);
    });
    if (lista) {
      ro.observe(lista);
      for (const c of lista.children) ro.observe(c);
    }
    return () => ro.disconnect();
  });
  // al cambio della scheda attiva (anche da fuori) o delle schede (cambia un segno, quindi la larghezza)
  $effect(() => {
    void attiva;
    void schede;
    tick().then(() => {
      mostraAttiva(true);
      misura();
    });
  });
</script>

<div class="sc-radice">
  <div class="sc-riga">
    <div class="sc-corsia">
      <div bind:this={lista} class="sc-lista" role="tablist" aria-label={etichetta} tabindex="-1" onkeydown={tasto} onscroll={misura}>
        {#each schede as t (t.id)}
          <button
            id={idTab(t.id)}
            data-scheda={t.id}
            type="button"
            class="sc-tab"
            role="tab"
            aria-selected={attiva === t.id}
            aria-controls={`${idBase}-pannello-${t.id}`}
            tabindex={attiva === t.id ? 0 : -1}
            onclick={() => (attiva = t.id)}
          >{t.nome}{#if t.segno}<span class="sc-segno" class:warn={t.segno.warn}>{t.segno.testo}</span>{/if}</button>
        {/each}
      </div>
      {#if sx}<span class="sc-freccia sx"><Azione cornice icona="sx" etichetta="Schede precedenti" onclick={() => scorri(-1)} /></span>{/if}
      {#if dx}<span class="sc-freccia dx"><Azione cornice icona="dx" etichetta="Schede successive" onclick={() => scorri(1)} /></span>{/if}
    </div>
    {#if azioni}{@render azioni()}{/if}
  </div>

  <div class="sc-pannello" id={`${idBase}-pannello-${attiva}`} role="tabpanel" aria-labelledby={idTab(attiva)}>
    {@render children(attiva)}
  </div>
</div>

<style>
  .sc-radice { display: flex; flex-direction: column; gap: var(--space-10); min-width: 0; }
  .sc-riga { display: flex; align-items: center; gap: var(--space-10); min-width: 0; border-bottom: 1px solid var(--line); }
  .sc-corsia { position: relative; flex: 1 1 0; min-width: 0; }
  .sc-lista { display: flex; gap: var(--space-4); overflow-x: auto; overscroll-behavior-x: contain; scrollbar-width: none; }
  .sc-lista::-webkit-scrollbar { display: none; }
  .sc-tab {
    position: relative; flex: 0 0 auto; display: inline-flex; align-items: center; gap: var(--space-8); min-height: 46px; padding: 0 var(--space-14);
    border: none; background: none; color: var(--muted); font: inherit; font-size: var(--text-md); font-weight: 600; cursor: pointer; white-space: nowrap;
    border-radius: var(--radius-sm) var(--radius-sm) 0 0;
  }
  .sc-tab:hover { color: var(--ink); background: var(--surface-sunken); }
  .sc-tab[aria-selected='true'] { color: var(--cloro-ink); }
  .sc-tab[aria-selected='true']::after {
    content: ''; position: absolute; left: var(--space-10); right: var(--space-10); bottom: 0; height: 3px; border-radius: 3px 3px 0 0; background: var(--cloro);
  }
  .sc-tab:focus-visible { outline-offset: -2px; }
  .sc-segno {
    min-width: 20px; height: 20px; padding: 0 var(--space-6); border-radius: var(--radius-pill); background: var(--surface-sunken); color: var(--ink);
    font-size: var(--text-2xs); font-weight: 700; display: inline-flex; align-items: center; justify-content: center;
  }
  .sc-segno.warn { background: var(--warn-bg); color: var(--warn); }

  /* le frecce stanno sopra i bordi della lista, con una sfumatura verso le schede: non cambiano la larghezza disponibile */
  .sc-freccia { position: absolute; top: 0; bottom: 0; z-index: 1; display: flex; align-items: center; }
  .sc-freccia.sx { left: 0; padding-right: var(--space-8); background: linear-gradient(to right, var(--carta) 75%, transparent); }
  .sc-freccia.dx { right: 0; padding-left: var(--space-8); background: linear-gradient(to left, var(--carta) 75%, transparent); }
  .sc-pannello { min-width: 0; }
</style>
