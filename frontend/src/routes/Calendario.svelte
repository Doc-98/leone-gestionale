<script lang="ts">
  import ElencoModificabile from '../components/ElencoModificabile.svelte';
  import Foglio from '../components/Foglio.svelte';
  import Fumetto from '../components/Fumetto.svelte';
  import Icona from '../components/Icona.svelte';
  import MenuAltro from '../components/MenuAltro.svelte';
  import SceltaStagione from '../components/SceltaStagione.svelte';
  import StatoVuoto from '../components/StatoVuoto.svelte';
  import { api, messaggioErrore, type Evento, type Stagione } from '../lib/api';
  import { iso, dataBreve } from '../lib/date';
  import { impila } from '../lib/impila';
  import type { VoceMenu } from '../lib/menu';
  import { quanti } from '../lib/plurali';
  import { ordinaStagioni } from '../lib/stagioni';
  import {
    chiusureDaCopiare, conteggi, eventiDaCreare, festivita, mesi, prossima, quando, statoDel, voci as raggruppa, type TipoChiusura, type Voce,
  } from '../lib/calendario';

  /**
   * Calendario di apertura (ADR 0024): le chiusure della stagione scelta. Si segnano dal foglio «Nuova chiusura», che si
   * apre dalla riga «Aggiungi una chiusura…» o toccando un giorno della griglia (Dal e Al sono quel giorno). Le festività
   * nazionali e la copia dalle altre stagioni stanno nel menu «⋯» della card.
   */
  const oggi = iso(new Date());
  let stagioni = $state<Stagione[]>([]);
  let caricate = $state(false);
  let stagioneId = $state('');
  let eventi = $state<Evento[]>([]);
  let impianto = $state<string | null>(null);
  let errore = $state('');
  let messaggio = $state('');
  let lavoro = $state(false);

  // il foglio «Nuova chiusura»
  let nuova = $state(false);
  let dal = $state('');
  let al = $state('');
  let tipo = $state<TipoChiusura>('chiuso');
  let apre = $state('');
  let chiude = $state('');
  let nome = $state('');
  let erroreFoglio = $state('');

  // il foglio «Copia le chiusure»
  let copiaAperta = $state(false);
  let copiaDa = $state('');
  let copiaNuove = $state<Voce[] | null>(null);
  let erroreCopia = $state('');
  let tokenCopia = 0;

  api.stagioni()
    .then((s) => {
      stagioni = ordinaStagioni(s);
      caricate = true;
    })
    .catch((e) => (errore = messaggioErrore(e, 'Non riesco a caricare le stagioni.')));

  const stagione = $derived(stagioni.find((s) => s.id === stagioneId));
  const altre = $derived(stagioni.filter((s) => s.id !== stagioneId));
  const precedente = $derived(stagione ? stagioni.filter((s) => s.al < stagione.dal).at(-1) : undefined);

  function carica() {
    const st = stagioni.find((x) => x.id === stagioneId);
    if (!st) return;
    api.chiusure(st.dal, st.al)
      .then((c) => ((eventi = c.chiusure), (impianto = c.impiantiGestiti[0] ?? null)))
      .catch((e) => (errore = messaggioErrore(e, 'Non riesco a caricare il calendario.')));
  }
  /** La scelta della stagione (anche quella iniziale, fatta dal componente). */
  function cambiaStagione(id: string) {
    stagioneId = id;
    nuova = false;
    carica();
  }

  const elenco = $derived(raggruppa(eventi));
  /** Le voci per l'elenco modificabile: l'id è quello del primo evento. */
  const righe = $derived(elenco.map((v) => ({ ...v, id: v.eventi[0] })));
  const calendario = $derived(stagione ? mesi(stagione.dal, stagione.al) : []);
  const conti = $derived(stagione ? conteggi(stagione.dal, stagione.al, elenco) : { chiusi: 0, ridotti: 0, aperti: 0 });
  const p = $derived(prossima(elenco, oggi));
  const lo = $derived(nuova && dal ? dal : null);
  const hi = $derived(nuova && dal ? (al && al > dal ? al : dal) : null);
  const periodo = $derived(!!dal && !!al && al !== dal);
  const mancanti = $derived(stagione ? festivita(stagione.dal, stagione.al)
    // già segnate, o dentro una chiusura di tutto il giorno (es. Natale nelle vacanze di Natale)
    .filter(([n, d1, d2]) => !elenco.some((v) => v.nome === n && v.dal === d1) && !(statoDel(d1, elenco)?.tipo === 'chiuso' && statoDel(d2, elenco)?.tipo === 'chiuso')) : []);
  const modifica = $derived(!!impianto);

  /** Apre «Nuova chiusura»: dal giorno toccato (Dal = Al) o, dalla riga «Aggiungi…», da oggi se cade nella stagione. */
  function apriNuova(giorno?: string) {
    const parte = giorno ?? (stagione && oggi >= stagione.dal && oggi <= stagione.al ? oggi : (stagione?.dal ?? oggi));
    dal = al = parte;
    tipo = 'chiuso';
    apre = chiude = nome = '';
    erroreFoglio = '';
    nuova = true;
  }
  function scegli(g: string) {
    if (!modifica) return;
    messaggio = '';
    apriNuova(g);
  }
  const chiudiNuova = () => (nuova = false);
  /** Cambiando Dal, Al non resta indietro. */
  function cambiaDal() {
    if (al < dal) al = dal;
  }

  /** Crea gli eventi di ogni voce; se uno fallisce lancia l'errore. Alla fine rilegge il calendario. */
  async function crea(vs: { nome: string; dal: string; al: string; tipo: TipoChiusura; apre: string | null; chiude: string | null }[]) {
    if (!impianto) throw new Error('Non c\'è un impianto di cui gestire il calendario.');
    lavoro = true;
    try {
      for (const v of vs)
        for (const e of eventiDaCreare(v))
          await api.creaEvento({ impiantoId: impianto, tipo: 'CHIUSURA', ...e, spazi: [], istruttoreId: null, note: null });
    } finally {
      lavoro = false;
      carica();
    }
  }

  async function aggiungi(e: SubmitEvent) {
    e.preventDefault();
    if (!dal || !al || al < dal || lavoro) return;
    if (tipo === 'ridotto' && !apre && !chiude) return void (erroreFoglio = 'Indica a che ora apre o a che ora chiude.');
    const n = nome.trim() || (periodo ? 'Chiusura' : 'Chiusura straordinaria');
    erroreFoglio = '';
    errore = '';
    try {
      await crea([{ nome: n, dal, al, tipo, apre: tipo === 'ridotto' ? apre || null : null, chiude: tipo === 'ridotto' ? chiude || null : null }]);
      messaggio = `«${n}» aggiunta al calendario.`;
      nuova = false;
    } catch (err) {
      erroreFoglio = messaggioErrore(err);
    }
  }

  /** Il cestino di una riga: annulla gli eventi della voce. */
  async function togli(r: Voce) {
    errore = '';
    messaggio = '';
    lavoro = true;
    try {
      for (const id of r.eventi) await api.annullaEvento(id);
      messaggio = `«${r.nome}» eliminata dal calendario.`;
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      lavoro = false;
      carica();
    }
  }

  async function aggiungiFestivita() {
    errore = '';
    messaggio = '';
    try {
      await crea(mancanti.map(([n, d1, d2]) => ({ nome: n, dal: d1, al: d2, tipo: 'chiuso' as const, apre: null, chiude: null })));
      messaggio = 'Festività nazionali aggiunte.';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  // ── copia dalle altre stagioni ──
  /** Legge le chiusure della stagione di partenza e calcola quelle nuove da copiare. */
  async function preparaCopia(id: string) {
    const da = stagioni.find((s) => s.id === id);
    const token = ++tokenCopia;
    copiaNuove = null;
    erroreCopia = '';
    if (!da || !stagione) return;
    try {
      const vecchie = raggruppa((await api.chiusure(da.dal, da.al)).chiusure);
      if (token === tokenCopia) copiaNuove = chiusureDaCopiare(vecchie, da, stagione, elenco);
    } catch (e) {
      if (token === tokenCopia) {
        copiaNuove = [];
        erroreCopia = messaggioErrore(e);
      }
    }
  }
  function apriCopia() {
    const partenza = precedente ?? altre[0];
    if (!partenza) return;
    copiaDa = partenza.id;
    copiaAperta = true;
    preparaCopia(partenza.id);
  }
  async function copia() {
    if (!copiaNuove?.length || lavoro) return;
    const nuove = copiaNuove;
    const da = stagioni.find((s) => s.id === copiaDa);
    erroreCopia = '';
    errore = '';
    try {
      await crea(nuove);
      messaggio = `${nuove.length === 1 ? 'Copiata una chiusura' : `Copiate ${nuove.length} chiusure`} dalla stagione ${da?.nome ?? ''}.`.replace(' .', '.');
      copiaAperta = false;
    } catch (e) {
      erroreCopia = messaggioErrore(e);
    }
  }

  const vociMenu = $derived.by((): VoceMenu[] => {
    const v: VoceMenu[] = [{ testo: 'Aggiungi le festività nazionali', icona: 'calendario', onclick: aggiungiFestivita, disabled: lavoro || !mancanti.length }];
    if (altre.length) v.push({ testo: 'Copia dalla stagione…', icona: 'copia', onclick: apriCopia, disabled: lavoro });
    return v;
  });

  function classe(g: string) {
    const v = statoDel(g, elenco);
    const dom = new Date(`${g}T12:00:00`).getDay() === 0;
    return [v ? (v.tipo === 'chiuso' ? 'chiuso' : 'ridotto') : dom ? 'dom' : '', lo && hi && g >= lo && g <= hi ? 'scelto' : '', g === oggi ? 'oggi' : ''].join(' ');
  }
  function titolo(g: string) {
    const v = statoDel(g, elenco);
    return v ? `${v.nome}${v.tipo === 'ridotto' ? ` · ${quando(v).split(' · ').at(-1)}` : ''}` : '';
  }
  const brevissimo = (nome: string) => nome.replace(/^20(\d\d)\/(\d\d)$/, '$1/$2');
</script>

<section class="page">
  <div class="barra">
    <SceltaStagione {stagioni} bind:valore={stagioneId} onchange={cambiaStagione} />
    {#if stagione}
      <Fumetto tema="il calendario">Si segnano una volta all'inizio della stagione e valgono per tutto: quando si genera un mese non si creano turni in quei giorni (o dopo l'orario di chiusura), il piano vasca li mostra chiusi e le lezioni dei corsi saltano.</Fumetto>
    {/if}
  </div>

  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
  {#if caricate && !stagioni.length}
    <section class="card vuota">
      <StatoVuoto testo="Nessuna stagione." />
      <p class="muted small nomargin">Aggiungila in <a href="#/societa">Dati, stagioni e gruppi</a>.</p>
    </section>
  {/if}

  {#if stagione}
    <div class="riep">
      <div class="tile t-ferma"><span class="lbl">Giorni di chiusura</span><span class="v">{conti.chiusi}</span><span class="n">chiusa tutto il giorno</span></div>
      <div class="tile t-warn"><span class="lbl">Orario ridotto</span><span class="v">{conti.ridotti}</span><span class="n">apre dopo o chiude prima</span></div>
      <div class="tile t-passa"><span class="lbl">Giorni di apertura</span><span class="v">{conti.aperti}</span><span class="n">per la stagione {brevissimo(stagione.nome)}</span></div>
      <div class="tile"><span class="lbl">Prossima chiusura</span>
        <span class="v piccolo">{p ? p.voce.nome : 'Nessuna'}</span>
        <span class="n">{p ? `${dataBreve(p.voce.dal)} · ${p.traGiorni === 0 ? (p.voce.dal === oggi ? 'oggi' : 'in corso') : p.traGiorni === 1 ? 'domani' : `tra ${p.traGiorni} giorni`}` : 'fino a fine stagione'}</span></div>
    </div>

    <div class="colonne-p">
      <div class="princ">
        <div class="mesi">
          {#each calendario as m (m.chiave)}
            <section class="card mese" aria-label={m.nome}>
              <b class="t">{m.nome}</b>
              <div class="mg">
                {#each ['L', 'M', 'M', 'G', 'V', 'S', 'D'] as l, i (i)}<span class="l" aria-hidden="true">{l}</span>{/each}
                {#each { length: m.vuote } as _, i (i)}<span></span>{/each}
                {#each m.giorni as g (g)}
                  {@const t = titolo(g)}
                  <button type="button" class={classe(g)} title={t || undefined} disabled={!modifica && !t}
                    aria-label={`${Number(g.slice(8))} ${m.nome.split(' ')[0]}${t ? `, ${t}` : ''}`} onclick={() => scegli(g)}>{Number(g.slice(8))}</button>
                {/each}
              </div>
            </section>
          {/each}
        </div>
        <div class="legenda">
          <span class="leg chiuso">chiuso</span>
          <span class="leg ridotto">orario ridotto</span>
          {#if modifica}<Fumetto tema="come si segna una chiusura">Tocca un giorno per segnarlo: si apre «Nuova chiusura» con quel giorno, e lì puoi allungarlo a un periodo.</Fumetto>{/if}
        </div>
      </div>

      <aside class="lato-p">
        <section class="card sez" aria-label="Chiusure">
          <div class="sez-t"><h2>Chiusure</h2><span class="spiega">{quanti(elenco.length, 'voce', 'voci')}</span><span class="grow"></span>
            {#if modifica}<MenuAltro voci={vociMenu} etichetta="Altre azioni per le chiusure" />{/if}</div>
          <div class="elenco">
            <ElencoModificabile
              elementi={righe}
              modificabile={modifica}
              riordinabile={false}
              haInterruttore={() => false}
              soloCestino
              conferma="Elimina?"
              nome={(r) => r.nome}
              nomeElemento="chiusura"
              articolo="una"
              testoVuoto="Nessuna chiusura segnata per questa stagione."
              riga={contenutoChiusura}
              onaggiungi={() => apriNuova()}
              onelimina={togli}
            />
          </div>
        </section>
      </aside>
    </div>
  {/if}
</section>

{#snippet contenutoChiusura(r: Voce)}
  <span class="voce">
    <i class={r.tipo} aria-hidden="true"></i>
    <span class="col">
      <strong class="nome">{r.nome}</strong>
      <span class="sotto" title={quando(r)}>{quando(r)}</span>
    </span>
  </span>
{/snippet}

{#if nuova && modifica}
  <Foglio titolo="Nuova chiusura" chiudi={chiudiNuova}>
    <form class="modulo" onsubmit={aggiungi}>
      <div class="due">
        <label class="field"><span class="lbl">Dal</span><input class="inp m" type="date" bind:value={dal} min={stagione?.dal} max={stagione?.al} oninput={cambiaDal} required /></label>
        <label class="field"><span class="lbl">Al</span><input class="inp m" type="date" bind:value={al} min={dal} max={stagione?.al} required /></label>
      </div>
      <div class="field"><span class="lbl" id="tipo-chiusura">Tipo</span>
        <span class="seg" style:--n={2} use:impila role="group" aria-labelledby="tipo-chiusura">
          <button type="button" aria-pressed={tipo === 'chiuso'} onclick={() => (tipo = 'chiuso')}>Tutto il giorno</button>
          <button type="button" aria-pressed={tipo === 'ridotto'} onclick={() => (tipo = 'ridotto')}>Orario ridotto</button>
        </span>
      </div>
      {#if tipo === 'ridotto'}
        <div class="due">
          <label class="field"><span class="lbl">Apre alle</span><input class="inp m" type="time" bind:value={apre} /></label>
          <label class="field"><span class="lbl">Chiude alle</span><input class="inp m" type="time" bind:value={chiude} /></label>
        </div>
        <span class="corta"><span class="t-c muted small">Lascia vuoto l'orario che non cambia.</span></span>
      {/if}
      <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={nome} maxlength="80" autocomplete="off" placeholder={periodo ? 'Chiusura' : 'Chiusura straordinaria'} /></label>
      {#if erroreFoglio}<p class="msg err nomargin" role="alert">{erroreFoglio}</p>{/if}
      <div class="azioni-foglio">
        <button type="button" class="btn btn-secondary" onclick={chiudiNuova}>Annulla</button>
        <button type="submit" class="btn btn-accent" disabled={lavoro || !dal || !al || al < dal}><Icona nome="piu" />Aggiungi al calendario</button>
      </div>
    </form>
  </Foglio>
{/if}

{#if copiaAperta && modifica}
  <Foglio titolo="Copia le chiusure" chiudi={() => (copiaAperta = false)}>
    <div class="modulo">
      <div class="field"><span class="lbl">Copia dalla stagione</span>
        <SceltaStagione stagioni={altre} bind:valore={copiaDa} etichetta="Copia dalla stagione" onchange={preparaCopia} />
      </div>
      <p class="nomargin conta" role="status">{copiaNuove === null ? 'Carico…' : copiaNuove.length ? `${quanti(copiaNuove.length, 'chiusura', 'chiusure')} da copiare` : 'Niente di nuovo da copiare'}</p>
      {#if erroreCopia}<p class="msg err nomargin" role="alert">{erroreCopia}</p>{/if}
      <div class="azioni-foglio">
        <button type="button" class="btn btn-secondary" onclick={() => (copiaAperta = false)}>Annulla</button>
        <button type="button" class="btn btn-accent" disabled={lavoro || !copiaNuove?.length} onclick={copia}><Icona nome="copia" />{copiaNuove?.length ? `Copia ${quanti(copiaNuove.length, 'chiusura', 'chiusure')}` : 'Copia le chiusure'}</button>
      </div>
    </div>
  </Foglio>
{/if}

<style>
  .barra { display: flex; align-items: center; gap: var(--space-8); }
  /* la colonna laterale può restringersi: le righe lunghe finiscono nei puntini invece di allargare la pagina */
  .colonne-p > .lato-p { min-width: 0; }
  .vuota { padding: var(--space-14) var(--space-16); display: flex; flex-direction: column; gap: var(--space-10); }
  .tile .v.piccolo { font-size: 22px; }
  .mesi { display: grid; grid-template-columns: repeat(auto-fill, minmax(172px, 1fr)); gap: var(--space-14); }
  .mese { padding: var(--space-12); display: flex; flex-direction: column; gap: var(--space-6); }
  /* intestazione del mese: una fascia appena più scura in cima al riquadro */
  .mese .t { font-family: var(--font-display); font-size: var(--text-lg); font-weight: 800; margin: calc(-1 * var(--space-12)) calc(-1 * var(--space-12)) var(--space-4);
    padding: var(--space-8) var(--space-12); background: var(--cloro-soft); color: var(--cloro-ink); border-bottom: 1px solid color-mix(in srgb, var(--cloro) 25%, transparent);
    border-radius: var(--radius) var(--radius) 0 0; }
  .mg { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 2px; }
  .mg .l { font-size: 10px; font-weight: 800; color: var(--seg-fg); text-align: center; padding: 3px 0; margin-bottom: 2px; background: var(--carta); border-radius: 4px; }
  .mg button { height: 30px; border: none; border-radius: 6px; background: none; font: inherit; font-size: var(--text-sm); color: var(--ink); cursor: pointer; padding: 0; }
  .mg button:hover:not(:disabled) { background: var(--surface-sunken); }
  .mg button:disabled { cursor: default; }
  .mg button.dom { color: var(--muted); }
  .mg button.oggi { text-decoration: underline; text-underline-offset: 3px; font-weight: 700; }
  .mg button.chiuso { background: var(--ferma-bg); color: var(--ferma-fg); font-weight: 700; }
  .mg button.ridotto { background: var(--warn-bg); color: var(--warn); font-weight: 700; }
  .mg button.scelto { box-shadow: inset 0 0 0 2px var(--ink); }
  .legenda { display: flex; align-items: center; gap: var(--space-14); flex-wrap: wrap; }
  .leg { display: inline-flex; align-items: center; height: 24px; padding: 0 var(--space-10); border-radius: var(--radius-pill); border: 1px solid; font-size: var(--text-xs); font-weight: 700; white-space: nowrap; }
  .leg.chiuso { background: var(--ferma-bg); color: var(--ferma-fg); border-color: var(--ferma); }
  .leg.ridotto { background: var(--warn-bg); color: var(--warn); border-color: var(--warn-line); }
  /* la riga di una chiusura: il segno del tipo, il nome e sotto quando; una riga sola, con i puntini se non ci sta */
  .elenco { padding: var(--space-6) var(--space-16) var(--space-10); }
  .voce { display: flex; align-items: center; gap: var(--space-10); min-width: 0; max-width: 100%; }
  .voce i { width: 10px; height: 10px; border-radius: 3px; flex-shrink: 0; background: var(--ferma); }
  .voce i.ridotto { background: var(--warn); }
  .col { display: flex; flex-direction: column; gap: var(--space-2); min-width: 0; }
  .nome { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .sotto { font-size: var(--text-sm); color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .modulo { display: flex; flex-direction: column; gap: var(--space-14); }
  .modulo .inp { height: 44px; }
  .azioni-foglio { display: flex; justify-content: flex-end; gap: var(--space-10); flex-wrap: wrap; }
  .grow { flex: 1 1 auto; min-width: 0; }
  .corta { display: flex; align-items: center; gap: var(--space-4); white-space: nowrap; min-width: 0; }
  .t-c { min-width: 0; overflow: hidden; text-overflow: ellipsis; }
  .conta { font-size: var(--text-base); }
  /* i segmenti: stessa larghezza per tutte le voci, alti 44; se non ci stanno in fila vanno uno sotto l'altro */
  .seg { grid-template-columns: repeat(var(--n), var(--col, 1fr)); }
  .seg button { min-height: 44px; }
  .seg:global([data-impilato]) { grid-template-columns: 1fr; }
  /* sul computer la colonna delle chiusure ha posto per nome e dettaglio interi */
  @media (min-width: 900px) { .colonne-p > .lato-p { flex-basis: 420px; max-width: 460px; } }
  @media (max-width: 599px) {
    .mesi { grid-template-columns: minmax(0, 1fr); }
    .riep { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  }
  /* sul telefono stretto una carta per riga: le etichette stanno intere */
  @media (max-width: 479px) {
    .riep { grid-template-columns: minmax(0, 1fr); }
    .nome, .sotto { white-space: normal; display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical; overflow-wrap: anywhere; }
  }
</style>
