<script lang="ts">
  import { onDestroy, onMount, tick, untrack } from 'svelte';
  import { righeBlocco } from '../lib/blocco';
  import { portaleEffimero as portale } from '../lib/portale';
  import { messaggioErrore } from '../lib/api';

  /**
   * La finestra dell'eliminazione pesante (design system, «Eliminazione pesante»): per le eliminazioni rare che toccano
   * tanti dati (un gruppo con persone dentro) il doppio tocco non basta.
   *   <EliminazionePesante titolo="Eliminare il gruppo «Agonisti»?" conseguenze={['38 persone escono dal gruppo']}
   *     azione="Elimina il gruppo" onconferma={…} onannulla={…} />
   * - `role="alertdialog"`, portata in <body>; al centro sul computer (al massimo 440 px), dal basso sotto i 1024 px,
   *   con il velo dietro: il tocco sul velo equivale a «Annulla».
   * - All'apertura il fuoco va su «Annulla». Invio su «Annulla» ed Esc chiudono senza fare niente; il fuoco gira dentro
   *   la finestra (Tab); alla chiusura torna a chi l'aveva aperta.
   * - Il pulsante rosso porta il verbo e il conto alla rovescia, «Elimina il gruppo (5)»: è disabilitato finché non
   *   arriva a 0; il conto riparte a ogni apertura. Mentre `onconferma` lavora dice «Elimino…».
   * - Se la promessa di `onconferma` fallisce, la finestra resta aperta e il messaggio (due righe, attacco in grassetto)
   *   compare sotto le conseguenze. Se va a buon fine tocca a chi l'ha aperta chiuderla.
   */
  let { titolo, conseguenze, azione, onconferma, onannulla, secondi = 5 }: {
    titolo: string;
    conseguenze: string[];
    azione: string;
    onconferma: () => unknown;
    onannulla: () => void;
    secondi?: number;
  } = $props();

  const uid = $props.id();
  let mancano = $state(untrack(() => secondi));
  let lavoro = $state(false);
  let errore = $state('');
  let finestra = $state<HTMLDivElement>();
  let annulla = $state<HTMLButtonElement>();
  let timer: ReturnType<typeof setInterval> | undefined;

  const fermaConto = () => {
    clearInterval(timer);
    timer = undefined;
  };

  onMount(() => {
    const apriva = document.activeElement;
    annulla?.focus({ preventScroll: true });
    mancano = secondi;
    if (mancano > 0)
      timer = setInterval(() => {
        mancano -= 1;
        if (mancano <= 0) fermaConto();
      }, 1000);
    return () => {
      fermaConto();
      if (apriva instanceof HTMLElement && apriva !== document.body && document.contains(apriva)) apriva.focus({ preventScroll: true });
    };
  });
  onDestroy(fermaConto);

  const scritta = $derived(lavoro ? 'Elimino…' : mancano > 0 ? `${azione} (${mancano})` : azione);

  async function conferma() {
    if (mancano > 0 || lavoro) return;
    lavoro = true;
    errore = '';
    try {
      await onconferma();
    } catch (e) {
      errore = messaggioErrore(e, 'Non è stato possibile eliminare.');
      lavoro = false;
      await tick();
      annulla?.focus({ preventScroll: true });
    }
  }

  /** Annulla (pulsante, Esc, velo): non mentre l'eliminazione è in corso, la finestra resta finché la richiesta non finisce. */
  const annullaSeLibero = () => {
    if (!lavoro) onannulla();
  };

  function tasto(e: KeyboardEvent) {
    if (e.key === 'Escape') {
      e.preventDefault();
      e.stopPropagation();
      annullaSeLibero();
      return;
    }
    if (e.key !== 'Tab' || !finestra) return;
    // il fuoco resta dentro la finestra: Tab dall'ultimo pulsante torna al primo e viceversa
    const pulsanti = [...finestra.querySelectorAll<HTMLButtonElement>('button:not(:disabled)')];
    if (!pulsanti.length) return;
    const primo = pulsanti[0];
    const ultimo = pulsanti[pulsanti.length - 1];
    const attivo = document.activeElement;
    if (e.shiftKey && (attivo === primo || !finestra.contains(attivo))) {
      e.preventDefault();
      ultimo.focus();
    } else if (!e.shiftKey && (attivo === ultimo || !finestra.contains(attivo))) {
      e.preventDefault();
      primo.focus();
    }
  }
</script>

<svelte:window onkeydown={tasto} />

<div class="ep" use:portale>
  <div class="velo" role="presentation" onclick={annullaSeLibero}></div>
  <div class="finestra" role="alertdialog" aria-modal="true" aria-labelledby={`${uid}-t`} aria-describedby={`${uid}-c`} tabindex="-1" bind:this={finestra}>
    <h2 id={`${uid}-t`}>{titolo}</h2>
    <ul id={`${uid}-c`} class="conseguenze">
      {#each conseguenze as c, i (i)}<li>{c}</li>{/each}
    </ul>
    {#if errore}
      <p class="msg err nomargin errore" role="alert">{#each righeBlocco(errore) as r, k (k)}<span class="riga-b">{#if r.attacco}<b>{r.attacco}</b>{' '}{/if}{r.resto}{' '}</span>{/each}</p>
    {/if}
    <div class="azioni">
      <button type="button" class="btn btn-secondary" bind:this={annulla} disabled={lavoro} onclick={annullaSeLibero}>Annulla</button>
      <button type="button" class="btn btn-danger" class:pieno={mancano <= 0} disabled={mancano > 0 || lavoro} onclick={conferma}>{scritta}</button>
    </div>
  </div>
</div>

<style>
  .velo { position: fixed; inset: 0; background: rgba(16, 33, 43, 0.45); z-index: 60; }
  /* dal basso sotto i 1024 px (come il foglio), al centro sul computer */
  .finestra {
    position: fixed; left: 0; right: 0; bottom: 0; z-index: 61; max-height: 88vh; overflow-y: auto; box-sizing: border-box;
    max-width: 440px; margin: 0 auto; background: var(--carta); border-radius: var(--radius-xl) var(--radius-xl) 0 0;
    padding: var(--space-20) var(--space-16) calc(env(safe-area-inset-bottom, 0px) + var(--space-20));
    display: flex; flex-direction: column; gap: var(--space-14);
  }
  .finestra:focus { outline: none; }
  @media (min-width: 1024px) {
    .finestra { top: 50%; bottom: auto; transform: translateY(-50%); width: 440px; border-radius: var(--radius-xl); padding: var(--space-20); }
  }
  h2 { margin: 0; font-size: var(--text-xl); overflow-wrap: anywhere; }
  .conseguenze { margin: 0; padding: 0 0 0 var(--space-20); display: flex; flex-direction: column; gap: var(--space-6); font-size: var(--text-base); }
  .conseguenze li { overflow-wrap: anywhere; }
  .azioni { display: flex; justify-content: flex-end; gap: var(--space-10); flex-wrap: wrap; }
  .azioni :global(.btn) { white-space: nowrap; }
  /* abilitato: rosso pieno con testo chiaro (`danger` su `on-danger`); mentre conta resta il bordo, disabilitato */
  .azioni :global(.btn-danger.pieno) { background: var(--danger); color: var(--on-danger); border-color: var(--danger); }
  .errore { display: flex; flex-direction: column; gap: var(--space-4); }
  .errore b { color: inherit; font-weight: 700; }
</style>
