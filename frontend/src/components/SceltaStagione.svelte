<script lang="ts">
  import { untrack } from 'svelte';
  import type { Stagione } from '../lib/api';
  import { iso } from '../lib/date';
  import { classePillStagione, ordinaStagioni, stagioneIniziale, statoStagione } from '../lib/stagioni';

  /**
   * La scelta della stagione (design system, «Scelta della stagione»; Calendario, Listino, Regole di prezzo): una
   * tendina col solo nome («2026/27») e, a destra, la pillola dello stato della stagione scelta: «in corso» verde,
   * «finita» grigia, «futura» azzurra (`lib/stagioni.ts`). Una riga sola, che non va a capo.
   * - `valore` è l'id della stagione scelta (`bind:valore`). Se è vuoto o non c'è tra le stagioni, il componente sceglie
   *   `stagioneIniziale` (in corso, poi la futura più vicina, poi l'ultima finita) e lo comunica con `onchange`.
   * - `onchange(id)` parte quando la persona cambia stagione e quando il componente sceglie al posto suo.
   * - La tendina è larga quanto il nome più lungo; ha `aria-label` = `etichetta` e `aria-describedby` verso la pillola.
   * - Senza stagioni non disegna niente: la pagina mostra il suo stato vuoto.
   */
  let {
    stagioni,
    valore = $bindable(),
    oggi = iso(new Date()),
    etichetta = 'Stagione',
    onchange,
  }: {
    stagioni: Stagione[];
    valore?: string;
    /** «AAAA-MM-GG»; di default il giorno di oggi */
    oggi?: string;
    etichetta?: string;
    onchange?: (id: string) => void;
  } = $props();

  const uid = $props.id();
  const ordinate = $derived(ordinaStagioni(stagioni));
  const scelta = $derived(ordinate.find((s) => s.id === valore));
  const stato = $derived(scelta ? statoStagione(scelta, oggi) : null);
  const larghezza = $derived(Math.max(0, ...ordinate.map((s) => s.nome.length)));

  // senza una scelta valida si parte dalla stagione iniziale
  $effect(() => {
    if (!stagioni.length || scelta) return;
    const id = stagioneIniziale(stagioni, oggi);
    valore = id;
    untrack(() => onchange?.(id));
  });

  function cambia(e: Event & { currentTarget: HTMLSelectElement }) {
    valore = e.currentTarget.value;
    onchange?.(valore);
  }
</script>

{#if ordinate.length}
  <span class="scelta-stagione">
    <select class="inp" aria-label={etichetta} aria-describedby={`${uid}-stato`} value={valore} onchange={cambia} style:--nome={`${larghezza}ch`}>
      {#each ordinate as s (s.id)}<option value={s.id}>{s.nome}</option>{/each}
    </select>
    {#if stato}<span class={classePillStagione(stato)} id={`${uid}-stato`}>{stato}</span>{/if}
  </span>
{/if}

<style>
  .scelta-stagione { display: inline-flex; align-items: center; gap: var(--space-8); white-space: nowrap; flex-shrink: 0; }
  select.inp { appearance: auto; width: calc(var(--nome) + 3 * var(--space-20)); /* nome più lungo + padding + freccia */ min-width: 0; }
</style>
