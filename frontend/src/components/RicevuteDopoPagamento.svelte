<script lang="ts">
  import Icona from './Icona.svelte';
  import RicevutaRiga from './RicevutaRiga.svelte';
  import { untrack } from 'svelte';
  import { api, messaggioErrore, type ImpostazioniRicevute, type Ricevuta } from '../lib/api';
  import { serveScelta } from '../lib/ricevute';

  /**
   * Le ricevute di uno o più pagamenti appena registrati (o di un pagamento dello storico). Come dicono le impostazioni:
   * si fanno subito (una per famiglia o una per figlio, o si chiede), si mandano per email a chi paga, si aprono per la
   * stampa; con «niente» si fanno solo su richiesta.
   */
  let { pagamentoIds, beneficiari = 1, appena = true, annullato = false, chiudi, cambiate }: {
    pagamentoIds: string[]; beneficiari?: number; appena?: boolean; annullato?: boolean; chiudi: () => void; cambiate?: () => void;
  } = $props();

  let imp = $state<ImpostazioniRicevute | null>(null);
  let ricevute = $state<Ricevuta[]>([]);
  let modo = $state<'FAMIGLIA' | 'PERSONA'>('FAMIGLIA');
  let daFare = $state(false);
  let errore = $state('');
  let avviso = $state('');
  let lavoro = $state(false);
  const ids = untrack(() => pagamentoIds);
  const attive = $derived(ricevute.filter((r) => !r.annullataIl));
  const chiedi = $derived(!!imp && ids.length === 1 && serveScelta(imp, beneficiari));

  async function ricarica() {
    ricevute = (await Promise.all(ids.map((id) => api.ricevuteDiPagamento(id)))).flat();
    cambiate?.();
  }

  async function avvio() {
    try {
      imp = (await api.impostazioniRicevute()).impostazioni;
      modo = imp.famiglia === 'PERSONA' ? 'PERSONA' : 'FAMIGLIA';
      await ricarica();
      if (attive.length || annullato) return;
      // si fanno da sole, tranne con «niente» (o da uno storico) e quando c'è da scegliere
      if (imp.dopo === 'NIENTE' || !appena || chiedi) daFare = true;
      else await fai(undefined);
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a fare le ricevute.');
    }
  }
  avvio();

  async function fai(scelto: 'FAMIGLIA' | 'PERSONA' | undefined) {
    errore = '';
    lavoro = true;
    try {
      const fatte = (await Promise.all(ids.map((id) => api.emettiRicevute(id, scelto)))).flat();
      daFare = false;
      if (appena && imp?.email) {
        const esiti = await Promise.allSettled(fatte.filter((r) => r.intestatario.email && !r.inviataIl).map((r) => api.inviaRicevuta(r.id)));
        const nonPartita = esiti.map((e) => (e.status === 'fulfilled' ? (e.value.stato === 'INVIATA' ? null : e.value.errore) : messaggioErrore(e.reason))).find((x) => x !== null);
        if (nonPartita !== undefined) avviso = `L'email non è partita${nonPartita ? `: ${nonPartita.replace(/\.$/, '')}` : ''}. Si può stampare o riprovare.`;
      }
      if (appena && imp?.dopo === 'STAMPA' && fatte[0]) {
        window.open(api.urlPdfRicevuta(fatte[0].id), '_blank', 'noopener');
        await Promise.allSettled(fatte.map((r) => api.ricevutaStampata(r.id)));
      }
      await ricarica();
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a fare le ricevute.');
    } finally {
      lavoro = false;
    }
  }
</script>

<section class="ricevute-d" aria-label="Le ricevute del pagamento">
  <div class="testa"><Icona nome="ricevuta" /><b class="grow">{attive.length === 1 ? 'La ricevuta' : attive.length > 1 ? `Le ${attive.length} ricevute` : 'Ricevuta'}</b>
    <button type="button" class="mini-x" aria-label="Chiudi le ricevute" onclick={chiudi}><Icona nome="chiudi" /></button></div>

  {#if daFare}
    {#if chiedi}
      <div class="scelta">
        <span class="t"><b>Più figli nello stesso pagamento</b><span>Una ricevuta per famiglia, o una per figlio: per le detrazioni spesso serve una per ciascuno.</span></span>
        <span class="seg" role="group" aria-label="Quante ricevute">
          <button type="button" aria-pressed={modo === 'FAMIGLIA'} onclick={() => (modo = 'FAMIGLIA')}>Una per famiglia</button>
          <button type="button" aria-pressed={modo === 'PERSONA'} onclick={() => (modo = 'PERSONA')}>Una per figlio</button>
        </span>
      </div>
    {/if}
    <div class="fai">
      <span class="muted small grow">{chiedi ? '' : appena ? 'Le ricevute si fanno su richiesta (impostazioni delle ricevute).' : 'Questo pagamento non ha ancora la ricevuta.'}</span>
      <button type="button" class="btn btn-primary" disabled={lavoro} onclick={() => fai(chiedi ? modo : undefined)}>
        {lavoro ? 'Un momento…' : chiedi && modo === 'PERSONA' ? 'Fai le ricevute' : 'Fai la ricevuta'}</button>
    </div>
  {:else if annullato && !attive.length}
    <p class="muted small nomargin">Il pagamento è annullato{ricevute.length ? ': le sue ricevute sono annullate con lui' : ' e non ha ricevute'}.</p>
  {:else if lavoro && !ricevute.length}
    <p class="muted small nomargin">Preparo la ricevuta…</p>
  {/if}

  {#if ricevute.length}
    <div class="elenco">
      {#each ricevute as r (r.id)}
        <RicevutaRiga ricevuta={r} cambiata={() => ricarica()} />
      {/each}
    </div>
  {/if}
  {#if avviso}<p class="hint nomargin" role="status"><span>{avviso}</span></p>{/if}
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}
</section>

<style>
  .ricevute-d { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); border: 1px solid var(--line);
    background: var(--surface); box-shadow: inset 3px 0 0 var(--passa); }
  .testa { display: flex; align-items: center; gap: var(--space-8); }
  .testa :global(.ic) { color: var(--passa); }
  .mini-x { position: relative; width: 36px; height: 36px; border-radius: 50%; border: none; background: none; color: var(--muted); cursor: pointer; display: grid; place-items: center; }
  .mini-x::after { content: ''; position: absolute; inset: -4px; }
  .scelta { display: flex; align-items: center; gap: var(--space-12); flex-wrap: wrap; padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); background: var(--carta); }
  .scelta .t { flex: 1 1 240px; display: flex; flex-direction: column; gap: 2px; }
  .scelta .t span { font-size: var(--text-sm); color: var(--muted); }
  .scelta .seg { grid-template-columns: repeat(2, minmax(0, 1fr)); min-width: 260px; }
  .fai { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .elenco { display: flex; flex-direction: column; }
  .elenco > :global(.ric + .ric) { border-top: 1px solid var(--line-soft); }
</style>
