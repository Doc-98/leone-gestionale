<script lang="ts">
  import { api, messaggioErrore, type Mansione, type PersonaBreve, type Tariffa } from '../lib/api';
  import { dataLunga, euro, iso } from '../lib/date';
  import Foglio from '../components/Foglio.svelte';

  let tariffe = $state<Tariffa[]>([]);
  let mansioni = $state<Mansione[]>([]);
  let personale = $state<PersonaBreve[]>([]);
  let caricamento = $state(true);
  let errore = $state('');
  let messaggio = $state('');

  let nuova = $state(false);
  let correzione = $state<Tariffa | null>(null);
  let fMansione = $state('');
  let fPersona = $state('');
  let fEuro = $state('');
  let fDal = $state('');
  let fAl = $state('');
  let fErrore = $state('');
  let lavoro = $state(false);

  function carica() {
    return api.tariffe().then((t) => (tariffe = t));
  }
  Promise.all([carica(), api.mansioni().then((m) => (mansioni = m)), api.personale().then((p) => (personale = p))])
    .catch((e) => (errore = messaggioErrore(e)))
    .finally(() => (caricamento = false));

  const oggi = iso(new Date());
  const valida = (t: Tariffa) => t.dal <= oggi && (!t.al || t.al >= oggi);
  const futura = (t: Tariffa) => t.dal > oggi;
  // Mansioni senza nessuna tariffa valida oggi (per tutti): il riepilogo le segnala come «tariffa mancante»
  const senzaTariffa = $derived(mansioni.filter((m) => !tariffe.some((t) => t.mansioneId === m.id && !t.persona && valida(t))));
  const gruppi = $derived(mansioni.map((m) => ({ mansione: m, tariffe: tariffe.filter((t) => t.mansioneId === m.id) })).filter((g) => g.tariffe.length));

  /** Accetta «9,50» e «9.50». */
  const numero = (s: string) => Number(s.replace(',', '.'));

  function apriNuova(mansioneId = '') {
    messaggio = '';
    fErrore = '';
    fMansione = mansioneId || mansioni[0]?.id || '';
    fPersona = '';
    fEuro = '';
    fDal = oggi.slice(0, 8) + '01';
    nuova = true;
  }

  function apriCorrezione(t: Tariffa) {
    messaggio = '';
    fErrore = '';
    fEuro = String(t.euroOra).replace('.', ',');
    fAl = t.al ?? '';
    correzione = t;
  }

  async function salvaNuova(e: SubmitEvent) {
    e.preventDefault();
    lavoro = true;
    fErrore = '';
    try {
      await api.nuovaTariffa({ mansioneId: fMansione, personaId: fPersona || null, euroOra: numero(fEuro), dal: fDal });
      await carica();
      messaggio = `Tariffa salvata dal ${dataLunga(fDal)}. Quella precedente, se c'era, si chiude il giorno prima.`;
      nuova = false;
    } catch (err) {
      fErrore = messaggioErrore(err);
    } finally {
      lavoro = false;
    }
  }

  async function salvaCorrezione(e: SubmitEvent) {
    e.preventDefault();
    if (!correzione) return;
    lavoro = true;
    fErrore = '';
    try {
      await api.correggiTariffa(correzione.id, numero(fEuro), fAl || null);
      await carica();
      messaggio = 'Tariffa corretta.';
      correzione = null;
    } catch (err) {
      fErrore = messaggioErrore(err);
    } finally {
      lavoro = false;
    }
  }
</script>

