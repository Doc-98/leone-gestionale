<script lang="ts">
  import { api, messaggioErrore, type RegolaAccesso } from '../lib/api';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Le regole del tornello (ADR 0024): ogni caso di rosso è un controllo che si può spegnere, con i suoi parametri.
   * In due colonne, così si leggono tutte senza scorrere; sotto quelle che avvisano soltanto. Si salvano tutte insieme.
   * Le vedono coordinatori e admin; le cambia chi ha il permesso «Regole del tornello».
   */
  /** Regole che non fermano nessuno: la persona passa, la segreteria lo vede. */
  const SOLO_AVVISO = ['SORVEGLIANZA'];

  type Bozza = { attiva: boolean; valori: Record<string, number | string | null>; vuoti: Record<string, boolean> };
  let regole = $state<RegolaAccesso[]>([]);
  let bozze = $state<Record<string, Bozza>>({});
  let errore = $state('');
  let messaggio = $state('');
  const admin = $derived(sessione.puo('REGOLE_TORNELLO'));

  const daRegola = (r: RegolaAccesso): Bozza => ({
    attiva: r.attiva,
    valori: Object.fromEntries(r.parametri.map((p) => [p.nome, p.valore])),
    vuoti: Object.fromEntries(r.parametri.map((p) => [p.nome, p.facoltativo && p.valore == null])),
  });
  async function carica() {
    try {
      regole = await api.regole();
      bozze = Object.fromEntries(regole.map((r) => [r.codice, daRegola(r)]));
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  carica();

  const valore = (b: Bozza, nome: string) => (b.vuoti[nome] || b.valori[nome] === '' || b.valori[nome] == null ? null : Number(b.valori[nome]));
  function cambiata(r: RegolaAccesso): boolean {
    const b = bozze[r.codice];
    return !!b && (b.attiva !== r.attiva || r.parametri.some((p) => valore(b, p.nome) !== p.valore));
  }
  const cambiate = $derived(regole.filter(cambiata));
  const rosse = $derived(regole.filter((r) => !SOLO_AVVISO.includes(r.codice)));
  const avvisi = $derived(regole.filter((r) => SOLO_AVVISO.includes(r.codice)));

  async function salva() {
    errore = '';
    messaggio = '';
    try {
      for (const r of cambiate) {
        const b = bozze[r.codice];
        await api.modificaRegola(r.codice, b.attiva, Object.fromEntries(r.parametri.map((p) => [p.nome, valore(b, p.nome)])));
      }
      await carica();
      messaggio = 'Regole salvate: valgono dal prossimo passaggio.';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
</script>

{#if !admin}<p class="hint nomargin">Per cambiare le regole serve il permesso «Regole del tornello» (di base, gli admin).</p>{/if}
{#if messaggio && !cambiate.length}<p class="msg ok" role="status">{messaggio}</p>{/if}
{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

{#snippet regola(r: RegolaAccesso)}
  {@const b = bozze[r.codice]}
  {#if b}
    <div class="regola" class:spenta={!b.attiva}>
      <button type="button" class="switch" role="switch" aria-checked={b.attiva} aria-label={r.nome} disabled={!admin} onclick={() => (b.attiva = !b.attiva)}><span></span></button>
      <span class="t">
        <b>{r.nome}{#if r.valeConGoldenPass}<span class="oro">vale anche con il golden pass</span>{/if}</b>
        <span class="desc">{r.descrizione}</span>
        {#each r.parametri as p (p.nome)}
          <span class="par">
            <span class="unita">{p.etichetta}</span>
            <input class="inp" type="number" min="0" max={p.max} bind:value={b.valori[p.nome]} disabled={!admin || b.vuoti[p.nome]} aria-label={p.etichetta} />
            <span class="unita">{p.unita}</span>
            {#if p.facoltativo}<label class="vuoto"><input type="checkbox" bind:checked={b.vuoti[p.nome]} disabled={!admin} /> {p.seVuoto}</label>{/if}
          </span>
        {/each}
      </span>
    </div>
  {/if}
{/snippet}

{#if regole.length}
  <section class="card sez" aria-label="Fanno diventare rosso">
    <div class="sez-t"><h2 class="grow">Fanno diventare rosso</h2><span class="spiega">{rosse.filter((r) => bozze[r.codice]?.attiva).length} accese su {rosse.length}</span></div>
    <div class="griglia">{#each rosse as r (r.codice)}{@render regola(r)}{/each}</div>
  </section>
  {#if avvisi.length}
    <section class="card sez" aria-label="Avvisano soltanto">
      <div class="sez-t"><h2 class="grow">Avvisano soltanto</h2><span class="spiega">la persona passa, la segreteria lo vede</span></div>
      <div class="griglia">{#each avvisi as r (r.codice)}{@render regola(r)}{/each}</div>
    </section>
  {/if}
  <span class="muted small">Si controllano tutte le regole accese e al banco si vedono tutti i motivi del rosso; un braccialetto sconosciuto dà sempre rosso. Le modifiche valgono dal prossimo passaggio.</span>
{/if}

{#if cambiate.length}
  <div class="salva-bar" role="status">
    <span>{cambiate.length === 1 ? 'Una regola cambiata' : `${cambiate.length} regole cambiate`}: valgono dopo il salvataggio</span>
    <button type="button" class="vuoto" onclick={carica}>Annulla</button>
    <button type="button" class="chiaro" onclick={salva}>Salva</button>
  </div>
{/if}

<style>
  /* le regole in due colonne: si leggono tutte senza scorrere */
  .griglia { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .regola { display: flex; align-items: flex-start; gap: var(--space-14); padding: var(--space-14) var(--space-16); border-top: 1px solid var(--line-soft); }
  .regola:nth-child(-n + 2) { border-top: none; }
  .regola:nth-child(odd):not(:last-child) { border-right: 1px solid var(--line-soft); }
  .regola .switch { margin-top: 2px; }
  .t { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
  .t b { font-size: var(--text-base); font-weight: 600; display: flex; align-items: center; gap: var(--space-8); flex-wrap: wrap; }
  .desc { font-size: var(--text-sm); color: var(--muted); }
  .par { display: flex; align-items: center; gap: var(--space-8); margin-top: var(--space-6); flex-wrap: wrap; }
  .par .inp { width: 70px; height: 36px; text-align: center; font-family: var(--font-mono); }
  .vuoto { display: flex; align-items: center; gap: var(--space-6); font-size: var(--text-sm); }
  .vuoto input { width: 18px; height: 18px; }
  .spenta .t b, .spenta .par { opacity: 0.5; }
  .oro { display: inline-flex; align-items: center; height: 20px; padding: 0 8px; border-radius: 999px; background: var(--oro-bg); color: var(--oro-fg); font-size: 11px; font-weight: 700; }
  @media (max-width: 800px) {
    .griglia { grid-template-columns: 1fr; }
    .regola:nth-child(2) { border-top: 1px solid var(--line-soft); }
    .regola:nth-child(odd) { border-right: none; }
  }
</style>
