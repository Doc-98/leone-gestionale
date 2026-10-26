<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import Azione from '../components/Azione.svelte';
  import Foglio from '../components/Foglio.svelte';
  import Iscritti from './Iscritti.svelte';
  import CorsoForm from './CorsoForm.svelte';
  import LezioneForm from './LezioneForm.svelte';
  import { tick } from 'svelte';
  import { api, messaggioErrore, type Corso, type FasciaOraria, type Lezione, type PersonaBreve, type SpazioPiano } from '../lib/api';
  import { aggiungiGiorni, dataBreve, dataLunga, GIORNI_BREVI, GIORNI_ESTESI, iso, MESI, ora } from '../lib/date';
  import { colore, eta, TIPI_CORSO } from '../lib/piano';
  import {
    assettoIn, cadeIl, corsieDelCorso, corsieInAssetto, dateDi, doveBreve, nomiBrevi, numeroDi, ordinale, piuFrequente, prossimaData, riempimento, righeOrario,
    settimanaDelMese, slotStandard, spaziOccupati, turniAttivi, type Ripeti, type Slot,
  } from '../lib/corsi';
  import { leggiPreferenza, salvaPreferenza } from '../lib/preferenze';
  import { schermo } from '../lib/schermo.svelte';

  /**
   * La scheda di un corso: in alto il riassunto (istruttori, turni, iscritti, posti liberi, lista d'attesa), sotto le
   * schede Turni · Lista d'attesa · Dettagli. I turni si vedono per orario (una riga per orario standard, una colonna per
   * giorno) o per giorno; chi pianifica aggiunge un turno al volo da una casella libera.
   */
  let { corso, corsi, spazi, fasce, personale, gestore, ricarica }: {
    corso: Corso;
    corsi: Corso[];
    spazi: SpazioPiano[];
    fasce: FasciaOraria[];
    personale: PersonaBreve[];
    gestore: boolean;
    ricarica: () => Promise<unknown>;
  } = $props();

  type Scheda = 'turni' | 'attesa' | 'dettagli';
  type ModoTurni = 'orario' | 'giorno';

  const oggi = iso(new Date());
  const SCHEDE: { id: Scheda; nome: string }[] = [
    { id: 'turni', nome: 'Turni' }, { id: 'attesa', nome: "Lista d'attesa" }, { id: 'dettagli', nome: 'Dettagli' },
  ];
  const leggiScheda = (): Scheda => {
    try { const s = sessionStorage.getItem('scheda-corso'); if (s === 'turni' || s === 'attesa' || s === 'dettagli') return s; } catch { /* niente */ }
    return 'turni';
  };
  let scheda = $state<Scheda>(leggiScheda());
  let modo = $state<ModoTurni>(leggiPreferenza<ModoTurni>('corso.turni', 'orario', ['orario', 'giorno']));
  $effect(() => salvaPreferenza('corso.turni', modo));
  const modoEffettivo = $derived<ModoTurni>(schermo.larga ? modo : 'giorno');

  let messaggio = $state('');
  let errore = $state('');
  let avvisi = $state<string[]>([]);
  let foglio = $state<{ tipo: 'turno'; id: string } | { tipo: 'nuovo' } | null>(null);

  // ── Numeri del corso ──
  const attivi = $derived(turniAttivi(corso.lezioni, oggi));
  const r = $derived(riempimento(attivi));
  const righe = $derived(righeOrario(attivi));
  const slots = $derived(slotStandard(attivi));
  const perGiorno = $derived([1, 2, 3, 4, 5, 6, 7].map((g) => attivi.filter((l) => l.giornoSettimana === g).sort((a, b) => a.inizio.localeCompare(b.inizio))));
  const parco = $derived(corso.istruttori.length ? corso.istruttori : corso.istruttore ? [corso.istruttore] : []);
  const giorniConTurni = $derived(perGiorno.map((l, i) => (l.length ? i + 1 : 0)).filter(Boolean));
  const giorniTesto = $derived(!giorniConTurni.length ? 'nessun giorno' : giorniConTurni.length === 1 ? `solo ${GIORNI_ESTESI[giorniConTurni[0]]}`
    : `da ${GIORNI_ESTESI[giorniConTurni[0]]} a ${GIORNI_ESTESI[giorniConTurni[giorniConTurni.length - 1]]}`);
  const conAttesa = $derived(attivi.filter((l) => l.inAttesa > 0));
  const conPostiLiberi = $derived(attivi.filter((l) => l.posti != null && l.posti > l.iscritti));
  const doveAbituale = $derived(doveBreve([...new Set(attivi.flatMap((l) => l.spazi))], spazi));

  const turnoAperto = $derived(foglio?.tipo === 'turno' ? corso.lezioni.find((l) => l.id === (foglio as { id: string }).id) ?? null : null);

  function vai(s: Scheda) {
    scheda = s;
    try { sessionStorage.setItem('scheda-corso', s); } catch { /* niente */ }
  }
  async function tastiSchede(e: KeyboardEvent) {
    const i = SCHEDE.findIndex((x) => x.id === scheda);
    const j = e.key === 'ArrowRight' ? (i + 1) % SCHEDE.length : e.key === 'ArrowLeft' ? (i - 1 + SCHEDE.length) % SCHEDE.length
      : e.key === 'Home' ? 0 : e.key === 'End' ? SCHEDE.length - 1 : -1;
    if (j < 0) return;
    e.preventDefault();
    vai(SCHEDE[j].id);
    await tick();
    document.getElementById(`tab-c-${SCHEDE[j].id}`)?.focus();
  }

  // ── Un turno ──
  const orario = (l: Pick<Lezione, 'inizio' | 'fine'>) => `${ora(l.inizio)}–${ora(l.fine)}`;
  const nomeTurno = (l: Lezione) => `${GIORNI_ESTESI[l.giornoSettimana]} ${orario(l)}`;
  const istruttoriDi = (l: Lezione) => (l.istruttori.length ? l.istruttori : corso.istruttore ? [corso.istruttore] : []);
  const pieno = (l: Lezione) => l.posti != null && l.iscritti >= l.posti;
  /** La prossima data (da oggi) in cui il turno si tiene; se è finito, l'ultima. */
  function prossimaOccorrenza(l: Lezione): string {
    let d = oggi < l.validaDal ? l.validaDal : oggi;
    for (let i = 0; i < 400; i++, d = aggiungiGiorni(d, 1)) {
      if (l.validaAl && d > l.validaAl) break;
      if (cadeIl(l, d)) return d;
    }
    return l.validaAl ?? l.validaDal;
  }
  function notaTurno(l: Lezione): string {
    if (l.validaAl && l.validaAl === l.validaDal) return `solo il ${dataBreve(l.validaDal)}`;
    const parti: string[] = [];
    if (l.cadenza === 'MENSILE') parti.push(`il ${ordinale(settimanaDelMese(l.validaDal))} del mese`);
    if (l.validaDal > oggi) parti.push(`dal ${dataBreve(l.validaDal)}`);
    return parti.join(' · ');
  }

  async function togli(l: Lezione) {
    try {
      await api.togliLezione(l.id);
      await ricarica();
      foglio = null;
      messaggio = l.validaDal > oggi ? 'Turno eliminato.' : 'Turno tolto dal piano da domani; le date passate restano nello storico.';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  // ── Menu rapido: un turno al volo da una casella libera ──
  interface Rapido { chiave: string; giorno: number; fisso: Slot | null }
  let rapido = $state<Rapido | null>(null);
  let slotScelto = $state<string>('');
  let dalle = $state('17:00');
  let alle = $state('17:45');
  let corsia = $state('');
  let posti = $state<number | null>(8);
  let istruttori = $state<string[]>([]);
  let ripeti = $state<Ripeti>('una');
  let giorni = $state<number[]>([]);
  let finoAl = $state('');
  let creando = $state(false);

  const corsieAbituali = $derived(corsieDelCorso(attivi, spazi));
  /** le corsie nell'assetto della fascia di quel giorno e orario (se la vasca lì è divisa in un altro modo) */
  const opzioniCorsia = $derived(rapido && corsieAbituali[0]?.padreId
    ? corsieInAssetto(corsieAbituali, assettoIn(fasce, corsieAbituali[0].padreId, prossimaData(rapido.giorno, oggi), dalle, alle), spazi)
    : corsieAbituali);
  const postiTipici = $derived(piuFrequente(attivi.map((l) => l.posti).filter((n): n is number => n != null)) ?? null);
  const spazioAbituale = $derived(piuFrequente(attivi.flatMap((l) => l.spazi)));
  /** la fine più comune tra i turni che si ripetono (di solito la fine della stagione); vuota se di solito non c'è */
  const finoAlTipico = $derived(piuFrequente(attivi.filter((l) => l.validaAl !== l.validaDal).map((l) => l.validaAl ?? '')) ?? '');
  const primaData = $derived(rapido ? prossimaData(rapido.giorno, oggi) : oggi);
  const occupati = $derived(rapido ? spaziOccupati(corsi, spazi, primaData, dalle, alle) : new Set<string>());
  const date = $derived(rapido ? dateDi(ripeti, rapido.giorno, giorni, oggi, ripeti === 'una' ? null : finoAl || null) : []);
  const usatiNelGiorno = $derived(rapido ? perGiorno[rapido.giorno - 1].map((l) => `${ora(l.inizio)}-${ora(l.fine)}`) : []);

  function apriRapido(chiave: string, giorno: number, fisso: Slot | null) {
    if (rapido?.chiave === chiave) return (rapido = null);
    let s: Slot | undefined = fisso ?? undefined;
    if (!s) {
      const usati = perGiorno[giorno - 1];
      const ultimo = usati.length ? ora(usati[usati.length - 1].inizio) : '';
      const liberi = slots.filter((x) => !usati.some((l) => `${ora(l.inizio)}-${ora(l.fine)}` === x.chiave));
      s = liberi.find((x) => x.inizio > ultimo) ?? liberi[0];
    }
    slotScelto = s?.chiave ?? 'altro';
    dalle = s?.inizio ?? '17:00';
    alle = s?.fine ?? '17:45';
    rapido = { chiave, giorno, fisso };
    const data = prossimaData(giorno, oggi);
    const occ = spaziOccupati(corsi, spazi, data, dalle, alle);
    const libere = opzioniCorsia.filter((o) => !occ.has(o.id));
    const abituale = spazi.find((x) => x.id === spazioAbituale);
    corsia = libere.find((o) => o.id === spazioAbituale)?.id ?? (abituale && libere.find((o) => numeroDi(o) === numeroDi(abituale))?.id) ?? libere[0]?.id ?? '';
    posti = postiTipici ?? (opzioniCorsia.some((o) => o.padreId) ? 8 : null);
    istruttori = parco.length ? [parco[0].id] : [];
    ripeti = 'una';
    giorni = [giorno];
    finoAl = finoAlTipico;
  }

  function scegliSlot(s: Slot | 'altro') {
    if (s === 'altro') return (slotScelto = 'altro');
    slotScelto = s.chiave;
    dalle = s.inizio;
    alle = s.fine;
  }

  // se la corsia scelta non va più bene (occupata a un altro orario, o la vasca lì è divisa in un altro modo)
  // si prende la corsia con lo stesso numero, altrimenti la prima libera
  $effect(() => {
    if (!rapido) return;
    if (corsia && opzioniCorsia.some((o) => o.id === corsia) && !occupati.has(corsia)) return;
    const prima = spazi.find((s) => s.id === corsia);
    const stessa = prima && opzioniCorsia.find((o) => numeroDi(o) === numeroDi(prima) && !occupati.has(o.id));
    corsia = stessa?.id ?? opzioniCorsia.find((o) => !occupati.has(o.id))?.id ?? '';
  });

  const titoloRapido = $derived.by(() => {
    if (!rapido) return '';
    const g = GIORNI_ESTESI[rapido.giorno];
    const d = primaData;
    switch (ripeti) {
      case 'una': return `${g[0].toUpperCase()}${g.slice(1)} ${Number(d.slice(8, 10))} ${MESI[Number(d.slice(5, 7)) - 1]}`;
      case 'settimana': return `Ogni ${g}`;
      case 'mese': return `Il ${ordinale(settimanaDelMese(d))} ${g} del mese`;
      default: return `Ogni ${[...giorni].sort().map((x) => GIORNI_ESTESI[x]).join(' e ') || '…'}`;
    }
  });
  const riepilogoRapido = $derived.by(() => {
    if (!rapido) return '';
    if (ripeti === 'una') return `Una lezione, ${dataLunga(primaData)}.`;
    if (!date.length) return 'Nessuna lezione in questo periodo.';
    return `${date.length} lezioni, dal ${dataBreve(date[0])} al ${dataBreve(date[date.length - 1])}${finoAl ? '' : ' (senza una fine: qui conto un anno)'}.`;
  });

  function chiudiRapido(e: MouseEvent) {
    if (rapido && !(e.target as HTMLElement).closest('.rapido, .vuoto-t, .agg-g')) rapido = null;
  }

  /** Con «giorni scelti» la vasca può essere divisa in modo diverso negli altri giorni: la corsia con lo stesso numero. */
  function corsiaPer(g: number): string {
    const scelta = spazi.find((x) => x.id === corsia);
    if (!scelta?.padreId) return corsia;
    const qui = corsieInAssetto(corsieAbituali, assettoIn(fasce, scelta.padreId, prossimaData(g, oggi), dalle, alle), spazi);
    return qui.find((o) => o.id === corsia)?.id ?? qui.find((o) => numeroDi(o) === numeroDi(scelta))?.id ?? corsia;
  }

  async function creaRapido() {
    if (!rapido) return;
    errore = '';
    if (!corsia) return (errore = 'Non ci sono corsie libere a quell\'ora: scegli un altro orario.');
    if (alle <= dalle) return (errore = 'La fine del turno deve essere dopo l\'inizio.');
    if (ripeti === 'giorni' && !giorni.length) return (errore = 'Scegli almeno un giorno.');
    if (ripeti !== 'una' && finoAl && finoAl < primaData) return (errore = 'La data di fine è prima della prima lezione.');
    creando = true;
    const base = { inizio: dalle, fine: alle, spazi: [corsia], istruttori, posti };
    const richieste = ripeti === 'giorni'
      ? [...giorni].sort().map((g) => ({ ...base, spazi: [corsiaPer(g)], giornoSettimana: g, validaDal: prossimaData(g, oggi), validaAl: finoAl || null, cadenza: 'SETTIMANALE' as const }))
      : [{ ...base, giornoSettimana: rapido.giorno, validaDal: primaData, validaAl: ripeti === 'una' ? primaData : finoAl || null,
        cadenza: ripeti === 'mese' ? ('MENSILE' as const) : ('SETTIMANALE' as const) }];
    try {
      const creati: Lezione[] = [];
      const nuoviAvvisi: string[] = [];
      for (const l of richieste) {
        const esito = await api.aggiungiLezione(corso.id, l);
        creati.push(esito.valore);
        nuoviAvvisi.push(...esito.avvisi);
      }
      await ricarica();
      rapido = null;
      avvisi = nuoviAvvisi;
      messaggio = creati.length === 1 ? 'Turno aggiunto.' : `${creati.length} turni aggiunti.`;
      foglio = { tipo: 'turno', id: creati[0].id };
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      creando = false;
    }
  }
</script>

<svelte:window onclick={chiudiRapido} onkeydown={(e) => e.key === 'Escape' && (rapido = null)} />

<section class="page scheda-corso {colore(corso.tipo)}">
  <a class="briciole" href="#/corsi"><Icona nome="sx" />Corsi</a>

  <section class="card riassunto">
    <div class="chi">
      <div class="grow">
        <h1 class="d">{#if corso.colore}<span class="livello" style="background: {corso.colore}"></span>{/if}{corso.nome}</h1>
        <div class="dati">
          <span class="tag cat-tag">{TIPI_CORSO[corso.tipo]}</span>
          {#if corso.livello || corso.etaDa != null || corso.etaA != null}<span>{[corso.livello, eta(corso.etaDa, corso.etaA)].filter(Boolean).join(' · ')}</span>{/if}
          {#if parco.length}<span>{parco.length === 1 ? 'Istruttore' : 'Istruttori'} {#each parco as p, i (p.id)}<b>{p.nome}</b>{i < parco.length - 1 ? ', ' : ''}{/each}</span>{/if}
          {#if doveAbituale}<span>{doveAbituale}</span>{/if}
          {#if !corso.attivo}<span class="tag t-altro">non attivo</span>{/if}
        </div>
      </div>
      {#if gestore}
        <div class="azioni-r">
          <button class="btn btn-primary" onclick={() => ((foglio = { tipo: 'nuovo' }), (messaggio = ''))}><Icona nome="piu" />Aggiungi un turno…</button>
          <button class="btn btn-secondary" onclick={() => vai('dettagli')}><Icona nome="matita" />Modifica il corso…</button>
        </div>
      {/if}
    </div>
    <div class="kpis">
      <div class="kpi"><span class="lbl">Turni</span><span class="val">{attivi.length}</span><span class="kpi-nota">{giorniTesto}</span></div>
      <div class="kpi"><span class="lbl">Iscritti</span>
        <span class="val">{r.iscritti}{#if r.posti != null} <span class="m su">/ {r.posti}</span>{/if}</span>
        <span class="kpi-nota">{r.percentuale != null ? `${r.percentuale}% dei posti` : 'senza limite di posti'}</span></div>
      <div class="kpi"><span class="lbl">Posti liberi</span><span class="val">{r.posti != null ? r.liberi : '—'}</span>
        <span class="kpi-nota">{r.posti != null ? `in ${r.turniLiberi} turni su ${attivi.length}` : ''}</span></div>
      <div class="kpi" class:warn={r.inAttesa > 0}><span class="lbl">In lista d'attesa</span><span class="val">{r.inAttesa}</span>
        <span class="kpi-nota">{r.inAttesa ? `per ${conAttesa.length === 1 ? '1 turno' : `${conAttesa.length} turni`}` : 'nessuno'}</span></div>
    </div>
  </section>

  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#each avvisi as av (av)}<p class="msg err nomargin">Attenzione: {av}</p>{/each}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

  <div class="schede" role="tablist" aria-label="Sezioni del corso" tabindex="-1" onkeydown={tastiSchede}>
    {#each SCHEDE as t (t.id)}
      <button id="tab-c-{t.id}" class="scheda-tab" role="tab" aria-selected={scheda === t.id} aria-controls="pannello-c-{t.id}" tabindex={scheda === t.id ? 0 : -1} onclick={() => vai(t.id)}>
        {t.nome}
        {#if t.id === 'turni'}<span class="conta">{attivi.length}</span>{/if}
        {#if t.id === 'attesa' && r.inAttesa}<span class="conta warn">{r.inAttesa}</span>{/if}
      </button>
    {/each}
  </div>

  <div id="pannello-c-{scheda}" role="tabpanel" aria-labelledby="tab-c-{scheda}">
    {#if scheda === 'turni'}
      <section class="card turni">
        <div class="testa-t">
          <h2>Settimana tipo</h2>
          <span class="muted small grow">Ogni turno è un gruppo con i suoi iscritti: un clic apre iscritti e impostazioni.</span>
          {#if schermo.larga}
            <div class="seg modo" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Come mostrare i turni">
              <button aria-pressed={modo === 'orario'} onclick={() => (modo = 'orario')}>Per orario</button>
              <button aria-pressed={modo === 'giorno'} onclick={() => (modo = 'giorno')}>Per giorno</button>
            </div>
          {/if}
        </div>

        {#if !attivi.length}
          <p class="muted nomargin">Ancora nessun turno.{gestore ? ' Aggiungine uno con «Aggiungi un turno…».' : ''}</p>
        {:else if modoEffettivo === 'orario'}
          <div class="matrice" role="grid" aria-label="Turni per orario e giorno">
            <span class="ang"></span>
            {#each [1, 2, 3, 4, 5, 6, 7] as g (g)}<span class="gt" role="columnheader">{GIORNI_ESTESI[g]}</span>{/each}
            {#each righe as riga (riga.slot.chiave)}
              <span class="ot" role="rowheader">{riga.slot.inizio}<small>–{riga.slot.fine}</small></span>
              {#each riga.celle as cella, i (i)}
                {@const g = i + 1}
                {@const chiave = `${g}|${riga.slot.chiave}`}
                <div class="ancora">
                  {#each cella as l (l.id)}{@render turno(l, false)}{/each}
                  {#if !cella.length}
                    {#if gestore}
                      <button class="vuoto-t" aria-expanded={rapido?.chiave === chiave} aria-label="Aggiungi un turno {GIORNI_ESTESI[g]} {riga.slot.inizio}–{riga.slot.fine}"
                              onclick={() => apriRapido(chiave, g, riga.slot)}><Icona nome="piu" /></button>
                      {#if rapido?.chiave === chiave}{@render menuRapido(g)}{/if}
                    {:else}<span class="nessuno"></span>{/if}
                  {/if}
                </div>
              {/each}
            {/each}
          </div>
        {:else}
          <div class="colonne-g">
            {#each perGiorno as turni, i (i)}
              {@const g = i + 1}
              {@const chiave = `${g}|giorno`}
              <div class="col-g">
                <h3><span>{schermo.larga ? GIORNI_BREVI[g] : GIORNI_ESTESI[g]}</span><span class="m">{turni.length || ''}</span></h3>
                {#each turni as l (l.id)}{@render turno(l, true)}{/each}
                {#if gestore}
                  <div class="ancora">
                    <button class="agg-g" aria-expanded={rapido?.chiave === chiave} aria-label="Aggiungi un turno {GIORNI_ESTESI[g]}" onclick={() => apriRapido(chiave, g, null)}><Icona nome="piu" />Turno</button>
                    {#if rapido?.chiave === chiave}{@render menuRapido(g)}{/if}
                  </div>
                {/if}
              </div>
            {/each}
          </div>
        {/if}

        {#if r.inAttesa && conPostiLiberi.length}
          <div class="hint">
            <Icona nome="info" />
            <span>{r.inAttesa === 1 ? 'Una persona aspetta' : `${r.inAttesa} persone aspettano`} un posto. Ci sono posti liberi
              {#each conPostiLiberi.slice(0, 4) as l, i (l.id)}{i ? ', ' : ''}<button class="link" onclick={() => (foglio = { tipo: 'turno', id: l.id })}>{GIORNI_BREVI[l.giornoSettimana]} {ora(l.inizio)} ({(l.posti ?? 0) - l.iscritti})</button>{/each}{conPostiLiberi.length > 4 ? '…' : '.'}</span>
          </div>
        {/if}
      </section>
    {:else if scheda === 'attesa'}
      <section class="card blocco">
        <h2>Lista d'attesa</h2>
        {#each conAttesa as l (l.id)}
          <button class="riga-a" onclick={() => (foglio = { tipo: 'turno', id: l.id })}>
            <span class="grow"><b>{nomeTurno(l)}</b><br /><span class="muted small">{doveBreve(l.spazi, spazi)} · {l.iscritti}{l.posti != null ? `/${l.posti}` : ''} iscritti</span></span>
            <span class="attesa-t">{l.inAttesa} in attesa</span>
            <Icona nome="dx" />
          </button>
        {:else}
          <p class="muted nomargin">Nessuno in lista d'attesa per questo corso.</p>
        {/each}
        <p class="muted small nomargin">Le persone in attesa si iscrivono, si avvisano o si tolgono dal turno: aprilo per vederle.</p>
      </section>
    {:else}
      <section class="card blocco dettagli">
        <h2>Dettagli del corso</h2>
        {#if gestore}
          <CorsoForm {corso} impiantoId={corso.impiantoId} {personale} fatto={async (_c, testo) => { await ricarica(); messaggio = testo; }} />
        {:else}
          <dl>
            <dt>Tipo</dt><dd>{TIPI_CORSO[corso.tipo]}</dd>
            <dt>Livello ed età</dt><dd>{[corso.livello, eta(corso.etaDa, corso.etaA)].filter(Boolean).join(' · ') || '—'}</dd>
            <dt>Istruttori</dt><dd>{parco.map((p) => p.nome).join(', ') || '—'}</dd>
            {#if corso.note}<dt>Note</dt><dd>{corso.note}</dd>{/if}
          </dl>
        {/if}
      </section>
    {/if}
  </div>
</section>

{#snippet turno(l: Lezione, conOrario: boolean)}
  {@const p = pieno(l)}
  {@const nota = notaTurno(l)}
  <button class="turno" class:pieno={p} aria-label="{nomeTurno(l)}: {l.iscritti} iscritti{l.posti != null ? ` su ${l.posti}` : ''}{l.inAttesa ? `, ${l.inAttesa} in attesa` : ''}"
          onclick={() => ((foglio = { tipo: 'turno', id: l.id }), (messaggio = ''), (avvisi = []))}>
    {#if conOrario}<span class="ora m">{orario(l)}</span>{/if}
    <span class="r1">
      <span class="posti-g"><span class="posti m" class:pieno={p}>{l.iscritti}{l.posti != null ? `/${l.posti}` : ''}</span>{#if l.inAttesa}<span class="att">+{l.inAttesa}</span>{/if}</span>
      <span class="dove">{doveBreve(l.spazi, spazi)}</span>
    </span>
    {#if l.posti != null && l.posti <= 16}
      <span class="pt" aria-hidden="true">{#each Array.from({ length: l.posti }, (_, k) => k) as k (k)}<i class:si={k < l.iscritti}></i>{/each}</span>
    {:else if l.posti != null}
      <span class="barra-t" aria-hidden="true"><span style="width: {Math.min(100, (l.iscritti / l.posti) * 100)}%"></span></span>
    {/if}
    {#if istruttoriDi(l).length}<span class="ist">{nomiBrevi(istruttoriDi(l))}</span>{/if}
    {#if nota}<span class="ist nota">{nota}</span>{/if}
  </button>
{/snippet}

{#snippet menuRapido(g: number)}
  {@const fisso = rapido?.fisso}
  <div class="rapido" class:dx={g >= 5} role="dialog" aria-label="Nuovo turno">
    <div class="rapido-testa">
      <div><p class="lbl">Nuovo turno</p><span class="titolo-r">{titoloRapido}{#if fisso}{' · '}<span class="m">{dalle}–{alle}</span>{/if}</span></div>
      <button class="azione chiudi" aria-label="Chiudi" title="Chiudi" onclick={() => (rapido = null)}><Icona nome="chiudi" /></button>
    </div>
    <div class="rapido-corpo">
      {#if !fisso}
        <div class="campo-r"><span class="lbl">Orario</span>
          <div class="mini-chips larghi" role="group" aria-label="Orario">
            {#each slots as s (s.chiave)}
              {@const preso = usatiNelGiorno.includes(s.chiave)}
              <button class="m" class:occupata={preso} aria-pressed={slotScelto === s.chiave} disabled={preso}
                      title={preso ? `${s.inizio}–${s.fine}: c'è già un turno` : `${s.inizio}–${s.fine}`} onclick={() => scegliSlot(s)}>{s.inizio}</button>
            {/each}
            <button aria-pressed={slotScelto === 'altro'} onclick={() => scegliSlot('altro')}>Altro orario</button>
          </div>
          {#if slotScelto === 'altro'}
            <div class="orari">
              <input class="inp m" type="time" aria-label="Dalle" bind:value={dalle} />
              <span class="muted">–</span>
              <input class="inp m" type="time" aria-label="Alle" bind:value={alle} />
            </div>
          {:else}<span class="muted small">{dalle}–{alle}</span>{/if}
        </div>
      {/if}
      <div class="due-r">
        <div class="campo-r"><span class="lbl">{opzioniCorsia.some((o) => o.padreId) ? 'Corsia' : 'Dove'}</span>
          <div class="mini-chips" class:tre={opzioniCorsia.length > 3 && opzioniCorsia.every((o) => o.padreId)} role="group" aria-label="Corsia">
            {#each opzioniCorsia as o (o.id)}
              {@const occ = occupati.has(o.id)}
              <button class:occupata={occ} class:larga={!o.padreId} aria-pressed={corsia === o.id} disabled={occ}
                      title={occ ? `${o.nome}: occupata a quell'ora` : o.nome} onclick={() => (corsia = o.id)}>{o.padreId ? numeroDi(o) : o.nome}</button>
            {/each}
          </div>
        </div>
        <div class="campo-r"><span class="lbl">Posti</span>
          <div class="passo">
            <button aria-label="Un posto in meno" disabled={posti == null || posti <= 1} onclick={() => (posti = Math.max(1, (posti ?? 8) - 1))}><Icona nome="meno" /></button>
            <span class="m">{posti ?? '∞'}</span>
            <button aria-label="Un posto in più" onclick={() => (posti = (posti ?? 0) + 1)}><Icona nome="piu" /></button>
          </div>
        </div>
      </div>
      <div class="campo-r"><span class="lbl">Istruttori</span>
        <div class="mini-chips larghi" role="group" aria-label="Istruttori">
          {#each parco as p (p.id)}
            <button aria-pressed={istruttori.includes(p.id)} onclick={() => (istruttori = istruttori.includes(p.id) ? istruttori.filter((x) => x !== p.id) : [...istruttori, p.id])}>{p.nome}</button>
          {:else}<span class="muted small">Il corso non ha ancora istruttori: aggiungili nei dettagli.</span>{/each}
        </div>
      </div>
      <div class="campo-r"><span class="lbl">Ripeti</span>
        <div class="mini-chips larghi" role="group" aria-label="Ripeti">
          <button aria-pressed={ripeti === 'una'} onclick={() => (ripeti = 'una')}>Una volta</button>
          <button aria-pressed={ripeti === 'settimana'} onclick={() => (ripeti = 'settimana')}>Ogni settimana</button>
          <button aria-pressed={ripeti === 'mese'} onclick={() => (ripeti = 'mese')}>Ogni mese</button>
          <button aria-pressed={ripeti === 'giorni'} onclick={() => ((ripeti = 'giorni'), giorni.length || (giorni = [g]))}>Giorni scelti</button>
        </div>
        {#if ripeti === 'giorni'}
          <div class="mini-chips" role="group" aria-label="Giorni">
            {#each [1, 2, 3, 4, 5, 6, 7] as d (d)}
              <button aria-pressed={giorni.includes(d)} aria-label={GIORNI_ESTESI[d]} onclick={() => (giorni = giorni.includes(d) ? giorni.filter((x) => x !== d) : [...giorni, d])}>{GIORNI_BREVI[d][0].toUpperCase()}</button>
            {/each}
          </div>
        {/if}
        {#if ripeti !== 'una'}
          <label class="fino">fino al <input class="inp" type="date" min={oggi} bind:value={finoAl} /></label>
        {/if}
      </div>
    </div>
    <div class="rapido-piede">
      <span class="muted small">{riepilogoRapido}</span>
      <button class="btn btn-primary" disabled={creando || !corsia || (ripeti !== 'una' && !date.length)} onclick={creaRapido}>
        <Icona nome="piu" />{creando ? 'Aggiungo…' : ripeti === 'una' || date.length === 1 ? 'Aggiungi il turno' : ripeti === 'giorni' && giorni.length > 1 ? `Aggiungi ${giorni.length} turni` : 'Aggiungi il turno'}
      </button>
    </div>
  </div>
{/snippet}

{#if foglio?.tipo === 'nuovo'}
  <Foglio titolo="Nuovo turno · {corso.nome}" chiudi={() => (foglio = null)}>
    <LezioneForm {corso} lezione={null} {spazi} {personale} fatto={async (testo, nuovi) => { await ricarica(); foglio = null; messaggio = testo; avvisi = nuovi; }} />
  </Foglio>
{:else if turnoAperto}
  {@const l = turnoAperto}
  <Foglio titolo="{GIORNI_ESTESI[l.giornoSettimana][0].toUpperCase()}{GIORNI_ESTESI[l.giornoSettimana].slice(1)} {orario(l)}" chiudi={() => (foglio = null)}>
    <div class="foglio-turno">
      <p class="muted nomargin">{[corso.nome, doveBreve(l.spazi, spazi), nomiBrevi(istruttoriDi(l)), notaTurno(l)].filter(Boolean).join(' · ')}</p>
      <div class="kpis piccoli">
        <div class="kpi" class:warn={pieno(l)}><span class="lbl">Iscritti</span><span class="val">{l.iscritti}{l.posti != null ? ` / ${l.posti}` : ''}</span></div>
        <div class="kpi" class:warn={l.inAttesa > 0}><span class="lbl">In attesa</span><span class="val">{l.inAttesa}</span></div>
        <div class="kpi"><span class="lbl">Prossima</span><span class="val small-v">{dataBreve(prossimaOccorrenza(l))}</span></div>
      </div>
      {#key l.id}
        <Iscritti lezioneId={l.id} data={prossimaOccorrenza(l)} cambiato={() => ricarica()} />
      {/key}
      {#if gestore}
        <details class="impostazioni">
          <summary>Impostazioni del turno</summary>
          {#key l.id}
            <LezioneForm {corso} lezione={l} {spazi} {personale} fatto={async (testo, nuovi) => { await ricarica(); messaggio = testo; avvisi = nuovi; }} />
          {/key}
          <div class="togli">
            <span class="muted small grow">{l.validaDal > oggi ? 'Il turno non è ancora iniziato: si elimina.' : 'Il turno smette da domani; le date passate restano nello storico.'}</span>
            <Azione icona="cestino" tono="danger" etichetta="Togli il turno {nomeTurno(l)}" conferma="Togli?" onclick={() => togli(l)} />
          </div>
        </details>
      {/if}
    </div>
  </Foglio>
{/if}

<style>
  .scheda-corso { gap: var(--space-14); }
  .briciole { display: inline-flex; align-items: center; gap: var(--space-4); align-self: flex-start; font-size: var(--text-sm); font-weight: 600; text-decoration: none; }
  .briciole :global(.ic) { width: 16px; height: 16px; }

  .riassunto { display: flex; flex-direction: column; gap: var(--space-16); padding: var(--space-16) var(--space-20) var(--space-20); }
  .chi { display: flex; align-items: flex-start; gap: var(--space-16); flex-wrap: wrap; }
  h1 { margin: 0; font-size: var(--text-4xl); font-weight: 800; line-height: 1.05; letter-spacing: -0.01em; display: flex; align-items: center; gap: var(--space-12); }
  .livello { display: inline-block; width: 14px; height: 14px; border-radius: 50%; flex-shrink: 0; box-shadow: 0 0 0 1px rgba(16, 33, 43, 0.25); }
  .dati { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-4) var(--space-14); margin-top: var(--space-6); font-size: var(--text-md); color: var(--muted); }
  .dati b { color: var(--ink); font-weight: 600; }
  .cat-tag { background: var(--c-bg); color: var(--c-fg); }
  .azioni-r { display: flex; flex-wrap: wrap; gap: var(--space-8); }
  .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: var(--space-10); }
  .kpi { display: flex; flex-direction: column; gap: var(--space-2); padding: var(--space-10) var(--space-14); border-radius: var(--radius-md); background: var(--carta); min-width: 0; }
  .kpi .val { font-family: var(--font-display); font-size: var(--text-3xl); font-weight: 800; line-height: 1.1; }
  .kpi .su { font-size: var(--text-md); font-weight: 500; color: var(--muted); }
  .kpi-nota { font-size: var(--text-xs); color: var(--muted); }
  .kpi.warn { background: var(--warn-bg); }
  .kpi.warn .val, .kpi.warn .kpi-nota, .kpi.warn .lbl { color: var(--warn); }
  .kpis.piccoli .val { font-size: var(--text-2xl); }
  .kpis.piccoli .small-v { font-size: var(--text-lg); padding: 4px 0; }

  .schede { display: flex; gap: var(--space-4); border-bottom: 1px solid var(--line); overflow-x: auto; }
  .scheda-tab { position: relative; display: inline-flex; align-items: center; gap: var(--space-8); min-height: 46px; padding: 0 var(--space-14); border: none; background: none;
    color: var(--muted); font: inherit; font-size: var(--text-md); font-weight: 600; cursor: pointer; white-space: nowrap; border-radius: var(--radius-sm) var(--radius-sm) 0 0; }
  .scheda-tab:hover { color: var(--ink); background: var(--surface-sunken); }
  .scheda-tab[aria-selected='true'] { color: var(--cloro-ink); }
  .scheda-tab[aria-selected='true']::after { content: ''; position: absolute; left: var(--space-10); right: var(--space-10); bottom: 0; height: 3px; border-radius: 3px 3px 0 0; background: var(--cloro); }
  .scheda-tab:focus-visible { outline-offset: -2px; }
  .conta { min-width: 20px; height: 20px; padding: 0 var(--space-6); border-radius: var(--radius-pill); background: var(--surface-sunken); color: var(--ink);
    font-size: var(--text-2xs); font-weight: 700; display: inline-flex; align-items: center; justify-content: center; }
  .conta.warn { background: var(--warn-bg); color: var(--warn); }

  .turni { display: flex; flex-direction: column; gap: var(--space-14); padding: var(--space-16) var(--space-20) var(--space-20); }
  .testa-t { display: flex; align-items: center; gap: var(--space-12); flex-wrap: wrap; }
  h2 { margin: 0; font-size: var(--text-xl); font-weight: 700; }
  .modo { width: 220px; }

  /* turni: griglia orari × giorni */
  .matrice { display: grid; grid-template-columns: 104px repeat(7, minmax(0, 1fr)); gap: var(--space-4); }
  .gt { padding: 0 var(--space-4) var(--space-4); font-size: var(--text-2xs); font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: var(--muted); }
  .ot { display: flex; flex-direction: column; justify-content: center; padding-right: var(--space-8); font-family: var(--font-mono); font-size: var(--text-sm); font-weight: 500; white-space: nowrap; }
  .ot small { font-size: var(--text-2xs); color: var(--muted); }
  .ancora { position: relative; display: flex; flex-direction: column; gap: var(--space-4); min-width: 0; }
  .turno { display: flex; flex-direction: column; justify-content: center; gap: var(--space-4); width: 100%; min-height: 56px; padding: var(--space-8) var(--space-10); border: none;
    border-radius: 10px; background: var(--c-bg); color: var(--c-fg); font: inherit; text-align: left; cursor: pointer; }
  .turno:hover { box-shadow: inset 0 0 0 1.5px var(--c-line); }
  .turno .ora { font-size: var(--text-sm); color: var(--ink); }
  .r1 { display: flex; align-items: baseline; justify-content: space-between; gap: var(--space-6); }
  .posti-g { display: inline-flex; align-items: baseline; gap: var(--space-6); min-width: 0; }
  .posti { font-size: var(--text-md); color: var(--ink); }
  .posti.pieno, .att { color: var(--warn); }
  .att { font-size: var(--text-2xs); font-weight: 700; }
  .dove { font-size: var(--text-2xs); color: var(--ink); white-space: nowrap; }
  .ist { font-size: var(--text-2xs); line-height: 1.3; }
  .ist.nota { font-style: italic; }
  .pt { display: flex; gap: 2px; }
  .pt i { flex: 1; height: 4px; border-radius: 2px; background: color-mix(in srgb, var(--ink) 14%, transparent); }
  .pt i.si { background: var(--c-line); }
  .turno.pieno .pt i.si { background: var(--warn); }
  .barra-t { display: block; height: 4px; border-radius: 2px; background: color-mix(in srgb, var(--ink) 14%, transparent); overflow: hidden; }
  .barra-t span { display: block; height: 100%; background: var(--c-line); }
  .vuoto-t { display: flex; align-items: center; justify-content: center; width: 100%; min-height: 56px; border: 1px dashed var(--line-dashed); border-radius: 10px; background: none; color: var(--muted); cursor: pointer; }
  .vuoto-t :global(.ic) { width: 16px; height: 16px; opacity: 0; }
  .vuoto-t:hover, .vuoto-t[aria-expanded='true'], .vuoto-t:focus-visible { border-color: var(--cloro); background: var(--cloro-soft); color: var(--cloro-ink); }
  .vuoto-t:hover :global(.ic), .vuoto-t[aria-expanded='true'] :global(.ic), .vuoto-t:focus-visible :global(.ic) { opacity: 1; }
  .nessuno { min-height: 56px; }

  /* turni: colonne dei giorni */
  .colonne-g { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: var(--space-8); }
  .col-g { display: flex; flex-direction: column; gap: var(--space-6); min-width: 0; }
  .col-g h3 { margin: 0 0 var(--space-2); display: flex; justify-content: space-between; font-size: var(--text-xs); font-weight: 600; letter-spacing: 0.05em; text-transform: uppercase; color: var(--muted); }
  .col-g .turno { min-height: 0; }
  .agg-g { display: flex; align-items: center; justify-content: center; gap: var(--space-6); width: 100%; height: 36px; border: 1px dashed var(--line-dashed); border-radius: 10px; background: none;
    color: var(--muted); font: inherit; font-size: var(--text-sm); cursor: pointer; }
  .agg-g :global(.ic) { width: 16px; height: 16px; }
  .agg-g:hover, .agg-g[aria-expanded='true'] { border-color: var(--cloro); color: var(--cloro-ink); background: var(--cloro-soft); }

  /* menu rapido */
  .rapido { position: absolute; top: calc(100% + 6px); left: 0; z-index: 6; width: min(400px, calc(100vw - 32px)); display: flex; flex-direction: column; overflow: hidden;
    background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); box-shadow: 0 6px 20px rgba(16, 33, 43, 0.14); color: var(--ink); cursor: default; }
  .rapido.dx { left: auto; right: 0; }
  .rapido-testa { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-8); padding: var(--space-14) var(--space-10) var(--space-14) var(--space-16); background: var(--hero); color: var(--on-fill); }
  .rapido-testa .lbl { margin: 0 0 2px; color: color-mix(in srgb, var(--on-fill) 78%, transparent); }
  .titolo-r { font-family: var(--font-display); font-size: var(--text-2xl); font-weight: 800; line-height: 1.15; letter-spacing: -0.01em; }
  .titolo-r .m { font-family: var(--font-mono); font-size: var(--text-lg); font-weight: 500; letter-spacing: 0; }
  .chiudi { color: var(--on-fill); }
  .chiudi:hover { background: color-mix(in srgb, var(--on-fill) 14%, transparent) !important; }
  .rapido-corpo { display: flex; flex-direction: column; gap: var(--space-16); padding: var(--space-16); }
  .rapido-corpo > * + * { padding-top: var(--space-16); border-top: 1px solid var(--line-soft); }
  .campo-r { display: flex; flex-direction: column; gap: var(--space-8); min-width: 0; }
  .due-r { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--space-16); }
  .mini-chips { display: flex; flex-wrap: wrap; gap: var(--space-4); }
  .mini-chips button { min-width: 36px; height: 36px; padding: 0 var(--space-10); border: 1px solid var(--line); border-radius: var(--radius-pill); background: var(--surface);
    font: inherit; font-size: var(--text-sm); font-weight: 600; color: var(--ink); cursor: pointer; }
  .mini-chips:not(.larghi) button:not(.larga) { padding: 0; font-family: var(--font-mono); font-weight: 500; }
  .mini-chips button.m { font-family: var(--font-mono); font-weight: 500; }
  .mini-chips button[aria-pressed='true'] { background: var(--fill); border-color: var(--fill); color: var(--on-fill); }
  .mini-chips button.occupata, .mini-chips button:disabled { background: var(--surface-sunken); border-style: dashed; color: var(--muted); cursor: not-allowed; }
  .mini-chips.tre { display: grid; grid-template-columns: repeat(3, 40px); gap: var(--space-6); }
  .mini-chips.tre button { width: 40px; }
  .passo { display: inline-flex; align-items: center; gap: var(--space-4); }
  .passo button { width: 36px; height: 36px; display: grid; place-items: center; border: 1px solid var(--line); border-radius: var(--radius-pill); background: var(--surface); color: var(--ink); cursor: pointer; }
  .passo button:disabled { opacity: 0.4; cursor: not-allowed; }
  .passo :global(.ic) { width: 16px; height: 16px; }
  .passo .m { min-width: 32px; text-align: center; font-size: var(--text-lg); }
  .orari { display: flex; align-items: center; gap: var(--space-6); }
  .orari .inp { height: 40px; width: 110px; font-size: var(--text-md); }
  .fino { display: flex; align-items: center; gap: var(--space-8); font-size: var(--text-sm); color: var(--muted); }
  .fino .inp { height: 40px; width: 170px; font-size: var(--text-md); }
  .rapido-piede { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16) var(--space-16); background: var(--carta); border-top: 1px solid var(--line-soft); }

  .hint { display: flex; align-items: flex-start; gap: var(--space-10); }
  .hint .link { display: inline; min-height: 0; height: auto; padding: 0; border: none; background: none; font: inherit; color: var(--cloro); text-decoration: underline; cursor: pointer; }

  .blocco { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-16) var(--space-20); }
  .riga-a { display: flex; align-items: center; gap: var(--space-12); width: 100%; padding: var(--space-10) 0; border: none; border-top: 1px solid var(--line-soft); background: none;
    font: inherit; color: var(--ink); text-align: left; cursor: pointer; }
  .riga-a :global(.ic) { width: 18px; height: 18px; color: var(--chevron); }
  .attesa-t { display: inline-flex; padding: 2px var(--space-8); border-radius: var(--radius-pill); background: var(--warn-bg); color: var(--warn); font-size: var(--text-xs); font-weight: 600; white-space: nowrap; }
  .dettagli { max-width: 760px; }
  dl { display: grid; grid-template-columns: 160px 1fr; gap: var(--space-6) var(--space-12); margin: 0; }
  dt { color: var(--muted); }
  dd { margin: 0; }

  .foglio-turno { display: flex; flex-direction: column; gap: var(--space-14); }
  .impostazioni { border-top: 1px solid var(--line); padding-top: var(--space-12); }
  .impostazioni summary { cursor: pointer; font-weight: 700; min-height: 44px; display: flex; align-items: center; }
  .impostazioni[open] summary { margin-bottom: var(--space-12); }
  .togli { display: flex; align-items: center; gap: var(--space-10); margin-top: var(--space-14); padding-top: var(--space-12); border-top: 1px solid var(--line-soft); }

  @media (max-width: 1023px) {
    .riassunto, .turni { padding: var(--space-14); }
    h1 { font-size: var(--text-3xl); }
    .azioni-r { width: 100%; }
    .azioni-r .btn { flex: 1; }
    .colonne-g { grid-template-columns: 1fr; }
    .col-g { display: grid; grid-template-columns: 1fr 1fr; }
    .col-g h3 { grid-column: 1 / -1; }
    .col-g .ancora { grid-column: 1 / -1; }
    .rapido, .rapido.dx { left: 0; right: auto; }
  }
</style>