<section class="page">
  <div class="barra">
    <p class="muted small nomargin grow">Il compenso orario di ogni mansione, con la data da cui vale. Una tariffa per una persona è un'eccezione e vince su quella della mansione.</p>
    <button class="btn btn-primary compatto" disabled={!mansioni.length} onclick={() => apriNuova()}>Nuova tariffa…</button>
  </div>
  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
  {#if !caricamento && senzaTariffa.length}
    <p class="msg err nomargin">Senza tariffa valida oggi: {senzaTariffa.map((m) => m.nome).join(', ')}. Nel riepilogo le loro ore non hanno un compenso.</p>
  {/if}

  {#each gruppi as g (g.mansione.id)}
    <div class="card gruppo-t">
      <header>
        <h2 class="grow">{g.mansione.nome}</h2>
        <button class="link small" onclick={() => apriNuova(g.mansione.id)}>+ Tariffa</button>
      </header>
      <table>
        <colgroup><col style="width: 28%" /><col style="width: 12%" /><col style="width: 20%" /><col style="width: 18%" /><col style="width: 12%" /><col /></colgroup>
        <thead><tr><th>Per</th><th class="num">€ / ora</th><th class="dopo-num">Dal</th><th>Al</th><th></th><th></th></tr></thead>
        <tbody>
          {#each g.tariffe as t (t.id)}
            <tr class:chiusa={!valida(t) && !futura(t)}>
              <td>{t.persona ? t.persona.nome : 'Tutti'}</td>
              <td class="num m">{euro(t.euroOra)}</td>
              <td class="dopo-num">{dataLunga(t.dal)}</td>
              <td>{t.al ? dataLunga(t.al) : '—'}</td>
              <td>{#if valida(t)}<span class="tag t-soft">in vigore</span>{:else if futura(t)}<span class="tag t-altro">dal {dataLunga(t.dal)}</span>{:else}<span class="tag t-altro">chiusa</span>{/if}</td>
              <td class="azioni"><button class="link small" onclick={() => apriCorrezione(t)}>Correggi</button></td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
  {:else}
    {#if !caricamento}<p class="muted">Nessuna tariffa.</p>{/if}
  {/each}
</section>

{#if nuova}
  <Foglio titolo="Nuova tariffa" chiudi={() => (nuova = false)}>
    <form class="form" onsubmit={salvaNuova}>
      <label class="field"><span class="lbl">Mansione</span>
        <select class="inp" bind:value={fMansione}>{#each mansioni as m (m.id)}<option value={m.id}>{m.nome}</option>{/each}</select>
      </label>
      <label class="field"><span class="lbl">Per</span>
        <select class="inp" bind:value={fPersona}>
          <option value="">Tutti</option>
          {#each personale as p (p.id)}<option value={p.id}>Solo {p.nome}</option>{/each}
        </select>
      </label>
      <div class="due">
        <label class="field"><span class="lbl">€ all'ora</span><input class="inp m" inputmode="decimal" bind:value={fEuro} required pattern="\d+([.,]\d{'{'}1,2{'}'})?" placeholder="9,50" /></label>
        <label class="field"><span class="lbl">Dal</span><input class="inp" type="date" bind:value={fDal} required /></label>
      </div>
      <p class="muted small nomargin">La tariffa precedente per la stessa mansione{fPersona ? ' e persona' : ''} si chiude il giorno prima. Le ore dei turni già fatti restano pagate con la tariffa del loro giorno.</p>
      {#if fErrore}<p class="msg err" role="alert">{fErrore}</p>{/if}
      <button class="btn btn-primary" disabled={lavoro}>Salva la tariffa</button>
    </form>
  </Foglio>
{/if}

{#if correzione}
  <Foglio titolo="Correggi tariffa · {correzione.mansione}" chiudi={() => (correzione = null)}>
    <form class="form" onsubmit={salvaCorrezione}>
      <p class="muted small nomargin">{correzione.persona ? `Solo ${correzione.persona.nome}` : 'Tutti'} · dal {dataLunga(correzione.dal)}. Per un aumento da una certa data usa invece «Nuova tariffa»: lo storico resta corretto.</p>
      <div class="due">
        <label class="field"><span class="lbl">€ all'ora</span><input class="inp m" inputmode="decimal" bind:value={fEuro} required pattern="\d+([.,]\d{'{'}1,2{'}'})?" /></label>
        <label class="field"><span class="lbl">Fino al (facoltativo)</span><input class="inp" type="date" bind:value={fAl} min={correzione.dal} /></label>
      </div>
      {#if fErrore}<p class="msg err" role="alert">{fErrore}</p>{/if}
      <button class="btn btn-primary" disabled={lavoro}>Salva</button>
    </form>
  </Foglio>
{/if}

<style>
  .barra { display: flex; align-items: center; gap: var(--space-16); }
  .compatto { height: 40px; font-size: var(--text-md); padding: 0 var(--space-14); }
  .gruppo-t { padding: var(--space-14) var(--space-16); display: flex; flex-direction: column; gap: var(--space-8); }
  header { display: flex; align-items: center; gap: var(--space-12); }
  h2 { margin: 0; font-size: var(--text-xl); }
  header .link { min-height: 32px; }
  table { width: 100%; border-collapse: collapse; font-size: var(--text-md); table-layout: fixed; }
  .dopo-num { padding-left: var(--space-20); }
  th { text-align: left; font-size: var(--text-2xs); font-weight: 600; letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted); padding: var(--space-6); border-bottom: 1px solid var(--line); }
  td { padding: var(--space-6); border-bottom: 1px solid var(--line-soft); }
  td .tag { height: 20px; }
  .num { text-align: right; }
  .chiusa td { color: var(--muted); }
  .azioni { text-align: right; }
  .azioni .link { min-height: 32px; }
  .form { display: flex; flex-direction: column; gap: var(--space-14); }
  select.inp { appearance: auto; }
</style>
