<script lang="ts">
  import Icona from './Icona.svelte';
  import { onDestroy } from 'svelte';
  import { api, type Passaggio } from '../lib/api';
  import { oraDi } from '../lib/date';

  /**
   * Gli avvisi per la segreteria: ogni passaggio di una persona «sotto controllo» compare qui, finché non si chiude.
   * Si controlla ogni 15 secondi; si parte da adesso, senza ripescare quelli già passati.
   */
  let avvisi = $state<Passaggio[]>([]);
  let ultimo = new Date().toISOString();
  const visti = new Set<string>();

  async function controlla() {
    try {
      const nuovi = (await api.segnalazioni(ultimo)).filter((p) => !visti.has(p.id));
      for (const p of nuovi) {
        visti.add(p.id);
        if (p.istante > ultimo) ultimo = p.istante;
      }
      if (nuovi.length) avvisi = [...nuovi.reverse(), ...avvisi].slice(0, 6);
    } catch {
      /* si riprova al giro dopo */
    }
  }
  const timer = setInterval(controlla, 15000);
  onDestroy(() => clearInterval(timer));

  const chiudi = (id: string) => (avvisi = avvisi.filter((a) => a.id !== id));
  const perche = (p: Passaggio) => p.motivi.filter((m) => m.codice.endsWith('SOTTO_CONTROLLO')).map((m) => m.messaggio.replace(/^.*?sotto controllo: |^Sotto controllo: /i, '')).join(' · ');
</script>

{#if avvisi.length}
  <div class="pila" role="status" aria-live="polite">
    {#each avvisi as a (a.id)}
      <div class="avviso card">
        <div class="testa">
          <span class="lbl">Sotto controllo · {a.verso === 'USCITA' ? 'uscita' : 'entrata'} alle {oraDi(a.istante)}</span>
          <button class="chiudi" aria-label="Chiudi l'avviso" onclick={() => chiudi(a.id)}><Icona nome="chiudi" /></button>
        </div>
        {#if a.persona}<a class="nome" href={`#/clienti/${a.persona.id}`} onclick={() => chiudi(a.id)}>{a.persona.nome} {a.persona.cognome}</a>{/if}
        <div class="small">{perche(a)}</div>
        <div class="small muted">{a.verde ? 'È passato' : 'Non è passato: ' + a.motivi.filter((m) => m.blocca).map((m) => m.messaggio).join(' · ')}</div>
      </div>
    {/each}
  </div>
{/if}

<style>
  .pila { position: fixed; right: var(--space-16); bottom: calc(env(safe-area-inset-bottom, 0px) + 80px); z-index: 30; display: flex; flex-direction: column;
    gap: var(--space-8); width: min(340px, calc(100vw - 32px)); }
  .avviso { padding: var(--space-10) var(--space-14); border: 2px solid var(--oro); background: var(--oro-bg); color: var(--oro-fg);
    box-shadow: 0 8px 24px rgba(16, 33, 43, 0.18); display: flex; flex-direction: column; gap: 2px; }
  .testa { display: flex; justify-content: space-between; align-items: center; }
  .testa .lbl { color: inherit; }
  .chiudi { border: none; background: none; font-size: 22px; line-height: 1; color: inherit; cursor: pointer; min-width: 32px; min-height: 32px; }
  .nome { font-weight: 700; font-size: var(--text-lg); color: inherit; }
  @media (min-width: 1024px) { .pila { bottom: var(--space-20); } }
</style>
