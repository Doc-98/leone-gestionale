<script lang="ts">
  import type { Snippet } from 'svelte';
  import Marchio from './Marchio.svelte';

  /**
   * La forma delle pagine prima di entrare (accesso, primo accesso): a sinistra il marchio e la società su fondo blu
   * notte con le corsie, a destra il modulo. Sul telefono il pannello scuro diventa una fascia in alto.
   */
  let { societa = '', titolo, testo = '', testoSempre = false, piede = '', children }: {
    societa?: string; titolo: string; testo?: string;
    /** Sul telefono la frase di accompagnamento si nasconde; qui no (es. il primo accesso, dove spiega perché cambiare la password). */
    testoSempre?: boolean;
    piede?: string; children: Snippet;
  } = $props();
</script>

<main class="acc">
  <section class="acc-hero" aria-label="La società">
    <div class="corsie" aria-hidden="true"><i></i><i></i><i></i><i></i></div>
    <div class="marchio-a">
      <Marchio misura={44} />
      <span class="nome"><b>Leone</b><span>{societa || 'Gestionale'}</span></span>
    </div>
    <div class="frase">
      <h2>{titolo}</h2>
      {#if testo}<p class:sempre={testoSempre}>{testo}</p>{/if}
    </div>
    {#if piede}<div class="piede">{piede}</div>{/if}
  </section>
  <section class="acc-form">
    <div class="acc-card">{@render children()}</div>
  </section>
</main>

<style>
  .acc { display: flex; flex-wrap: wrap; min-height: 100vh; min-height: 100dvh; background: var(--carta); }
  .acc-hero {
    flex: 1 1 520px; position: relative; overflow: hidden; box-sizing: border-box; padding: 48px 56px; display: flex; flex-direction: column;
    justify-content: space-between; gap: 40px; background: var(--hero); color: var(--on-ink);
  }
  .marchio-a { position: relative; z-index: 1; display: flex; align-items: center; gap: var(--space-12); }
  .nome { display: flex; flex-direction: column; gap: 1px; }
  .nome b { font-family: var(--font-display); font-weight: 800; font-size: 26px; line-height: 1; letter-spacing: -0.01em; }
  .nome span { font-size: var(--text-2xs); font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; color: var(--on-ink-muted); }
  .frase { position: relative; z-index: 1; max-width: 460px; display: flex; flex-direction: column; gap: var(--space-12); }
  .frase h2 { margin: 0; font-family: var(--font-display); font-weight: 800; font-size: var(--text-5xl); line-height: 1.05; letter-spacing: -0.015em; }
  .frase p { margin: 0; color: var(--on-ink-soft); font-size: var(--text-lg); }
  .piede { position: relative; z-index: 1; color: var(--on-ink-faint); font-size: var(--text-sm); }
  /* le corsie: fasce arrotondate che escono dal bordo, come nella copertina del design system */
  .corsie { position: absolute; right: -40px; top: 40px; bottom: 40px; width: 300px; display: flex; flex-direction: column; justify-content: center; gap: var(--space-14); }
  .corsie i { display: block; height: 64px; border-radius: 22px; background: var(--on-ink-veil); }
  .corsie i:nth-child(2) { background: var(--cloro); margin-left: 60px; }
  .corsie i:nth-child(3) { margin-left: 120px; }
  .corsie i:nth-child(4) { margin-left: 30px; height: 28px; border-radius: var(--radius-md); }
  .acc-form { flex: 1 1 480px; display: flex; justify-content: center; align-items: center; padding: 40px var(--space-20); box-sizing: border-box; }
  .acc-card { width: 100%; max-width: 420px; display: flex; flex-direction: column; gap: var(--space-16); }

  /* telefono: una fascia in alto, poi il modulo */
  @media (max-width: 1000px) {
    .acc { flex-direction: column; flex-wrap: nowrap; }
    .acc-hero { flex: none; padding: calc(env(safe-area-inset-top, 0px) + 24px) 20px 22px; gap: 18px; }
    .frase h2 { font-size: var(--text-3xl); }
    .frase p:not(.sempre), .piede { display: none; }
    .frase p.sempre { font-size: var(--text-md); }
    .corsie { right: -60px; top: 10px; bottom: auto; width: 180px; gap: var(--space-8); }
    .corsie i { height: 30px; border-radius: var(--radius-md); }
    .corsie i:nth-child(4) { height: 14px; border-radius: 7px; }
    .acc-form { flex: 1; align-items: flex-start; padding: var(--space-20) var(--space-16) calc(env(safe-area-inset-bottom, 0px) + var(--space-20)); }
  }
</style>
