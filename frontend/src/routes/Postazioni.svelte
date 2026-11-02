<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import { api, messaggioErrore, type Impianto, type Mansione, type PersonaBreve, type PostazioneGestione, type Postazione, type Spazio, type Tariffa } from '../lib/api';
  import { dataLunga, euro, iso } from '../lib/date';
  import { gruppi, nomeNuovaPostazione, type Gruppo } from '../lib/gestione';

  /**
   * Mansioni, postazioni e tariffe (ADR 0024): ogni mansione è un ruolo pagato, con la sua tariffa e la sua colonna nel
   * foglio ore; le postazioni sono le righe della pianificazione, divise per area. Tocca una postazione, la tariffa o
   * «Modifica» per cambiarle nel pannello a destra.
   */
  let mansioni = $state<Mansione[]>([]);
  let tariffe = $state<Tariffa[]>([]);
  let spazi = $state<Spazio[]>([]);
  let impianti = $state<Impianto[]>([]);
  let voci = $state<string[]>([]);
  let personale = $state<PersonaBreve[]>([]);
  let caricamento = $state(true);
  let errore = $state('');
  let messaggio = $state('');
  let lavoro = $state(false);
  const oggi = iso(new Date());

  Promise.all([api.mansioni(), api.tariffe(), api.spazi(), api.impianti(), api.vociFoglioOre()])
    .then(([m, t, s, i, v]) => { mansioni = m; tariffe = t; spazi = s; impianti = i; voci = v; })
    .catch((e) => (errore = messaggioErrore(e)))
    .finally(() => (caricamento = false));
  api.personale().then((p) => (personale = p)).catch(() => {});

  const valida = (t: Tariffa) => t.dal <= oggi && (!t.al || t.al >= oggi);
  const tariffaDi = (m: Mansione) => tariffe.find((t) => t.mansioneId === m.id && !t.persona && valida(t));
  const eccezioni = (m: Mansione) => tariffe.filter((t) => t.mansioneId === m.id && t.persona && (valida(t) || t.dal > oggi));
  const gruppiDi = (m: Mansione): Gruppo[] =>
    gruppi(m.postazioni.map((p): Postazione => ({ id: p.id, nome: p.nome, mansione: m.nome, impiantoId: m.impiantoId, mansioneId: m.id, soloSesso: p.soloSesso, spazioId: p.spazioId })));
  const riserva = (g: Gruppo) => {
    const s = new Set(g.righe.map((r) => r.postazione.soloSesso ?? ''));
    return s.size === 1 ? ({ F: '· solo donne', M: '· solo uomini', '': '' } as Record<string, string>)[[...s][0]] : '';
  };
  const postazione = (id: string) => mansioni.flatMap((m) => m.postazioni).find((p) => p.id === id)!;
  const nomeSpazio = (id: string | null) => spazi.find((s) => s.id === id)?.nome ?? '';

  // ── Il pannello a destra: una postazione, una tariffa o una mansione ──
  type Pannello =
    | { cosa: 'postazione'; mansione: Mansione; id: string | null; nome: string; spazio: string; sesso: '' | 'M' | 'F'; attiva: boolean; originale: string }
    | { cosa: 'tariffa'; mansione: Mansione; euro: string; dal: string; persona: string; correggi: Tariffa | null; al: string }
    | { cosa: 'mansione'; mansione: Mansione | null; nome: string; voce: string; impianto: string; coordinatori: string[] };
  let pannello = $state<Pannello | null>(null);
  const firma = (p: { nome: string; spazio: string; sesso: string; attiva: boolean }) => JSON.stringify([p.nome, p.spazio, p.sesso, p.attiva]);

  function apriPostazione(m: Mansione, p: PostazioneGestione | null, g?: Gruppo) {
    messaggio = '';
    const b = p ? { nome: p.nome, spazio: p.spazioId ?? '', sesso: (p.soloSesso ?? '') as '' | 'M' | 'F', attiva: p.attiva }
      : { nome: g ? nomeNuovaPostazione(g) : '', spazio: '', sesso: (g?.righe[0]?.postazione.soloSesso ?? '') as '' | 'M' | 'F', attiva: true };
    pannello = { cosa: 'postazione', mansione: m, id: p?.id ?? null, ...b, originale: p ? firma(b) : '' };
  }
  function apriTariffa(m: Mansione) {
    messaggio = '';
    const t = tariffaDi(m);
    pannello = { cosa: 'tariffa', mansione: m, euro: t ? String(t.euroOra).replace('.', ',') : '', dal: oggi.slice(0, 8) + '01', persona: '', correggi: null, al: '' };
  }
  function apriMansione(m: Mansione | null) {
    messaggio = '';
    pannello = { cosa: 'mansione', mansione: m, nome: m?.nome ?? '', voce: m?.voceFoglioOre ?? '', impianto: m?.impiantoId ?? impianti[0]?.id ?? '', coordinatori: m?.coordinatori.map((c) => c.id) ?? [] };
  }
  const chiudi = () => (pannello = null);

  async function esegui(azione: () => Promise<unknown>, fatto: string, chiudiDopo = true) {
    lavoro = true;
    errore = '';
    messaggio = '';
    try {
      await azione();
      [mansioni, tariffe] = await Promise.all([api.mansioni(), api.tariffe()]);
      messaggio = fatto;
      if (chiudiDopo) pannello = null;
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      lavoro = false;
    }
  }

  function salvaPostazione() {
    const p = pannello as Extract<Pannello, { cosa: 'postazione' }>;
    if (p.id) {
      const id = p.id;
      return esegui(async () => {
        const esito = await api.modificaPostazione(id, p.nome, p.spazio || null, p.attiva, p.sesso || null);
        if (esito.turniFuturi && !p.attiva) setTimeout(() => (messaggio = `Postazione disattivata. Ha ancora ${esito.turniFuturi === 1 ? 'un turno futuro' : `${esito.turniFuturi} turni futuri`}: restano validi, ma non se ne creano di nuovi.`));
      }, 'Postazione salvata.');
    }
    return esegui(() => api.creaPostazione(p.mansione.id, p.nome, p.spazio || null, p.sesso || null), 'Postazione aggiunta.');
  }
  /** Sposta una postazione di un posto (l'ordine è quello delle righe in pianificazione). */
  function sposta(verso: -1 | 1) {
    const p = pannello as Extract<Pannello, { cosa: 'postazione' }>;
    const tutte = mansioni.flatMap((x) => x.postazioni).sort((a, b) => a.ordine - b.ordine);
    const dentro = p.mansione.postazioni.map((x) => x.id);
    const i = tutte.findIndex((x) => x.id === p.id);
    let j = i + verso;
    while (j >= 0 && j < tutte.length && !dentro.includes(tutte[j].id)) j += verso;
    if (j < 0 || j >= tutte.length) return;
    [tutte[i], tutte[j]] = [tutte[j], tutte[i]];
    return esegui(async () => { mansioni = await api.ordinaPostazioni(tutte.map((x) => x.id)); }, 'Ordine cambiato.', false);
  }

  const numero = (s: string) => Number(s.replace(',', '.'));
  function salvaTariffa() {
    const t = pannello as Extract<Pannello, { cosa: 'tariffa' }>;
    if (t.correggi) {
      const c = t.correggi;
      return esegui(() => api.correggiTariffa(c.id, numero(t.euro), t.al || null), 'Tariffa corretta.');
    }
    return esegui(() => api.nuovaTariffa({ mansioneId: t.mansione.id, personaId: t.persona || null, euroOra: numero(t.euro), dal: t.dal }),
      `Tariffa salvata dal ${dataLunga(t.dal)}. Quella precedente, se c'era, si chiude il giorno prima.`);
  }
  function salvaMansione() {
    const m = pannello as Extract<Pannello, { cosa: 'mansione' }>;
    const esistente = m.mansione;
    return esegui(async () => {
      if (esistente) await api.modificaMansione(esistente.id, m.nome, m.voce || null, m.coordinatori);
      else {
        const nuova = await api.creaMansione(m.impianto, m.nome, m.voce || null);
        if (m.coordinatori.length) await api.modificaMansione(nuova.id, m.nome, m.voce || null, m.coordinatori);
      }
    }, esistente ? 'Mansione aggiornata.' : 'Mansione creata: ora aggiungi le sue postazioni.');
  }
  const storia = (m: Mansione) => tariffe.filter((t) => t.mansioneId === m.id).sort((a, b) => b.dal.localeCompare(a.dal));
