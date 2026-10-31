<script lang="ts">
  import PersoneInAttesa from '../components/attesa/PersoneInAttesa.svelte';
  import PostiDaAssegnare from '../components/attesa/PostiDaAssegnare.svelte';
  import { api, ErroreApi, messaggioErrore, type PaginaAttesa, type TurnoPosti, type VoceAttesa } from '../lib/api';
  import { postoLibero, quandoTurno, VISTE_ATTESA, type AzioniAttesa, type VistaAttesa } from '../lib/attesa';
  import { dataBreve } from '../lib/date';
  import { leggiPreferenza, salvaPreferenza } from '../lib/preferenze';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Le liste d'attesa dei turni pieni, in ordine di arrivo (ADR 0015), in due viste a scelta con l'interruttore in alto:
   * «Posti da assegnare» (dove c'è un posto, chi tocca, l'avviso con i giorni per rispondere) e «Persone in attesa» (una
   * riga per persona con tutti i turni che aspetta). La scelta resta per account.
   */
  let pagina = $state<PaginaAttesa | null>(null);
  let errore = $state('');
  let messaggio = $state('');
  let cerca = $state('');
  let soprannumero = $state<{ voce: VoceAttesa; turno: TurnoPosti } | null>(null);
  let vista = $state<VistaAttesa>(leggiPreferenza<VistaAttesa>('attesa.vista', 'posti', ['posti', 'persone']));
  $effect(() => salvaPreferenza('attesa.vista', vista));

  const schede = $derived(sessione.puo('CLIENTI'));

  async function carica() {
    try {
      pagina = await api.paginaAttesa();
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a caricare le liste d\'attesa.');
    }
  }
  carica();

  async function esegui(azione: () => Promise<unknown>, fatto: string) {
    errore = '';
    messaggio = '';
    soprannumero = null;
    try {
      await azione();
      await carica();
      messaggio = fatto;
      return true;
    } catch (e) {
      errore = messaggioErrore(e);
      return false;
    }
  }

  /** Iscrive dal primo giorno con un posto; se il turno è pieno propone il soprannumero. */
  async function iscrivi(v: VoceAttesa, t: TurnoPosti, forza = false) {
    errore = '';
    messaggio = '';
    soprannumero = null;
    const dal = postoLibero(t, pagina!.oggi).dal;
    try {
      await api.iscriviDaAttesa(v.id, { dal, forza });
      await carica();
      messaggio = `${v.nome} ${v.cognome} iscritto a ${t.corso} (${quandoTurno(t)}) dal ${dataBreve(dal)}${forza ? ', in soprannumero' : ''}.`;
    } catch (e) {
      if (e instanceof ErroreApi && e.status === 409 && e.message.includes('al completo')) {
        errore = e.message;
        soprannumero = { voce: v, turno: t };
      } else errore = messaggioErrore(e);
    }
  }

  const azioni: AzioniAttesa = {
    iscrivi: (v, t) => iscrivi(v, t),
    esegui,
    fatto: (m) => {
      errore = '';
      messaggio = m;
      carica();
    },
  };
</script>

<div class="barra">
  <div class="seg viste" role="group" aria-label="Come mostrare le liste d'attesa">
    {#each VISTE_ATTESA as v (v.id)}
      <button type="button" aria-pressed={vista === v.id} onclick={() => (vista = v.id)}>{v.nome}</button>
    {/each}
  </div>
  <span class="grow"></span>
  <input class="inp cerca" type="search" bind:value={cerca} placeholder="Cerca un corso o un nome" aria-label="Cerca un corso o un nome" />
</div>

{#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
{#if errore}
  <p class="msg err" role="alert">{errore}</p>
  {#if soprannumero}
    {@const s = soprannumero}
    <button class="btn btn-secondary sx" onclick={() => iscrivi(s.voce, s.turno, true)}>Iscrivi comunque in soprannumero</button>
  {/if}
{/if}

{#if pagina}
  {#if vista === 'posti'}
    <PostiDaAssegnare {pagina} {cerca} {schede} {azioni} />
  {:else}
    <PersoneInAttesa {pagina} {cerca} {schede} {azioni} />
  {/if}
{:else if !errore}
  <p class="muted">Caricamento…</p>
{/if}

<style>
  .barra { display: flex; flex-wrap: wrap; gap: var(--space-12); align-items: center; }
  .viste { grid-template-columns: repeat(2, auto); }
  .viste button { padding: 0 var(--space-14); }
  .cerca { width: 260px; }
  .sx { align-self: flex-start; }
  @media (max-width: 700px) { .cerca { width: 100%; } .viste { width: 100%; grid-template-columns: 1fr 1fr; } }
</style>
