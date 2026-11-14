<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import LineaStagione from '../components/impostazioni/LineaStagione.svelte';
  import { api, messaggioErrore, type DatiSocieta, type GruppoClienti, type MembroGruppo, type NuovaStagione, type Stagione } from '../lib/api';
  import { dataBreve, iso } from '../lib/date';
  import { chiudiNovita, dataIndice, novitaChiusa } from '../lib/impostazioni';
  import { euro } from '../lib/listino';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Dati, stagioni e gruppi (ADR 0018, 0024): chi è la società (compare su ricevute ed email), le sue stagioni con la
   * quota associativa e i gruppi di clienti. Le modifiche ai dati e alla stagione scelta si salvano tutte insieme.
   */
  let dati = $state<DatiSocieta | null>(null);
  let originale = $state('');
  let stagioni = $state<Stagione[]>([]);
  let offerte = $state<Record<string, number>>({});
  let gruppi = $state<GruppoClienti[]>([]);
  let errore = $state('');
  let messaggio = $state('');
  const oggi = iso(new Date());

  type BozzaStagione = { nome: string; dal: string; al: string; quota: string; quotaRimborsabile: boolean };
  let scelta = $state<string | null>(null);
  let bozza = $state<BozzaStagione | null>(null);
  let bozzaOriginale = '';
  let tutte = $state(false);

  const NOVITA = 'impostazioni-2026-11';
  let novita = $state(!novitaChiusa(NOVITA, sessione.utente?.accountId ?? ''));

  async function carica() {
    try {
      let d: DatiSocieta;
      [d, stagioni, gruppi] = await Promise.all([api.societa(), api.stagioni(), api.gruppi()]);
      dati = d;
      originale = JSON.stringify(d);
      if (!scelta || !stagioni.some((s) => s.id === scelta)) scegli(stagioni.find((s) => s.dal <= oggi && oggi <= s.al) ?? stagioni[0] ?? null);
      else scegli(stagioni.find((s) => s.id === scelta) ?? null);
      // quante offerte ha il listino di ogni stagione (se si può vedere)
      if (sessione.puo('IMPOSTAZIONI', 'CLIENTI'))
        for (const s of stagioni) api.listino(s.id).then((l) => (offerte[s.id] = l.offerte.length)).catch(() => {});
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a caricare le impostazioni.');
    }
  }
  carica();

  function scegli(s: Stagione | null) {
    scelta = s?.id ?? null;
    bozza = s ? { nome: s.nome, dal: s.dal, al: s.al, quota: s.quota == null ? '' : String(s.quota), quotaRimborsabile: s.quotaRimborsabile } : null;
    bozzaOriginale = JSON.stringify(bozza);
  }

  const datiCambiati = $derived(!!dati && JSON.stringify(dati) !== originale);
  const stagioneCambiata = $derived(!!bozza && JSON.stringify(bozza) !== bozzaOriginale);
  const modifiche = $derived(Number(datiCambiati) + Number(stagioneCambiata));

  async function salva() {
    errore = '';
    messaggio = '';
    try {
      if (datiCambiati) await api.modificaSocieta({ ...dati!, giorniAnticipo: Number(dati!.giorniAnticipo), giorniRispostaAttesa: Number(dati!.giorniRispostaAttesa) });
      if (stagioneCambiata && bozza) {
        const corpo: NuovaStagione = { nome: bozza.nome, dal: bozza.dal, al: bozza.al, quota: bozza.quota === '' ? null : Number(bozza.quota), quotaRimborsabile: bozza.quotaRimborsabile };
        if (scelta === 'nuova') {
          const s = await api.creaStagione(corpo);
          scelta = s.id;
        } else if (scelta) await api.modificaStagione(scelta, corpo);
      }
      await carica();
      messaggio = 'Modifiche salvate.';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  function annulla() {
    dati = JSON.parse(originale);
    scegli(stagioni.find((s) => s.id === scelta) ?? stagioni[0] ?? null);
  }

  // ── Stagioni ──────────────────────────────────────────────────────────
  const ordinate = $derived([...stagioni].sort((a, b) => a.dal.localeCompare(b.dal)));
  /** Si vedono la stagione finita più recente, quella in corso e le future (tutte, a richiesta). */
  const mostrate = $derived.by(() => {
    if (tutte) return ordinate;
    const i = Math.max(0, ordinate.findIndex((s) => s.al >= oggi) - 1);
    return ordinate.slice(i, i + 3);
  });
  const inCorso = $derived(stagioni.find((s) => s.dal <= oggi && oggi <= s.al) ?? null);
  const stato = (s: Stagione) => (oggi < s.dal ? { nome: 'futura', cls: 'cloro', card: '' } : oggi > s.al ? { nome: 'finita', cls: '', card: 'finita' } : { nome: 'in corso', cls: 'passa', card: 'ora' });
  /** Il giorno da cui si propone il tesseramento della stagione. */
  const tesseramentoDa = (s: Stagione) => dataBreve(dataIndice(s.dal, -Number(dati?.giorniAnticipo ?? 0)));

  const piuUnGiorno = (d: string) => dataIndice(d, 1);
  const piuUnAnno = (d: string) => `${Number(d.slice(0, 4)) + 1}${d.slice(4)}`.replace(/-02-29$/, '-02-28');
  function nomeSuccessivo(n: string): string {
    const m = /^(\d{4})\/(\d{2})$/.exec(n);
    if (m) return `${Number(m[1]) + 1}/${String((Number(m[2]) + 1) % 100).padStart(2, '0')}`;
    return /^\d{4}$/.test(n) ? String(Number(n) + 1) : '';
  }
  const ultima = $derived(ordinate.at(-1));
  const nomeNuova = $derived(ultima ? nomeSuccessivo(ultima.nome) : '');
  function nuovaStagione() {
    scelta = 'nuova';
    if (ultima) bozza = { nome: nomeNuova, dal: piuUnGiorno(ultima.al), al: piuUnAnno(ultima.al), quota: ultima.quota == null ? '' : String(ultima.quota), quotaRimborsabile: ultima.quotaRimborsabile };
    else {
      const a = new Date().getMonth() >= 8 ? new Date().getFullYear() : new Date().getFullYear() - 1;
      bozza = { nome: `${a}/${String((a + 1) % 100).padStart(2, '0')}`, dal: `${a}-09-01`, al: `${a + 1}-08-31`, quota: '', quotaRimborsabile: false };
    }
    bozzaOriginale = '';
  }
  async function eliminaStagione() {
    const s = stagioni.find((x) => x.id === scelta);
    if (!s || !confirm(`Eliminare la stagione ${s.nome}? I tesseramenti già registrati restano com'erano.`)) return;
    try {
      await api.eliminaStagione(s.id);
      scelta = null;
      await carica();
      messaggio = `Stagione ${s.nome} eliminata.`;
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  // ── Gruppi ────────────────────────────────────────────────────────────
  type BozzaGruppo = { id: string | null; nome: string; descrizione: string; attivo: boolean; conPrecedenza: boolean; giorni: string; dichiarabile: boolean; frase: string };
  let gruppo = $state<BozzaGruppo | null>(null);
  const nuovoGruppo = (): BozzaGruppo => ({ id: null, nome: '', descrizione: '', attivo: true, conPrecedenza: false, giorni: '', dichiarabile: false, frase: '' });
  const bozzaDi = (g: GruppoClienti): BozzaGruppo => ({
    id: g.id, nome: g.nome, descrizione: g.descrizione ?? '', attivo: g.attivo, conPrecedenza: g.precedenza != null,
    giorni: g.giorniPerPagare == null ? '' : String(g.giorniPerPagare), dichiarabile: g.dichiarabile, frase: g.fraseDichiarazione ?? '',
  });
  /** I gruppi con precedenza, nel loro ordine (1 = la più forte). */
  const conPrecedenza = $derived(gruppi.filter((g) => g.precedenza != null).sort((a, b) => a.precedenza! - b.precedenza!));
  async function sposta(i: number, verso: -1 | 1) {
    const ordine = conPrecedenza.map((g) => g.id);
    const j = i + verso;
    if (j < 0 || j >= ordine.length) return;
    [ordine[i], ordine[j]] = [ordine[j], ordine[i]];
    errore = '';
    try {
      await api.ordinaPrecedenze(ordine);
      gruppi = await api.gruppi();
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  let aperto = $state('');
  let membri = $state<MembroGruppo[]>([]);
  async function salvaGruppo() {
    const g = gruppo!;
    const descrizione = g.descrizione.trim() || null;
    const vantaggi = {
      conPrecedenza: g.conPrecedenza, giorniPerPagare: g.giorni.trim() ? Number(g.giorni) : null,
      dichiarabile: g.dichiarabile, fraseDichiarazione: g.dichiarabile ? g.frase.trim() || null : null,
    };
    errore = '';
    try {
      if (g.id) await api.modificaGruppo(g.id, { nome: g.nome, descrizione, attivo: g.attivo, ...vantaggi });
      else await api.creaGruppo({ nome: g.nome, descrizione, ...vantaggi });
      gruppi = await api.gruppi();
      messaggio = g.id ? 'Gruppo salvato.' : `Gruppo «${g.nome.trim()}» creato.`;
      gruppo = null;
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  async function apriMembri(g: GruppoClienti) {
    if (aperto === g.id) return void (aperto = '');
    aperto = g.id;
    membri = [];
    try {
      membri = await api.membriGruppo(g.id);
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  // ── Ricevuta ──────────────────────────────────────────────────────────
  const luogo = $derived(dati ? [[dati.cap, dati.citta].filter(Boolean).join(' '), dati.provincia ? `(${dati.provincia})` : ''].filter(Boolean).join(' ') : '');
</script>

{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
{#if messaggio && !modifiche}<p class="msg ok" role="status">{messaggio}</p>{/if}

{#if novita}
  <div class="novita" role="status">
    <span class="t"><b>Novità dell'ultimo aggiornamento</b><span>Le impostazioni sono state riordinate. «Lista d'attesa» ora sta nei Promemoria email, «una famiglia paga per più persone» nei Metodi di pagamento, e Account e ruoli sono insieme. La ricerca in alto trova tutto da qualsiasi pagina.</span></span>
    <button class="x-btn" type="button" aria-label="Chiudi l'avviso" onclick={() => { chiudiNovita(NOVITA, sessione.utente?.accountId ?? ''); novita = false; }}><Icona nome="chiudi" /></button>
  </div>
{/if}

{#if dati}
  <section class="card sez" id="imp-stagioni" aria-label="Stagioni">
    <div class="sez-t"><h2>Stagioni</h2><span class="spiega grow">Le date e la quota associativa le decide la società; due stagioni non si accavallano.</span>
      {#if ordinate.length > 3}<button class="mini-btn" type="button" onclick={() => (tutte = !tutte)}>{tutte ? 'Solo le ultime' : `Tutte (${ordinate.length})`}</button>{/if}</div>
    <div class="stagioni">
      {#each mostrate as s (s.id)}
        {@const st = stato(s)}
        <button type="button" class="stag {st.card}" aria-pressed={scelta === s.id} onclick={() => scegli(s)}>
          <span class="testa-s"><b class="n">{s.nome}</b><span class="grow"></span><span class="pill {st.cls}">{st.nome}</span></span>
          <span class="date">{dataBreve(s.dal)} – {dataBreve(s.al)}</span>
          <ul>
            <li><span class="k">Quota associativa</span><b>{s.quota == null ? 'da decidere' : euro(s.quota)}</b></li>
            <li><span class="k">Tesseramento da</span><span>{tesseramentoDa(s)}</span></li>
            {#if s.id in offerte}<li><span class="k">Listino</span><span class:manca={!offerte[s.id]}>{offerte[s.id] ? `${offerte[s.id]} offerte` : 'da preparare'}</span></li>{/if}
          </ul>
        </button>
      {/each}
      <button type="button" class="stag nuova" aria-pressed={scelta === 'nuova'} onclick={nuovaStagione}><Icona nome="piu" />{nomeNuova ? `Aggiungi la ${nomeNuova}` : 'Aggiungi una stagione'}</button>
    </div>
    {#if inCorso}<div class="linea"><LineaStagione dal={inCorso.dal} al={inCorso.al} /></div>{/if}
    {#if bozza}
      <div class="riga-imp sopra">
        <span class="t"><b>{scelta === 'nuova' ? 'Nuova stagione' : `Stagione ${bozza.nome}`}</b><span>Nome e date di inizio e fine.</span></span>
        <span class="ctl">
          <input class="inp nome-s" bind:value={bozza.nome} maxlength="20" aria-label="Nome della stagione" />
          <input class="inp m data" type="date" bind:value={bozza.dal} aria-label="Dal" /><span class="unita">→</span><input class="inp m data" type="date" bind:value={bozza.al} min={bozza.dal} aria-label="Al" />
        </span>
      </div>
      <div class="riga-imp">
        <span class="t"><b>Quota associativa</b><span>Il tesseramento annuale; senza, il tornello lo segnala (se il controllo è acceso).</span></span>
        <span class="ctl"><input class="inp num" type="number" min="0" step="0.01" bind:value={bozza.quota} placeholder="—" aria-label="Quota associativa" /><span class="unita">€</span>
          <label class="riga-switch corto"><span>rimborsabile</span><button type="button" class="switch" role="switch" aria-checked={bozza.quotaRimborsabile} aria-label="Quota rimborsabile"
            onclick={() => (bozza!.quotaRimborsabile = !bozza!.quotaRimborsabile)}><span></span></button></label></span>
      </div>
      <div class="riga-imp">
        <span class="t"><b>Proponi il tesseramento della stagione nuova</b><span>Al banco e nella scheda compare «da rinnovare» da questo giorno. Vale per tutte le stagioni.</span></span>
        <span class="ctl"><input class="inp num" type="number" min="0" max="365" bind:value={dati.giorniAnticipo} aria-label="Giorni prima" /><span class="unita">giorni prima del suo inizio</span></span>
      </div>
      {#if scelta !== 'nuova'}
        <div class="riga-imp"><span class="t"><span>Una stagione si elimina solo se non ha listino né tesseramenti legati.</span></span>
          <span class="ctl"><button class="mini-btn rosso" type="button" onclick={eliminaStagione}><Icona nome="cestino" />Elimina la stagione</button></span></div>
      {/if}
    {/if}
  </section>

  <div class="colonne-p">
    <div class="princ">
      <section class="card sez" id="imp-dati" aria-label="La società">
        <div class="sez-t"><h2>La società</h2><span class="spiega grow">Il nome compare nelle email; il resto sulle ricevute.</span></div>
        <div class="dati">
          <label class="field c3"><span class="lbl">Nome</span><input class="inp" bind:value={dati.nome} required /></label>
          <label class="field c3"><span class="lbl">Ragione sociale</span><input class="inp" bind:value={dati.ragioneSociale} placeholder="Es. Società Sportiva S.S.D. a r.l." /></label>
          <label class="field c3"><span class="lbl">Partita IVA</span><input class="inp m" bind:value={dati.partitaIva} inputmode="numeric" maxlength="13" placeholder="da completare" /></label>
          <label class="field c3"><span class="lbl">Codice fiscale</span><input class="inp m" bind:value={dati.codiceFiscale} maxlength="16" placeholder="da completare" /></label>
          <label class="field c6"><span class="lbl">Indirizzo</span><input class="inp" bind:value={dati.indirizzo} placeholder="da completare" /></label>
          <label class="field c1"><span class="lbl">CAP</span><input class="inp m" bind:value={dati.cap} maxlength="10" /></label>
          <label class="field c4"><span class="lbl">Città</span><input class="inp" bind:value={dati.citta} /></label>
          <label class="field c1"><span class="lbl">Prov.</span><input class="inp m" bind:value={dati.provincia} maxlength="2" /></label>
          <label class="field c3"><span class="lbl">Email</span><input class="inp" type="email" bind:value={dati.email} /></label>
          <label class="field c3"><span class="lbl">Telefono</span><input class="inp m" type="tel" bind:value={dati.telefono} placeholder="da completare" /></label>
        </div>
      </section>

      <section class="card sez" id="imp-gruppi" aria-label="Gruppi di clienti">
        <div class="sez-t"><h2>Gruppi di clienti</h2><span class="spiega grow">Etichette per prezzi diversi e precedenza nei rinnovi; si danno dalla scheda del cliente.</span>
          <button type="button" class="mini-btn" onclick={() => (gruppo = nuovoGruppo())}><Icona nome="piu" />Nuovo gruppo</button></div>
        {#if gruppo && !gruppo.id}{@render moduloGruppo()}{/if}
        {#each gruppi as g (g.id)}
          {#if gruppo?.id === g.id}
            {@render moduloGruppo()}
          {:else}
            <div class="gruppo-r" class:spento={!g.attivo}>
              <span class="t"><b>{g.nome}{#if !g.attivo} <span class="pill">non attivo</span>{/if}</b><span>{g.descrizione ?? ''}</span>
                {#if g.precedenza != null || g.giorniPerPagare != null || g.dichiarabile}
                  <span class="vantaggi">
                    {#if g.precedenza != null}<span class="tag t-soft">{g.precedenza}ª precedenza</span>{/if}
                    {#if g.giorniPerPagare != null}<span class="tag t-soft">{g.giorniPerPagare} giorni per pagare</span>{/if}
                    {#if g.dichiarabile}<span class="tag t-soft">dichiarabile</span>{/if}
                    {#if g.daVerificare}<span class="tag t-warn">{g.daVerificare === 1 ? '1 da verificare' : `${g.daVerificare} da verificare`}</span>{/if}
                  </span>
                {/if}
              </span>
              <button class="mini-btn" type="button" aria-expanded={aperto === g.id} onclick={() => apriMembri(g)}><Icona nome="persone" />{g.persone === 1 ? '1 persona' : `${g.persone} persone`}</button>
              <button class="mini-btn" type="button" onclick={() => (gruppo = bozzaDi(g))}>Modifica</button>
            </div>
            {#if aperto === g.id}
              <div class="membri small">
                {#each membri as m (m.appartenenzaId)}<a href={`#/clienti/${m.personaId}`}>{m.cognome} {m.nome}</a>{:else}<span class="muted">Nessuno.</span>{/each}
              </div>
            {/if}
          {/if}
        {:else}
          {#if !gruppo}<p class="muted small vuoto">Nessun gruppo.</p>{/if}
        {/each}
        {#if conPrecedenza.length > 1}
          <div class="ordine-p">
            <span class="lbl">Ordine delle precedenze nelle graduatorie</span>
            <ol>
              {#each conPrecedenza as g, i (g.id)}
                <li><span class="n">{i + 1}</span><span class="grow">{g.nome}</span>
                  <button type="button" class="icon-btn piccolo" aria-label={`Sposta ${g.nome} più su`} title="Più su" disabled={i === 0} onclick={() => sposta(i, -1)}><Icona nome="su" /></button>
                  <button type="button" class="icon-btn piccolo" aria-label={`Sposta ${g.nome} più giù`} title="Più giù" disabled={i === conPrecedenza.length - 1} onclick={() => sposta(i, 1)}><Icona nome="giu" /></button></li>
              {/each}
            </ol>
          </div>
        {/if}
      </section>
    </div>

    <aside class="lato-p">
      <section class="card pann">
        <span class="lbl">Come compare su una ricevuta</span>
        <div class="ricevuta">
          <span class="rs">{dati.ragioneSociale || dati.nome}</span>
          {#if dati.partitaIva || dati.codiceFiscale}<span>{dati.partitaIva ? `P. IVA ${dati.partitaIva}` : `C.F. ${dati.codiceFiscale}`}</span>{:else}<span class="mancante">P. IVA da completare</span>{/if}
          {#if dati.indirizzo}<span>{dati.indirizzo}</span>{:else}<span class="mancante">Indirizzo da completare</span>{/if}
          <span>{[luogo, dati.email, dati.telefono].filter(Boolean).join(' · ')}</span>
          <span class="riga-r"><span>Ricevuta n. 128 · {dataBreve(oggi)}</span><b class="m">520,00 €</b></span>
        </div>
        <span class="muted small">Per le ricevute servono partita IVA (o codice fiscale) e indirizzo.</span>
      </section>
    </aside>
  </div>

  {#if modifiche}
    <div class="salva-bar" role="status">
      <span>{modifiche === 1 ? '1 modifica da salvare' : `${modifiche} modifiche da salvare`}</span>
      <button type="button" class="vuoto" onclick={annulla}>Annulla</button>
      <button type="button" class="chiaro" onclick={salva}>Salva</button>
    </div>
  {/if}
{:else if !errore}
  <p class="muted">Caricamento…</p>
{/if}

{#snippet moduloGruppo()}
  {@const g = gruppo!}
  <form class="modulo" onsubmit={(e) => { e.preventDefault(); salvaGruppo(); }}>
    <div class="due">
      <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={g.nome} maxlength="80" /></label>
      <label class="field"><span class="lbl">Descrizione</span><input class="inp" bind:value={g.descrizione} maxlength="300" /></label>
    </div>
    <label class="riga-switch"><span>Precedenza nelle graduatorie<br /><span class="muted small">Va in fondo all'ordine delle precedenze; l'ordine si cambia sotto l'elenco.</span></span>
      <button type="button" class="switch" role="switch" aria-checked={g.conPrecedenza} aria-label="Precedenza nelle graduatorie" onclick={() => (g.conPrecedenza = !g.conPrecedenza)}><span></span></button></label>
    <label class="field giorni"><span class="lbl">Giorni per pagare il posto offerto</span>
      <span class="unita"><input class="inp m" type="number" min="1" max="60" bind:value={g.giorni} placeholder="di base" /><span>giorni</span></span></label>
    <label class="riga-switch"><span>Le famiglie lo possono dichiarare<br /><span class="muted small">Lo segnano nella richiesta; vale solo dopo la verifica della segreteria.</span></span>
      <button type="button" class="switch" role="switch" aria-checked={g.dichiarabile} aria-label="Dichiarabile dalle famiglie" onclick={() => (g.dichiarabile = !g.dichiarabile)}><span></span></button></label>
    {#if g.dichiarabile}<label class="field"><span class="lbl">Cosa leggono le famiglie</span><input class="inp" bind:value={g.frase} maxlength="160" placeholder="es. Frequenta la scuola dell'Istituto" /></label>{/if}
    {#if g.id}<label class="riga-switch"><span>Attivo<br /><span class="muted small">Un gruppo non attivo resta sulle persone che ci sono, ma non se ne aggiungono altre.</span></span>
      <button type="button" class="switch" role="switch" aria-checked={g.attivo} aria-label="Gruppo attivo" onclick={() => (g.attivo = !g.attivo)}><span></span></button></label>{/if}
    <div class="row">
      <button type="button" class="btn btn-secondary grow" onclick={() => (gruppo = null)}>Annulla</button>
      <button class="btn btn-primary grow" disabled={!g.nome.trim() || (g.dichiarabile && !g.frase.trim())}>{g.id ? 'Salva il gruppo' : 'Crea il gruppo'}</button>
    </div>
  </form>
{/snippet}

<style>
  .stagioni { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: var(--space-10); padding: var(--space-16) var(--space-16) var(--space-6); }
  .stag { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-14); border-radius: var(--radius-md); border: 1px solid var(--line); background: var(--surface);
    font: inherit; color: var(--ink); text-align: left; cursor: pointer; }
  .testa-s { display: flex; align-items: center; gap: var(--space-8); }
  .stag b.n { font-family: var(--font-display); font-size: 22px; font-weight: 800; line-height: 1; }
  .stag .date { font-size: var(--text-xs); color: var(--muted); font-family: var(--font-mono); }
  .stag ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; font-size: var(--text-sm); }
  .stag li { display: flex; gap: var(--space-6); }
  .stag .k { color: var(--muted); flex: 1; }
  .stag.ora { border: 2px solid var(--cloro); background: var(--cloro-soft); }
  .stag.finita { background: var(--carta); color: var(--muted); }
  .stag[aria-pressed='true'] { box-shadow: 0 0 0 3px color-mix(in srgb, var(--cloro) 35%, transparent); }
  .stag.nuova { border: 1.5px dashed var(--line-dashed); background: none; align-items: center; justify-content: center; flex-direction: row; color: var(--cloro); font-weight: 700; min-height: 100px; }
  .stag.nuova :global(.ic) { width: 18px; height: 18px; }
  .manca { color: var(--warn); font-weight: 600; }
  .linea { padding: var(--space-6) var(--space-16) var(--space-14); }
  .sopra { border-top: 1px solid var(--line) !important; }
  .nome-s { width: 110px; height: 40px; }
  .data { width: 160px; height: 40px; }
  .corto { min-height: 40px; gap: var(--space-8); font-size: var(--text-md); }
  .dati { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: var(--space-12) var(--space-14); padding: var(--space-16); }
  .c1 { grid-column: span 1; } .c3 { grid-column: span 3; } .c4 { grid-column: span 4; } .c6 { grid-column: span 6; }
  .gruppo-r { display: flex; align-items: center; gap: var(--space-12); flex-wrap: wrap; padding: var(--space-12) var(--space-16); border-top: 1px solid var(--line-soft); }
  .sez-t + .gruppo-r { border-top: none; }
  .gruppo-r .t { flex: 1 1 220px; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
  .gruppo-r .t span { font-size: var(--text-sm); color: var(--muted); }
  .gruppo-r .t .vantaggi { display: flex; flex-wrap: wrap; gap: var(--space-4); margin-top: var(--space-4); }
  .gruppo-r .t .vantaggi .tag { color: inherit; }
  .ordine-p { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-12) var(--space-16) var(--space-16); border-top: 1px solid var(--line); }
  .ordine-p ol { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--space-6); }
  .ordine-p li { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-6) var(--space-8) var(--space-6) var(--space-12); border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface); }
  .ordine-p .n { width: 22px; height: 22px; border-radius: 50%; display: grid; place-items: center; background: var(--fill); color: var(--on-fill); font-size: 12px; font-weight: 700; }
  .icon-btn.piccolo { width: 36px; height: 36px; }
  .spento { opacity: 0.65; }
  .membri { display: flex; flex-wrap: wrap; gap: var(--space-4) var(--space-12); padding: 0 var(--space-16) var(--space-12); }
  .modulo { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16); background: var(--carta); border-top: 1px solid var(--line-soft); }
  .vuoto { padding: var(--space-12) var(--space-16); margin: 0; }
  .ricevuta { padding: var(--space-16); display: flex; flex-direction: column; gap: var(--space-6); background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md);
    font-size: var(--text-sm); box-shadow: 0 1px 2px rgba(16, 33, 43, 0.06); }
  .ricevuta .rs { font-family: var(--font-display); font-size: 17px; font-weight: 800; }
  .riga-r { display: flex; justify-content: space-between; gap: var(--space-8); white-space: nowrap; border-top: 1px dashed var(--line); padding-top: var(--space-6); margin-top: var(--space-4); }
  .mancante { color: var(--warn); font-weight: 600; }
  @media (max-width: 700px) {
    .dati { grid-template-columns: 1fr 1fr; }
    .c1, .c3, .c4, .c6 { grid-column: span 2; }
  }
</style>
