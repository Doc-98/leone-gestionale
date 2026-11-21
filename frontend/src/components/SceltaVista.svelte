<script lang="ts">
  import { salvaPreferenza } from '../lib/preferenze';
  import { schermo } from '../lib/schermo.svelte';
  import { vistaControllo } from '../lib/periodo';
  import { vistaEffettiva, type OpzioneVista } from '../lib/viste';

  /**
   * La vista della barra di pagina (ADR 0028): fino a 3 opzioni un segmentato, oltre una tendina. Le opzioni sono già
   * quelle usabili su questo schermo (`opzioniUsabili`); se la scelta salvata non c'è più tra queste, si mostra la
   * prima. Con `preferenza` la scelta resta su questo dispositivo, per l'account.
   */
  let { opzioni, valore = $bindable(), etichetta, preferenza }: {
    opzioni: readonly Omit<OpzioneVista, 'solo'>[]; valore: string; etichetta: string; preferenza?: string;
  } = $props();

  const attiva = $derived(vistaEffettiva(valore, opzioni));
  const nome = (o: Omit<OpzioneVista, 'solo'>) => (schermo.telefono && o.breve ? o.breve : o.nome);

  function scegli(id: string) {
    valore = id;
    if (preferenza) salvaPreferenza(preferenza, id);
  }
</script>

{#if vistaControllo(opzioni.length) === 'segmentato'}
  <div class="seg vista" role="group" aria-label={etichetta} style="grid-template-columns: repeat({opzioni.length}, auto)">
    {#each opzioni as o (o.id)}
      <button type="button" aria-pressed={attiva === o.id} onclick={() => scegli(o.id)}>{nome(o)}</button>
    {/each}
  </div>
{:else}
  <select class="inp tendina" aria-label={etichetta} value={attiva} onchange={(e) => scegli(e.currentTarget.value)}>
    {#each opzioni as o (o.id)}<option value={o.id}>{o.nome}</option>{/each}
  </select>
{/if}

<style>
  .vista button { padding: 0 var(--space-12); }
  .tendina { width: auto; max-width: 100%; height: 44px; appearance: auto; }
</style>
