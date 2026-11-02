<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import Azione from '../components/Azione.svelte';
  import Foglio from '../components/Foglio.svelte';
  import GrigliaPostazioni, { type Blocco, type Colonna } from '../components/GrigliaPostazioni.svelte';
  import { api, messaggioErrore, type Evento, type Modello, type PersonaStaff, type Postazione, type RigaModello, type Turno } from '../lib/api';
  import { GIORNI_BREVI, GIORNI_ESTESI, iso, meseIso, nomeMese, ora, oreTra, spostaMese } from '../lib/date';
  import { ammessa, anteprima, formatH, gruppoDi, nomeNuovaPostazione, testoRiservata, type Gruppo } from '../lib/gestione';
  import { sessione } from '../lib/sessione.svelte';
  import { schermo } from '../lib/schermo.svelte';

  let modelli = $state<Modello[]>([]);
  let postazioni = $state<Postazione[]>([]);
  let personale = $state<PersonaStaff[]>([]);
  let sceltoId = $state('');
  let caricamento = $state(true);
  let errore = $state('');
  let messaggio = $state('');
  let lavoro = $state(false);

  // pannelli: una nuova settimana tipo, o una riga (esistente o nuova), altrimenti «Genera un mese»
  let nuovo = $state(false);
  let bozza = $state('');
  let partenza = $state<'copia' | 'vuota'>('copia');
  let rinomina = $state(false);
  let riga = $state<{ id: string | null; postazioneId: string; giorno: number } | null>(null);
  let fInizio = $state('');
  let fFine = $state('');
  let fPersona = $state('');
  let fGiorni = $state<number[]>([]);
  let generaTel = $state(false);

  Promise.all([api.modelli(), api.postazioni(), api.personale()])
    .then(([m, p, s]) => {
      modelli = m;
      postazioni = p;
      personale = s;
      sceltoId = m[0]?.id ?? '';
    })
    .catch((e) => (errore = messaggioErrore(e)))
    .finally(() => (caricamento = false));

  const scelto = $derived(modelli.find((m) => m.id === sceltoId) ?? null);
  const COLONNE: Colonna[] = [1, 2, 3, 4, 5, 6, 7].map((n) => ({ chiave: String(n), giorno: GIORNI_BREVI[n] }));
  const blocchi = $derived<Blocco[]>(scelto?.righe.map((r) => ({
    id: r.id, postazioneId: r.postazioneId, colonna: String(r.giornoSettimana), inizio: r.inizio, fine: r.fine, persona: r.persona,
  })) ?? []);
  const oreSett = $derived(scelto?.righe.reduce((s, r) => s + oreTra(r.inizio, r.fine), 0) ?? 0);
  const senzaNessuno = $derived(scelto?.righe.filter((r) => !r.persona).length ?? 0);
  const rigaAperta = $derived(riga?.id ? scelto?.righe.find((r) => r.id === riga!.id) : undefined);
  const postazioneRiga = $derived(postazioni.find((p) => p.id === riga?.postazioneId));
  const adatti = $derived(personale.filter((p) => ammessa(postazioneRiga, p)));

  function aggiornato(m: Modello, testo = '') {
    modelli = modelli.some((x) => x.id === m.id) ? modelli.map((x) => (x.id === m.id ? m : x)) : [...modelli, m];
    sceltoId = m.id;
    messaggio = testo;
  }

  const unico = (base: string) => {
    let n = base, k = 2;
    while (modelli.some((m) => m.nome === n)) n = `${base} ${k++}`;
    return n;
  };

  async function esegui(f: () => Promise<void>) {
    errore = '';
    lavoro = true;
    try {
      await f();
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      lavoro = false;
    }
  }

  // ── Settimane tipo ──
  function scegli(id: string) {
    sceltoId = id;
    riga = null;
    rinomina = false;
    messaggio = '';
  }

  const impianto = () => scelto?.impiantoId ?? postazioni[0]?.impiantoId ?? '';
  // la validità resta nel modello ma la pagina non la usa: le settimane tipo nuove valgono sempre
  const SEMPRE = '2000-01-01';

  const crea = () => esegui(async () => {
    const copia = partenza === 'copia' && scelto;
    const m = await api.creaModello({ impiantoId: impianto(), nome: unico(bozza.trim() || 'Nuova settimana tipo'),
      validoDal: copia ? scelto.validoDal : SEMPRE, validoAl: copia ? scelto.validoAl : null, copiaDa: copia ? scelto.id : null });
    aggiornato(m, `Settimana tipo «${m.nome}» creata.`);
    nuovo = false;
  });

  const duplica = () => esegui(async () => {
    if (!scelto) return;
    const m = await api.creaModello({ impiantoId: scelto.impiantoId, nome: unico(`Copia di ${scelto.nome}`), validoDal: scelto.validoDal, validoAl: scelto.validoAl, copiaDa: scelto.id });
    aggiornato(m, `Creata «${m.nome}».`);
  });

  const salvaNome = () => esegui(async () => {
    if (!scelto || !bozza.trim()) return;
    aggiornato(await api.modificaModello(scelto.id, { impiantoId: scelto.impiantoId, nome: bozza.trim(), validoDal: scelto.validoDal, validoAl: scelto.validoAl }), 'Nome cambiato.');
    rinomina = false;
  });

  const elimina = () => esegui(async () => {
    if (!scelto) return;
    await api.eliminaModello(scelto.id);
    const nome = scelto.nome;
    modelli = modelli.filter((m) => m.id !== scelto.id);
    sceltoId = modelli[0]?.id ?? '';
    messaggio = `«${nome}» eliminata.`;
  });

  // ── Righe ──
  function apriRiga(r: RigaModello | null, postazioneId: string, giorno: number) {
    nuovo = false;
    messaggio = '';
    riga = { id: r?.id ?? null, postazioneId, giorno };
    const simile = r ?? scelto?.righe.find((x) => x.postazioneId === postazioneId);
    fInizio = ora(r?.inizio ?? simile?.inizio ?? '16:00');
    fFine = ora(r?.fine ?? simile?.fine ?? '19:00');
    fPersona = r?.persona?.id ?? '';
    // i giorni in cui la stessa postazione ha lo stesso orario: la riga «si ripete»
    fGiorni = r ? (scelto?.righe.filter((x) => x.postazioneId === postazioneId && x.inizio === r.inizio).map((x) => x.giornoSettimana) ?? [giorno]) : [giorno];
  }

  const gemelle = () => (rigaAperta ? scelto?.righe.filter((x) => x.postazioneId === rigaAperta.postazioneId && x.inizio === rigaAperta.inizio) ?? [] : []);

  const salvaRiga = () => esegui(async () => {
    if (!scelto || !riga) return;
    if (!fGiorni.length) return void (errore = 'Scegli almeno un giorno.');
    const base = { postazioneId: riga.postazioneId, inizio: fInizio, fine: fFine, personaId: fPersona || null };
    let m = scelto;
    const esistenti = riga.id ? gemelle() : [];
    try {
      for (const g of [1, 2, 3, 4, 5, 6, 7]) {
        const r = esistenti.find((x) => x.giornoSettimana === g);
        if (fGiorni.includes(g)) m = r ? await api.modificaRiga(m.id, r.id, { ...base, giornoSettimana: g }) : await api.aggiungiRiga(m.id, { ...base, giornoSettimana: g });
        else if (r) m = await api.togliRiga(m.id, r.id);
      }
    } catch (e) {
      // una parte dei giorni può essere già salvata: si ricarica la settimana tipo com'è ora
      modelli = await api.modelli();
      throw e;
    }
    aggiornato(m, 'Settimana tipo aggiornata: vale dai prossimi mesi che generi.');
    riga = null;
  });

  const togliRiga = () => esegui(async () => {
    if (!scelto || !riga?.id) return;
    aggiornato(await api.togliRiga(scelto.id, riga.id), 'Turno tolto dalla settimana tipo.');
    riga = null;
  });

  const aggiungiPostazione = (g: Gruppo) => esegui(async () => {
    const p0 = g.righe[0].postazione;
    if (!p0.mansioneId) return;
    await api.creaPostazione(p0.mansioneId, nomeNuovaPostazione(g), p0.spazioId ?? null, p0.soloSesso ?? null);
    postazioni = await api.postazioni();
    messaggio = `Aggiunta una postazione a ${g.nome}.`;
  });

  // ── Genera un mese ──
  let mese = $state(spostaMese(meseIso(new Date()), 1));
  let chiusure = $state<Evento[]>([]);
  let esistenti = $state<Turno[]>([]);
  let generato = $state<string>('');
  const giorniMese = $derived.by(() => {
    const [y, m] = mese.split('-').map(Number);
    return Array.from({ length: new Date(y, m, 0).getDate() }, (_, i) => iso(new Date(y, m - 1, i + 1)));
  });
  $effect(() => {
    const g = giorniMese;
    generato = '';
    api.chiusure(g[0], g.at(-1)!).then((c) => (chiusure = c.chiusure)).catch(() => (chiusure = []));
    api.turniTutti(g[0], g.at(-1)!).then((t) => (esistenti = t.filter((x) => x.stato === 'PIANIFICATO'))).catch(() => (esistenti = []));
  });
  const prev = $derived(anteprima(scelto?.righe ?? [], giorniMese, chiusure, esistenti));
  const vuoteMese = $derived((new Date(`${giorniMese[0]}T12:00:00`).getDay() + 6) % 7);

  const genera = () => esegui(async () => {
    if (!scelto) return;
    const e = await api.generaMese(scelto.id, mese);
    generato = `Fatto: ${e.creati + e.scoperti} turni creati${e.scoperti ? `, ${e.scoperti} scoperti` : ''}.`;
    if (e.avvisi.length) errore = e.avvisi.join(' ');
    esistenti = (await api.turniTutti(giorniMese[0], giorniMese.at(-1)!)).filter((x) => x.stato === 'PIANIFICATO');
  });
