<script lang="ts">
  import { api, messaggioErrore, type RegolaAccesso } from '../lib/api';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Le regole del tornello: ogni caso di rosso è un controllo che si può spegnere, con i suoi parametri. Le vedono
   * coordinatori e admin; le cambiano gli admin.
   */
  let regole = $state<RegolaAccesso[]>([]);
  let bozze = $state<Record<string, { attiva: boolean; valori: Record<string, number | string | null>; vuoti: Record<string, boolean> }>>({});
  let errore = $state('');
  let messaggio = $state('');
  const admin = $derived(sessione.puo('REGOLE_TORNELLO'));

  async function carica() {
    try {
      regole = await api.regole();
      bozze = Object.fromEntries(regole.map((r) => [r.codice, {
        attiva: r.attiva,
        valori: Object.fromEntries(r.parametri.map((p) => [p.nome, p.valore])),
        vuoti: Object.fromEntries(r.parametri.map((p) => [p.nome, p.facoltativo && p.valore == null])),
      }]));
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  carica();

  function cambiata(r: RegolaAccesso): boolean {
    const b = bozze[r.codice];
    if (!b) return false;
    if (b.attiva !== r.attiva) return true;
    return r.parametri.some((p) => (b.vuoti[p.nome] ? null : Number(b.valori[p.nome])) !== p.valore);
  }

  async function salva(r: RegolaAccesso) {
    const b = bozze[r.codice];
    errore = '';
    messaggio = '';
    try {
      const parametri = Object.fromEntries(r.parametri.map((p) => [p.nome, b.vuoti[p.nome] || b.valori[p.nome] === '' || b.valori[p.nome] == null ? null : Number(b.valori[p.nome])]));
      await api.modificaRegola(r.codice, b.attiva, parametri);
      messaggio = `«${r.nome}» salvata: vale dal prossimo passaggio.`;
      await carica();
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
</script>

<p class="muted nomargin">Al tornello si controllano tutte le regole attive e si mostrano tutti i motivi del rosso. Un braccialetto sconosciuto dà sempre rosso.</p>
{#if !admin}<p class="hint nomargin">Per cambiare le regole serve il permesso «Regole del tornello» (di base, gli admin).</p>{/if}
{#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

<div class="regole">
  {#each regole as r (r.codice)}
    {@const b = bozze[r.codice]}
    {#if b}
      <section class="card regola" class:spenta={!b.attiva}>
        <div class="riga">
          <div class="grow">
            <h3>{r.nome}</h3>
            {#if r.valeConGoldenPass}<span class="tag t-altro">Vale anche con il golden pass</span>{/if}
          </div>
          <button class="switch" role="switch" aria-checked={b.attiva} aria-label="Regola attiva" disabled={!admin}
                  onclick={() => (b.attiva = !b.attiva)}><span></span></button>
        </div>
        <p class="muted small nomargin">{r.descrizione}</p>
        {#each r.parametri as p (p.nome)}
          <div class="parametro">
            <label class="field grow"><span class="lbl">{p.etichetta}</span>
              <div class="row centro">
                <input class="inp m num" type="number" min="0" max={p.max} bind:value={b.valori[p.nome]} disabled={!admin || b.vuoti[p.nome]} />
                <span class="muted small">{p.unita}</span>
              </div>
            </label>
            {#if p.facoltativo}
              <label class="vuoto small"><input type="checkbox" bind:checked={b.vuoti[p.nome]} disabled={!admin} /> {p.seVuoto}</label>
            {/if}
          </div>
        {/each}
        {#if admin && cambiata(r)}
          <div class="row"><button class="btn btn-primary" onclick={() => salva(r)}>Salva</button>
            <button class="btn btn-secondary" onclick={carica}>Annulla</button></div>
        {/if}
      </section>
    {/if}
  {/each}
</div>

<style>
  .regole { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: var(--space-14); }
  .regola { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16); }
  .spenta { opacity: 0.7; }
  .riga { display: flex; align-items: flex-start; gap: var(--space-12); }
  h3 { margin: 0 0 var(--space-4); font-size: var(--text-xl); }
  .parametro { display: flex; flex-direction: column; gap: var(--space-6); }
  .centro { align-items: center; }
  .num { width: 110px; }
  .vuoto { display: flex; align-items: center; gap: var(--space-8); }
  .vuoto input { width: 18px; height: 18px; }
</style>
