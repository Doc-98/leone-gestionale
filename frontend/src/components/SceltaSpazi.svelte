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
  const tutteLeCorsie = (id: string) => spazi.filter((s) => s.padreId === id);
  const assettiDi = (id: string) => [...new Set(tutteLeCorsie(id).map((c) => c.assetto ?? 0))].sort((a, b) => b - a);
  // assetto mostrato per ogni vasca: quello delle corsie già scelte, altrimenti il più fitto
  let assettoScelto = $state<Record<string, number>>({});
  const assettoDi = (id: string) => {
    const dalleScelte = tutteLeCorsie(id).find((c) => scelti.includes(c.id))?.assetto;
    return assettoScelto[id] ?? dalleScelte ?? assettiDi(id)[0];
  };
  const corsieDi = (id: string) => tutteLeCorsie(id).filter((c) => (c.assetto ?? 0) === assettoDi(id));

  function cambiaAssetto(vascaId: string, a: number) {
    assettoScelto = { ...assettoScelto, [vascaId]: a };
    // le corsie dell'altro assetto non valgono più
    scelti = scelti.filter((x) => !tutteLeCorsie(vascaId).some((c) => c.id === x && (c.assetto ?? 0) !== a));
  }

  function alterna(id: string, vascaId: string | null) {
    let nuovi = scelti.includes(id) ? scelti.filter((x) => x !== id) : [...scelti, id];
    // la vasca intera e le sue corsie si escludono
    if (vascaId) nuovi = nuovi.filter((x) => x !== vascaId);
    else nuovi = nuovi.filter((x) => !tutteLeCorsie(id).some((c) => c.id === x));
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
      {#if assettiDi(v.id).length > 1}
        <div class="seg assetti" style="grid-template-columns: repeat({assettiDi(v.id).length}, 1fr)" role="group" aria-label="Divisione in corsie">
          {#each assettiDi(v.id) as a (a)}
            <button type="button" aria-pressed={assettoDi(v.id) === a} onclick={() => cambiaAssetto(v.id, a)}>a {a} corsie</button>
          {/each}
        </div>
      {/if}
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
  .assetti { width: 220px; }
</style>
