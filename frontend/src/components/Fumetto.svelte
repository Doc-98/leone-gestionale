<script lang="ts">
  import type { Snippet } from 'svelte';
  import { onDestroy, tick } from 'svelte';
  import Icona from './Icona.svelte';
  import { fumetti } from '../lib/fumetto.svelte';
  import { portaleEffimero } from '../lib/portale';
  import { posizionaBolla } from '../lib/posiziona';

  /**
   * Il fumetto «i» (design system, «Componenti comuni»): in pagina una riga corta, il resto qui.
   *   <Fumetto tema="le rate">Testo della spiegazione, al massimo cinque righe.</Fumetto>
   * - Il pulsante ha l'etichetta «Spiega le rate». Si apre al tocco, con Invio o spazio e, dove c'è il mouse, al
   *   passaggio (aperto dal passaggio si chiude uscendo; aperto dal clic resta finché non si tocca fuori, Esc o la «i»).
   * - La bolla sta in <body> (esce dalle card con `overflow: hidden`), sotto la «i» e sopra solo se sotto non c'è posto,
   *   sempre dentro i margini della schermata; la punta indica la «i». Ne sta aperto uno solo.
   * - Toggletip: accanto alla «i» c'è sempre un `<span class="sr" role="status">`, vuoto da chiuso; all'apertura, dopo un
   *   tick, vi entra il testo e lo screen reader lo legge. La bolla visibile è `aria-hidden` (il testo lo legge una volta
   *   sola). Il focus resta sulla «i».
   * - Nel fumetto niente link né azioni: ciò che serve sta nella riga corta in pagina.
   */
  let { tema, children }: { tema: string; children: Snippet } = $props();

  const uid = $props.id();
  const id = `fumetto-${uid}`;
  const aperto = $derived(fumetti.aperto === id);
  const CHIUSURA_MS = 150;
  const HOVER = '(hover: hover) and (pointer: fine)';
  const MARGINE = 16;

  let pulsante = $state<HTMLButtonElement>();
  /** Il testo per lo screen reader: entra solo dopo l'apertura, perché una regione `status` annuncia ciò che cambia. */
  let annuncio = $state(false);
  /** Larghezza massima della bolla: 280 px, o la finestra senza barra di scorrimento meno i margini. */
  let largMax = $state(280);
  let bolla = $state<HTMLDivElement>();
  let pos = $state<{ x: number; y: number; sotto: boolean; punta: number } | null>(null);
  /** Aperto dal passaggio del mouse (si chiude uscendo) e non ancora dal clic. */
  let daPassaggio = false;
  let timer: ReturnType<typeof setTimeout> | undefined;

  const ferma = () => {
    clearTimeout(timer);
    timer = undefined;
  };
  function chiudi() {
    ferma();
    daPassaggio = false;
    fumetti.chiudi(id);
  }
  function clic() {
    ferma();
    if (!aperto) {
      daPassaggio = false;
      fumetti.apri(id);
    } else if (daPassaggio) {
      daPassaggio = false; // aperto col mouse e poi cliccato: ora resta
    } else {
      chiudi();
    }
  }
  function entra(e: PointerEvent) {
    if (e.pointerType === 'touch' || e.pointerType === 'pen') return;
    if (!window.matchMedia?.(HOVER).matches) return;
    ferma();
    if (!aperto) {
      daPassaggio = true;
      fumetti.apri(id);
    }
  }
  function esce() {
    if (!aperto || !daPassaggio) return;
    ferma();
    timer = setTimeout(chiudi, CHIUSURA_MS);
  }
  function fuoriFuoco(e: FocusEvent) {
    const verso = e.relatedTarget as Node | null;
    if (aperto && verso && !bolla?.contains(verso)) chiudi();
  }

  /** Misura la «i» e la bolla (larga quanto il testo, fino a 280 px) e mette la bolla dove sta. */
  function ricalcola() {
    if (!pulsante || !bolla) return;
    const vista = misuraVista();
    largMax = Math.min(280, vista.w - 2 * MARGINE);
    const a = pulsante.getBoundingClientRect();
    const b = bolla.getBoundingClientRect();
    pos = posizionaBolla({ x: a.x, y: a.y, w: a.width, h: a.height }, { w: b.width, h: b.height }, vista, { margine: MARGINE });
  }
  /** La parte visibile della finestra: `clientWidth` non conta la barra di scorrimento (`innerWidth` sì). */
  function misuraVista() {
    const d = document.documentElement;
    return { w: d.clientWidth || window.innerWidth, h: d.clientHeight || window.innerHeight };
  }

  $effect(() => {
    if (!aperto) {
      pos = null;
      annuncio = false;
      return;
    }
    largMax = Math.min(280, misuraVista().w - 2 * MARGINE);
    tick().then(() => {
      ricalcola();
      annuncio = true;
    });
    const tasto = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      // Un foglio aperto sotto non si chiude insieme al fumetto: ma solo se il fumetto è «di chi lo sta usando»
      // (aperto con clic o tastiera, o con il focus sulla «i» o nella bolla). Aperto solo dal passaggio del mouse,
      // Esc lo chiude e passa anche agli altri. `stopPropagation` in cattura su window ferma gli ascoltatori in
      // bubbling (come quello di `Foglio`); gli ascoltatori in cattura registrati prima non li ferma.
      const a = document.activeElement;
      const suo = !daPassaggio || (!!a && (pulsante?.contains(a) || bolla?.contains(a)));
      if (suo) e.stopPropagation();
      chiudi();
    };
    const fuori = (e: PointerEvent) => {
      const t = e.target as Node | null;
      if (t && (pulsante?.contains(t) || bolla?.contains(t))) return;
      chiudi();
    };
    window.addEventListener('keydown', tasto, true);
    window.addEventListener('pointerdown', fuori, true);
    window.addEventListener('resize', ricalcola);
    window.addEventListener('scroll', ricalcola, true);
    return () => {
      window.removeEventListener('keydown', tasto, true);
      window.removeEventListener('pointerdown', fuori, true);
      window.removeEventListener('resize', ricalcola);
      window.removeEventListener('scroll', ricalcola, true);
      ferma();
    };
  });
  onDestroy(() => fumetti.chiudi(id));
