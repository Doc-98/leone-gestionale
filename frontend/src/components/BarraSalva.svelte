<script lang="ts">
  import { testoModifiche } from '../lib/salvataggio';

  /**
   * La barra di salvataggio delle impostazioni di pagina (design system, «Barra di salvataggio»): una sola, uguale in
   * tutte le pagine. Compare solo quando ci sono modifiche e dice quante («1 modifica da salvare»); «Annulla» le butta,
   * «Salva» le salva tutte insieme e resta spento finché la bozza non è valida.
   *   <BarraSalva modifiche={contaModifiche(prima, bozza)} valida={!errore} onannulla={annulla} onsalva={salva} />
   * Il «quando vale» o «per chi» non sta qui: va in un fumetto accanto al titolo della sezione.
   */
  let {
    modifiche,
    valida = true,
    onannulla,
    onsalva,
  }: {
    /** Quante cose sono cambiate (`contaModifiche`); 0 = la barra non c'è */
    modifiche: number;
    /** false: «Salva» è disabilitato */
    valida?: boolean;
    onannulla: () => void;
    onsalva: () => void;
  } = $props();
</script>

<!-- La regione `status` c'è sempre, anche vuota, e contiene solo il testo: così la prima comparsa viene annunciata e
     i pulsanti non vengono letti. Senza modifiche la barra si nasconde alla vista ma la regione resta nella pagina. -->
<div class="salva-bar" class:nascosta={modifiche <= 0}>
  <span class="testo" role="status">{modifiche > 0 ? testoModifiche(modifiche) : ''}</span>
  {#if modifiche > 0}
    <button type="button" class="vuoto" onclick={onannulla}>Annulla</button>
    <button type="button" class="chiaro" disabled={!valida} onclick={onsalva}>Salva</button>
  {/if}
</div>

<style>
  /* resta in vista in fondo alla pagina, una pillola centrata larga quanto serve; sotto i 1024 px sopra la barra di navigazione (60 px, fissa) */
  .salva-bar {
    position: sticky; bottom: var(--space-16); align-self: center; justify-self: center; width: fit-content; max-width: 100%; margin-inline: auto; box-sizing: border-box;
    z-index: 6; display: flex; align-items: center; gap: var(--space-12); flex-wrap: wrap;
    padding: var(--space-8) var(--space-8) var(--space-8) var(--space-16); border-radius: var(--radius-lg); background: var(--hero, var(--fill)); color: var(--on-ink);
    border: 1px solid var(--barra-bordo); box-shadow: var(--shadow-barra); font-size: var(--text-md);
  }
  .testo { white-space: nowrap; }
  .nascosta { position: absolute; width: 1px; height: 1px; padding: 0; overflow: hidden; clip-path: inset(50%); border: none; box-shadow: none; }
  @media (max-width: 1023.98px) { .salva-bar { bottom: calc(env(safe-area-inset-bottom, 0px) + 60px + var(--space-12)); } }
  /* telefono stretto: il testo ha la sua riga e i due pulsanti stanno insieme sotto, a destra */
  @media (max-width: 420px) {
    .salva-bar { gap: var(--space-8); justify-content: flex-end; padding-right: var(--space-8); }
    .testo { flex: 1 1 100%; }
  }
  /* alti 38 px da vedere, 44 px da toccare (l'area in più è invisibile) */
  button { position: relative; height: 38px; padding: 0 var(--space-16); border-radius: var(--radius-md); font: inherit; font-size: var(--text-md); font-weight: 700; cursor: pointer; white-space: nowrap; }
  button::after { content: ''; position: absolute; left: 0; right: 0; top: 50%; height: 44px; transform: translateY(-50%); }
  .chiaro { border: none; background: var(--chiaro-su-hero); color: var(--on-chiaro-su-hero); }
  button:disabled { opacity: 0.5; cursor: default; }
  .vuoto { border: 1px solid color-mix(in srgb, var(--on-ink) 35%, transparent); background: transparent; color: var(--on-ink); font-weight: 600; }
</style>
