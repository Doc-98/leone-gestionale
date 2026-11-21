<script lang="ts">
  import { tick } from 'svelte';
  import Icona from './Icona.svelte';
  import Conferma from './Conferma.svelte';
  import Foglio from './Foglio.svelte';
  import { schermo } from '../lib/schermo.svelte';
  import { portaleEffimero } from '../lib/portale';
  import { posizionaMenu } from '../lib/posiziona';
  import { ordinaVoci, voceDopoTasto, type VoceMenu } from '../lib/menu';

  /**
   * Il menu «⋯» (design system, «Componenti comuni»): le azioni rare di una pagina o di una riga.
   *   <MenuAltro voci={[{ testo: "Modifica l'anagrafica", icona: 'matita', onclick: … }, …]} />
   * - Sul computer (`schermo.larga`, da 1024 px) un pannello sotto il pulsante, in <body> (esce dalle card) e sempre dentro i
   *   margini; sul telefono e sul tablet un foglio dal basso con `etichetta` per titolo.
   * - Al massimo 7 voci; in fondo la voce che elimina (rossa, con i due tocchi dentro la voce, `elimina`) e prima di lei la
   *   voce rossa che apre un modulo (`rossa`, niente due tocchi). `separatore` mette una linea sopra la voce.
   * - Tastiera da menu: all'apertura il focus va alla prima voce; su e giù a giro, Inizio e Fine; Esc chiude e rimette il focus
   *   sul pulsante; Tab chiude; un tocco fuori chiude. Scegliere una voce chiude il menu, rimette il focus sul pulsante e
   *   poi esegue l'azione (che può spostare il focus altrove, per esempio in un modulo).
   */
  let { voci, etichetta = 'Altre azioni', pulsante: nomePulsante }: {
    voci: VoceMenu[];
    /** Il nome del pulsante e, sul telefono, il titolo del foglio. */
    etichetta?: string;
    /** Il nome del pulsante se diverso dal titolo del foglio (di riga: «Altre azioni per Giulia Rossi»). */
    pulsante?: string;
  } = $props();

  const uid = $props.id();
  const id = `menu-${uid}`;
  const MARGINE = 16;
  /** Più di 7 voci o due che eliminano: in sviluppo un errore chiaro, in produzione le voci restano com'erano (la pagina non si rompe). */
  const ordinate = $derived.by(() => {
    try {
      return ordinaVoci(voci);
    } catch (e) {
      if (import.meta.env.DEV) throw e;
      return voci;
    }
  });
  // l'errore si vede subito al disegno, non solo all'apertura
  $effect.pre(() => void ordinate);

  let aperto = $state(false);
  let bottone = $state<HTMLButtonElement>();
  let menuEl = $state<HTMLElement>();
  let pannello = $state<HTMLElement>();
  let pos = $state<{ x: number; y: number } | null>(null);
  /** Il layout con cui il menu si è aperto (pannello o foglio): se la finestra cambia mentre è aperto resta com'era. */
  let comePannello = $state(false);

  function apri() {
    comePannello = schermo.larga;
    aperto = true;
  }
  /** `ritorna`: il focus torna al pulsante (Esc, voce scelta); con il tocco fuori resta dove l'ha messo chi ha toccato. */
  function chiudi(ritorna = true) {
    if (!aperto) return;
    aperto = false;
    if (ritorna) bottone?.focus();
  }
  function scegli(v: VoceMenu) {
    chiudi(true);
    v.onclick();
  }

  const voceEls = () => (menuEl ? [...menuEl.querySelectorAll<HTMLButtonElement>('[role="menuitem"]')] : []);
  function tasto(e: KeyboardEvent) {
    if (e.key === 'Tab') {
      // il focus torna al pulsante e Tab prosegue da lì
      chiudi(true);
      return;
    }
    const els = voceEls();
    const spente = new Set(els.flatMap((el, k) => (el.disabled ? [k] : [])));
    const da = els.indexOf(document.activeElement as HTMLButtonElement);
    const verso = voceDopoTasto(e.key, da, els.length, spente);
    if (verso < 0) return;
    e.preventDefault();
    els[verso]?.focus();
  }

  function misuraVista() {
    const d = document.documentElement;
    return { w: d.clientWidth || window.innerWidth, h: d.clientHeight || window.innerHeight };
  }
  function ricalcola() {
    if (!bottone || !pannello) return;
    const a = bottone.getBoundingClientRect();
    const b = pannello.getBoundingClientRect();
    const p = posizionaMenu({ x: a.x, y: a.y, w: a.width, h: a.height }, { w: b.width, h: b.height }, misuraVista(), { margine: MARGINE });
    pos = { x: p.x, y: p.y };
  }

  $effect(() => {
    if (!aperto) {
      pos = null;
      return;
    }
    tick().then(async () => {
      if (comePannello) {
        ricalcola();
        await tick(); // il pannello si vede solo da misurato: il focus non va su un elemento nascosto
      }
      voceEls().find((el) => !el.disabled)?.focus();
    });
    const esc = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      // un foglio aperto sotto (il menu dentro un modulo) non si chiude insieme al menu
      e.stopPropagation();
      chiudi(true);
    };
    const fuori = (e: PointerEvent) => {
      const t = e.target as Node | null;
      if (t && (bottone?.contains(t) || pannello?.contains(t))) return;
      chiudi(false);
    };
    window.addEventListener('keydown', esc, true);
    if (comePannello) {
      window.addEventListener('pointerdown', fuori, true);
      window.addEventListener('resize', ricalcola);
      window.addEventListener('scroll', ricalcola, true);
    }
    return () => {
      window.removeEventListener('keydown', esc, true);
      window.removeEventListener('pointerdown', fuori, true);
      window.removeEventListener('resize', ricalcola);
      window.removeEventListener('scroll', ricalcola, true);
    };
  });