</script>

<div class="barra"><span class="grow"></span><button class="btn btn-primary" onclick={() => apriMansione(null)}><Icona nome="piu" />Nuova mansione</button></div>
{#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

<div class="colonne-p">
  <div class="princ">
    {#each mansioni as m (m.id)}
      {@const t = tariffaDi(m)}
      {@const ecc = eccezioni(m)}
      <section class="card sez" aria-label={m.nome}>
        <div class="mans-t">
          <span class="info"><h2>{m.nome}</h2>
            <span>{#if impianti.length > 1}{impianti.find((i) => i.id === m.impiantoId)?.nome} · {/if}Foglio ore: {m.voceFoglioOre ? `colonna «${m.voceFoglioOre}»` : 'colonna propria'}
              · coordina {m.coordinatori.length ? m.coordinatori.map((c) => c.nome).join(', ') : 'chi ha il permesso «Ore dello staff»'} · {m.postazioni.length === 1 ? '1 postazione' : `${m.postazioni.length} postazioni`}</span></span>
          <button type="button" class="tariffa-b" class:manca={!t} aria-pressed={pannello?.cosa === 'tariffa' && pannello.mansione.id === m.id} onclick={() => apriTariffa(m)}>
            <span class="r1"><span>tariffa</span><b>{t ? `${euro(t.euroOra)}/h` : 'manca'}</b></span>
            <span>{ecc.length ? (ecc.length === 1 ? '1 eccezione' : `${ecc.length} eccezioni`) : t ? `dal ${dataLunga(t.dal)}` : 'tocca per impostarla'}</span>
          </button>
          <button type="button" class="mini-btn" onclick={() => apriMansione(m)}>Modifica</button>
        </div>
        <div class="aree-m">
          {#each gruppiDi(m) as g (g.chiave)}
            <div class="area-m ar-{g.area}">
              <span class="nome-a"><i></i>{g.nome}<span class="riserva">{riserva(g)}</span></span>
              <div class="posti">
                {#each g.righe as r (r.postazione.id)}
                  {@const p = postazione(r.postazione.id)}
                  <button type="button" class="posto" class:spento={!p.attiva} aria-pressed={pannello?.cosa === 'postazione' && pannello.id === p.id} onclick={() => apriPostazione(m, p)}>
                    <b>{r.etichetta}</b><span>{p.attiva ? (nomeSpazio(p.spazioId) || (p.soloSesso ? (p.soloSesso === 'F' ? 'solo donne' : 'solo uomini') : 'tutti')) : 'spenta'}</span>
                  </button>
                {/each}
                <button type="button" class="posto nuovo" onclick={() => apriPostazione(m, null, g)}><Icona nome="piu" />Postazione</button>
              </div>
            </div>
          {/each}
          <button type="button" class="mini-btn tratt sx" onclick={() => apriPostazione(m, null)}><Icona nome="piu" />{m.postazioni.length ? 'Nuova area' : 'Prima postazione'}</button>
        </div>
      </section>
    {:else}
      {#if !caricamento}<p class="muted">Nessuna mansione.</p>{/if}
    {/each}
  </div>

  <aside class="lato-p">
    {#if pannello?.cosa === 'postazione'}
      {@const p = pannello}
      <form class="card pann ar-{gruppiDi(p.mansione).find((g) => g.righe.some((r) => r.postazione.id === p.id))?.area ?? 'x'}" onsubmit={(e) => { e.preventDefault(); salvaPostazione(); }}>
        <span class="testa-pn"><span class="lbl grow">{p.id ? 'Postazione' : 'Nuova postazione'}</span><button class="x-btn" type="button" aria-label="Chiudi" onclick={chiudi}><Icona nome="chiudi" /></button></span>
        <h2>{p.id ? p.nome : `${p.mansione.nome} · nuova`}</h2>
        <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={p.nome} maxlength="120" required placeholder="Es. Spogliatoio M · postazione 3" />
          <span class="muted small">La parte prima di « · » è l'area: le postazioni con la stessa area stanno insieme.</span></label>
        <label class="field"><span class="lbl">Spazio (facoltativo)</span>
          <select class="inp" bind:value={p.spazio}><option value="">—</option>{#each spazi.filter((s) => s.impiantoId === p.mansione.impiantoId) as s (s.id)}<option value={s.id}>{s.nome}</option>{/each}</select></label>
        <div class="field"><span class="lbl">Chi ci può lavorare</span>
          <div class="seg seg3" role="group" aria-label="Chi ci può lavorare">
            <button type="button" aria-pressed={p.sesso === ''} onclick={() => (p.sesso = '')}>Tutti</button>
            <button type="button" aria-pressed={p.sesso === 'F'} onclick={() => (p.sesso = 'F')}>Solo donne</button>
            <button type="button" aria-pressed={p.sesso === 'M'} onclick={() => (p.sesso = 'M')}>Solo uomini</button>
          </div>
          <span class="muted small">Chi non può lavorarci non compare tra i candidati (conta il sesso in anagrafica).</span></div>
        {#if p.id}
          <label class="riga-switch"><span>Stato postazione: <b>{p.attiva ? 'Attiva' : 'Disattiva'}</b><br />
            <span class="muted small">{p.attiva ? 'Si può assegnare ed entra nei mesi generati.' : 'Non si può assegnare e non entra nei mesi generati.'}</span></span>
            <button type="button" class="switch" role="switch" aria-checked={p.attiva} aria-label="Postazione attiva" onclick={() => (p.attiva = !p.attiva)}><span></span></button></label>
          <span class="sposta"><span class="muted small grow">Ordine in pianificazione</span>
            <button type="button" class="mini-btn" onclick={() => sposta(-1)}><Icona nome="su" />Prima</button><button type="button" class="mini-btn" onclick={() => sposta(1)}><Icona nome="giu" />Dopo</button></span>
        {/if}
        <!-- Salva compare solo se c'è qualcosa da salvare: una postazione nuova, o una modifica -->
        <div class="row fine">
          {#if !p.id}<button type="button" class="btn btn-secondary" onclick={chiudi}>Annulla</button>{/if}
          {#if !p.id || firma(p) !== p.originale}<button class="btn btn-primary grow" disabled={lavoro || !p.nome.trim()}>{p.id ? 'Salva' : 'Aggiungi la postazione'}</button>{/if}
        </div>
      </form>
    {:else if pannello?.cosa === 'tariffa'}
      {@const t = pannello}
      <form class="card pann" onsubmit={(e) => { e.preventDefault(); salvaTariffa(); }}>
        <span class="testa-pn"><span class="lbl grow">{t.correggi ? 'Correggi la tariffa' : t.persona ? 'Eccezione per una persona' : 'Tariffa oraria'}</span><button class="x-btn" type="button" aria-label="Chiudi" onclick={chiudi}><Icona nome="chiudi" /></button></span>
        <h2>{t.mansione.nome}</h2>
        {#if t.correggi}
          <span class="muted small">{t.correggi.persona ? `Solo ${t.correggi.persona.nome}` : 'Tutti'} · dal {dataLunga(t.correggi.dal)}. Per un aumento da una certa data aggiungi invece una tariffa nuova: lo storico resta corretto.</span>
          <div class="due">
            <label class="field"><span class="lbl">€ all'ora</span><input class="inp m" inputmode="decimal" bind:value={t.euro} required /></label>
            <label class="field"><span class="lbl">Fino al</span><input class="inp" type="date" bind:value={t.al} min={t.correggi.dal} /></label>
          </div>
        {:else}
          {#if t.persona !== ''}
            <label class="field"><span class="lbl">Per</span><select class="inp" bind:value={t.persona}>{#each personale as p (p.id)}<option value={p.id}>{p.nome}</option>{/each}</select></label>
          {/if}
          <div class="due">
            <label class="field"><span class="lbl">€ all'ora{t.persona ? '' : ', per tutti'}</span><input class="inp m" inputmode="decimal" bind:value={t.euro} required placeholder="9,50" /></label>
            <label class="field"><span class="lbl">Dal</span><input class="inp" type="date" bind:value={t.dal} required /></label>
          </div>
          <span class="muted small">Una tariffa nuova vale dalla data scelta; i mesi già passati restano con quella di allora.</span>
        {/if}
        {#if storia(t.mansione).length}
          <span class="lbl">Nel tempo</span>
          <ul class="storia">
            {#each storia(t.mansione) as s (s.id)}
              <li><span class="q">{euro(s.euroOra)}</span><span class="p">{s.persona ? `${s.persona.nome} (eccezione)` : 'tutti'}</span>
                <span class="pill {valida(s) ? 'passa' : ''}">{valida(s) ? `dal ${dataLunga(s.dal)}` : s.dal > oggi ? `dal ${dataLunga(s.dal)}` : `fino al ${s.al ? dataLunga(s.al) : '—'}`}</span>
                <button type="button" class="x-btn" aria-label="Correggi" title="Correggi" onclick={() => { t.correggi = s; t.euro = String(s.euroOra).replace('.', ','); t.al = s.al ?? ''; }}><Icona nome="matita" /></button></li>
            {/each}
          </ul>
        {/if}
        {#if !t.correggi && t.persona === '' && personale.length}
          <button type="button" class="mini-btn tratt sx" onclick={() => { t.persona = personale[0].id; t.euro = ''; }}><Icona nome="piu" />Eccezione per una persona</button>
        {/if}
        <button class="btn btn-primary" disabled={lavoro || !t.euro.trim()}>{t.correggi ? 'Salva la correzione' : 'Salva la tariffa'}</button>
      </form>
    {:else if pannello?.cosa === 'mansione'}
      {@const m = pannello}
      <form class="card pann" onsubmit={(e) => { e.preventDefault(); salvaMansione(); }}>
        <span class="testa-pn"><span class="lbl grow">{m.mansione ? 'Mansione' : 'Nuova mansione'}</span><button class="x-btn" type="button" aria-label="Chiudi" onclick={chiudi}><Icona nome="chiudi" /></button></span>
        <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={m.nome} maxlength="120" required placeholder="Es. Assistente spogliatoio" /></label>
        {#if !m.mansione && impianti.length > 1}
          <label class="field"><span class="lbl">Impianto</span><select class="inp" bind:value={m.impianto}>{#each impianti as i (i.id)}<option value={i.id}>{i.nome}</option>{/each}</select></label>
        {/if}
        <label class="field"><span class="lbl">Colonna nel foglio ore</span>
          <select class="inp" bind:value={m.voce}><option value="">Colonna propria (col nome della mansione)</option>{#each voci as v (v)}<option value={v}>{v}</option>{/each}</select></label>
        <div class="field"><span class="lbl">Chi coordina il gruppo</span>
          <span class="muted small">Con il permesso «Ore dello staff», chi coordina dei gruppi vede le ore solo di quelli; chi non coordina nessun gruppo (la direzione) li vede tutti.</span>
          <div class="coordinatori">
            {#each personale as p (p.id)}
              <label class="spunta"><input type="checkbox" checked={m.coordinatori.includes(p.id)} onchange={(e) => (m.coordinatori = e.currentTarget.checked ? [...m.coordinatori, p.id] : m.coordinatori.filter((x) => x !== p.id))} />{p.nome}</label>
            {/each}
          </div>
        </div>
        <button class="btn btn-primary" disabled={lavoro || !m.nome.trim()}>{m.mansione ? 'Salva' : 'Crea la mansione'}</button>
      </form>
    {:else}
      <section class="card pann"><h2>Come funziona</h2>
        <span class="muted small">Tocca una postazione per cambiarne nome, chi ci può lavorare o per spegnerla; tocca la tariffa di una mansione per cambiarla da una data in poi o aggiungere un'eccezione per una persona.</span>
        <span class="muted small">L'ordine delle postazioni qui è l'ordine delle righe in pianificazione.</span></section>
    {/if}
  </aside>
</div>

<style>
  .barra { display: flex; align-items: center; gap: var(--space-12); }
  /* intestazione della mansione: stessa fascia color cloro delle sezioni */
  .mans-t { display: flex; align-items: center; gap: var(--space-14); flex-wrap: wrap; padding: var(--space-14) var(--space-16); background: var(--cloro-soft);
    border-bottom: 1px solid color-mix(in srgb, var(--cloro) 28%, transparent); }
  .mans-t h2 { margin: 0; font-family: var(--font-display); font-size: 19px; font-weight: 800; color: var(--cloro-ink); }
  .info { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 220px; }
  .info > span { font-size: var(--text-sm); color: var(--cloro-ink); opacity: 0.85; }
  .tariffa-b { display: flex; flex-direction: column; align-items: flex-end; gap: 1px; padding: 6px 12px; border-radius: var(--radius-md); border: 1px solid var(--line); background: var(--surface);
    font: inherit; color: var(--ink); cursor: pointer; text-align: right; }
  .tariffa-b b { font-family: var(--font-mono); font-size: 17px; }
  .tariffa-b span { font-size: var(--text-xs); color: var(--muted); }
  /* due righe: «tariffa 9,00 €/h» e sotto da quando vale */
  .tariffa-b .r1 { display: flex; align-items: baseline; gap: 6px; }
  .tariffa-b.manca b { color: var(--warn); }
  .tariffa-b[aria-pressed='true'] { border-color: var(--cloro); box-shadow: 0 0 0 3px color-mix(in srgb, var(--cloro) 25%, transparent); }
  .aree-m { display: flex; flex-direction: column; gap: var(--space-14); padding: var(--space-16); }
  .area-m { display: flex; flex-direction: column; gap: var(--space-8); --a: var(--area-x); --a-bg: var(--area-x-bg); --a-fg: var(--area-x-fg); }
  .area-m.ar-m, .pann.ar-m { --a: var(--area-m); --a-bg: var(--area-m-bg); --a-fg: var(--area-m-fg); }
  .area-m.ar-f, .pann.ar-f { --a: var(--area-f); --a-bg: var(--area-f-bg); --a-fg: var(--area-f-fg); }
  .area-m.ar-p, .pann.ar-p { --a: var(--area-p); --a-bg: var(--area-p-bg); --a-fg: var(--area-p-fg); }
  .area-m.ar-a, .pann.ar-a { --a: var(--area-a); --a-bg: var(--area-a-bg); --a-fg: var(--area-a-fg); }
  .nome-a { display: flex; align-items: center; gap: var(--space-8); font-size: var(--text-xs); font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--a-fg); }
  .nome-a i { width: 10px; height: 10px; border-radius: 3px; background: var(--a); }
  .riserva { letter-spacing: 0; text-transform: none; font-weight: 600; color: var(--muted); }
  .posti { display: flex; flex-wrap: wrap; gap: var(--space-8); }
  .posto { display: flex; flex-direction: column; gap: 2px; min-width: 132px; padding: 9px 12px; border-radius: var(--radius-md); border: 1.5px solid var(--a); background: var(--a-bg);
    font: inherit; color: var(--ink); text-align: left; cursor: pointer; }
  .posto b { font-size: var(--text-md); }
  .posto span { font-size: var(--text-xs); color: var(--a-fg); }
  .posto.spento { border-style: dashed; background: var(--surface); opacity: 0.6; }
  .posto[aria-pressed='true'] { box-shadow: 0 0 0 3px var(--a); }
  .posto.nuovo { border: 1.5px dashed color-mix(in srgb, var(--cloro) 55%, transparent); background: var(--surface); color: var(--cloro); justify-content: center; align-items: center;
    font-weight: 700; font-size: var(--text-sm); flex-direction: row; gap: 6px; }
  .posto.nuovo :global(.ic) { width: 16px; height: 16px; }
  .sx { align-self: flex-start; }
  .testa-pn { display: flex; align-items: center; gap: var(--space-8); }
  .pann.ar-m h2, .pann.ar-f h2, .pann.ar-p h2, .pann.ar-a h2 { color: var(--a-fg); }
  select.inp { appearance: auto; }
  .seg3 { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .sposta { display: flex; align-items: center; gap: var(--space-6); }
  .fine { justify-content: flex-end; }
  .storia { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
  .storia li { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-6) 0; border-top: 1px solid var(--line-soft); font-size: var(--text-md); }
  .storia li:first-child { border-top: none; }
  .storia .q { font-family: var(--font-mono); font-weight: 600; min-width: 64px; }
  .storia .p { flex: 1; min-width: 0; color: var(--muted); font-size: var(--text-sm); }
  .coordinatori { display: flex; flex-direction: column; gap: var(--space-4); max-height: 220px; overflow-y: auto; padding: var(--space-6) var(--space-10); border: 1px solid var(--line); border-radius: var(--radius-md); }
  .spunta { display: flex; align-items: center; gap: var(--space-8); min-height: 36px; font-size: var(--text-md); }
</style>
