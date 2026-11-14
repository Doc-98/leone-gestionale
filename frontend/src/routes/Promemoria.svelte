<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import { api, messaggioErrore, type DatiSocieta, type PaginaPromemoria, type RegolaPromemoria, type StatoEmail, type TipoPromemoria } from '../lib/api';
  import { dataBreve, dataDi, iso, oraDi } from '../lib/date';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * I promemoria email (ADR 0024): ogni email è una pillola sulla linea dei giorni intorno alla scadenza; toccala per
   * cambiarne giorno e testo, o usa il «+» per aggiungerne una. A destra gli avvisi della lista d'attesa e le ultime email.
   */
  const TIPI: { tipo: TipoPromemoria; nome: string; sotto: string }[] = [
    { tipo: 'CERTIFICATO', nome: 'Certificato medico', sotto: 'prima che scada' },
    { tipo: 'ABBONAMENTO', nome: 'Abbonamento', sotto: 'se non è già rinnovato' },
    { tipo: 'TESSERAMENTO', nome: 'Tesseramento in scadenza', sotto: 'prima che scada' },
    { tipo: 'TESSERAMENTO_MANCANTE', nome: 'Tesseramento mancante', sotto: 'dal giorno in cui manca' },
    { tipo: 'PAGAMENTO', nome: 'Pagamento scaduto', sotto: 'dopo la scadenza, se non è pagato' },
  ];
  /** Questi si ripetono ogni N giorni, al massimo M volte; gli altri partono una volta, N giorni prima della scadenza. */
  const ripetuto = (t: TipoPromemoria) => t === 'TESSERAMENTO_MANCANTE' || t === 'PAGAMENTO';
  function ogni(r: Pick<RegolaPromemoria, 'tipo' | 'giorni' | 'maxInvii'>): string {
    const volte = `al massimo ${r.maxInvii ?? 1} volte`;
    return r.tipo === 'PAGAMENTO' ? `${r.giorni} giorni dopo la scadenza, poi ogni ${r.giorni}, ${volte}` : `ogni ${r.giorni} giorni, ${volte}`;
  }
  /** I dati del cliente che si possono inserire nel testo: si sostituiscono da soli all'invio. */
  const DATI: [string, string][] = [['nome', '{nome}'], ['cognome', '{cognome}'], ['data di scadenza', '{scadenza}'], ['giorni che mancano', '{giorni}'],
    ['corso', '{corso}'], ['stagione', '{stagione}'], ['importo da pagare', '{importo}'], ['società', '{societa}']];
  const STATI: Record<StatoEmail, { nome: string; classe: string }> = {
    INVIATA: { nome: 'inviata', classe: 'passa' },
    NON_INVIATA: { nome: 'non spedita', classe: 'warn' },
    SENZA_EMAIL: { nome: 'senza email', classe: 'warn' },
    ERRORE: { nome: 'errore', classe: 'ferma' },
  };

  let pagina = $state<PaginaPromemoria | null>(null);
  let societa = $state<DatiSocieta | null>(null);
  let attesaOriginale = $state(0);
  let scelta = $state('');
  let bozza = $state<RegolaPromemoria | null>(null);
  let errore = $state('');
  let messaggio = $state('');
  const puoAttesa = $derived(sessione.puo('IMPOSTAZIONI'));

  async function carica() {
    try {
      pagina = await api.promemoria();
      if (!pagina.regole.some((r) => r.id === scelta)) scelta = pagina.regole[0]?.id ?? '';
      apri(scelta);
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  carica();
  api.societa().then((s) => { societa = s; attesaOriginale = s.giorniRispostaAttesa; }).catch(() => {});

  function apri(id: string) {
    scelta = id;
    const r = pagina?.regole.find((x) => x.id === id);
    bozza = r ? { ...r } : null;
  }
  async function esegui(azione: () => Promise<unknown>, fatto: string) {
    errore = '';
    messaggio = '';
    try {
      await azione();
      await carica();
      messaggio = fatto;
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  const originale = $derived(pagina?.regole.find((r) => r.id === scelta) ?? null);
  const cambiata = $derived(!!bozza && !!originale && JSON.stringify(bozza) !== JSON.stringify(originale));
  const corpo = (r: RegolaPromemoria) => ({ giorni: Number(r.giorni), maxInvii: r.maxInvii == null ? null : Number(r.maxInvii), attivo: r.attivo, oggetto: r.oggetto, testo: r.testo });
  const salva = () => esegui(() => api.modificaPromemoria(bozza!.id, corpo(bozza!)), 'Promemoria salvato.');
  async function togli() {
    if (!bozza || !confirm('Togliere questo promemoria?')) return;
    const id = bozza.id;
    scelta = '';
    await esegui(() => api.eliminaPromemoria(id), 'Promemoria tolto.');
  }
  async function aggiungi(tipo: TipoPromemoria) {
    errore = '';
    try {
      const r = await api.creaPromemoria(tipo);
      scelta = r.id;
      await carica();
      messaggio = 'Promemoria aggiunto (spento): scegli il giorno, scrivi il testo e accendilo.';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  /** Accende o spegne tutte le email di un tipo. */
  async function interruttore(tipo: TipoPromemoria) {
    const tutte = pagina!.regole.filter((r) => r.tipo === tipo);
    const accendi = !tutte.some((r) => r.attivo);
    await esegui(async () => { for (const r of tutte) await api.modificaPromemoria(r.id, corpo({ ...r, attivo: accendi })); }, accendi ? 'Promemoria accesi.' : 'Promemoria spenti.');
  }
  async function eseguiOra() {
    errore = '';
    messaggio = '';
    try {
      const r = await api.eseguiPromemoria();
      await carica();
      messaggio = r.email === 0 ? 'Oggi non c\'era nessun promemoria da preparare.'
        : `${r.email} promemoria: ${r.inviate} inviati${r.nonInviate ? `, ${r.nonInviate} registrati senza partire (posta non configurata)` : ''}${r.senzaEmail ? `, ${r.senzaEmail} senza email` : ''}${r.errori ? `, ${r.errori} con errore` : ''}.`;
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  // ── La linea dei giorni ───────────────────────────────────────────────
  /** I giorni delle email rispetto alla scadenza: negativi prima, 0 il giorno stesso, positivi dopo. */
  function pillole(r: RegolaPromemoria): { g: number; testo: string }[] {
    // il tesseramento mancante parte dal giorno in cui manca; il pagamento N giorni dopo la scadenza
    if (ripetuto(r.tipo)) {
      const passo = Number(r.giorni) || 7, primo = r.tipo === 'PAGAMENTO' ? passo : 0;
      return Array.from({ length: Math.max(1, Number(r.maxInvii) || 1) }, (_, i) => ({ g: primo + i * passo, testo: `${i + 1}ª` }));
    }
    const g = Number(r.giorni) || 0;
    return [{ g: -g, testo: g === 0 ? 'il giorno' : `${g} g prima` }];
  }
  const tutti = $derived((pagina?.regole ?? []).flatMap((r) => pillole(r.id === scelta && bozza ? bozza : r).map((p) => p.g)));
  const minimo = $derived(Math.min(-35, ...tutti.map((g) => Math.floor((g - 5) / 7) * 7)));
  const massimo = $derived(Math.max(14, ...tutti.map((g) => Math.ceil((g + 3) / 7) * 7)));
  const x = (g: number) => `${3 + ((g - minimo) / (massimo - minimo)) * 94}%`;
  const tacche = $derived(Array.from({ length: Math.floor((massimo - minimo) / 7) + 1 }, (_, i) => minimo + i * 7));
  /** Dove mettere il «+»: in un punto libero della riga. */
  function piuX(tipo: TipoPromemoria) {
    const usati = (pagina?.regole ?? []).filter((r) => r.tipo === tipo).flatMap((r) => pillole(r).map((p) => p.g));
    for (const g of [-21, -28, -14, -35, -7]) if (usati.every((u) => Math.abs(u - g) > 12)) return x(g);
    return x(minimo + 3);
  }

  // ── Il testo ──────────────────────────────────────────────────────────
  const tipoScelto = $derived(TIPI.find((t) => t.tipo === bozza?.tipo));
  const titolo = $derived(!bozza ? '' : ripetuto(bozza.tipo) ? ogni(bozza)
    : Number(bozza.giorni) === 0 ? 'il giorno della scadenza' : `${bozza.giorni} giorni prima della scadenza`);
  function anteprima(t: string, r: RegolaPromemoria): string {
    const g = r.tipo === 'TESSERAMENTO_MANCANTE' ? 0 : r.tipo === 'PAGAMENTO' ? -Number(r.giorni) : Number(r.giorni);
    const scad = iso(new Date(Date.now() + g * 86400000));
    return t.replaceAll('{nome}', 'Giulia').replaceAll('{cognome}', 'Rossi').replaceAll('{scadenza}', dataBreve(scad)).replaceAll('{giorni}', String(Math.max(0, g)))
      .replaceAll('{importo}', '60,00 €').replaceAll('{corso}', 'Delfino Giallo').replaceAll('{stagione}', '2026/27').replaceAll('{societa}', societa?.nome ?? 'la società');
  }
  /** Il campo (oggetto o testo) dove sta il cursore: le pillole scrivono lì. */
  let campo: HTMLInputElement | HTMLTextAreaElement | null = null;
  function inserisci(tag: string) {
    const el = campo;
    if (!el || document.activeElement !== el || !bozza) return;
    const quale = el.dataset.campo as 'oggetto' | 'testo';
    const a = el.selectionStart ?? bozza[quale].length, b = el.selectionEnd ?? a;
    bozza[quale] = bozza[quale].slice(0, a) + tag + bozza[quale].slice(b);
    setTimeout(() => { el.focus(); el.setSelectionRange(a + tag.length, a + tag.length); }, 0);
  }

  async function salvaAttesa() {
    errore = '';
    try {
      societa = await api.modificaSocieta({ ...societa!, giorniRispostaAttesa: Number(societa!.giorniRispostaAttesa) });
      attesaOriginale = societa.giorniRispostaAttesa;
      messaggio = 'Giorni per rispondere salvati.';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
</script>

{#if pagina && !pagina.postaConfigurata}
  <div class="stato-posta" role="status">
    <Icona nome="avviso" />
    <span class="t"><b>Il server di posta non è configurato</b><span>Le email si preparano ogni giorno ({pagina.orario}) e si vedono qui sotto, ma non arrivano a nessuno. Lo configura il tecnico sul server.</span></span>
    <button type="button" class="btn btn-secondary" onclick={eseguiOra}>Prepara adesso quelle di oggi</button>
  </div>
{:else if pagina}
  <div class="posta-ok"><span class="pill passa">Posta configurata</span><span class="muted small grow">Promemoria automatici: {pagina.orario}</span>
    <button type="button" class="mini-btn" onclick={eseguiOra}>Manda adesso quelli di oggi</button></div>
{/if}
{#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

{#if pagina}
  <div class="colonne-p">
    <div class="princ">
      <section class="card sez" aria-label="Quando partono">
        <div class="sez-t"><h2 class="grow">Quando partono</h2><span class="spiega">giorni rispetto alla scadenza</span></div>
        <div class="lt">
          <span></span>
          <div class="asse" aria-hidden="true">{#each tacche as g (g)}<span class:zero={g === 0} style="left: {x(g)}">{g === 0 ? 'scade' : g > 0 ? `+${g}` : g}</span>{/each}</div>
          {#each TIPI as t (t.tipo)}
            {@const regole = pagina.regole.filter((r) => r.tipo === t.tipo)}
            {@const acceso = regole.some((r) => r.attivo)}
            <span class="tipo-n"><button type="button" class="switch" role="switch" aria-checked={acceso} aria-label={t.nome} disabled={!regole.length} onclick={() => interruttore(t.tipo)}><span></span></button>
              <span class="t"><b>{t.nome}</b><span>{ripetuto(t.tipo) && regole[0] ? ogni(regole[0]) : t.sotto}</span></span></span>
            <div class="binario">
              <span class="filo"></span><span class="scad-v" style="left: {x(0)}"></span>
              {#each regole as r (r.id)}
                {#each pillole(r.id === scelta && bozza ? bozza : r) as p (p.g)}
                  <button type="button" class="mail" class:spenta={!r.attivo} class:dopo={p.g >= 0} style="left: {x(p.g)}" aria-pressed={r.id === scelta} onclick={() => apri(r.id)}>
                    <i><Icona nome="promemoria" /></i>{p.testo}</button>
                {/each}
              {/each}
              {#if !ripetuto(t.tipo) || !regole.length}
                <button type="button" class="piu-m" style="left: {piuX(t.tipo)}" aria-label="Aggiungi un'email per {t.nome}" onclick={() => aggiungi(t.tipo)}><Icona nome="piu" /></button>
              {/if}
            </div>
          {/each}
        </div>
      </section>

      {#if bozza}
        <section class="card sez" aria-label="Testo dell'email">
          <div class="sez-t"><h2 class="grow">Testo dell'email</h2><span class="spiega">{tipoScelto?.nome} · {titolo}</span></div>
          <div class="sez-c">
            <div class="ed-col">
              <div class="ed-s">
                <div class="field"><span class="lbl">Quando parte</span>
                  {#if bozza.tipo === 'PAGAMENTO'}
                    <span class="frase"><span class="unita">dopo</span><input class="inp m corto" type="number" min="1" max="365" bind:value={bozza.giorni} aria-label="Giorni dopo la scadenza" />
                      <span class="unita">giorni dalla scadenza, poi ogni {bozza.giorni || '…'}</span></span>
                    <span class="frase"><span class="unita">al massimo</span><input class="inp m corto" type="number" min="1" max="20" bind:value={bozza.maxInvii} aria-label="Quante volte al massimo" /><span class="unita">volte</span></span>
                  {:else if bozza.tipo === 'TESSERAMENTO_MANCANTE'}
                    <span class="frase"><span class="unita">ogni</span><input class="inp m corto" type="number" min="1" max="365" bind:value={bozza.giorni} aria-label="Ogni quanti giorni" />
                      <span class="unita">giorni, al massimo</span><input class="inp m corto" type="number" min="1" max="20" bind:value={bozza.maxInvii} aria-label="Quante volte al massimo" /><span class="unita">volte</span></span>
                  {:else}
                    <span class="frase"><input class="inp m corto" type="number" min="0" max="365" bind:value={bozza.giorni} aria-label="Giorni prima" /><span class="unita">giorni prima della scadenza</span></span>
                  {/if}
                </div>
                <label class="field"><span class="lbl">Oggetto</span><input class="inp" data-campo="oggetto" bind:value={bozza.oggetto} onfocus={(e) => (campo = e.currentTarget)} /></label>
                <label class="field"><span class="lbl">Testo</span><textarea class="inp testo" data-campo="testo" rows="8" bind:value={bozza.testo} onfocus={(e) => (campo = e.currentTarget)}></textarea></label>
                <div class="field"><span class="lbl">Inserisci un dato del cliente</span>
                  <div class="segnaposto" role="group" aria-label="Inserisci un dato del cliente">
                    {#each DATI as [t, tag] (tag)}<button type="button" title="Scrive {tag} dove c'è il cursore" onmousedown={(e) => e.preventDefault()} onclick={() => inserisci(tag)}>{t}</button>{/each}
                  </div>
                  <span class="muted small">Mentre scrivi il testo, clicca uno di questi bottoni per inserire un'etichetta. Ognuna verrà sostituita dall'informazione del cliente che riceverà l'email.</span>
                </div>
                <label class="riga-switch"><span>Acceso<br /><span class="muted small">Spento non parte, ma resta qui con il suo testo.</span></span>
                  <button type="button" class="switch" role="switch" aria-checked={bozza.attivo} aria-label="Promemoria acceso" onclick={() => (bozza!.attivo = !bozza!.attivo)}><span></span></button></label>
              </div>
              <div class="ed-s">
                <span class="lbl">Anteprima</span>
                <div class="anteprima"><span class="da">A: genitori di Giulia Rossi · da {societa?.nome ?? 'la società'}</span><span class="ogg">{anteprima(bozza.oggetto, bozza)}</span><span class="corpo">{anteprima(bozza.testo, bozza)}</span></div>
              </div>
            </div>
            <div class="row fine">
              <button class="btn btn-danger" type="button" onclick={togli}>Togli</button>
              <button class="btn btn-primary largo" type="button" disabled={!cambiata} onclick={salva}>Salva</button>
            </div>
          </div>
        </section>
      {/if}
    </div>

    <aside class="lato-p largo-p">
      <section class="card sez" id="imp-attesa" aria-label="Lista d'attesa">
        <div class="sez-t"><h2 class="grow">Avvisi della lista d'attesa</h2></div>
        <div class="riga-imp"><span class="t"><b>Chi è avvisato di un posto libero ha</b><span>Poi l'avviso passa al prossimo in lista.</span></span>
          <span class="ctl"><input class="inp num" type="number" min="1" max="30" disabled={!puoAttesa || !societa} value={societa?.giorniRispostaAttesa ?? ''}
            oninput={(e) => societa && (societa.giorniRispostaAttesa = Number(e.currentTarget.value))} aria-label="Giorni" /><span class="unita">giorni per rispondere</span></span></div>
        {#if societa && Number(societa.giorniRispostaAttesa) !== attesaOriginale}<div class="riga-imp"><button class="mini-btn pieno" type="button" onclick={salvaAttesa}>Salva</button></div>{/if}
      </section>

      <section class="card sez" aria-label="Ultime email">
        <div class="sez-t"><h2 class="grow">Ultime email</h2></div>
        <ul class="ultime">
          {#each pagina.ultimeEmail as e (e.id)}
            <li>
              <span class="q">{dataBreve(dataDi(e.creataIl))}<br />{oraDi(e.creataIl)}</span>
              <span class="grow">{#if e.personaId}<a href={`#/clienti/${e.personaId}`}>{e.oggetto}</a>{:else}{e.oggetto}{/if}<span class="dest">{e.destinatario ?? 'nessun indirizzo'}</span></span>
              <span class="pill {STATI[e.stato].classe} stato">{STATI[e.stato].nome.split(' ')[0]}{#if STATI[e.stato].nome.includes(' ')}<br />{STATI[e.stato].nome.split(' ').slice(1).join(' ')}{/if}</span>
            </li>
          {:else}
            <li class="muted small">Nessuna email finora.</li>
          {/each}
        </ul>
      </section>
    </aside>
  </div>
{/if}

<style>
  .stato-posta { display: flex; align-items: center; gap: var(--space-14); flex-wrap: wrap; padding: var(--space-14) var(--space-16); border-radius: var(--radius-lg); background: var(--warn-bg);
    border: 1px solid var(--warn-line); color: var(--warn); }
  .stato-posta .t { flex: 1; min-width: 260px; display: flex; flex-direction: column; gap: 2px; }
  .stato-posta .t span { font-size: var(--text-sm); }
  .posta-ok { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  /* i giorni intorno alla scadenza: le email sono pillole sulla linea */
  .lt { display: grid; grid-template-columns: 230px minmax(0, 1fr); row-gap: 6px; padding: var(--space-14) var(--space-16) var(--space-16); overflow-x: auto; }
  .asse { position: relative; height: 22px; font-family: var(--font-mono); font-size: 11px; color: var(--muted); }
  .asse span { position: absolute; transform: translateX(-50%); white-space: nowrap; }
  .asse span.zero { color: var(--ferma); font-weight: 700; }
  .tipo-n { display: flex; align-items: center; gap: var(--space-10); padding-right: var(--space-12); min-height: 52px; }
  .tipo-n .t { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
  .tipo-n .t b { font-size: var(--text-md); }
  .tipo-n .t span { font-size: var(--text-xs); color: var(--muted); }
  .binario { position: relative; height: 52px; min-width: 420px; border-top: 1px solid var(--line-soft); }
  .filo { position: absolute; left: 0; right: 0; top: 50%; height: 2px; background: var(--line); }
  .scad-v { position: absolute; top: 8px; bottom: 8px; width: 4px; margin-left: -2px; border-radius: 2px; background: var(--ferma); }
  .mail { position: absolute; top: 50%; transform: translate(-50%, -50%); display: flex; align-items: center; gap: 6px; height: 30px; padding: 0 10px 0 6px; border-radius: 999px;
    border: 1.5px solid var(--cloro); background: var(--surface); font: inherit; font-size: var(--text-xs); font-weight: 700; color: var(--cloro-ink); cursor: pointer; white-space: nowrap; z-index: 1; }
  .mail i { width: 18px; height: 18px; border-radius: 50%; background: var(--cloro); display: grid; place-items: center; color: var(--on-cloro); }
  .mail i :global(.ic) { width: 11px; height: 11px; }
  /* l'email del giorno stesso della scadenza parte subito dopo la linea rossa, senza coprirla */
  .mail.dopo { transform: translate(6px, -50%); }
  .mail[aria-pressed='true'] { background: var(--cloro); color: var(--on-cloro); }
  .mail.spenta { border-color: var(--line-strong); color: var(--muted); }
  .mail.spenta i { background: var(--line-strong); }
  /* spenta e scelta: piena ma grigia, con il testo leggibile */
  .mail.spenta[aria-pressed='true'] { background: var(--seg-fg); border-color: var(--seg-fg); color: var(--surface); }
  .mail.spenta[aria-pressed='true'] i { background: var(--surface); color: var(--seg-fg); }
  .piu-m { position: absolute; top: 50%; transform: translate(-50%, -50%); width: 26px; height: 26px; border-radius: 50%; border: 1.5px dashed var(--line-dashed); background: var(--surface);
    color: var(--muted); padding: 0; display: grid; place-items: center; cursor: pointer; }
  .piu-m :global(.ic) { width: 12px; height: 12px; }
  .ed-col { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-18, 18px); }
  .ed-s { display: flex; flex-direction: column; gap: var(--space-12); min-width: 0; }
  .frase { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .corto { width: 72px; height: 40px; text-align: center; }
  .testo { min-height: 160px; font-family: var(--font-body); line-height: 1.45; resize: vertical; padding: var(--space-10) var(--space-12); }
  .segnaposto { display: flex; flex-wrap: wrap; gap: var(--space-6); }
  .segnaposto button { height: 26px; padding: 0 9px; border-radius: 999px; border: 1px solid var(--line); background: var(--carta); font-family: var(--font-mono); font-size: var(--text-xs);
    color: var(--seg-fg); cursor: pointer; }
  .anteprima { flex: 1; display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-14); border-radius: var(--radius-md); background: var(--carta); font-size: var(--text-md); }
  .anteprima .ogg { font-weight: 700; }
  .anteprima .da { font-size: var(--text-xs); color: var(--muted); }
  .anteprima .corpo { white-space: pre-line; }
  .fine { justify-content: flex-end; }
  .largo { min-width: 140px; }
  .largo-p { flex-basis: 340px !important; max-width: 400px !important; }
  .ultime { list-style: none; margin: 0; padding: var(--space-4) var(--space-16) var(--space-10); }
  .ultime li { display: flex; gap: var(--space-10); align-items: center; padding: var(--space-8) 0; border-top: 1px solid var(--line-soft); font-size: var(--text-sm); }
  .ultime li:first-child { border-top: none; }
  .ultime .q { font-family: var(--font-mono); color: var(--muted); min-width: 64px; font-size: var(--text-xs); }
  .ultime .grow { min-width: 0; display: flex; flex-direction: column; }
  .ultime .dest { font-size: var(--text-xs); color: var(--muted); overflow: hidden; text-overflow: ellipsis; }
  /* stato su due righe, così la colonna del destinatario ha più spazio */
  .stato { height: auto; padding: 3px 10px; line-height: 1.15; text-align: center; border-radius: 10px; }
  @media (max-width: 900px) { .ed-col { grid-template-columns: 1fr; } .lt { grid-template-columns: 1fr; } }
</style>
