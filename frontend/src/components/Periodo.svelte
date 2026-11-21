<script lang="ts">
  import Azione from './Azione.svelte';
  import { iso } from '../lib/date';
  import { contiene, daCalendario, etichettaPeriodo, inizioPeriodo, limitaPeriodo, perCalendario, sidopo, spostaPeriodo, tipoCalendario, type Passo } from '../lib/periodo';
  import { schermo } from '../lib/schermo.svelte';

  /**
   * Il periodo della barra di pagina (ADR 0028): `sx` · la scritta · `dx`, poi `oggi`. Toccare la scritta apre il
   * calendario del sistema (`input type="date"` o `"month"`); dove `showPicker` non c'è, il campo sta sopra la scritta
   * e il tocco lo raggiunge da solo.
   * `inizio` è «aaaa-mm-gg»; `sotto` è una riga piccola sotto la scritta («12 turni · 38 h»); con `max` non si va oltre.
   */
  let { inizio = $bindable(), passo, oggi = iso(new Date()), sotto, max }: {
    inizio: string; passo: Passo; oggi?: string; sotto?: string; max?: string;
  } = $props();

  const NOMI: Record<Passo, { prima: string; dopo: string; torna: string; scegli: string }> = {
    giorno: { prima: 'Giorno precedente', dopo: 'Giorno successivo', torna: 'Torna a oggi', scegli: 'Scegli il giorno' },
    settimana: { prima: 'Settimana precedente', dopo: 'Settimana successiva', torna: 'Torna a questa settimana', scegli: 'Scegli la settimana' },
    mese: { prima: 'Mese precedente', dopo: 'Mese successivo', torna: 'Torna a questo mese', scegli: 'Scegli il mese' },
  };
  const nomi = $derived(NOMI[passo]);
  const testo = $derived(etichettaPeriodo(inizio, passo, schermo.telefono));
  const aOggi = $derived(contiene(inizio, passo, oggi));
  const haDopo = $derived(sidopo(inizio, passo, max));

  const haPicker = typeof HTMLInputElement !== 'undefined' && 'showPicker' in HTMLInputElement.prototype;
  const haMese = (() => {
    if (typeof document === 'undefined') return false;
    const i = document.createElement('input');
    i.type = 'month';
    return i.type === 'month';
  })();
  const tipo = $derived(tipoCalendario(passo, haMese));
  let campo = $state<HTMLInputElement>();

  function apri() {
    try {
      campo?.showPicker();
    } catch {
      campo?.focus(); // il browser non lo permette qui: il campo resta raggiungibile con la tastiera
    }
  }
  function scelto() {
    const scelta = campo ? daCalendario(campo.value, passo) : null;
    if (!scelta) return;
    inizio = limitaPeriodo(scelta, passo, max);
    if (campo) campo.value = perCalendario(inizio, tipo); // un valore oltre `max` torna dentro
  }
</script>

<div class="periodo">
  <Azione cornice icona="sx" etichetta={nomi.prima} onclick={() => (inizio = spostaPeriodo(inizio, passo, -1))} />
  <span class="scritta-box">
    <button type="button" class="scritta" onclick={apri}>
      <b class="t nowrap">{testo}</b>
      {#if sotto}<span class="muted s nowrap">{sotto}</span>{/if}
      <span class="sr">{nomi.scegli}</span>
    </button>
    <input bind:this={campo} class="cal" class:diretto={!haPicker} type={tipo} value={perCalendario(inizio, tipo)} max={max ? perCalendario(max, tipo) : undefined} aria-label={nomi.scegli}
      aria-hidden={haPicker ? 'true' : undefined} tabindex={haPicker ? -1 : 0} onchange={scelto} />
  </span>
  <Azione cornice icona="dx" etichetta={nomi.dopo} disabled={!haDopo} onclick={() => (inizio = spostaPeriodo(inizio, passo, 1))} />
  <Azione cornice icona="oggi" etichetta={nomi.torna} disabled={aOggi} onclick={() => (inizio = inizioPeriodo(oggi, passo))} />
</div>

<style>
  .periodo { display: inline-flex; align-items: center; gap: var(--space-4); min-width: 0; }
  .scritta-box { position: relative; display: inline-flex; min-width: 0; }
  .scritta {
    display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 44px; padding: 0 var(--space-8);
    border: none; border-radius: var(--radius-md); background: transparent; color: var(--ink); font: inherit; cursor: pointer; line-height: 1.2;
  }
  .scritta:hover { background: var(--surface-sunken); }
  .t { font-size: var(--text-base); font-weight: 700; }
  .s { font-size: var(--text-xs); }
  /* il campo vero sta sopra la scritta, invisibile: il calendario si apre lì sotto */
  .cal { position: absolute; inset: 0; width: 100%; height: 100%; opacity: 0; pointer-events: none; border: none; padding: 0; }
  .cal.diretto { pointer-events: auto; cursor: pointer; }
  /* senza showPicker il campo prende il focus al posto del pulsante: l'anello si vede sulla scritta */
  .scritta-box:has(.cal.diretto:focus-visible) .scritta { outline: 2px solid var(--cloro); outline-offset: 2px; }
</style>
