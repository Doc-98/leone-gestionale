<script lang="ts">
  import Azione from '../Azione.svelte';
  import { api, messaggioErrore, type Cassaforte } from '../../lib/api';
  import { dataBreve, dataOra, iso } from '../../lib/date';
  import { euro, euroColonna } from '../../lib/numeri';
  import { sessione } from '../../lib/sessione.svelte';

  /**
   * I contanti in cassaforte (se nelle impostazioni si tiene il conto): quello messo da parte alle chiusure e non ancora
   * portato in banca; «Portati in banca» con il giorno e la distinta; gli ultimi versamenti (un responsabile li annulla).
   */
  let { cambiata }: { cambiata?: () => void } = $props();
  let cf = $state<Cassaforte | null>(null);
  let errore = $state('');
  let fatto = $state('');
  let lavoro = $state(false);
  let banca = $state<{ data: string; distinta: string; note: string } | null>(null);
  const puoAnnullare = $derived(sessione.puo('PAGAMENTI_ANNULLA'));
  const piuCasse = $derived(new Set(cf?.chiusure.map((s) => s.cassaId) ?? []).size > 1);

  async function carica() {
    try {
      cf = await api.cassaforte();
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  carica();

  async function fai(azione: () => Promise<unknown>, detto = '') {
    errore = '';
    fatto = '';
    lavoro = true;
    try {
      await azione();
      banca = null;
      await carica();
      cambiata?.();
      fatto = detto;
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      lavoro = false;
    }
  }
</script>

<section class="card pann" aria-label="Contanti in cassaforte">
  <span class="lbl">Contanti in cassaforte</span>
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}
  {#if fatto}<p class="msg ok nomargin" role="status">{fatto}</p>{/if}
  {#if cf}
    <span class="v">{euro(cf.totale)}</span>
    {#if cf.chiusure.length}
      <ul class="el">
        {#each cf.chiusure as s (s.id)}<li><span>{piuCasse ? `${s.cassa} · ` : ''}{dataOra(s.chiusaIl!)}</span><span class="m">{euroColonna(s.perCassaforte ?? 0)}</span></li>{/each}
      </ul>
      {#if banca}
        <form class="banca" onsubmit={(e) => { e.preventDefault(); fai(() => api.versaInBanca({ data: banca!.data, distinta: banca!.distinta.trim() || undefined, note: banca!.note.trim() || undefined })); }}>
          <label class="campo"><span class="small">Giorno del versamento</span><input class="inp" type="date" bind:value={banca.data} max={iso(new Date())} required /></label>
          <label class="campo"><span class="small">Numero della distinta (facoltativo)</span><input class="inp" bind:value={banca.distinta} maxlength="60" /></label>
          <span class="bottoni"><button class="btn btn-primary btn-sm" disabled={lavoro || !banca.data}>Segna versati {euro(cf.totale)}</button>
            <button type="button" class="btn btn-secondary btn-sm" onclick={() => (banca = null)}>Annulla</button></span>
        </form>
      {:else}
        <button type="button" class="btn btn-primary" onclick={() => (banca = { data: iso(new Date()), distinta: '', note: '' })}>Portati in banca…</button>
      {/if}
    {:else}
      <span class="muted small">Niente da portare in banca.</span>
    {/if}
    {#if cf.versamenti.length}
      <span class="lbl">Ultimi versamenti</span>
      <ul class="el">
        {#each cf.versamenti as v (v.id)}
          <li class:annullato={v.annullato}><span>{dataBreve(v.data)}{v.distinta ? ` · distinta ${v.distinta}` : ''}{v.annullato ? ' · annullato' : ''}</span>
            <span class="dx"><span class="m">{euroColonna(v.importo)}</span>
              {#if puoAnnullare && !v.annullato}<Azione icona="annulla" etichetta={`Annulla il versamento del ${dataBreve(v.data)}`} conferma="Annulla?" disabled={lavoro}
                onclick={() => fai(() => api.annullaVersamento(v.id), `Versamento del ${dataBreve(v.data)}: annullato.`)} />{/if}</span></li>
        {/each}
      </ul>
    {/if}
    <span class="muted small">Quello versato alle chiusure resta qui finché non si segna che è andato in banca.</span>
  {/if}
</section>

<style>
  .pann { gap: var(--space-10); } /* un po' più stretto del .pann globale (12px) */
  .v { font-family: var(--font-display); font-size: var(--text-3xl); font-weight: 800; line-height: 1; }
  .el { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--space-4); font-size: var(--text-sm); }
  .el li { display: flex; justify-content: space-between; align-items: center; gap: var(--space-8); min-height: 28px; }
  .el li.annullato { color: var(--muted); text-decoration: line-through; }
  .dx { display: inline-flex; align-items: center; gap: var(--space-4); }
  .banca { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-12); border-radius: var(--radius-md); border: 1px solid var(--line); }
  .campo { display: flex; flex-direction: column; gap: var(--space-4); }
  .campo .inp { height: 40px; }
  .bottoni { display: flex; gap: var(--space-8); flex-wrap: wrap; }
</style>
