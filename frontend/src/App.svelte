<script lang="ts">
  import { onMount } from 'svelte';
  import { sessione } from './lib/sessione.svelte';
  import Login from './routes/Login.svelte';
  import Turni from './routes/Turni.svelte';
  import Richieste from './routes/Richieste.svelte';
  import Ore from './routes/Ore.svelte';
  import Pianifica from './routes/Pianifica.svelte';
  import Approva from './routes/Approva.svelte';

  type Scheda = 'turni' | 'richieste' | 'ore' | 'pianifica' | 'approva';
  const leggiScheda = (): Scheda => {
    const h = location.hash.replace('#/', '');
    return (['turni', 'richieste', 'ore', 'pianifica', 'approva'] as const).includes(h as Scheda) ? (h as Scheda) : 'turni';
  };
  let scheda = $state<Scheda>(leggiScheda());
  let daApprovare = $state(0);

  onMount(() => {
    sessione.carica();
    const f = () => (scheda = leggiScheda());
    window.addEventListener('hashchange', f);
    return () => window.removeEventListener('hashchange', f);
  });

  const coordinatore = $derived(sessione.ha('ADMIN', 'COORDINATORE'));
  const schede = $derived([
    { id: 'turni', nome: 'I miei turni' },
    { id: 'richieste', nome: 'Richieste' },
    ...(coordinatore ? [{ id: 'pianifica', nome: 'Pianifica' }, { id: 'approva', nome: 'Approva' }] : []),
    { id: 'ore', nome: 'Ore' },
  ] as { id: Scheda; nome: string }[]);
  const titoli: Record<Scheda, string> = { turni: 'I miei turni', richieste: 'Richieste', ore: 'Ore e compensi', pianifica: 'Pianifica', approva: 'Da approvare' };

  $effect(() => {
    if (coordinatore) import('./lib/api').then(({ api }) => api.richiesteDaApprovare().then((r) => (daApprovare = r.length)).catch(() => {}));
  });
</script>

{#if !sessione.pronto}
  <p class="muted centro">Caricamento…</p>
{:else if !sessione.utente}
  <main><Login /></main>
{:else}
  <main class="con-nav">
    <header class="testa">
      <div>
        <div class="lbl">Leone XIII Sport · Piscina</div>
        <h1 class="page-title">{titoli[scheda]}</h1>
      </div>
      <div class="chi">
        <span class="muted small">{sessione.utente.nome}</span>
        <button class="link small" onclick={() => sessione.esci()}>Esci</button>
      </div>
    </header>
    {#key scheda}
      {#if scheda === 'turni'}<Turni />
      {:else if scheda === 'richieste'}<Richieste />
      {:else if scheda === 'ore'}<Ore />
      {:else if scheda === 'pianifica' && coordinatore}<Pianifica />
      {:else if scheda === 'approva' && coordinatore}<Approva cambiate={(n) => (daApprovare = n)} />
      {:else}<Turni />{/if}
    {/key}
  </main>
  <nav aria-label="Sezioni" style="--n: {schede.length}">
    {#each schede as s (s.id)}
      <a href={`#/${s.id}`} class:on={scheda === s.id} aria-current={scheda === s.id ? 'page' : undefined}>
        {s.nome}{#if s.id === 'approva' && daApprovare}<span class="badge" aria-label="{daApprovare} da approvare">{daApprovare}</span>{/if}
      </a>
    {/each}
  </nav>
{/if}

<style>
  main {
    max-width: 640px;
    margin: 0 auto;
    padding: calc(env(safe-area-inset-top, 0px) + var(--space-20)) var(--space-16) calc(env(safe-area-inset-bottom, 0px) + var(--space-20));
    display: flex; flex-direction: column; gap: var(--space-16);
  }
  main.con-nav { padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 96px); }
  .centro { text-align: center; margin-top: 40vh; }
  .testa { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-12); }
  .chi { display: flex; flex-direction: column; align-items: flex-end; }
  .chi .link { min-height: 32px; }
  nav {
    position: fixed; left: 0; right: 0; bottom: 0; z-index: 20;
    display: grid; grid-template-columns: repeat(var(--n), minmax(0, 1fr));
    padding-bottom: env(safe-area-inset-bottom, 0px);
    background: var(--surface); border-top: 1px solid var(--line);
  }
  nav a { position: relative; display: flex; align-items: center; justify-content: center; gap: 4px; min-height: 60px; padding: 0 4px; font-size: var(--text-xs); font-weight: 600; color: var(--muted); text-decoration: none; text-align: center; }
  nav a.on { color: var(--cloro); box-shadow: inset 0 3px 0 var(--cloro); }
  .badge { min-width: 18px; height: 18px; border-radius: 9px; background: var(--cloro); color: var(--on-cloro); font-size: 11px; display: inline-flex; align-items: center; justify-content: center; padding: 0 5px; }
</style>
