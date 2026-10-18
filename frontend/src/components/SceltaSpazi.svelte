<script lang="ts">
  import type { SpazioPiano } from '../lib/api';

  /** Vasche e corsie da toccare: la vasca intera oppure alcune corsie. */
  let { spazi, scelti = $bindable(), vuotoSignifica = '' }: {
    spazi: SpazioPiano[];
    scelti: string[];
    /** Se indicato, nessuna scelta è ammessa e vuol dire questo (es. «Tutto l'impianto»). */
    vuotoSignifica?: string;
  } = $props();

  const vasche = $derived(spazi.filter((s) => !s.padreId && (s.tipo === 'VASCA' || s.tipo === 'CORSIA')));
  const corsieDi = (id: string) => spazi.filter((s) => s.padreId === id);

  function alterna(id: string, vascaId: string | null) {
    let nuovi = scelti.includes(id) ? scelti.filter((x) => x !== id) : [...scelti, id];
    // la vasca intera e le sue corsie si escludono
    if (vascaId) nuovi = nuovi.filter((x) => x !== vascaId);
    else nuovi = nuovi.filter((x) => !corsieDi(id).some((c) => c.id === x));
    scelti = nuovi;
  }
</script>

<div class="scelta">
  {#if vuotoSignifica}
    <button type="button" class="chip" aria-pressed={scelti.length === 0} onclick={() => (scelti = [])}>{vuotoSignifica}</button>
  {/if}
  {#each vasche as v (v.id)}
    {@const corsie = corsieDi(v.id)}
    <div class="vasca">
      <button type="button" class="chip" aria-pressed={scelti.includes(v.id)} onclick={() => alterna(v.id, null)}>
        {v.nome}{corsie.length ? ' (intera)' : ''}
      </button>
      {#if corsie.length}
        <div class="corsie" role="group" aria-label="Corsie di {v.nome}">
          {#each corsie as c (c.id)}
            <button type="button" class="chip corsia" aria-pressed={scelti.includes(c.id)} onclick={() => alterna(c.id, v.id)}>
              {c.nome.replace(/^Corsia\s*/i, '')}
            </button>
          {/each}
        </div>
      {/if}
    </div>
  {/each}
</div>

<style>
  .scelta { display: flex; flex-direction: column; gap: var(--space-10); }
  .vasca { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-8); }
  .corsie { display: flex; flex-wrap: wrap; gap: var(--space-6); }
  .corsia { min-width: 40px; padding: 0 var(--space-10); }
</style>
