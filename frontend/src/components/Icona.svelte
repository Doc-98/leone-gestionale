<script lang="ts">
  import { ICONE, type NomeIcona } from '../lib/icone';

  /** Un'icona del set (src/lib/icone.ts). Sempre aria-hidden: il nome lo porta il controllo che la contiene. */
  let { nome, class: cls = '' }: { nome: NomeIcona; class?: string } = $props();
</script>

<svg class="ic {cls}" viewBox="0 0 24 24" aria-hidden="true">{@html ICONE[nome]}</svg>
