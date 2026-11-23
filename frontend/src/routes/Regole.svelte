<script lang="ts">
  import { api, messaggioErrore, type RegolaAccesso } from '../lib/api';
  import BarraSalva from '../components/BarraSalva.svelte';
  import Fumetto from '../components/Fumetto.svelte';
  import ErroreCampo from '../components/ErroreCampo.svelte';
  import { contaModifiche } from '../lib/salvataggio';
  import { msgParametro, parametroRegolaNonValido } from '../lib/validita';
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

  /** Il nome senza l'ultima parola, e l'ultima: il fumetto resta attaccato a questa, mai a capo da solo. */
  const testaNome = (n: string) => n.slice(0, n.lastIndexOf(' ') + 1);
  const codaNome = (n: string) => n.slice(n.lastIndexOf(' ') + 1);
  const valore = (b: Bozza, nome: string) => (b.vuoti[nome] || b.valori[nome] === '' || b.valori[nome] == null ? null : Number(b.valori[nome]));
  /** Una regola com'è salvata e com'è in pagina, nella stessa forma: acceso o spento e il valore di ogni parametro. */
  const vistaSalvata = (r: RegolaAccesso) => ({ attiva: r.attiva, valori: Object.fromEntries(r.parametri.map((p) => [p.nome, p.valore])) });
  const vistaBozza = (r: RegolaAccesso) => {
    const b = bozze[r.codice];
    return { attiva: b.attiva, valori: Object.fromEntries(r.parametri.map((p) => [p.nome, valore(b, p.nome)])) };
  };
  const modificheDi = (r: RegolaAccesso) => (bozze[r.codice] ? contaModifiche(vistaSalvata(r), vistaBozza(r)) : 0);
  const cambiate = $derived(regole.filter((r) => modificheDi(r) > 0));
  const modifiche = $derived(regole.reduce((n, r) => n + modificheDi(r), 0));
  /** Un parametro scritto male: da 0 al massimo (come il backend); vuoto solo se è facoltativo e «se vuoto» è spuntato. */
  const parametroNonValido = (b: Bozza, p: RegolaAccesso['parametri'][number]) => parametroRegolaNonValido(b.valori[p.nome], b.vuoti[p.nome], p.facoltativo, p.max);
  const valida = $derived(regole.every((r) => !bozze[r.codice] || r.parametri.every((p) => !parametroNonValido(bozze[r.codice], p))));
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

{#if messaggio && !cambiate.length}<p class="msg ok" role="status">{messaggio}</p>{/if}
{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

{#snippet regola(r: RegolaAccesso)}
  {@const b = bozze[r.codice]}
  {#if b}
    <div class="regola" class:spenta={!b.attiva}>
      <span class="t">
        <b><span class="nome-r">{testaNome(r.nome)}<span class="coda">{codaNome(r.nome)}<Fumetto tema={`la regola «${r.nome}»`}>{r.descrizione}</Fumetto></span></span>{#if r.valeConGoldenPass}<span class="pill oro">vale anche con il golden pass</span>{/if}</b>
        {#each r.parametri as p (p.nome)}
          <span class="par">
            <span class="unita">{p.etichetta}</span>
            <span class="campo-u"><input class="inp" type="number" min="0" max={p.max} bind:value={b.valori[p.nome]} disabled={!admin || b.vuoti[p.nome]} aria-label={p.etichetta} aria-invalid={parametroNonValido(b, p)} aria-describedby={parametroNonValido(b, p) ? `err-${r.codice}-${p.nome}` : undefined} />
              <span class="unita">{p.unita}</span></span>
            {#if parametroNonValido(b, p)}<ErroreCampo id="err-{r.codice}-{p.nome}">{msgParametro(p.max)}</ErroreCampo>{/if}
            {#if p.facoltativo}<label class="se-vuoto"><input type="checkbox" bind:checked={b.vuoti[p.nome]} disabled={!admin} /> {p.seVuoto}</label>{/if}
          </span>
        {/each}
      </span>
      <button type="button" class="switch" role="switch" aria-checked={b.attiva} aria-label={r.nome} disabled={!admin} onclick={() => (b.attiva = !b.attiva)}><span></span></button>
    </div>
  {/if}
{/snippet}

{#if regole.length}
  <section class="card sez" aria-label="Fanno diventare rosso">
    <div class="sez-t"><h2 class="grow">Fanno diventare rosso</h2><span class="spiega">{rosse.filter((r) => bozze[r.codice]?.attiva).length} accese su {rosse.length}</span>
      <Fumetto tema="le regole del tornello">Si controllano tutte le regole accese e al banco si vedono tutti i motivi del rosso; un braccialetto sconosciuto dà sempre rosso. Le modifiche valgono dal prossimo passaggio, appena salvate.</Fumetto></div>
    <div class="griglia">{#each rosse as r (r.codice)}{@render regola(r)}{/each}</div>
  </section>
  {#if avvisi.length}
    <section class="card sez" aria-label="Avvisano soltanto">
      <div class="sez-t"><h2 class="grow">Avvisano soltanto</h2><span class="spiega">la persona passa, la segreteria lo vede</span></div>
      <div class="griglia">{#each avvisi as r (r.codice)}{@render regola(r)}{/each}</div>
    </section>
  {/if}
{/if}

<BarraSalva {valida} {modifiche} onannulla={carica} onsalva={salva} />

<style>
  /* le regole in due colonne: si leggono tutte senza scorrere */
  .griglia { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .regola { display: flex; align-items: flex-start; gap: var(--space-14); padding: var(--space-14) var(--space-16); border-top: 1px solid var(--line-soft); }
  .regola:nth-child(-n + 2) { border-top: none; }
  .regola:nth-child(odd):not(:last-child) { border-right: 1px solid var(--line-soft); }
  .regola .switch { margin-top: 2px; flex-shrink: 0; }
  .t { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
  .t b { font-size: var(--text-base); font-weight: 600; display: flex; align-items: center; gap: var(--space-8); flex-wrap: wrap; }
  .coda { white-space: nowrap; display: inline-flex; align-items: center; gap: var(--space-6); }
  .par { display: flex; align-items: center; gap: var(--space-8); margin-top: var(--space-6); flex-wrap: wrap; }
  .campo-u { display: inline-flex; align-items: center; gap: var(--space-8); white-space: nowrap; }
  .par .inp { width: 70px; height: 36px; text-align: center; font-family: var(--font-mono); }
  .se-vuoto { display: flex; align-items: center; gap: var(--space-6); font-size: var(--text-sm); }
  .se-vuoto input { width: 18px; height: 18px; }
  .spenta .t b, .spenta .par { opacity: 0.5; }
  @media (max-width: 800px) {
    .griglia { grid-template-columns: 1fr; }
    .regola:nth-child(2) { border-top: 1px solid var(--line-soft); }
    .regola:nth-child(odd) { border-right: none; }
  }
</style>