</script>

<button
  bind:this={bottone}
  type="button"
  class="azione cornice mn-pulsante"
  aria-label={nomePulsante ?? etichetta}
  title={nomePulsante ?? etichetta}
  aria-haspopup="menu"
  aria-expanded={aperto}
  aria-controls={aperto ? id : undefined}
  onclick={() => (aperto ? chiudi(true) : apri())}
><Icona nome="altro" /></button>

{#snippet elenco()}
  <div bind:this={menuEl} {id} class="mn-voci" role="menu" aria-label={etichetta} tabindex="-1" onkeydown={tasto}>
    {#each ordinate as v, k (`${k}-${v.testo}`)}
      {#if v.separatore}<div class="mn-sep" role="separator"></div>{/if}
      {#if v.elimina}
        <Conferma role="menuitem" class="mn-voce rossa" domanda={v.elimina} disabled={v.disabled} onclick={() => scegli(v)}>
          <Icona nome="cestino" />{v.testo}
        </Conferma>
      {:else}
        <button type="button" role="menuitem" class="mn-voce" class:rossa={v.rossa} disabled={v.disabled} onclick={() => scegli(v)}>
          <Icona nome={v.icona} />{v.testo}
        </button>
      {/if}
    {/each}
  </div>
{/snippet}

{#if aperto}
  {#if comePannello}
    <div
      bind:this={pannello}
      class="mn-pannello"
      class:misurato={pos !== null}
      use:portaleEffimero
      style:left={pos ? `${pos.x}px` : '0'}
      style:top={pos ? `${pos.y}px` : '0'}
    >
      {@render elenco()}
    </div>
  {:else}
    <Foglio titolo={etichetta} chiudi={() => chiudi(true)} dalBasso troncaTitolo>
      <div bind:this={pannello} class="mn-foglio">{@render elenco()}</div>
    </Foglio>
  {/if}
{/if}

<style>
  .mn-pulsante { z-index: 1; }
  .mn-pulsante[aria-expanded='true'] { background: var(--cloro-soft); color: var(--cloro-ink); border-color: var(--cloro); }

  .mn-pannello {
    position: fixed; z-index: 50; box-sizing: border-box; padding: var(--space-6);
    width: max-content; min-width: 220px; max-width: calc(100vw - 2 * var(--space-16));
    background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md);
    box-shadow: 0 6px 20px rgba(16, 33, 43, 0.14);
    visibility: hidden; /* finché non è misurato e messo al suo posto: niente salto */
  }
  .mn-pannello.misurato { visibility: visible; }
  .mn-voci { display: flex; flex-direction: column; outline: none; }
  .mn-voci :global(.mn-voce) {
    display: flex; align-items: center; gap: var(--space-10); min-height: 44px; padding: 0 var(--space-10); border: none; background: none;
    border-radius: var(--radius-sm); font: inherit; font-size: var(--text-md); color: var(--ink); cursor: pointer; text-align: left; white-space: nowrap;
  }
  .mn-voci :global(.mn-voce:hover:not(:disabled)) { background: var(--carta); }
  .mn-voci :global(.mn-voce:disabled) { opacity: 0.5; cursor: default; }
  .mn-voci :global(.mn-voce .ic) { width: 18px; height: 18px; color: var(--muted); flex-shrink: 0; }
  .mn-voci :global(.mn-voce.rossa), .mn-voci :global(.mn-voce.rossa .ic) { color: var(--danger); }
  /* la voce armata (due tocchi) la disegna Conferma: bordo rosso pieno; qui solo l'allineamento nel menu */
  .mn-voci :global(.mn-voce.conf) { width: 100%; }
  .mn-sep { height: 1px; background: var(--line-soft); margin: var(--space-4) var(--space-6); }
  /* nel foglio dal basso le voci stanno a tutta larghezza, senza cornice */
  .mn-foglio { display: flex; flex-direction: column; }
</style>
