<script lang="ts">
  /**
   * Il simbolo del gestionale (direzione «La corsia», scelta a novembre 2026): una L di bordo vasca con i galleggianti
   * della corsia, su un quadrato verde cloro. Sotto i 24px i galleggianti si tolgono e la L si ingrossa.
   */
  let { misura = 44 }: { misura?: number } = $props();
  const piccolo = $derived(misura < 24);
</script>

<svg class="marchio-s" width={misura} height={misura} viewBox="0 0 64 64" aria-hidden="true">
  <rect width="64" height="64" rx={piccolo ? 14 : 16} fill="#0b6e78" />
  <path d="M22 14v34h26" fill="none" stroke="#fff" stroke-width={piccolo ? 9 : 7} stroke-linecap="round" stroke-linejoin="round" />
  {#if !piccolo}<path d="M32 26h16M32 37h16" fill="none" stroke="#fff" stroke-width="4" stroke-linecap="round" stroke-dasharray="0.1 7" />{/if}
</svg>

<style>
  .marchio-s { flex-shrink: 0; display: block; }
</style>
