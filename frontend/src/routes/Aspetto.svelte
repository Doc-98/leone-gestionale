<script lang="ts">
  import { tema, type SceltaTema } from '../lib/tema.svelte';

  /**
   * L'aspetto (ADR 0024): chiaro, scuro o come il sistema. È una scelta di chi usa questo dispositivo, non della
   * società: la vede chiunque, cambia subito e non passa dalla barra «Salva».
   */
  const SCELTE: { id: SceltaTema; nome: string; spiega: string }[] = [
    { id: 'sistema', nome: 'Come il sistema', spiega: 'Segue il dispositivo: chiaro di giorno, scuro di sera, se lo fa anche lui' },
    { id: 'chiaro', nome: 'Chiaro', spiega: 'Sempre chiaro' },
    { id: 'scuro', nome: 'Scuro', spiega: 'Sempre scuro: riposa gli occhi con poca luce' },
  ];
</script>

<section class="card sez" id="imp-tema" aria-label="Tema">
  <div class="sez-t"><h2 class="grow">Tema</h2><span class="spiega">vale su questo dispositivo, per chiunque lo usi</span></div>
  <div class="scelte" role="radiogroup" aria-label="Tema">
    {#each SCELTE as s (s.id)}
      <button type="button" class="scelta" role="radio" aria-checked={tema.scelta === s.id} onclick={() => tema.imposta(s.id)}>
        <!-- un'anteprima in miniatura, con i colori fissi del tema che mostra -->
        <span class="anteprima {s.id}" aria-hidden="true">
          <span class="meta chiaro"><i class="barra"></i><i class="riga"></i><i class="riga corta"></i><i class="pillola"></i></span>
          <span class="meta scuro"><i class="barra"></i><i class="riga"></i><i class="riga corta"></i><i class="pillola"></i></span>
        </span>
        <span class="testo"><span class="pallino" aria-hidden="true"></span><b>{s.nome}</b></span>
        <span class="muted small">{s.spiega}</span>
      </button>
    {/each}
  </div>
  <p class="muted small nomargin ora">Ora si vede <b>{tema.effettivo === 'scuro' ? 'scuro' : 'chiaro'}</b>{tema.scelta === 'sistema' ? ', come il dispositivo' : ''}.</p>
</section>

<style>
  .scelte { display: grid; grid-template-columns: repeat(3, minmax(0, 240px)); gap: var(--space-14); padding: var(--space-16); }
  .scelta { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-10); border-radius: var(--radius-md); border: 1.5px solid var(--line);
    background: var(--surface); font: inherit; color: var(--ink); text-align: left; cursor: pointer; }
  .scelta:hover { border-color: var(--line-strong); }
  .scelta[aria-checked='true'] { border-color: var(--cloro); box-shadow: 0 0 0 3px color-mix(in srgb, var(--cloro) 25%, transparent); }
  .anteprima { display: flex; height: 96px; border-radius: var(--radius-sm); overflow: hidden; border: 1px solid var(--line); }
  .meta { flex: 1; display: flex; flex-direction: column; gap: 7px; padding: 10px; }
  /* colori fissi: l'anteprima mostra il tema, non quello attuale */
  .meta.chiaro { background: #f6f4ef; --b: #10212b; --r: #c9d3d8; --p: #0e7c86; }
  .meta.scuro { background: #0e1a21; --b: #e6edf0; --r: #2b3c46; --p: #5ec4cc; }
  .anteprima.chiaro .scuro, .anteprima.scuro .chiaro { display: none; }
  .barra { display: block; width: 55%; height: 8px; border-radius: 4px; background: var(--b); }
  .riga { display: block; height: 6px; border-radius: 3px; background: var(--r); }
  .riga.corta { width: 70%; }
  .pillola { display: block; width: 38%; height: 12px; border-radius: 999px; background: var(--p); margin-top: auto; }
  .testo { display: flex; align-items: center; gap: var(--space-8); }
  .pallino { width: 18px; height: 18px; border-radius: 50%; border: 2px solid var(--line-strong); flex-shrink: 0; }
  .scelta[aria-checked='true'] .pallino { border: 5px solid var(--cloro); }
  .ora { padding: 0 var(--space-16) var(--space-16); }
  @media (max-width: 700px) {
    .scelte { grid-template-columns: 1fr; }
    .anteprima { height: 64px; }
  }
</style>