</script>

<section class="page">
  <div class="barra" role="group" aria-label="Quale settimana tipo">
    {#each modelli as m (m.id)}
      <button type="button" class="chip" aria-pressed={m.id === sceltoId} onclick={() => scegli(m.id)}>{m.nome}<span class="sotto">{m.righe.length} turni</span></button>
    {/each}
    <button type="button" class="chip nuova" onclick={() => ((nuovo = true), (riga = null), (bozza = ''), (partenza = scelto ? 'copia' : 'vuota'), (messaggio = ''))}>+ Nuova settimana tipo</button>
  </div>

  {#if scelto}
    <div class="barra">
      {#if rinomina}
        <label class="sr" for="nome-preset">Nome della settimana tipo</label>
        <input id="nome-preset" class="inp nome-in" bind:value={bozza} onkeydown={(e) => e.key === 'Enter' && salvaNome()} />
        <button class="mini-btn pieno" type="button" disabled={lavoro} onclick={salvaNome}>Salva</button>
        <button class="mini-btn" type="button" onclick={() => (rinomina = false)}>Annulla</button>
      {:else}
        <b class="titolo-m">{scelto.nome}</b>
        <button class="mini-btn" type="button" onclick={() => ((rinomina = true), (bozza = scelto.nome))}>Rinomina</button>
        <button class="mini-btn" type="button" disabled={lavoro} onclick={duplica}>Duplica</button>
        {#if modelli.length > 1}<button class="mini-btn rosso" type="button" disabled={lavoro} onclick={elimina}>Elimina</button>{/if}
      {/if}
    </div>
  {/if}

  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

  {#if scelto}
    <div class="colonne-p">
      <div class="princ">
        <span class="muted small">{scelto.righe.length} turni · {formatH(oreSett)} a settimana · {senzaNessuno} senza nessuno · tocca un turno per cambiarlo, o il «+» in una casella vuota per aggiungerne uno. Una postazione è una persona alla volta: se serve qualcuno in più, aggiungi una postazione. Chi è indicato qui viene messo nei turni generati; «scoperto» resta da assegnare.</span>
        {#if schermo.larga}
          <GrigliaPostazioni colonne={COLONNE} {postazioni} {blocchi} scelto={riga?.id ?? ''} etichetta="Settimana tipo per postazione"
            apri={(id) => { const r = scelto.righe.find((x) => x.id === id); if (r) apriRiga(r, r.postazioneId, r.giornoSettimana); }}
            nuovo={(p, c) => apriRiga(null, p, Number(c))}
            nuovaPostazione={sessione.puo('ANAGRAFICHE_STAFF') ? aggiungiPostazione : undefined} />
        {:else}
          <button class="btn btn-secondary" type="button" onclick={() => (generaTel = true)}>Genera un mese…</button>
          {#each [1, 2, 3, 4, 5, 6, 7] as g (g)}
            {@const delGiorno = scelto.righe.filter((r) => r.giornoSettimana === g).sort((a, b) => a.inizio.localeCompare(b.inizio))}
            <div class="giorno">
              <div class="intesta"><h2 class="lbl grow">{GIORNI_ESTESI[g]}</h2>
                <Azione icona="piu" tono="cloro" etichetta={`Aggiungi un turno il ${GIORNI_ESTESI[g]}`} onclick={() => postazioni[0] && apriRiga(null, postazioni[0].id, g)} /></div>
              {#each delGiorno as r (r.id)}
                {@const p = postazioni.find((x) => x.id === r.postazioneId)}
                <button class="card riga-tel" class:scoperta={!r.persona} onclick={() => apriRiga(r, r.postazioneId, g)}>
                  <span class="m">{ora(r.inizio)}–{ora(r.fine)}</span>
                  <span class="dove"><b>{r.persona?.nome ?? 'Scoperto'}</b><span class="muted small">{p ? `${gruppoDi(p).nome} · ${gruppoDi(p).etichetta}` : r.postazione}</span></span>
                </button>
              {:else}
                <p class="muted small nomargin">Nessun turno</p>
              {/each}
            </div>
          {/each}
        {/if}
      </div>

      {#if schermo.larga}
        <aside class="lato-p">
          {#if nuovo}
            <section class="card pann">{@render pannelloNuovo()}</section>
          {:else if riga}
            <section class="card pann">{@render pannelloRiga()}</section>
          {:else}
            <section class="card pann">{@render pannelloGenera()}</section>
          {/if}
        </aside>
      {/if}
    </div>
  {:else if !caricamento}
    {#if nuovo && !schermo.larga}{:else if nuovo}
      <section class="card pann solo">{@render pannelloNuovo()}</section>
    {:else}
      <p class="muted">Nessuna settimana tipo: creane una per generare i turni del mese.</p>
    {/if}
  {/if}
</section>

{#snippet chiudi(f: () => void)}
  <button class="x-btn" type="button" aria-label="Chiudi" onclick={f}><Icona nome="chiudi" /></button>
{/snippet}

{#snippet pannelloNuovo()}
  {#if schermo.larga}<span class="intesta"><span class="lbl grow">Nuova settimana tipo</span>{@render chiudi(() => (nuovo = false))}</span>{/if}
  <label class="field"><span class="lbl">Nome</span><input class="inp" placeholder="Es. Estate, Vacanze di Natale" bind:value={bozza} /></label>
  {#if scelto}
    <div class="field"><span class="lbl">Parti da</span>
      <div class="cand" role="group" aria-label="Da cosa partire">
        <button type="button" aria-pressed={partenza === 'copia'} onclick={() => (partenza = 'copia')}><span class="n">Una copia di «{scelto.nome}»</span><span class="s muted">poi cambi solo quello che serve</span></button>
        <button type="button" aria-pressed={partenza === 'vuota'} onclick={() => (partenza = 'vuota')}><span class="n">Vuota</span><span class="s muted">aggiungi i turni da zero</span></button>
      </div>
    </div>
  {/if}
  <span class="muted small">Puoi averne quante ne servono, cambiarle e rinominarle quando vuoi.</span>
  <button class="btn btn-primary" type="button" disabled={lavoro || !impianto()} onclick={crea}>Crea</button>
{/snippet}

{#snippet pannelloRiga()}
  {#if schermo.larga}<span class="intesta"><span class="lbl grow">{riga?.id ? 'Turno della settimana tipo' : 'Nuovo turno della settimana tipo'}</span>{@render chiudi(() => (riga = null))}</span>{/if}
  {#if riga && !riga.id && !schermo.larga}
    <label class="field"><span class="lbl">Postazione</span>
      <select class="inp" bind:value={riga.postazioneId}>{#each postazioni as p (p.id)}<option value={p.id}>{p.nome}</option>{/each}</select>
    </label>
  {:else if postazioneRiga}
    {@const gr = gruppoDi(postazioneRiga)}
    <h2>{gr.nome} · {gr.etichetta}</h2>
  {/if}
  <div class="due">
    <label class="field"><span class="lbl">Inizio</span><input class="inp" type="time" bind:value={fInizio} /></label>
    <label class="field"><span class="lbl">Fine</span><input class="inp" type="time" bind:value={fFine} /></label>
  </div>
  <div class="field"><span class="lbl">Si ripete</span>
    <div class="giorni-r" role="group" aria-label="Giorni">
      {#each [1, 2, 3, 4, 5, 6, 7] as g (g)}
        <button type="button" aria-pressed={fGiorni.includes(g)} aria-label={GIORNI_ESTESI[g]}
          onclick={() => (fGiorni = fGiorni.includes(g) ? fGiorni.filter((x) => x !== g) : [...fGiorni, g])}>{GIORNI_BREVI[g][0].toUpperCase()}</button>
      {/each}
    </div>
  </div>
  <label class="field"><span class="lbl">Di solito lo fa</span>
    <select class="inp" bind:value={fPersona}>
      <option value="">Scoperto (da assegnare)</option>
      {#each adatti as p (p.id)}<option value={p.id}>{p.nome}</option>{/each}
    </select>
  </label>
  <span class="muted small">{testoRiservata(postazioneRiga)}Cambia la settimana tipo, non i turni già creati: vale dai prossimi mesi che generi.</span>
  <div class="azioni-r">
    <button class="btn btn-primary grow" type="button" disabled={lavoro} onclick={salvaRiga}>Salva</button>
    {#if riga?.id}<button class="btn btn-danger" type="button" disabled={lavoro} onclick={togliRiga}>Togli</button>{/if}
  </div>
{/snippet}

{#snippet pannelloGenera()}
  {#if schermo.larga}<h2>Genera un mese</h2>{/if}
  <span class="muted small">Crea i turni del mese da «{scelto?.nome}». Si può rilanciare: i turni che ci sono già non si duplicano.</span>
  <div class="mese-nav">
    <button class="tondo" type="button" aria-label="Mese precedente" onclick={() => (mese = spostaMese(mese, -1))}><Icona nome="sx" /></button>
    <b>{nomeMese(mese).replace(/^./, (c) => c.toUpperCase())}</b>
    <button class="tondo" type="button" aria-label="Mese successivo" onclick={() => (mese = spostaMese(mese, 1))}><Icona nome="dx" /></button>
  </div>
  <div class="cal">
    {#each ['L', 'M', 'M', 'G', 'V', 'S', 'D'] as l, i (i)}<span class="l" aria-hidden="true">{l}</span>{/each}
    {#each { length: vuoteMese } as _, i (i)}<span></span>{/each}
    {#each prev.giorni as d (d.giorno)}
      <span class="g {d.stato}" title={d.titolo || undefined}><b>{Number(d.giorno.slice(8))}</b><span>{d.stato === 'chiuso' ? 'chiuso' : d.stato === 'gia' ? 'fatto' : d.nuovi}</span></span>
    {/each}
  </div>
  <span class="legenda-g">
    <span><i class="crea"></i>turni da creare</span><span><i class="chiuso"></i>chiuso</span><span><i class="ridotto"></i>chiude prima</span><span><i class="gia"></i>già generato</span>
  </span>
  <span class="small">Le chiusure vengono dal calendario della stagione.{' '}{#if sessione.puo('IMPOSTAZIONI', 'PIANO_MODIFICA')}<a href="#/calendario" class="forte">Modifica il calendario →</a>{/if}</span>
  <div class="conto">
    <span><b>{prev.nuovi}</b> turni nuovi · {formatH(prev.ore)}</span>
    {#if prev.scoperti}<span class="warn"><b>{prev.scoperti}</b> resteranno scoperti</span>{/if}
    <span class="muted">{prev.chiusure.length ? `Saltati dal calendario: ${prev.chiusure.join(', ')}.` : 'Nessuna chiusura in questo mese.'}</span>
  </div>
  {#if generato}<a class="fatto" href="#/pianifica">{generato} Apri la pianificazione →</a>{/if}
  <button class="btn btn-primary" type="button" disabled={lavoro || !prev.nuovi} onclick={genera}>{prev.nuovi ? `Genera ${prev.nuovi} turni` : 'Niente da generare'}</button>
{/snippet}

{#if !schermo.larga}
  {#if nuovo}<Foglio titolo="Nuova settimana tipo" chiudi={() => (nuovo = false)}>{@render pannelloNuovo()}</Foglio>
  {:else if riga}<Foglio titolo={riga.id ? 'Turno della settimana tipo' : 'Nuovo turno'} chiudi={() => (riga = null)}>{@render pannelloRiga()}</Foglio>
  {:else if generaTel}<Foglio titolo="Genera un mese" chiudi={() => (generaTel = false)}>{@render pannelloGenera()}</Foglio>{/if}
{/if}

<style>
  .barra { display: flex; align-items: center; gap: var(--space-8); flex-wrap: wrap; }
  .chip .sotto { margin-left: var(--space-6); opacity: 0.7; font-weight: 500; }
  .chip.nuova { border-style: dashed; color: var(--cloro); }
  .titolo-m { font-family: var(--font-display); font-size: var(--text-2xl); font-weight: 800; margin-right: var(--space-4); }
  .nome-in { width: 280px; height: 40px; }
  .mini-btn { position: relative; height: 32px; padding: 0 var(--space-12); border-radius: var(--radius-sm); border: 1px solid var(--line); background: var(--surface); font: inherit;
    font-size: var(--text-sm); font-weight: 700; color: var(--ink); cursor: pointer; white-space: nowrap; }
  .mini-btn.pieno { background: var(--fill); border-color: var(--fill); color: var(--on-fill); height: 40px; }
  .mini-btn.rosso { color: var(--danger); }
  .intesta { display: flex; align-items: center; gap: var(--space-8); }
  .solo { max-width: 420px; }
  .cand { display: flex; flex-direction: column; border: 1px solid var(--line); border-radius: var(--radius-md); overflow: hidden; }
  .cand button { display: flex; flex-direction: column; align-items: flex-start; gap: 1px; min-height: 48px; padding: var(--space-8) var(--space-12); border: none; border-top: 1px solid var(--line-soft);
    background: var(--surface); font: inherit; font-size: var(--text-md); color: var(--ink); text-align: left; cursor: pointer; }
  .cand button:first-child { border-top: none; }
  .cand button[aria-pressed='true'] { background: var(--cloro-soft); box-shadow: inset 3px 0 0 var(--cloro); }
  .cand .s { font-size: var(--text-xs); }
  .giorni-r { display: flex; gap: var(--space-4); }
  .giorni-r button { position: relative; flex: 1 1 0; max-width: 40px; aspect-ratio: 1; padding: 0; border-radius: 50%; border: 1px solid var(--line); background: var(--surface); font: inherit; font-size: var(--text-xs);
    font-weight: 700; color: var(--ink); cursor: pointer; }
  .giorni-r button[aria-pressed='true'] { background: var(--cloro); border-color: var(--cloro); color: var(--on-cloro); }
  .azioni-r { display: flex; gap: var(--space-8); }
  .mese-nav { display: flex; align-items: center; gap: var(--space-8); }
  .mese-nav b { flex: 1; text-align: center; font-family: var(--font-display); font-size: var(--text-lg); font-weight: 800; }
  .tondo { position: relative; width: 36px; height: 36px; border-radius: 50%; border: 1px solid var(--line); background: var(--surface); color: var(--ink); display: grid; place-items: center; cursor: pointer; }
  .tondo :global(.ic) { width: 18px; height: 18px; }
  .tondo::after { content: ''; position: absolute; inset: -4px; }
  .cal { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 3px; }
  .cal .l { font-size: 11px; font-weight: 700; color: var(--muted); text-align: center; padding-bottom: 2px; }
  .cal .g { display: flex; flex-direction: column; align-items: center; gap: 1px; padding: 5px 0; border-radius: 8px; background: var(--cloro-soft); color: var(--cloro-ink); }
  .cal .g b { font-size: var(--text-sm); }
  .cal .g span { font-size: 10px; font-family: var(--font-mono); }
  .cal .g.chiuso { background: var(--ferma-bg); color: var(--ferma-fg); }
  .cal .g.ridotto { background: var(--warn-bg); color: var(--warn); }
  .cal .g.gia { background: var(--surface); box-shadow: inset 0 0 0 1px var(--line); color: var(--ink); }
  .legenda-g { display: flex; flex-wrap: wrap; gap: var(--space-6) var(--space-14); font-size: var(--text-xs); color: var(--muted); }
  .legenda-g span { display: inline-flex; align-items: center; gap: 5px; }
  .legenda-g i { width: 10px; height: 10px; border-radius: 3px; display: inline-block; }
  .legenda-g i.crea { background: var(--cloro-soft); } .legenda-g i.chiuso { background: var(--ferma-bg); } .legenda-g i.ridotto { background: var(--warn-bg); }
  .legenda-g i.gia { background: var(--surface); box-shadow: inset 0 0 0 1px var(--line); }
  .forte { font-weight: 700; }
  .conto { display: flex; flex-direction: column; gap: var(--space-4); padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); background: var(--carta); font-size: var(--text-md); }
  .conto .warn { color: var(--warn); }
  .fatto { padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); background: var(--passa-bg); color: var(--passa-fg); font-size: var(--text-md); font-weight: 600; text-decoration: none; }
  .giorno { display: flex; flex-direction: column; gap: var(--space-6); }
  .giorno h2 { margin: var(--space-6) 0 0; }
  .riga-tel { display: flex; align-items: center; gap: var(--space-12); padding: var(--space-10) var(--space-14); min-height: 56px; width: 100%; text-align: left; color: var(--ink); font: inherit; cursor: pointer; }
  .riga-tel .dove { display: flex; flex-direction: column; min-width: 0; }
  .riga-tel.scoperta { border-color: var(--ferma); background: var(--ferma-bg); }
  .riga-tel.scoperta b { color: var(--ferma-fg); }
</style>
