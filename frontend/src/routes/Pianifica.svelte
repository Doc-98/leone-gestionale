<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import Foglio from '../components/Foglio.svelte';
  import Settimana from '../components/Settimana.svelte';
  import GrigliaPostazioni, { type Blocco, type Colonna } from '../components/GrigliaPostazioni.svelte';
  import PannelloTurno from '../components/pianifica/PannelloTurno.svelte';
  import PerPersona from '../components/pianifica/PerPersona.svelte';
  import Pianta from '../components/pianifica/Pianta.svelte';
  import { api, eAvviso, messaggioErrore, type PersonaStaff, type Postazione, type Richiesta, type Turno } from '../lib/api';
  import { aggiungiGiorni, daIso, GIORNI_BREVI, giornoEsteso, iso, lunedi, MESI, ora } from '../lib/date';
  import { formatH, gruppi, gruppoDi, nomeNuovaPostazione, orePerPersona, scoperto, type Gruppo } from '../lib/gestione';
  import { cosaDaFare, giornoCorto } from '../lib/richieste';
  import { leggiPreferenza, salvaPreferenza } from '../lib/preferenze';
  import { sessione } from '../lib/sessione.svelte';
  import { schermo } from '../lib/schermo.svelte';
  import { areaDi } from '../lib/turni';

  type Vista = 'postazione' | 'persona' | 'pianta';
  const VISTE: { id: Vista; nome: string }[] = [{ id: 'postazione', nome: 'Per postazione' }, { id: 'persona', nome: 'Per persona' }, { id: 'pianta', nome: 'Pianta' }];

  const oggi = iso(new Date());
  let vista = $state<Vista>(leggiPreferenza('pianifica.vista', 'postazione', VISTE.map((v) => v.id)));
  let settimana = $state(lunedi(oggi));
  let turni = $state<Turno[]>([]);
  let postazioni = $state<Postazione[]>([]);
  let personale = $state<PersonaStaff[]>([]);
  let richieste = $state<Richiesta[]>([]);
  let caricamento = $state(true);
  let errore = $state('');
  let messaggio = $state('');
  let avviso = $state<{ testo: string; conferma: () => void } | null>(null);
  let evidenzia = $state('');
  // il turno aperto nel pannello (o un turno nuovo su una casella libera)
  let aperto = $state<{ id: string } | { nuovo: { postazioneId: string; data: string } } | null>(null);
  // telefono: il giorno e il filtro
  let giornoTel = $state(oggi);
  let soloScoperti = $state(false);

  Promise.all([api.postazioni(), api.personale()])
    .then(([p, s]) => ((postazioni = p), (personale = s)))
    .catch((e) => (errore = messaggioErrore(e)));

  function carica() {
    caricamento = true;
    api.turniTutti(settimana, aggiungiGiorni(settimana, 6))
      .then((t) => (turni = t))
      .catch((e) => (errore = messaggioErrore(e, 'Non riesco a caricare i turni.')))
      .finally(() => (caricamento = false));
    if (sessione.puo('APPROVAZIONI')) api.richiesteDaGestire().then((r) => (richieste = r)).catch(() => {});
  }
  $effect(() => {
    void settimana;
    carica();
  });
  $effect(() => {
    if (!giorni.includes(giornoTel)) giornoTel = giorni.includes(oggi) ? oggi : giorni[0];
  });

  function cambiaVista(v: Vista) {
    vista = v;
    salvaPreferenza('pianifica.vista', v);
    aperto = null;
  }

  const giorni = $derived(Array.from({ length: 7 }, (_, i) => aggiungiGiorni(settimana, i)));
  const attivi = $derived(turni.filter((t) => t.stato === 'PIANIFICATO'));
  const scoperti = $derived(attivi.filter((t) => !t.persona).sort((a, b) => a.data.localeCompare(b.data) || a.inizio.localeCompare(b.inizio)));
  const oreTot = $derived(attivi.reduce((s, t) => s + Number(t.ore), 0));
  const perPersona = $derived(orePerPersona(attivi.map((t) => ({ id: t.id, giorno: t.data, inizio: t.inizio, fine: t.fine, postazioneId: t.postazioneId, persona: t.persona }))));
  const richiestaDi = (id: string) => richieste.find((r) => r.turno.id === id || r.turnoCollega?.id === id);
  const daDecidere = $derived(richieste.filter((r) => r.stato === 'IN_ATTESA_APPROVAZIONE').length);
  const daCoprire = $derived(richieste.filter((r) => r.stato === 'IN_ATTESA_COLLEGA' && r.tipo === 'SOSTITUZIONE').length);
  const titoloSettimana = $derived.by(() => {
    const a = daIso(giorni[0]), b = daIso(giorni[6]);
    return a.getMonth() === b.getMonth() ? `${a.getDate()} – ${b.getDate()} ${MESI[b.getMonth()]}` : `${a.getDate()} ${MESI[a.getMonth()].slice(0, 3)} – ${b.getDate()} ${MESI[b.getMonth()].slice(0, 3)}`;
  });

  const colonne = $derived<Colonna[]>(giorni.map((g) => ({ chiave: g, giorno: GIORNI_BREVI[((daIso(g).getDay() + 6) % 7) + 1], numero: daIso(g).getDate(), oggi: g === oggi })));
  const blocchi = $derived<Blocco[]>(attivi.map((t) => {
    const r = richiestaDi(t.id);
    return { id: t.id, postazioneId: t.postazioneId, colonna: t.data, inizio: t.inizio, fine: t.fine, persona: t.persona, notifica: r ? cosaDaFare(r).toLowerCase() : undefined };
  }));
  const turnoAperto = $derived.by(() => {
    const a = aperto;
    return a && 'id' in a ? (attivi.find((t) => t.id === a.id) ?? null) : null;
  });
  const chiaveAperto = $derived(aperto ? ('id' in aperto ? aperto.id : `${aperto.nuovo.postazioneId}|${aperto.nuovo.data}`) : '');
  const postazioniGriglia = $derived.by(() => {
    // anche le postazioni disattivate che hanno ancora turni nella settimana
    const extra = new Map<string, Postazione>();
    for (const t of attivi) if (!postazioni.some((p) => p.id === t.postazioneId)) extra.set(t.postazioneId, { id: t.postazioneId, nome: t.postazione, mansione: t.mansione, impiantoId: t.impiantoId });
    return [...postazioni, ...extra.values()];
  });
  const dove = (t: Turno) => {
    const p = postazioni.find((x) => x.id === t.postazioneId);
    return p ? `${gruppoDi(p).nome} · ${gruppoDi(p).etichetta}` : t.postazione;
  };

  function fatto(m: string) {
    messaggio = m;
    carica();
  }

  /** Per la vista per persona e la pianta: assegna (o toglie) con un gesto; da = il turno da cui si sposta la persona. */
  async function assegna(t: Turno, p: PersonaStaff | null, forza: boolean, da?: Turno): Promise<boolean> {
    errore = '';
    avviso = null;
    const input = (x: Turno, personaId: string | null, f = false) => ({ postazioneId: x.postazioneId, data: x.data, inizio: ora(x.inizio), fine: ora(x.fine), personaId, note: x.note, forza: f });
    try {
      if (da) await api.modificaTurno(da.id, input(da, null));
      try {
        await api.modificaTurno(t.id, input(t, p?.id ?? null, forza));
      } catch (e) {
        if (da && p) await api.modificaTurno(da.id, input(da, p.id, true)).catch(() => {});
        throw e;
      }
      carica();
      return true;
    } catch (e) {
      if (eAvviso(e) && p) avviso = { testo: e.message, conferma: () => assegna(t, p, true, da) };
      else errore = messaggioErrore(e);
      return false;
    }
  }

  const aggiungiPostazione = async (g: Gruppo) => {
    const p0 = g.righe[0].postazione;
    if (!p0.mansioneId) return;
    try {
      await api.creaPostazione(p0.mansioneId, nomeNuovaPostazione(g), p0.spazioId ?? null, p0.soloSesso ?? null);
      postazioni = await api.postazioni();
      messaggio = `Aggiunta una postazione a ${g.nome}.`;
    } catch (e) {
      errore = messaggioErrore(e);
    }
  };

  // telefono
  const delGiornoTel = $derived(attivi.filter((t) => t.data === giornoTel && (!soloScoperti || !t.persona)));
  const gruppiTel = $derived(gruppi(postazioniGriglia).map((g) => ({ g, turni: delGiornoTel.filter((t) => g.righe.some((r) => r.postazione.id === t.postazioneId)).sort((a, b) => a.inizio.localeCompare(b.inizio)) })).filter((x) => x.turni.length));
