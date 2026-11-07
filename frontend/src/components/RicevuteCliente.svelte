<script lang="ts">
  import Icona from './Icona.svelte';
  import RicevutaRiga from './RicevutaRiga.svelte';
  import { untrack } from 'svelte';
  import { api, messaggioErrore, type Ricevuta } from '../lib/api';

  /**
   * Le ricevute di un cliente, nella scheda: intestate a lui o con lui tra i beneficiari (es. quelle che il genitore ha
   * per il figlio). Le annullate restano, barrate, con il motivo. «versione» cambia quando il conto registra qualcosa.
   */
  let { personaId, versione = 0 }: { personaId: string; versione?: number } = $props();
  const id = untrack(() => personaId);
  let ricevute = $state<Ricevuta[]>([]);
  let errore = $state('');
  let tutte = $state(false);

  async function carica() {
    try {
      ricevute = await api.ricevuteDiPersona(id);
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  $effect(() => {
    void versione;
    untrack(carica);
  });
  const viste = $derived(tutte ? ricevute : ricevute.slice(0, 4));
</script>

{#if ricevute.length || errore}
  <div class="ricevute-c">
    <div class="lbl">Ricevute</div>
    {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}
    <div class="elenco">
      {#each viste as r (r.id)}<RicevutaRiga ricevuta={r} compatta cambiata={carica} />{/each}
    </div>
    {#if ricevute.length > 4}
      <button class="btn btn-quieto btn-sm sx" aria-expanded={tutte} onclick={() => (tutte = !tutte)}><Icona nome={tutte ? 'su' : 'giu'} />{tutte ? 'Solo le ultime' : `Tutte le ${ricevute.length} ricevute`}</button>
    {/if}
  </div>
{/if}

<style>
  .ricevute-c { display: flex; flex-direction: column; gap: var(--space-4); padding-top: var(--space-8); border-top: 1px solid var(--line); }
  .elenco > :global(.ric + .ric) { border-top: 1px solid var(--line-soft); }
  .sx { align-self: flex-start; min-height: 32px; }
</style>
