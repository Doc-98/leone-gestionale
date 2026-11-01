<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import Foglio from '../components/Foglio.svelte';
  import { schermo } from '../lib/schermo.svelte';
  import { api, messaggioErrore, type Evento, type Stagione } from '../lib/api';
  import { iso } from '../lib/date';
  import {
    breve, conteggi, eventiDaCreare, festivita, mesi, prossima, quando, statoDel, unAnnoDopo, voci as raggruppa, type TipoChiusura, type Voce,
  } from '../lib/calendario';

  const oggi = iso(new Date());
  let stagioni = $state<Stagione[]>([]);
  let caricate = $state(false);
  let stagioneId = $state('');
  let eventi = $state<Evento[]>([]);
  let impianto = $state<string | null>(null);
  let errore = $state('');
  let messaggio = $state('');
  let lavoro = $state(false);

  // selezione di un giorno o di un periodo, e la voce che si sta per aggiungere
  let da = $state<string | null>(null);
  let a = $state<string | null>(null);
  let tipo = $state<TipoChiusura>('chiuso');
  let apre = $state('');
  let chiude = $state('');
  let nome = $state('');

  api.stagioni()
    .then((s) => {
      stagioni = [...s].sort((x, y) => x.dal.localeCompare(y.dal));
      caricate = true;
      stagioneId = (stagioni.find((x) => x.dal <= oggi && x.al >= oggi) ?? stagioni.find((x) => x.dal > oggi) ?? stagioni.at(-1))?.id ?? '';
      carica();
    })
    .catch((e) => (errore = messaggioErrore(e, 'Non riesco a caricare le stagioni.')));

  const stagione = $derived(stagioni.find((s) => s.id === stagioneId));
  const precedente = $derived(stagione ? stagioni.filter((s) => s.al < stagione.dal).at(-1) : undefined);

  function carica() {
    const st = stagioni.find((x) => x.id === stagioneId);
    if (!st) return;
    api.chiusure(st.dal, st.al)
      .then((c) => ((eventi = c.chiusure), (impianto = c.impiantiGestiti[0] ?? null)))
      .catch((e) => (errore = messaggioErrore(e, 'Non riesco a caricare il calendario.')));
  }

  const elenco = $derived(raggruppa(eventi));
  const calendario = $derived(stagione ? mesi(stagione.dal, stagione.al) : []);
  const conti = $derived(stagione ? conteggi(stagione.dal, stagione.al, elenco) : { chiusi: 0, ridotti: 0, aperti: 0 });
  const p = $derived(prossima(elenco, oggi));
  const lo = $derived(da && a ? (da < a ? da : a) : da);
  const hi = $derived(da && a ? (da < a ? a : da) : da);
  const periodo = $derived(!!lo && !!hi && hi !== lo);
  const mancanti = $derived(stagione ? festivita(stagione.dal, stagione.al)
    // già segnate, o dentro una chiusura di tutto il giorno (es. Natale nelle vacanze di Natale)
    .filter(([n, d1, d2]) => !elenco.some((v) => v.nome === n && v.dal === d1) && !(statoDel(d1, elenco)?.tipo === 'chiuso' && statoDel(d2, elenco)?.tipo === 'chiuso')) : []);
  const modifica = $derived(!!impianto);

  function scegli(g: string) {
    if (!modifica) return;
    messaggio = '';
    if (!da || a) {
      da = g;
      a = null;
      nome = '';
    } else a = g;
  }

  async function crea(vs: { nome: string; dal: string; al: string; tipo: TipoChiusura; apre: string | null; chiude: string | null }[]) {
    if (!impianto) return;
    errore = '';
    lavoro = true;
    try {
      for (const v of vs)
        for (const e of eventiDaCreare(v))
          await api.creaEvento({ impiantoId: impianto, tipo: 'CHIUSURA', ...e, spazi: [], istruttoreId: null, note: null });
      return true;
    } catch (e) {
      errore = messaggioErrore(e);
      return false;
    } finally {
      lavoro = false;
      carica();
    }
  }

  async function aggiungi() {
    if (!lo || !hi) return;
    if (tipo === 'ridotto' && !apre && !chiude) return void (errore = 'Indica a che ora apre o a che ora chiude.');
    const n = nome.trim() || (periodo ? 'Chiusura' : 'Chiusura straordinaria');
    if (await crea([{ nome: n, dal: lo, al: hi, tipo, apre: tipo === 'ridotto' ? apre || null : null, chiude: tipo === 'ridotto' ? chiude || null : null }])) {
      messaggio = `«${n}» aggiunta al calendario.`;
      da = a = null;
    }
  }

  async function togli(v: Voce) {
    errore = '';
    lavoro = true;
    try {
      for (const id of v.eventi) await api.annullaEvento(id);
      messaggio = `«${v.nome}» tolta dal calendario.`;
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      lavoro = false;
      carica();
    }
  }

  async function aggiungiFestivita() {
    if (await crea(mancanti.map(([n, d1, d2]) => ({ nome: n, dal: d1, al: d2, tipo: 'chiuso' as const, apre: null, chiude: null }))))
      messaggio = 'Festività nazionali aggiunte.';
  }

  async function copia() {
    if (!precedente || !stagione) return;
    errore = '';
    try {
      const vecchie = raggruppa((await api.chiusure(precedente.dal, precedente.al)).chiusure);
      const nuove = vecchie
        .map((v) => ({ ...v, dal: unAnnoDopo(v.dal), al: unAnnoDopo(v.al) }))
        .filter((v) => v.dal >= stagione.dal && v.al <= stagione.al && !elenco.some((x) => x.nome === v.nome && x.dal === v.dal));
      if (!nuove.length) return void (messaggio = `Dalla stagione ${precedente.nome} non c'è niente di nuovo da copiare.`);
      if (await crea(nuove)) messaggio = `${nuove.length === 1 ? 'Copiata una chiusura' : `Copiate ${nuove.length} chiusure`} dalla stagione ${precedente.nome}.`;
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

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
    <p class="muted nomargin spiega">Si segnano una volta all'inizio della stagione e valgono per tutto: quando si genera un mese non si creano turni in quei giorni (o dopo l'orario di chiusura), il piano vasca li mostra chiusi e le lezioni dei corsi saltano.</p>
    <label class="stag"><span class="lbl">Stagione</span>
      <select class="inp" bind:value={stagioneId} aria-label="Stagione" onchange={() => ((da = a = null), carica())}>
        {#each stagioni as s (s.id)}<option value={s.id}>{s.nome}</option>{/each}
      </select>
    </label>
  </div>

  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
  {#if caricate && !stagioni.length}<p class="muted">Non c'è ancora nessuna stagione: aggiungila in <a href="#/societa">Società e stagioni</a>.</p>{/if}

  {#if stagione}
    <div class="riep">
      <div class="tile t-ferma"><span class="lbl">Giorni di chiusura</span><span class="v">{conti.chiusi}</span><span class="n">struttura chiusa tutto il giorno</span></div>
      <div class="tile t-warn"><span class="lbl">Giorni a orario ridotto</span><span class="v">{conti.ridotti}</span><span class="n">apre più tardi o chiude prima</span></div>
      <div class="tile t-passa"><span class="lbl">Giorni di apertura</span><span class="v">{conti.aperti}</span><span class="n">per la stagione {brevissimo(stagione.nome)}</span></div>
      <div class="tile"><span class="lbl">Prossima chiusura</span>
        <span class="v piccolo">{p ? p.voce.nome : 'Nessuna'}</span>
        <span class="n">{p ? `${quando(p.voce)} · ${p.traGiorni === 0 ? (p.voce.dal === oggi ? 'oggi' : 'in corso') : p.traGiorni === 1 ? 'domani' : `tra ${p.traGiorni} giorni`}` : 'fino a fine stagione'}</span></div>
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
          {#if modifica}<span class="muted small">Tocca un giorno, o il primo e l'ultimo di un periodo, per segnarlo.</span>{/if}
        </div>
      </div>

      <aside class="lato-p">
        {#if lo && modifica && schermo.larga}
          <section class="card pann" aria-label="Nuova chiusura">
            <span class="intesta"><span class="lbl grow">Nuova chiusura</span>
              <button class="x-btn" type="button" aria-label="Annulla" onclick={() => (da = a = null)}><Icona nome="chiudi" /></button></span>
            {@render nuova()}
          </section>
        {/if}

        <section class="card pann" aria-label="Chiusure">
          <span class="intesta"><h2 class="grow">Chiusure</h2><span class="muted small">{elenco.length === 1 ? '1 voce' : `${elenco.length} voci`}</span></span>
          {#each elenco as v (v.eventi[0])}
            <div class="ch"><i class={v.tipo}></i><span class="t"><b>{v.nome}</b><span>{quando(v)}</span></span>
              {#if modifica}<button class="x-btn" type="button" aria-label={`Togli ${v.nome}`} disabled={lavoro} onclick={() => togli(v)}><Icona nome="chiudi" /></button>{/if}
            </div>
          {:else}
            <p class="muted small nomargin">Nessuna chiusura segnata per questa stagione.</p>
          {/each}
          {#if modifica}
            <button class="btn btn-secondary" type="button" disabled={lavoro || !mancanti.length} onclick={aggiungiFestivita}>
              {mancanti.length ? 'Aggiungi le festività nazionali' : 'Festività nazionali aggiunte'}</button>
            {#if precedente}<button class="btn btn-secondary" type="button" disabled={lavoro} onclick={copia}>Copia dalla stagione {precedente.nome}</button>{/if}
          {/if}
        </section>
      </aside>
    </div>
  {/if}
</section>

{#snippet nuova()}
            <h2>{periodo ? `Dal ${breve(lo!)} al ${breve(hi!)}` : breve(lo!)}</h2>
            <div class="seg due-s" role="group" aria-label="Tipo">
              <button type="button" aria-pressed={tipo === 'chiuso'} onclick={() => (tipo = 'chiuso')}>Tutto il giorno</button>
              <button type="button" aria-pressed={tipo === 'ridotto'} onclick={() => (tipo = 'ridotto')}>Orario ridotto</button>
            </div>
            {#if tipo === 'ridotto'}
              <div class="due">
                <label class="field"><span class="lbl">Apre alle</span><input class="inp" type="time" bind:value={apre} /></label>
                <label class="field"><span class="lbl">Chiude alle</span><input class="inp" type="time" bind:value={chiude} /></label>
              </div>
              <span class="muted small">Lascia vuoto l'orario che non cambia.</span>
            {/if}
            <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={nome} placeholder={periodo ? 'Chiusura' : 'Chiusura straordinaria'} /></label>
            {#if schermo.larga}
              {#if !periodo}<span class="muted small">Tocca un altro giorno per farne un periodo.</span>{/if}
            {:else}
              <label class="field"><span class="lbl">Fino al (per un periodo)</span><input class="inp" type="date" min={da} max={stagione?.al} bind:value={a} /></label>
            {/if}
            <button class="btn btn-primary" type="button" disabled={lavoro} onclick={aggiungi}>Aggiungi al calendario</button>
{/snippet}

{#if lo && modifica && !schermo.larga}
  <Foglio titolo="Nuova chiusura" chiudi={() => (da = a = null)}>
    {@render nuova()}
  </Foglio>
{/if}

<style>
  .barra { display: flex; align-items: flex-end; gap: var(--space-16); flex-wrap: wrap; }
  .spiega { flex: 1 1 420px; max-width: 820px; }
  .stag { display: flex; align-items: center; gap: var(--space-8); }
  .stag .inp { width: 150px; appearance: auto; }
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
  .intesta { display: flex; align-items: center; gap: var(--space-8); }
  .due-s { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .ch { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-10) 0; border-top: 1px solid var(--line-soft); font-size: var(--text-md); }
  .ch:first-of-type { border-top: none; }
  .ch .t { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
  .ch .t span { font-size: var(--text-xs); color: var(--muted); }
  .ch i { width: 10px; height: 10px; border-radius: 3px; flex-shrink: 0; background: var(--ferma); }
  .ch i.ridotto { background: var(--warn); }
  @media (max-width: 599px) {
    .mesi { grid-template-columns: minmax(0, 1fr); }
    .riep { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  }
</style>
