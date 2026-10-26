<script lang="ts">
  import type { PersonaBreve } from '../lib/api';

  /**
   * Uno o più istruttori, come pillole da accendere e spegnere. Le pillole sono le persone «proposte» (per un turno:
   * il parco del corso); dall'elenco «altri» (lo staff) se ne aggiunge una che non c'è ancora.
   */
  let { scelti = $bindable([]), proposte, altri = [], etichetta = 'Istruttori' }: {
    scelti: string[];
    proposte: PersonaBreve[];
    altri?: PersonaBreve[];
    etichetta?: string;
  } = $props();

  // chi è stato aggiunto da «altri» resta tra le pillole anche se poi si spegne
  let aggiunti = $state<PersonaBreve[]>([]);
  const pillole = $derived.by(() => {
    const viste = new Set<string>();
    const out: PersonaBreve[] = [];
    for (const p of [...proposte, ...aggiunti, ...altri.filter((a) => scelti.includes(a.id))]) {
      if (!viste.has(p.id)) (viste.add(p.id), out.push(p));
    }
    return out;
  });
  const daAggiungere = $derived(altri.filter((a) => !pillole.some((p) => p.id === a.id)));

  function cambia(id: string) {
    scelti = scelti.includes(id) ? scelti.filter((x) => x !== id) : [...scelti, id];
  }

  function aggiungi(e: Event) {
    const sel = e.currentTarget as HTMLSelectElement;
    const p = altri.find((a) => a.id === sel.value);
    if (p) {
      aggiunti = [...aggiunti, p];
      scelti = [...scelti, p.id];
    }
    sel.value = '';
  }
</script>

<div class="scelta" role="group" aria-label={etichetta}>
  <div class="chips">
    {#each pillole as p (p.id)}
      <button type="button" class="chip" aria-pressed={scelti.includes(p.id)} onclick={() => cambia(p.id)}>{p.nome}</button>
    {:else}
      <span class="muted small">Nessun istruttore ancora.</span>
    {/each}
  </div>
  {#if daAggiungere.length}
    <select class="inp aggiungi" aria-label="Aggiungi un istruttore" onchange={aggiungi}>
      <option value="">Aggiungi un istruttore…</option>
      {#each daAggiungere as p (p.id)}<option value={p.id}>{p.nome}</option>{/each}
    </select>
  {/if}
</div>

<style>
  .scelta { display: flex; flex-direction: column; gap: var(--space-8); }
  .aggiungi { appearance: auto; height: 40px; font-size: var(--text-md); max-width: 320px; }
</style>
