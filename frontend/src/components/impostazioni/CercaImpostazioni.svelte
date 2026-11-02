<script lang="ts">
  import Icona from '../Icona.svelte';
  import { cercaImpostazioni, hrefVoce } from '../../lib/impostazioni';

  /**
   * La ricerca delle impostazioni: in cima a ogni pagina delle impostazioni (sul PC, ferma mentre si scorre),
   * così si cerca da qualsiasi punto. Ctrl K (o ⌘K) porta qui. I risultati si aprono sotto, senza spostare la pagina.
   */
  let { sezioni, telefono = false }: { sezioni: string[]; telefono?: boolean } = $props();

  let q = $state('');
  let attivo = $state(0);
  let aperto = $state(false);
  let campo: HTMLInputElement;
  const risultati = $derived(cercaImpostazioni(q, sezioni));

  function vai(i: number) {
    const v = risultati[i];
    if (!v) return;
    location.hash = hrefVoce(v);
    q = '';
    aperto = false;
    campo.blur();
  }

  function tasti(e: KeyboardEvent) {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      attivo = Math.min(risultati.length - 1, attivo + 1);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      attivo = Math.max(0, attivo - 1);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      vai(attivo);
    } else if (e.key === 'Escape') {
      q = '';
      campo.blur();
    }
  }

  function globale(e: KeyboardEvent) {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      campo?.focus();
      campo?.select();
    }
  }
  const mac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform);
</script>

<svelte:window onkeydown={globale} />

<div class="cerca" class:telefono>
  <label class="campo">
    <Icona nome="cerca" />
    <span class="sr">Cerca un'impostazione</span>
    <input class="inp" bind:this={campo} bind:value={q} placeholder={telefono ? 'Cerca: cauzione, quota, recesso…' : 'Cerca in tutte le impostazioni: cauzione, quota, recesso…'}
           oninput={() => { attivo = 0; aperto = true; }} onfocus={() => (aperto = true)} onblur={() => setTimeout(() => (aperto = false), 150)} onkeydown={tasti}
           role="combobox" aria-expanded={aperto && !!q.trim()} aria-controls="risultati-impostazioni" autocomplete="off" />
    {#if !telefono}<kbd>{mac ? '⌘K' : 'Ctrl K'}</kbd>{/if}
  </label>
  {#if aperto && q.trim()}
    <div class="card pop" id="risultati-impostazioni" role="listbox">
      {#each risultati as v, i (v.titolo)}
        <a href={hrefVoce(v)} class:on={i === attivo} role="option" aria-selected={i === attivo} onmousedown={(e) => e.preventDefault()} onclick={(e) => { e.preventDefault(); vai(i); }}>
          <span class="t"><b>{v.titolo}</b><span>{v.dove}</span></span><Icona nome="dx" class="chev" />
        </a>
      {:else}
        <p class="muted small nomargin vuoto">Nessuna impostazione con «{q.trim()}».</p>
      {/each}
    </div>
  {/if}
</div>

<style>
  .cerca { position: relative; width: 100%; max-width: 600px; margin: 0 auto; }
  .campo { position: relative; display: block; }
  .campo :global(.ic) { position: absolute; left: 14px; top: 50%; transform: translateY(-50%); width: 18px; height: 18px; color: var(--muted); }
  .campo .inp { width: 100%; box-sizing: border-box; height: 44px; padding-left: 44px; padding-right: 70px; border-radius: var(--radius-pill); background: var(--carta); }
  .campo .inp:focus { background: var(--surface); }
  .telefono .campo .inp { padding-right: 14px; border-radius: var(--radius-md); background: var(--surface); }
  kbd { position: absolute; right: 12px; top: 50%; transform: translateY(-50%); padding: 2px 7px; border-radius: 6px; border: 1px solid var(--line); background: var(--surface);
    font-family: var(--font-mono); font-size: 11px; color: var(--muted); white-space: nowrap; pointer-events: none; }
  .pop { position: absolute; top: calc(100% + 6px); left: 0; right: 0; z-index: 30; padding: var(--space-6); display: flex; flex-direction: column;
    box-shadow: 0 12px 32px rgba(16, 33, 43, 0.18); }
  .pop a { display: flex; align-items: center; gap: var(--space-12); padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); color: var(--ink); text-decoration: none; }
  .pop a.on, .pop a:hover { background: var(--cloro-soft); }
  .pop .t { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
  .pop .t span { font-size: var(--text-sm); color: var(--muted); }
  .pop :global(.chev) { width: 16px; height: 16px; color: var(--chevron); }
  .vuoto { padding: var(--space-10) var(--space-12); }
</style>
