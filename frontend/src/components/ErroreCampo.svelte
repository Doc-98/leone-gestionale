<script lang="ts">
  import type { Snippet } from 'svelte';

  /**
   * Il messaggio breve sotto un campo scritto male (design system: «gli errori dicono cosa fare»): fondo d'errore e
   * triangolo d'avviso. Il campo lo richiama con `aria-describedby={id}` e `aria-invalid`.
   *   <ErroreCampo id="err-giorni">Da 0 a 365 giorni.</ErroreCampo>
   */
  let { id, children }: { id: string; children: Snippet } = $props();
</script>

<span class="msg err small" {id}>{@render children()}</span>
