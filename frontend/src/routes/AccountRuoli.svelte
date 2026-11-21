<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import Schede from '../components/Schede.svelte';
  import Account from './Account.svelte';
  import Ruoli from './Ruoli.svelte';
  import { sessione } from '../lib/sessione.svelte';
  import type { Scheda } from '../lib/schede';

  /**
   * Account e ruoli (ADR 0024): chi accede al gestionale e cosa può fare, in una pagina con due schede. Si vede la
   * scheda per cui si ha il permesso («Account», «Ruoli e permessi»); «#/account/ruoli» apre quella dei ruoli.
   * Le schede sono il componente comune `Schede` (frecce, Inizio e Fine da tastiera); la scheda aperta resta nell'indirizzo
   * solo in ingresso: la pagina la legge dall'hash e la passa con `bind:attiva`.
   */
  type Voce = 'ruoli' | 'account';
  const puoRuoli = $derived(sessione.puo('RUOLI'));
  const puoAccount = $derived(sessione.puo('ACCOUNT'));
  const leggi = (): Voce => (location.hash.split('/')[2] === 'ruoli' ? 'ruoli' : 'account');
  let scheda = $state<Voce>(leggi());
  const schede = $derived<Scheda<Voce>[]>([
    ...(puoAccount ? [{ id: 'account' as const, nome: 'Account' }] : []),
    ...(puoRuoli ? [{ id: 'ruoli' as const, nome: 'Ruoli' }] : []),
  ]);
  $effect(() => {
    if (scheda === 'ruoli' && !puoRuoli) scheda = 'account';
    if (scheda === 'account' && !puoAccount) scheda = 'ruoli';
  });
  let nuovoRuolo = $state(0);
  let nuovoAccount = $state(0);
</script>

<svelte:window onhashchange={() => (scheda = leggi())} />

<Schede {schede} bind:attiva={scheda} etichetta="Cosa vedere" id="account-ruoli">
  {#snippet azioni()}
    {#if scheda === 'ruoli'}<button type="button" class="btn btn-primary nuovo" onclick={() => nuovoRuolo++}><Icona nome="piu" />Nuovo ruolo…</button>
    {:else}<button type="button" class="btn btn-primary nuovo" onclick={() => nuovoAccount++}><Icona nome="piu" />Nuovo account…</button>{/if}
  {/snippet}
  {#snippet children(s)}
    {#if s === 'ruoli'}<Ruoli nuovo={nuovoRuolo} />{:else}<Account nuovo={nuovoAccount} />{/if}
  {/snippet}
</Schede>

<style>
  .nuovo { height: 40px; flex-shrink: 0; margin-bottom: var(--space-4); }
</style>
