<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import Account from './Account.svelte';
  import Ruoli from './Ruoli.svelte';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Account e ruoli (ADR 0024): chi accede al gestionale e cosa può fare, in una pagina con due schede. Si vede la
   * scheda per cui si ha il permesso («Account», «Ruoli e permessi»); «#/account/ruoli» apre quella dei ruoli.
   */
  const puoRuoli = $derived(sessione.puo('RUOLI'));
  const puoAccount = $derived(sessione.puo('ACCOUNT'));
  const leggi = () => (location.hash.split('/')[2] === 'ruoli' ? 'ruoli' : 'account');
  let scheda = $state<'ruoli' | 'account'>(leggi());
  $effect(() => {
    if (scheda === 'ruoli' && !puoRuoli) scheda = 'account';
    if (scheda === 'account' && !puoAccount) scheda = 'ruoli';
  });
  let nuovoRuolo = $state(0);
  let nuovoAccount = $state(0);
</script>

<svelte:window onhashchange={() => (scheda = leggi())} />

<div class="tab-c" role="tablist" aria-label="Cosa vedere">
  {#if puoAccount}<button type="button" role="tab" aria-selected={scheda === 'account'} onclick={() => (scheda = 'account')}>Account</button>{/if}
  {#if puoRuoli}<button type="button" role="tab" aria-selected={scheda === 'ruoli'} onclick={() => (scheda = 'ruoli')}>Ruoli</button>{/if}
  <span class="grow"></span>
  {#if scheda === 'ruoli'}<button type="button" class="btn btn-primary" onclick={() => nuovoRuolo++}><Icona nome="piu" />Nuovo ruolo</button>
  {:else}<button type="button" class="btn btn-primary" onclick={() => nuovoAccount++}><Icona nome="piu" />Nuovo account</button>{/if}
</div>

{#if scheda === 'ruoli'}<Ruoli nuovo={nuovoRuolo} />{:else}<Account nuovo={nuovoAccount} />{/if}

<style>
  .tab-c { display: flex; gap: var(--space-4); border-bottom: 1px solid var(--line); align-items: center; padding-bottom: var(--space-6); }
  .tab-c > button[role='tab'] { position: relative; height: 44px; padding: 0 var(--space-16); border: none; background: none; font: inherit; font-size: var(--text-base); font-weight: 600;
    color: var(--muted); cursor: pointer; }
  .tab-c > button[aria-selected='true'] { color: var(--ink); }
  .tab-c > button[aria-selected='true']::after { content: ''; position: absolute; left: 10px; right: 10px; bottom: -7px; height: 3px; border-radius: 3px 3px 0 0; background: var(--cloro); }
  .tab-c .btn { height: 40px; }
</style>
