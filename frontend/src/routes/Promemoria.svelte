<script lang="ts">
  import BarraSalva from '../components/BarraSalva.svelte';
  import Conferma from '../components/Conferma.svelte';
  import Fumetto from '../components/Fumetto.svelte';
  import Icona from '../components/Icona.svelte';
  import RiquadroAcceso from '../components/RiquadroAcceso.svelte';
  import StatoVuoto from '../components/StatoVuoto.svelte';
  import { api, messaggioErrore, type DatiSocieta, type PaginaPromemoria, type RegolaPromemoria, type StatoEmail, type TipoPromemoria } from '../lib/api';
  import { creaCoda } from '../lib/coda';
  import { quanti } from '../lib/plurali';
  import { euro } from '../lib/numeri';
  import { dataBreve, dataDi, iso, oraDi } from '../lib/date';
  import { pillole, ripetuto, sottoTipo, statoTipo, TIPI } from '../lib/promemoria';
  import { contaModifiche, interoFra } from '../lib/salvataggio';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * I promemoria email (ADR 0024): ogni email è una pillola sulla linea dei giorni intorno alla scadenza; toccala per
   * cambiarne giorno e testo, o usa il «+» per aggiungerne una. L'interruttore di ogni tipo agisce subito, con una
   * chiamata sola. Il testo si cambia in pagina, con «Salva il promemoria». I giorni della lista d'attesa sono
   * l'unica impostazione di pagina: passano dalla barra di salvataggio.
   */
  function ogni(r: Pick<RegolaPromemoria, 'tipo' | 'giorni' | 'maxInvii'>): string {
    const volte = (r.maxInvii ?? 1) === 1 ? 'una volta sola' : `al massimo ${r.maxInvii} volte`;
    return r.tipo === 'PAGAMENTO' ? `${quanti(Number(r.giorni), 'giorno', 'giorni')} dopo la scadenza, poi ${Number(r.giorni) === 1 ? 'ogni giorno' : `ogni ${r.giorni}`}, ${volte}` : `${Number(r.giorni) === 1 ? 'ogni giorno' : `ogni ${r.giorni} giorni`}, ${volte}`;
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
  /** I giorni per rispondere, come si scrivono nel campo: si salvano con la barra. */
  let attesa = $state('');
  let scelta = $state('');
  let bozza = $state<RegolaPromemoria | null>(null);
  let errore = $state('');
  let messaggio = $state('');
  let lavora = $state(false);
  const puoAttesa = $derived(sessione.puo('IMPOSTAZIONI'));
  const inCoda = creaCoda();

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
  api.societa().then((s) => { societa = s; attesa = String(s.giorniRispostaAttesa); }).catch(() => {});

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
  const giorniOk = $derived(!!bozza && interoFra(bozza.giorni, ripetuto(bozza.tipo) ? 1 : 0, 365));
  const volteOk = $derived(!!bozza && (!ripetuto(bozza.tipo) || interoFra(bozza.maxInvii, 1, 20)));
  const daCompletare = $derived(!!bozza && /…|\.\.\./.test(`${bozza.oggetto}\n${bozza.testo}`));
  const corpo = (r: RegolaPromemoria) => ({ giorni: Number(r.giorni), maxInvii: r.maxInvii == null ? null : Number(r.maxInvii), attivo: r.attivo, oggetto: r.oggetto, testo: r.testo });
  const salva = () => esegui(() => api.modificaPromemoria(bozza!.id, corpo(bozza!)), 'Promemoria salvato.');
  async function togli() {
    if (!bozza) return;
    const id = bozza.id;
    scelta = '';
    await esegui(() => api.eliminaPromemoria(id), 'Promemoria eliminato.');
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

  /**
   * L'interruttore di un tipo: subito, con una chiamata sola per tutte le email del tipo. La risposta sono tutte le
   * regole: si aggiorna la linea senza ricaricare la bozza (giorni, oggetto e testo non salvati restano).
   */
  async function interruttore(tipo: TipoPromemoria) {
    if (!pagina) return;
    errore = '';
    messaggio = '';
    const accendi = statoTipo(tipo, pagina.regole) !== 'acceso';
    pagina.regole = pagina.regole.map((r) => (r.tipo === tipo ? { ...r, attivo: accendi } : r));
    if (bozza?.tipo === tipo) bozza.attivo = accendi;
    try {
      const nuove = await inCoda(() => api.accendiTipoPromemoria(tipo, accendi));
      if (Array.isArray(nuove)) {
        pagina.regole = nuove;
        const mia = bozza ? nuove.find((r) => r.id === bozza!.id) : null;
        if (bozza && mia) bozza.attivo = mia.attivo;
      }
    } catch (e) {
      errore = messaggioErrore(e);
      try {
        const p = await api.promemoria();
        pagina.regole = p.regole;
        const mia = bozza ? p.regole.find((r) => r.id === bozza!.id) : null;
        if (bozza && mia) bozza.attivo = mia.attivo;
      } catch { /* resta com'è */ }
    }
  }

  /** L'interruttore del riquadro dell'editor: cambia subito lo stato salvato del promemoria aperto, non la bozza. */
  async function accendiPromemoria(attivo: boolean) {
    const salvato = originale;
    if (!salvato || !bozza) return;
    errore = '';
    messaggio = '';
    try {
      const nuovo = await inCoda(() => api.modificaPromemoria(salvato.id, corpo({ ...salvato, attivo })));
      pagina!.regole = pagina!.regole.map((r) => (r.id === nuovo.id ? nuovo : r));
      if (bozza?.id === nuovo.id) bozza.attivo = nuovo.attivo;
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  async function eseguiOra() {
    errore = '';
    messaggio = '';
    lavora = true;
    try {
      const r = await api.eseguiPromemoria();
      // si rileggono solo le ultime email: la bozza aperta non si tocca
      const p = await api.promemoria();
      if (pagina) pagina.ultimeEmail = p.ultimeEmail;
      messaggio = r.email === 0 ? 'Oggi non c\'era nessun promemoria da preparare.'
        : `${r.email} promemoria: ${r.inviate} ${r.inviate === 1 ? 'inviato' : 'inviati'}${r.nonInviate ? `, ${r.nonInviate} registrati senza partire (posta non configurata)` : ''}${r.senzaEmail ? `, ${r.senzaEmail} senza email` : ''}${r.errori ? `, ${r.errori} con errore` : ''}.`;
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      lavora = false;
    }
  }

  // ── La linea dei giorni ───────────────────────────────────────────────
  const tutti = $derived((pagina?.regole ?? []).flatMap((r) => pillole(r.id === scelta && bozza ? bozza : r).map((p) => p.g)));
  const minimo = $derived(Math.min(-35, ...tutti.map((g) => Math.floor((g - 5) / 7) * 7)));
  const massimo = $derived(Math.max(14, ...tutti.map((g) => Math.ceil((g + 3) / 7) * 7)));
  const x = (g: number) => `${3 + ((g - minimo) / (massimo - minimo)) * 94}%`;
  const tacche = $derived(Array.from({ length: Math.floor((massimo - minimo) / 7) + 1 }, (_, i) => minimo + i * 7));
  /** Dove mettere il «+»: in un punto libero della riga, ad almeno 7 giorni (≈ 77 px) da ogni pillola, prima della scadenza. */
  function piuX(tipo: TipoPromemoria) {
    const usati = (pagina?.regole ?? []).filter((r) => r.tipo === tipo).flatMap((r) => pillole(r).map((p) => p.g));
    const libero = (g: number) => usati.every((u) => Math.abs(u - g) >= 7);
    const candidati = [-21, -28, -14, -35, -7, ...Array.from({ length: Math.max(0, -minimo - 2) }, (_, i) => -(i + 1))];
    const g = candidati.find((c) => c > minimo + 1 && libero(c));
    return x(g ?? minimo + 2);
  }

  /** Le linee dei giorni scorrono insieme: i nomi dei tipi restano fermi, l'asse e le linee si muovono di pari passo. */
  let sincronizzando = false;
  function sincronizza(e: Event) {
    if (sincronizzando) return;
    sincronizzando = true;
    const da = e.currentTarget as HTMLElement;
    da.closest('.lt')?.querySelectorAll<HTMLElement>('.scorri').forEach((el) => { if (el !== da) el.scrollLeft = da.scrollLeft; });
    requestAnimationFrame(() => (sincronizzando = false));
  }

  // ── Il testo ──────────────────────────────────────────────────────────
  const tipoScelto = $derived(TIPI.find((t) => t.tipo === bozza?.tipo));
  const titolo = $derived(!bozza ? '' : ripetuto(bozza.tipo) ? ogni(bozza)
    : Number(bozza.giorni) === 0 ? 'il giorno della scadenza' : `${quanti(Number(bozza.giorni), 'giorno', 'giorni')} prima della scadenza`);
  function anteprima(t: string, r: RegolaPromemoria): string {
    const g = r.tipo === 'TESSERAMENTO_MANCANTE' ? 0 : r.tipo === 'PAGAMENTO' ? -Number(r.giorni) : Number(r.giorni);
    const scad = iso(new Date(Date.now() + g * 86400000));
    return t.replaceAll('{nome}', 'Giulia').replaceAll('{cognome}', 'Rossi').replaceAll('{scadenza}', dataBreve(scad)).replaceAll('{giorni}', String(Math.max(0, g)))
      .replaceAll('{importo}', euro(60)).replaceAll('{corso}', 'Delfino Giallo').replaceAll('{stagione}', '2026/27').replaceAll('{societa}', societa?.nome ?? 'la società');
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

  // ── I giorni della lista d'attesa: l'unica impostazione di pagina, con la barra ──
  const attesaOk = $derived(interoFra(attesa, 1, 30));
  const modificheAttesa = $derived(societa ? contaModifiche(String(societa.giorniRispostaAttesa), attesa) : 0);
  async function salvaAttesa() {
    errore = '';
    messaggio = '';
    try {
      societa = await api.modificaSocieta({ ...societa!, giorniRispostaAttesa: Number(attesa) });
      attesa = String(societa.giorniRispostaAttesa);
      messaggio = 'Giorni per rispondere salvati.';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
</script>

{#snippet spiega(corta: string, tema: string, lunga: string)}
  <span class="corta"><span class="t-c">{corta}</span><Fumetto {tema}>{lunga}</Fumetto></span>
{/snippet}

{#snippet manda()}
  <button type="button" class="btn btn-sm btn-secondary nowrap" disabled={lavora} onclick={eseguiOra}><Icona nome="promemoria" />{lavora ? 'Mando…' : 'Manda i promemoria di oggi'}</button>
{/snippet}

{#if pagina && !pagina.postaConfigurata}
  <div class="stato-posta" role="status">
    <Icona nome="avviso" />
    <span class="corta grow"><b class="t-c">Il server di posta non è configurato</b>
      <Fumetto tema="la posta">Le email si preparano ogni giorno ({pagina.orario}) e si vedono qui sotto, ma non arrivano a nessuno. Lo configura il tecnico sul server.</Fumetto></span>
    {@render manda()}
  </div>
{:else if pagina}
  <div class="posta-ok"><span class="pill passa">Posta configurata</span><span class="muted small grow t-c">Automatici: {pagina.orario}</span>
    {@render manda()}</div>
{/if}
{#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

{#if pagina}
  <div class="colonne-p">
    <div class="princ">
      <section class="card sez" aria-label="Quando partono">
        <div class="sez-t"><h2 class="grow">Quando partono</h2><span class="spiega una-riga">giorni rispetto alla scadenza</span></div>
        <div class="lt" style="--larga: {(massimo - minimo) * 11}px">
          <span class="vuoto"></span>
          <div class="scorri" onscroll={sincronizza}><div class="asse" aria-hidden="true">{#each tacche as g (g)}<span class:zero={g === 0} style="left: {x(g)}">{g === 0 ? 'scade' : g > 0 ? `+${g}` : g}</span>{/each}</div></div>
          {#each TIPI as t (t.tipo)}
            {@const regole = pagina.regole.filter((r) => r.tipo === t.tipo)}
            {@const stato = statoTipo(t.tipo, pagina.regole)}
            <span class="tipo-n"><span class="t"><b>{t.nome}</b><span>{sottoTipo(t.tipo, pagina.regole)}</span></span>
              <button type="button" class="switch tap44" role="switch" aria-checked={stato === 'acceso'} aria-label="Acceso: «{t.nome}»" disabled={stato === 'vuoto'} onclick={() => interruttore(t.tipo)}><span></span></button></span>
            <div class="scorri" onscroll={sincronizza}><div class="binario">
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
            </div></div>
          {/each}
        </div>
      </section>

      {#if bozza}
        <section class="card sez" aria-label="Testo dell'email">
          <div class="sez-t"><h2>Testo dell'email</h2><span class="spiega una-riga">{tipoScelto?.nome}</span>
            <Fumetto tema="quando parte">Quando parte: {titolo}.</Fumetto></div>
          <div class="sez-c">
            <div class="ed-col">
              <div class="ed-s">
                <RiquadroAcceso acceso={originale?.attivo ?? bozza.attivo} onacceso={accendiPromemoria} />
                <div class="field"><span class="lbl">Quando parte</span>
                  {#if bozza.tipo === 'PAGAMENTO'}
                    <span class="frase"><span class="unita">dopo</span><input class="inp m corto" type="number" min="1" max="365" bind:value={bozza.giorni} aria-invalid={!giorniOk} aria-label="Giorni dopo la scadenza" />
                      <span class="unita">giorni dalla scadenza, poi ogni {bozza.giorni || '…'}</span></span>
                    <span class="frase"><span class="unita">al massimo</span><input class="inp m corto" type="number" min="1" max="20" bind:value={bozza.maxInvii} aria-invalid={!volteOk} aria-label="Quante volte al massimo" /><span class="unita">volte</span></span>
                  {:else if bozza.tipo === 'TESSERAMENTO_MANCANTE'}
                    <span class="frase"><span class="unita">ogni</span><input class="inp m corto" type="number" min="1" max="365" bind:value={bozza.giorni} aria-invalid={!giorniOk} aria-label="Ogni quanti giorni" />
                      <span class="unita">giorni, al massimo</span><input class="inp m corto" type="number" min="1" max="20" bind:value={bozza.maxInvii} aria-invalid={!volteOk} aria-label="Quante volte al massimo" /><span class="unita">volte</span></span>
                  {:else}
                    <span class="frase"><input class="inp m corto" type="number" min="0" max="365" bind:value={bozza.giorni} aria-invalid={!giorniOk} aria-label="Giorni prima" /><span class="unita">giorni prima della scadenza</span></span>
                  {/if}
                </div>
                <label class="field"><span class="lbl">Oggetto</span><input class="inp" data-campo="oggetto" bind:value={bozza.oggetto} onfocus={(e) => (campo = e.currentTarget)} /></label>
                <label class="field"><span class="lbl">Testo</span><textarea class="inp testo" data-campo="testo" rows="8" bind:value={bozza.testo} onfocus={(e) => (campo = e.currentTarget)}></textarea></label>
                <div class="field"><span class="lbl lbl-i" id="lbl-dati">Inserisci un dato del cliente
                  <Fumetto tema="i dati del cliente">Mentre scrivi il testo, clicca uno di questi bottoni per inserire un'etichetta. Ognuna verrà sostituita dall'informazione del cliente che riceverà l'email.</Fumetto></span>
                  <div class="segnaposto" role="group" aria-labelledby="lbl-dati">
                    {#each DATI as [t, tag] (tag)}<button type="button" title="Scrive {tag} dove c'è il cursore" onmousedown={(e) => e.preventDefault()} onclick={() => inserisci(tag)}>{t}</button>{/each}
                  </div>
                </div>
                {#if daCompletare}
                  <div class="da-completare"><Icona nome="avviso" /><span class="corta"><span class="t-c">Il testo ha ancora dei «…» da completare.</span>
                    <Fumetto tema="i puntini nel testo">Un promemoria nuovo parte con un testo di esempio: completa i «…» prima di accenderlo. Il salvataggio e l'accensione non sono bloccati.</Fumetto></span></div>
                {/if}
                <span class="corta"><span class="t-c muted small">Spento non parte.</span><Fumetto tema="il promemoria spento">Spento non parte, ma resta qui con il suo testo.</Fumetto></span>
              </div>
              <div class="ed-s">
                <span class="lbl">Anteprima</span>
                <div class="anteprima"><span class="da">A: genitori di Giulia Rossi · da {societa?.nome ?? 'la società'}</span><span class="ogg">{anteprima(bozza.oggetto, bozza)}</span><span class="corpo">{anteprima(bozza.testo, bozza)}</span></div>
              </div>
            </div>
            <div class="row fine">
              <Conferma class="btn btn-danger" domanda="Elimina?" onclick={togli}>Elimina il promemoria</Conferma>
              <button class="btn btn-primary largo" type="button" disabled={!cambiata || !giorniOk || !volteOk} onclick={salva}>Salva il promemoria</button>
            </div>
          </div>
        </section>
      {/if}
    </div>

    <aside class="lato-p largo-p">
      <section class="card sez" id="imp-attesa" aria-label="Lista d'attesa">
        <div class="sez-t"><h2 class="grow">Avvisi della lista d'attesa</h2></div>
        <div class="riga-imp"><span class="t"><b>Chi è avvisato ha tempo</b>{@render spiega('Poi passa al prossimo in lista.', 'i giorni per rispondere', 'Chi è avvisato di un posto libero ha tempo questi giorni per rispondere. Poi l\'avviso passa al prossimo in lista.')}</span>
          {#if puoAttesa}
            <span class="ctl"><input class="inp num" type="number" min="1" max="30" disabled={!societa} bind:value={attesa} aria-invalid={!attesaOk} aria-label="Giorni per rispondere" /><span class="unita">giorni</span></span>
          {:else}
            <span class="ctl"><b class="mono">{societa?.giorniRispostaAttesa ?? '—'}</b><span class="unita">giorni</span></span>
          {/if}</div>
      </section>

      <section class="card sez" aria-label="Ultime email">
        <div class="sez-t"><h2 class="grow">Ultime email</h2></div>
        <ul class="ultime">
          {#each pagina.ultimeEmail as e (e.id)}
            <li>
              <span class="q"><span>{dataBreve(dataDi(e.creataIl))}</span><span>{oraDi(e.creataIl)}</span></span>
              <span class="oggetto">{#if e.personaId}<a href={`#/clienti/${e.personaId}`}>{e.oggetto}</a>{:else}<span class="ogg-t">{e.oggetto}</span>{/if}<span class="dest">{e.destinatario ?? 'nessun indirizzo'}</span></span>
              <span class="pill {STATI[e.stato].classe}">{STATI[e.stato].nome}</span>
            </li>
          {:else}
            <StatoVuoto come="li" testo="Nessuna email finora." />
          {/each}
        </ul>
      </section>
    </aside>
  </div>

  {#if puoAttesa}<BarraSalva modifiche={modificheAttesa} valida={attesaOk} onannulla={() => (attesa = String(societa!.giorniRispostaAttesa))} onsalva={salvaAttesa} />{/if}
{/if}

<style>
  .stato-posta { display: flex; align-items: center; gap: var(--space-10) var(--space-14); flex-wrap: wrap; padding: var(--space-10) var(--space-16); border-radius: var(--radius-lg); background: var(--warn-bg);
    border: 1px solid var(--warn-line); color: var(--warn); }
  .stato-posta :global(.ic) { width: 18px; height: 18px; flex-shrink: 0; }
  .posta-ok { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .grow { flex: 1 1 auto; min-width: 0; }
  /* la riga corta: non va a capo, la «i» sta in fondo e non allarga la riga; a 320 px il testo si accorcia con i puntini */
  .corta { display: flex; align-items: center; gap: var(--space-4); white-space: nowrap; min-width: 0; }
  .t-c { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .una-riga { white-space: nowrap; min-width: 0; overflow: hidden; text-overflow: ellipsis; }
  .lbl-i { display: flex; align-items: center; gap: var(--space-4); white-space: nowrap; }
  /* i giorni intorno alla scadenza: le email sono pillole sulla linea */
  .lt { display: grid; grid-template-columns: 252px minmax(0, 1fr); row-gap: var(--space-6); padding: var(--space-14) var(--space-16) var(--space-16); }
  .scorri { min-width: 0; overflow-x: auto; scrollbar-width: thin; }
  .asse { position: relative; height: 22px; width: 100%; min-width: var(--larga); font-family: var(--font-mono); font-size: var(--text-2xs); color: var(--muted); }
  .asse span { position: absolute; transform: translateX(-50%); white-space: nowrap; }
  .asse span.zero { color: var(--ferma); font-weight: 700; }
  .tipo-n { display: flex; align-items: center; justify-content: space-between; gap: var(--space-8); padding-right: var(--space-12); min-height: 56px; min-width: 0; }
  .tipo-n .t { display: flex; flex-direction: column; gap: var(--space-2); min-width: 0; }
  .tipo-n .t b { font-size: var(--text-md); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .tipo-n .t span { font-size: var(--text-xs); color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .tipo-n .switch { flex-shrink: 0; }
  .binario { position: relative; height: 56px; width: 100%; min-width: var(--larga); border-top: 1px solid var(--line-soft); }
  .filo { position: absolute; left: 0; right: 0; top: 50%; height: 2px; background: var(--line); }
  .scad-v { position: absolute; top: var(--space-8); bottom: var(--space-8); width: 4px; margin-left: -2px; border-radius: 2px; background: var(--ferma); }
  .mail { position: absolute; top: 50%; transform: translate(-50%, -50%); display: flex; align-items: center; gap: var(--space-6); padding: var(--space-4) var(--space-10) var(--space-4) var(--space-4); border-radius: var(--radius-pill);
    border: 1.5px solid var(--cloro); background: var(--surface); font: inherit; font-size: var(--text-xs); font-weight: 700; color: var(--cloro-ink); cursor: pointer; white-space: nowrap; z-index: 1; }
  /* l'area di tocco è di 44 px anche se la pillola è più bassa */
  .mail::after, .piu-m::after { content: ''; position: absolute; left: 0; right: 0; top: 50%; height: 44px; transform: translateY(-50%); }
  .mail i { width: 20px; height: 20px; border-radius: 50%; background: var(--cloro); display: grid; place-items: center; color: var(--on-cloro); }
  .mail i :global(.ic) { width: 12px; height: 12px; }
  /* l'email del giorno stesso della scadenza parte subito dopo la linea rossa, senza coprirla */
  .mail.dopo { transform: translate(var(--space-6), -50%); }
  .mail[aria-pressed='true'] { background: var(--cloro); color: var(--on-cloro); }
  .mail.spenta { border-color: var(--line-strong); color: var(--muted); }
  .mail.spenta i { background: var(--line-strong); }
  /* spenta e scelta: piena ma grigia, con il testo leggibile */
  .mail.spenta[aria-pressed='true'] { background: var(--seg-fg); border-color: var(--seg-fg); color: var(--surface); }
  .mail.spenta[aria-pressed='true'] i { background: var(--surface); color: var(--seg-fg); }
  .piu-m { position: absolute; top: 50%; transform: translate(-50%, -50%); width: 28px; height: 28px; border-radius: 50%; border: 1.5px dashed var(--line-dashed); background: var(--surface);
    color: var(--muted); padding: 0; display: grid; place-items: center; cursor: pointer; }
  .piu-m::after { left: -10px; right: -10px; }
  .piu-m :global(.ic) { width: 12px; height: 12px; }
  .ed-col { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-20); }
  .ed-s { display: flex; flex-direction: column; gap: var(--space-12); min-width: 0; }
  .frase { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .corto { width: 76px; height: 44px; text-align: center; }
  .testo { min-height: 160px; font-family: var(--font-body); line-height: 1.45; resize: vertical; padding: var(--space-10) var(--space-12); }
  .segnaposto { display: flex; flex-wrap: wrap; gap: var(--space-8); }
  .segnaposto button { position: relative; height: 36px; padding: 0 var(--space-12); border-radius: var(--radius-pill); border: 1px solid var(--line); background: var(--carta); font-family: var(--font-mono); font-size: var(--text-xs);
    color: var(--seg-fg); cursor: pointer; white-space: nowrap; }
  .segnaposto button::after { content: ''; position: absolute; left: 0; right: 0; top: 50%; height: 44px; transform: translateY(-50%); }
  .da-completare { display: flex; align-items: center; gap: var(--space-8); padding: var(--space-8) var(--space-10); border-radius: var(--radius-sm); background: var(--warn-bg); color: var(--warn); font-size: var(--text-sm); font-weight: 600; min-width: 0; }
  .da-completare :global(.ic) { width: 16px; height: 16px; flex-shrink: 0; }
  .anteprima { flex: 1; display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-14); border-radius: var(--radius-md); background: var(--carta); font-size: var(--text-md); }
  .anteprima .ogg { font-weight: 700; }
  .anteprima .da { font-size: var(--text-xs); color: var(--muted); }
  .anteprima .corpo { white-space: pre-line; }
  .fine { justify-content: flex-end; flex-wrap: wrap; }
  .fine :global(.btn) { white-space: nowrap; }
  .largo-p { min-width: 0; flex-basis: min(340px, 100%) !important; max-width: 400px !important; }
  .ultime { list-style: none; margin: 0; padding: var(--space-4) var(--space-16) var(--space-10); }
  .ultime li { display: flex; gap: var(--space-10); align-items: center; padding: var(--space-8) 0; font-size: var(--text-sm); }
  .ultime li:not(.stato-vuoto) { border-top: 1px solid var(--line-soft); }
  .ultime li:not(.stato-vuoto):first-child { border-top: none; }
  .ultime .q { display: flex; flex-direction: column; font-family: var(--font-mono); color: var(--muted); min-width: 64px; font-size: var(--text-xs); white-space: nowrap; }
  .ultime .oggetto { flex: 1 1 auto; min-width: 0; display: flex; flex-direction: column; }
  .ultime .oggetto a, .ultime .ogg-t { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .ultime .dest { font-size: var(--text-xs); color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .ultime .oggetto a { position: relative; }
  .ultime .oggetto a::after { content: ''; position: absolute; left: 0; right: 0; top: 50%; height: 44px; transform: translateY(-50%); }
  .ultime .pill { flex-shrink: 0; white-space: nowrap; }
  @media (max-width: 360px) { .stato-posta { padding-inline: var(--space-12); } .stato-posta :global(.ic) { display: none; } .stato-posta b { font-size: var(--text-sm); } }
  @media (max-width: 900px) { .ed-col { grid-template-columns: 1fr; } .lt { grid-template-columns: 1fr; } .lt .vuoto { display: none; } }
</style>