</script>

<section class="page">
  {#if schermo.larga}
    <div class="barra">
      <div class="seg viste" role="group" aria-label="Come vedere la settimana">
        {#each VISTE as v (v.id)}<button type="button" aria-pressed={vista === v.id} onclick={() => cambiaVista(v.id)}>{v.nome}</button>{/each}
      </div>
      <span class="grow"></span>
      <span class="sett-nav">
        <button class="tondo" type="button" aria-label="Settimana precedente" onclick={() => ((settimana = aggiungiGiorni(settimana, -7)), (aperto = null))}><Icona nome="sx" /></button>
        <b>{titoloSettimana}</b>
        <button class="tondo" type="button" aria-label="Settimana successiva" onclick={() => ((settimana = aggiungiGiorni(settimana, 7)), (aperto = null))}><Icona nome="dx" /></button>
      </span>
      {#if settimana !== lunedi(oggi)}<button class="btn btn-quieto oggi" type="button" onclick={() => (settimana = lunedi(oggi))}>Oggi</button>{/if}
      <a class="btn btn-secondary genera" href="#/modelli">Genera un mese…</a>
    </div>

    <div class="riep">
      <div class="tile"><span class="lbl">Turni</span><span class="v">{caricamento ? '…' : attivi.length}</span><span class="n">questa settimana</span></div>
      <div class="tile"><span class="lbl">Ore</span><span class="v">{formatH(oreTot).replace(' h', '')}</span><span class="n">di lavoro questa settimana</span></div>
      <div class="tile"><span class="lbl">Persone</span><span class="v">{perPersona.size}</span><span class="n">in turno su {personale.length} dello staff</span></div>
      <button type="button" class="tile" class:t-ferma={scoperti.length} class:t-passa={!scoperti.length && !caricamento} disabled={!scoperti.length}
        onclick={() => scoperti[0] && (cambiaVista('postazione'), (aperto = { id: scoperti[0].id }))}>
        <span class="lbl">Scoperti</span><span class="v">{scoperti.length}</span><span class="n">{scoperti.length ? 'turni senza nessuno · tocca per assegnare' : 'tutti i turni hanno qualcuno'}</span></button>
      {#if sessione.puo('APPROVAZIONI')}
        <a class="tile" class:t-warn={richieste.length} href="#/approva"><span class="lbl">Richieste in attesa</span><span class="v">{richieste.length}</span>
          <span class="n">{richieste.length ? [daDecidere ? `${daDecidere} da decidere` : '', daCoprire ? `${daCoprire} da coprire` : ''].filter(Boolean).join(' · ') + ' →' : 'niente da decidere'}</span></a>
      {/if}
    </div>

    {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
    {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
    {#if avviso}
      <div class="avviso-p" role="alert"><Icona nome="avviso" /><span>{avviso.testo}</span>
        <button class="mini-btn" type="button" onclick={() => (avviso = null)}>Lascia stare</button><button class="mini-btn pieno" type="button" onclick={avviso.conferma}>Assegna comunque</button></div>
    {/if}

    {#if vista === 'postazione'}
      <div class="colonne-p">
        <div class="princ">
          <GrigliaPostazioni {colonne} postazioni={postazioniGriglia} {blocchi} scelto={turnoAperto?.id ?? ''} {evidenzia} oreRiga etichetta="Turni della settimana per postazione"
            apri={(id) => ((aperto = { id }), (messaggio = ''))} nuovo={(postazioneId, data) => ((aperto = { nuovo: { postazioneId, data } }), (messaggio = ''))}
            nuovaPostazione={sessione.puo('ANAGRAFICHE_STAFF') ? aggiungiPostazione : undefined} />
        </div>
        <aside class="lato-p">
          {#if aperto && (turnoAperto || 'nuovo' in aperto)}
            <section class="card pann">
              {#key chiaveAperto}
                <PannelloTurno turno={turnoAperto} nuovo={'nuovo' in aperto ? aperto.nuovo : undefined} {postazioni} {personale} turni={attivi}
                  richiesta={turnoAperto ? richiestaDi(turnoAperto.id) : undefined} {fatto} chiudi={() => (aperto = null)} />
              {/key}
            </section>
          {:else}
            <section class="card pann">
              <h2>Da sistemare</h2>
              {#each scoperti as t (t.id)}
                <div class="voce-s ar-{areaDi(t.postazione)}"><i class="quadro"></i>
                  <span class="t"><b class="m">{giornoCorto(t.data)} · {ora(t.inizio)}–{ora(t.fine)}</b><span>{dove(t)}</span></span>
                  <button type="button" class="mini-btn pieno-scuro" onclick={() => (aperto = { id: t.id })}>Assegna</button></div>
              {/each}
              {#each richieste as r (r.id)}
                <div class="voce-s"><i class="pallino-w"></i>
                  <span class="t"><b>{cosaDaFare(r)}</b><span>{giornoCorto(r.turno.data)} · {r.richiedente.nome}{r.tipo === 'CAMBIO_ORARIO' ? `, ${ora(r.turno.inizio)}–${ora(r.turno.fine)} → ${ora(r.nuovoInizio!)}–${ora(r.nuovoFine!)}` : ''}</span></span>
                  <a class="mini-btn" href={`#/approva/${r.id}`}>Apri</a></div>
              {/each}
              {#if !scoperti.length && !richieste.length}<p class="muted small nomargin">{caricamento ? '…' : 'Niente da sistemare questa settimana.'}</p>{/if}
            </section>
            {#if perPersona.size}
              <section class="card pann">
                <span class="intesta"><h2 class="grow nowrap">Ore della settimana</h2><span class="muted small">tocca per evidenziare</span></span>
                <div class="persone-o">
                  {#each [...perPersona.entries()].sort((a, b) => b[1].ore - a[1].ore) as [id, r] (id)}
                    <button type="button" aria-pressed={evidenzia === id} onclick={() => (evidenzia = evidenzia === id ? '' : id)}>
                      <span class="n">{r.nome}</span><span class="barra-o"><i style="width: {Math.min(100, (r.ore / 20) * 100)}%"></i></span><span class="m">{formatH(r.ore)}</span></button>
                  {/each}
                </div>
              </section>
            {/if}
          {/if}
        </aside>
      </div>
    {:else if vista === 'persona'}
      <PerPersona {giorni} turni={attivi} {personale} {postazioni} assegna={(t, p, f) => assegna(t, p, f)} />
    {:else}
      <Pianta {giorni} turni={attivi} {personale} {postazioni} {assegna} />
    {/if}
  {:else}
    <Settimana bind:lunediSettimana={settimana} sotto={caricamento ? '…' : `${attivi.length} turni · ${formatH(oreTot)}${scoperti.length ? ` · ${scoperto(scoperti.length)}` : ''}`} />
    {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
    {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
    <div class="card giorni-tel" role="group" aria-label="Giorno">
      {#each giorni as g (g)}
        {@const s = attivi.filter((t) => t.data === g && !t.persona).length}
        {@const r = attivi.some((t) => t.data === g && richiestaDi(t.id))}
        <button type="button" class="gg" aria-pressed={giornoTel === g} onclick={() => (giornoTel = g)}
          aria-label={`${giornoEsteso(g)}${s ? `, ${scoperto(s)}` : ''}${r ? ', richieste aperte' : ''}`}>
          <span class="l">{GIORNI_BREVI[((daIso(g).getDay() + 6) % 7) + 1]}</span><span class="n">{daIso(g).getDate()}</span>
          {#if s || r}<span class="w" class:rosso={!!s}></span>{/if}
        </button>
      {/each}
    </div>
    <div class="testa-giorno">
      <span class="tg"><b>{giornoEsteso(giornoTel).replace(/^./, (c) => c.toUpperCase())}</b>
        <span class="muted small">{attivi.filter((t) => t.data === giornoTel).length} turni{attivi.some((t) => t.data === giornoTel && !t.persona) ? ` · ${scoperto(attivi.filter((t) => t.data === giornoTel && !t.persona).length)}` : ''}</span></span>
      <div class="seg due-s" role="group" aria-label="Quali turni">
        <button type="button" aria-pressed={!soloScoperti} onclick={() => (soloScoperti = false)}>Tutti</button>
        <button type="button" aria-pressed={soloScoperti} onclick={() => (soloScoperti = true)}>Scoperti</button>
      </div>
    </div>
    {#each gruppiTel as { g, turni: tt } (g.chiave)}
      <section class="gruppo-tel ar-{g.area}">
        <span class="nome-g"><i></i>{g.nome}</span>
        <div class="card elenco-tel">
          {#each tt as t (t.id)}
            <button type="button" class="rt" class:vuoto={!t.persona} onclick={() => ((aperto = { id: t.id }), (messaggio = ''))}>
              <span class="o m">{ora(t.inizio)}–{ora(t.fine)}</span>
              <span class="t"><b>{t.persona?.nome ?? 'Scoperto'}</b><span>{dove(t)}{richiestaDi(t.id) ? ` · ${cosaDaFare(richiestaDi(t.id)!).toLowerCase()}` : ''}</span></span>
              <Icona nome="dx" class="chev" />
            </button>
          {/each}
        </div>
      </section>
    {:else}
      <p class="muted vuoto-tel">{caricamento ? '' : soloScoperti ? 'Nessun turno scoperto in questo giorno.' : 'Nessun turno in questo giorno.'}</p>
    {/each}
    <button type="button" class="btn btn-secondary" disabled={!postazioni.length} onclick={() => (aperto = { nuovo: { postazioneId: postazioni[0].id, data: giornoTel } })}>+ Aggiungi un turno</button>
    <div class="card voci altre">
      <a class="voce" href="#/modelli"><span class="voce-txt"><span class="voce-nome">Settimana tipo e genera un mese</span></span><Icona nome="dx" class="chev" /></a>
      <a class="voce" href="#/postazioni"><span class="voce-txt"><span class="voce-nome">Mansioni e postazioni</span></span><Icona nome="dx" class="chev" /></a>
    </div>
  {/if}
</section>

{#if !schermo.larga && aperto && (turnoAperto || 'nuovo' in aperto)}
  <Foglio titolo={turnoAperto ? `${ora(turnoAperto.inizio)}–${ora(turnoAperto.fine)} · ${turnoAperto.persona?.nome ?? 'scoperto'}` : 'Nuovo turno'} chiudi={() => (aperto = null)}>
    {#key chiaveAperto}
      <PannelloTurno compatto turno={turnoAperto} nuovo={'nuovo' in aperto ? aperto.nuovo : undefined} {postazioni} {personale} turni={attivi}
        richiesta={turnoAperto ? richiestaDi(turnoAperto.id) : undefined} fatto={(m) => { fatto(m); aperto = null; }} chiudi={() => (aperto = null)} />
    {/key}
  </Foglio>
{/if}

<style>
  .barra { display: flex; align-items: center; gap: var(--space-12); flex-wrap: wrap; }
  .viste { grid-auto-flow: column; }
  .viste button { padding: 0 var(--space-14); white-space: nowrap; }
  .sett-nav { display: inline-flex; align-items: center; gap: var(--space-8); }
  .sett-nav b { font-family: var(--font-display); font-size: var(--text-xl); font-weight: 800; min-width: 180px; text-align: center; white-space: nowrap; }
  .tondo { position: relative; width: 38px; height: 38px; border-radius: 50%; border: 1px solid var(--line); background: var(--surface); color: var(--ink); display: grid; place-items: center; cursor: pointer; }
  .tondo :global(.ic) { width: 18px; height: 18px; }
  .tondo::after { content: ''; position: absolute; inset: -3px; }
  .oggi { height: 38px; padding: 0 var(--space-10); }
  .genera { height: 40px; padding: 0 var(--space-14); text-decoration: none; }
  a.tile { text-decoration: none; color: var(--ink); }
  button.tile:disabled { cursor: default; }
  .avviso-p { align-self: flex-start; display: inline-flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; padding: var(--space-6) var(--space-6) var(--space-6) var(--space-14);
    border-radius: var(--radius-md); background: var(--warn-bg); color: var(--warn); font-weight: 600; }
  .avviso-p :global(.ic) { width: 18px; height: 18px; }
  .mini-btn { position: relative; display: inline-flex; align-items: center; height: 32px; padding: 0 var(--space-12); border-radius: var(--radius-sm); border: 1px solid var(--line);
    background: var(--surface); font: inherit; font-size: var(--text-sm); font-weight: 700; color: var(--ink); cursor: pointer; white-space: nowrap; text-decoration: none; }
  .mini-btn::after { content: ''; position: absolute; left: 0; right: 0; top: 50%; height: 44px; transform: translateY(-50%); }
  .mini-btn.pieno { background: var(--warn); border-color: var(--warn); color: var(--surface); }
  .mini-btn.pieno-scuro { background: var(--fill); border-color: var(--fill); color: var(--on-fill); }
  .intesta { display: flex; align-items: baseline; gap: var(--space-8); }
  .voce-s { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-10) 0; border-top: 1px solid var(--line-soft); font-size: var(--text-md); }
  .pann > .voce-s:nth-child(2) { border-top: none; }
  .voce-s .t { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
  .voce-s .t b { font-size: var(--text-sm); }
  .voce-s .t span { font-size: var(--text-xs); color: var(--muted); }
  .quadro { width: 10px; height: 10px; border-radius: 3px; background: var(--a); flex-shrink: 0; }
  .pallino-w { width: 9px; height: 9px; border-radius: 50%; background: var(--warn); flex-shrink: 0; }
  .persone-o { display: flex; flex-direction: column; gap: 2px; }
  .persone-o button { display: grid; grid-template-columns: minmax(0, 1fr) 80px 52px; gap: var(--space-10); align-items: center; padding: var(--space-6) var(--space-8); border: none;
    border-radius: var(--radius-xs); background: none; font: inherit; font-size: var(--text-sm); color: var(--ink); text-align: left; cursor: pointer; }
  .persone-o button[aria-pressed='true'] { background: var(--cloro-soft); color: var(--cloro-ink); }
  .persone-o .n { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .persone-o .barra-o { height: 8px; border-radius: 4px; background: var(--surface-sunken); overflow: hidden; }
  .persone-o .barra-o i { display: block; height: 100%; background: var(--cloro); }
  .persone-o .m { text-align: right; white-space: nowrap; }
  /* telefono */
  .giorni-tel { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 2px; padding: 4px; }
  .gg { position: relative; display: flex; flex-direction: column; align-items: center; gap: 2px; padding: 7px 0 6px; border-radius: 12px; border: none; background: none; font: inherit;
    color: var(--ink); cursor: pointer; }
  .gg .n { font-family: var(--font-display); font-size: var(--text-lg); font-weight: 800; line-height: 1; }
  .gg .l { font-size: 11px; font-weight: 700; color: var(--muted); text-transform: uppercase; }
  .gg .w { position: absolute; top: -3px; right: -3px; width: 10px; height: 10px; border-radius: 50%; background: var(--warn); box-shadow: 0 0 0 2px var(--surface); }
  .gg .w.rosso { background: var(--ferma); }
  .gg[aria-pressed='true'] { background: var(--fill); color: var(--on-fill); }
  .gg[aria-pressed='true'] .l { color: color-mix(in srgb, var(--on-fill) 70%, transparent); }
  .testa-giorno { display: flex; align-items: center; gap: var(--space-8); }
  .tg { display: flex; flex-direction: column; flex: 1; line-height: 1.25; }
  .tg b { font-family: var(--font-display); font-size: var(--text-xl); font-weight: 800; }
  .due-s { grid-template-columns: repeat(2, auto); }
  .gruppo-tel { display: flex; flex-direction: column; gap: var(--space-6); }
  .nome-g { display: flex; align-items: center; gap: var(--space-8); font-size: var(--text-xs); font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--a-fg); }
  .nome-g i { width: 10px; height: 10px; border-radius: 3px; background: var(--a); }
  .elenco-tel { padding: 0; overflow: hidden; }
  .rt { display: flex; align-items: center; gap: var(--space-12); padding: 11px var(--space-14); border: none; border-top: 1px solid var(--line-soft); background: var(--surface); width: 100%;
    font: inherit; color: var(--ink); text-align: left; cursor: pointer; }
  .rt:first-child { border-top: none; }
  .rt .o { font-size: var(--text-sm); width: 92px; flex-shrink: 0; }
  .rt .t { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
  .rt .t b { font-size: var(--text-md); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .rt .t span { font-size: var(--text-xs); color: var(--muted); }
  .rt.vuoto { background: var(--ferma-bg); box-shadow: inset 4px 0 0 var(--ferma); }
  .rt.vuoto b { color: var(--ferma-fg); }
  .vuoto-tel { text-align: center; margin: var(--space-20) 0; }
  .altre { margin-top: var(--space-8); }
</style>