</script>

<span class="fum">
  <button
    bind:this={pulsante}
    type="button"
    class="azione fum-i"
    aria-label={`Spiega ${tema}`}
    aria-expanded={aperto}
    aria-controls={id}
    onclick={clic}
    onpointerenter={entra}
    onpointerleave={esce}
    onfocusout={fuoriFuoco}
  ><Icona nome="info" /></button>
  <span class="sr" role="status">{#if aperto && annuncio}{@render children()}{/if}</span>
</span>

{#if aperto}
  <div
    bind:this={bolla}
    {id}
    class="bolla"
    class:sopra={pos ? !pos.sotto : false}
    class:misurata={pos !== null}
    aria-hidden="true"
    use:portaleEffimero
    style:left={pos ? `${pos.x}px` : '0'}
    style:top={pos ? `${pos.y}px` : '0'}
    style:max-width={`${largMax}px`}
    style:--punta={pos ? `${pos.punta}px` : '0'}
    onpointerenter={() => ferma()}
    onpointerleave={esce}
  >
    <div class="testo">{@render children()}</div>
  </div>
{/if}

<style>
  .fum { display: inline-flex; align-items: center; vertical-align: middle; flex-shrink: 0; }
  /* il pulsante è alto 40 px ma sta in una riga di testo senza allargarla: l'area da 44 px resta */
  .fum-i { margin-block: calc(var(--space-10) * -1); }
  /* sopra i vicini che vengono dopo: senza, i 2 px d'area di tocco oltre il bordo (::after da 44 px) li prendono loro */
  .fum-i { z-index: 1; }
  .fum-i[aria-expanded='true'] { background: var(--cloro-soft); color: var(--cloro-ink); }

  .bolla {
    position: fixed; z-index: 50; box-sizing: border-box;
    width: max-content; max-width: 280px; /* il limite vero lo mette lo stile in linea (finestra senza barra di scorrimento) */
    background: var(--surface); color: var(--ink); border: 1px solid var(--line); border-radius: var(--radius-md);
    padding: var(--space-10) var(--space-12); font-size: var(--text-sm); line-height: 1.4;
    visibility: hidden; /* finché non è misurata e messa al suo posto: niente salto */
  }
  .bolla.misurata { visibility: visible; }
  .testo { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 5; line-clamp: 5; overflow: hidden; overflow-wrap: break-word; }
  /* la punta: un quadratino ruotato, con il bordo solo sui due lati che si vedono */
  .bolla::before {
    content: ''; position: absolute; left: var(--punta); top: calc(var(--space-10) / -2 - 1px);
    width: var(--space-10); height: var(--space-10); background: var(--surface);
    border-left: 1px solid var(--line); border-top: 1px solid var(--line);
    transform: translateX(-50%) rotate(45deg);
  }
  .bolla.sopra::before {
    top: auto; bottom: calc(var(--space-10) / -2 - 1px);
    border: none; border-right: 1px solid var(--line); border-bottom: 1px solid var(--line);
  }
</style>
