<script lang="ts">
  import { onMount } from 'svelte';
  import { api, messaggioErrore, type Corso, type Lezione, type PersonaBreve, type SpazioPiano } from '../lib/api';
  import { dataLunga, GIORNI_BREVI, iso, ora } from '../lib/date';
  import { colore, eta, etichettaSpazi, TIPI_CORSO } from '../lib/piano';
  
  import { schermo } from '../lib/schermo.svelte';
  import Foglio from '../components/Foglio.svelte';
  import CorsoForm from './CorsoForm.svelte';
  import LezioneForm from './LezioneForm.svelte';

  type Pannello =
    | { tipo: 'corso'; corso: Corso | null }
    | { tipo: 'lezione'; corso: Corso; lezione: Lezione | null };

  let corsi = $state<Corso[]>([]);
  let spazi = $state<SpazioPiano[]>([]);
  let personale = $state<PersonaBreve[]>([]);
  let impiantoId = $state('');
  let caricamento = $state(true);
  let errore = $state('');
  let messaggio = $state('');
  let avvisi = $state<string[]>([]);
  let soloAttivi = $state(true);
  let pannello = $state<Pannello | null>(null);
  let confermaTogli = $state('');

  const oggi = iso(new Date());

  function carica() {
    return api.corsi().then((c) => (corsi = c));
  }

  /** «#/corsi/<id>» apre direttamente quel corso (arrivando dal piano vasca). */
  function daIndirizzo() {
    const id = location.hash.split('/')[2];
    const c = id ? corsi.find((x) => x.id === id) : undefined;
    if (c) pannello = { tipo: 'corso', corso: c };
  }

  onMount(() => {
    Promise.all([carica(), api.piano(oggi, oggi), api.personale()])
      .then(([, p, s]) => {
        spazi = p.spazi;
        impiantoId = p.impiantiGestiti[0] ?? '';
        personale = s;
        daIndirizzo();
      })
      .catch((e) => (errore = messaggioErrore(e)))
      .finally(() => (caricamento = false));
  });

  const visibili = $derived(corsi.filter((c) => !soloAttivi || c.attivo));
  const lezioniAttive = (c: Corso) => c.lezioni.filter((l) => !l.validaAl || l.validaAl >= oggi);

  function aggiornaPannello() {
    if (pannello?.tipo === 'corso' && pannello.corso) pannello = { tipo: 'corso', corso: corsi.find((c) => c.id === pannello!.corso!.id) ?? null };
  }

  async function corsoSalvato(c: Corso, testo: string) {
    await carica();
    messaggio = testo;
    avvisi = [];
    pannello = { tipo: 'corso', corso: corsi.find((x) => x.id === c.id) ?? c };
  }

  async function lezioneSalvata(testo: string, nuovi: string[]) {
    const corsoId = pannello?.corso?.id;
    await carica();
    messaggio = testo;
    avvisi = nuovi;
    const c = corsi.find((x) => x.id === corsoId);
    pannello = c ? { tipo: 'corso', corso: c } : null;
  }

  async function togli(l: Lezione) {
    if (confermaTogli !== l.id) return (confermaTogli = l.id);
    confermaTogli = '';
    try {
      await api.togliLezione(l.id);
      await carica();
      aggiornaPannello();
      messaggio = l.validaDal > oggi ? 'Lezione eliminata.' : 'Lezione tolta dal piano da domani; le date passate restano nello storico.';
      avvisi = [];
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  const lezioneBreve = (l: Lezione) => `${GIORNI_BREVI[l.giornoSettimana]} ${ora(l.inizio)}–${ora(l.fine)}`;
</script>

<section class="page">
  <div class="barra">
    <div class="seg filtro" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Quali corsi">
      <button aria-pressed={soloAttivi} onclick={() => (soloAttivi = true)}>Attivi</button>
      <button aria-pressed={!soloAttivi} onclick={() => (soloAttivi = false)}>Tutti</button>
    </div>
    <p class="muted small nomargin grow">I corsi e le loro lezioni settimanali: da qui nasce il piano vasca.</p>
    <button class="btn btn-primary compatto" disabled={!impiantoId} onclick={() => ((pannello = { tipo: 'corso', corso: null }), (messaggio = ''))}>Nuovo corso…</button>
  </div>
  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#each avvisi as av (av)}<p class="msg err nomargin">Attenzione: {av}</p>{/each}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

  {#if schermo.larga}
    <div class="card tabella">
      <table>
        <thead><tr><th>Corso</th><th>Tipo</th><th>Istruttore</th><th>Lezioni</th><th></th></tr></thead>
        <tbody>
          {#each visibili as c (c.id)}
            <tr class:spento={!c.attivo} onclick={() => ((pannello = { tipo: 'corso', corso: c }), (messaggio = ''))}>
              <td><div class="nome">{c.nome}</div><div class="muted small">{[c.livello, eta(c.etaDa, c.etaA)].filter(Boolean).join(' · ') || '—'}</div></td>
              <td><span class="tipo {colore(c.tipo)}"><span class="pallino"></span>{TIPI_CORSO[c.tipo]}</span></td>
              <td>{c.istruttore?.nome ?? '—'}</td>
              <td>
                <div class="lezioni">
                  {#each lezioniAttive(c) as l (l.id)}
                    <span class="lez"><span class="m">{lezioneBreve(l)}</span> <span class="muted">· {etichettaSpazi(l.spazi, spazi)}</span></span>
                  {:else}<span class="muted small">Nessuna lezione in programma</span>{/each}
                </div>
              </td>
              <td class="stato">{#if !c.attivo}<span class="tag t-altro">non attivo</span>{/if}</td>
            </tr>
          {:else}
            <tr><td colspan="5" class="muted">{caricamento ? 'Caricamento…' : 'Nessun corso.'}</td></tr>
          {/each}
        </tbody>
      </table>
    </div>
  {:else}
    {#each visibili as c (c.id)}
      <button class="card corso-card {colore(c.tipo)}" class:spento={!c.attivo} onclick={() => (pannello = { tipo: 'corso', corso: c })}>
        <span class="nome">{c.nome}</span>
        <span class="muted small">{TIPI_CORSO[c.tipo]}{c.istruttore ? ` · ${c.istruttore.nome}` : ''}</span>
        <span class="small m">{lezioniAttive(c).map(lezioneBreve).join(' · ') || 'Nessuna lezione'}</span>
      </button>
    {/each}
  {/if}
</section>

{#if pannello?.tipo === 'corso'}
  {@const c = pannello.corso}
  <Foglio titolo={c ? c.nome : 'Nuovo corso'} chiudi={() => (pannello = null)}>
    <CorsoForm corso={c} {impiantoId} {personale} fatto={corsoSalvato} />
    {#if c}
      <div class="sezione">
        <div class="intesta"><h3 class="lbl">Lezioni</h3>
          <button class="link small" onclick={() => (pannello = { tipo: 'lezione', corso: c, lezione: null })}>+ Lezione</button>
        </div>
        {#each c.lezioni as l (l.id)}
          {@const finita = !!l.validaAl && l.validaAl < oggi}
          <div class="card lezione" class:spento={finita}>
            <div class="grow">
              <div class="m">{lezioneBreve(l)}</div>
              <div class="muted small">{etichettaSpazi(l.spazi, spazi)}{l.istruttore ? ` · ${l.istruttore.nome}` : ''}</div>
              <div class="muted small">dal {dataLunga(l.validaDal)}{l.validaAl ? ` al ${dataLunga(l.validaAl)}` : ''}</div>
            </div>
            {#if !finita}
              <div class="azioni">
                <button class="link small" onclick={() => (pannello = { tipo: 'lezione', corso: c, lezione: l })}>Modifica</button>
                <button class="link small rosso" onclick={() => togli(l)}>{confermaTogli === l.id ? 'Conferma' : 'Togli'}</button>
              </div>
            {/if}
          </div>
        {:else}
          <p class="muted small nomargin">Ancora nessuna lezione.</p>
        {/each}
      </div>
    {/if}
  </Foglio>
{:else if pannello?.tipo === 'lezione'}
  {@const p = pannello}
  <Foglio titolo={p.lezione ? 'Modifica lezione' : `Nuova lezione · ${p.corso.nome}`} chiudi={() => (pannello = { tipo: 'corso', corso: p.corso })}>
    <LezioneForm corso={p.corso} lezione={p.lezione} {spazi} {personale} fatto={lezioneSalvata} />
  </Foglio>
{/if}

<style>
  .barra { display: flex; align-items: center; gap: var(--space-16); flex-wrap: wrap; }
  .filtro { width: 200px; }
  .compatto { height: 40px; font-size: var(--text-md); padding: 0 var(--space-14); }
  .tabella { overflow-x: auto; }
  table { width: 100%; border-collapse: collapse; font-size: var(--text-md); }
  th { text-align: left; font-size: var(--text-2xs); font-weight: 600; letter-spacing: .06em; text-transform: uppercase; color: var(--muted); padding: var(--space-10) var(--space-12); border-bottom: 1px solid var(--line); }
  td { padding: var(--space-10) var(--space-12); border-bottom: 1px solid var(--line-soft); vertical-align: top; }
  tbody tr { cursor: pointer; }
  tbody tr:hover { background: var(--carta); }
  .nome { font-weight: 600; }
  .tipo { display: inline-flex; align-items: center; gap: var(--space-6); white-space: nowrap; }
  .lezioni { display: flex; flex-direction: column; gap: 2px; font-size: var(--text-sm); }
  .stato { text-align: right; }
  .spento { opacity: 0.6; }
  .corso-card { display: flex; flex-direction: column; align-items: flex-start; gap: 2px; width: 100%; padding: var(--space-10) var(--space-14); border-left: 4px solid var(--c-line); color: var(--ink); font: inherit; text-align: left; cursor: pointer; }
  .sezione { display: flex; flex-direction: column; gap: var(--space-8); margin-top: var(--space-12); padding-top: var(--space-12); border-top: 1px solid var(--line); }
  .intesta { display: flex; justify-content: space-between; align-items: center; }
  .intesta h3 { margin: 0; }
  .lezione { display: flex; gap: var(--space-12); align-items: flex-start; padding: var(--space-10) var(--space-12); }
  .azioni { display: flex; flex-direction: column; align-items: flex-end; }
  .azioni .link { min-height: 30px; }
  .rosso { color: var(--danger); }
  @media (max-width: 1023px) { .filtro { width: 100%; } .barra .btn { width: 100%; } }
</style>
