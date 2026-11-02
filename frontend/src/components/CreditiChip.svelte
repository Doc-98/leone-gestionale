<script lang="ts">
  import Icona from './Icona.svelte';
  import { untrack } from 'svelte';
  import {
    api, ErroreApi, messaggioErrore,
    type EsitoLetturaChip, type MemoriaChip, type MetodoPagamento, type RegoleBorsellino, type RicaricaPreparata, type SaldoChip, type StatoCreditiBraccialetto,
  } from '../lib/api';
  import { lettoreChip, lettoreSimulato } from '../lib/chip.svelte';
  import { dataBreve, dataDi, oraDi } from '../lib/date';
  import { euro } from '../lib/listino';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Phon e docce con i crediti scritti sul braccialetto (ADR 0025): le macchine non sono collegate, quindi il gestionale
   * sa solo l'ultimo saldo visto. Qui si legge il braccialetto (dal lettore o dal display della macchina) e si ricarica
   * in due tempi: si scrive il saldo nuovo, si rilegge e solo allora si registra il pagamento.
   */
  let {
    personaId, stato, regole, metodi, pagantiPossibili, euroDisponibili, cambiato,
  }: {
    personaId: string;
    stato: StatoCreditiBraccialetto;
    regole: RegoleBorsellino;
    metodi: MetodoPagamento[];
    pagantiPossibili: { personaId: string; nome: string; relazione: string | null }[];
    euroDisponibili: number;
    cambiato: () => void;
  } = $props();
  const id = untrack(() => personaId);

  let saldi = $state<SaldoChip[]>([]);
  let errore = $state('');
  let messaggio = $state('');
  let lavoro = $state(false);
  const puoRicaricare = $derived(sessione.puo('BORSELLINO'));
  const scelta = $derived(lettoreChip(stato));
  const lettore = $derived(scelta.lettore);
  const attivi = $derived(saldi.filter((s) => s.attivo));

  async function carica() {
    try {
      saldi = await api.saldiChip(id);
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  carica();

  /** Cosa sta facendo l'operatore su un braccialetto. */
  type Passo =
    | { tipo: 'display'; b: SaldoChip; crediti: string }
    | { tipo: 'ricarica'; b: SaldoChip; letto: EsitoLetturaChip | null; importo: string; metodoId: string; paganteId: string }
    | { tipo: 'scrittura'; b: SaldoChip; preparata: RicaricaPreparata; importo: number; metodoId: string; paganteId: string; problema: string };
  let passo = $state<Passo | null>(null);
  const metodoPredefinito = () => metodi.find((m) => !m.borsellino)?.id ?? '';
  const comprati = (imp: string) => (Number(imp) > 0 ? Math.floor(Math.round(Number(imp) * 100) / Math.round(regole.valoreCredito * 100)) : 0);
  const multiplo = (imp: string) => Math.round(Number(imp) * 100) % Math.round(regole.valoreCredito * 100) === 0;

  /** Gli errori del lettore sono Error semplici, quelli del server ErroreApi. */
  const testo = (e: unknown) => (e instanceof ErroreApi || !(e instanceof Error) ? messaggioErrore(e) : e.message);

  async function fai(f: () => Promise<void>) {
    lavoro = true;
    errore = '';
    messaggio = '';
    try {
      await f();
    } catch (e) {
      errore = testo(e);
    } finally {
      lavoro = false;
    }
  }

  const leggiMemoria = async (): Promise<MemoriaChip> => lettore!.leggi(stato.accesso!);

  function dopoLettura(b: SaldoChip, e: EsitoLetturaChip) {
    if (e.inSospeso) return `Sul braccialetto ${e.crediti} crediti. C'è una ricarica in sospeso: registrala o annullala.`;
    return `Sul braccialetto ${b.codice}: ${e.crediti} crediti${e.consumati ? ` (${e.consumati} consumati alle macchine dall'ultima volta)` : ''}.`;
  }

  /** Legge dal lettore e aggiorna l'ultimo saldo visto (e i consumi nel registro). */
  const leggi = (b: SaldoChip) => fai(async () => {
    if (lettore?.tipo === 'simulato' && !lettoreSimulato.appoggiato) lettoreSimulato.appoggia(b.codice);
    const e = await api.leggiChip(b.braccialettoId, { memoria: await leggiMemoria() });
    await carica();
    cambiato();
    messaggio = dopoLettura(b, e);
  });

  const salvaDisplay = () => fai(async () => {
    const p = passo as Extract<Passo, { tipo: 'display' }>;
    const e = await api.leggiChip(p.b.braccialettoId, { crediti: Number(p.crediti) });
    passo = null;
    await carica();
    cambiato();
    messaggio = dopoLettura(p.b, e);
  });

  function apriRicarica(b: SaldoChip) {
    messaggio = '';
    errore = '';
    if (lettore?.tipo === 'simulato') lettoreSimulato.appoggia(b.codice);
    passo = { tipo: 'ricarica', b, letto: null, importo: String(Math.max(regole.ricaricaMinima, 5)), metodoId: metodoPredefinito(), paganteId: id };
  }

  const leggiPerRicarica = () => fai(async () => {
    const p = passo as Extract<Passo, { tipo: 'ricarica' }>;
    p.letto = await api.leggiChip(p.b.braccialettoId, { memoria: await leggiMemoria() });
    await carica();
    cambiato();
  });

  /** Primo tempo (il server prepara i blocchi), poi scrittura, rilettura e conferma. */
  const ricarica = () => fai(async () => {
    const p = passo as Extract<Passo, { tipo: 'ricarica' }>;
    const memoria = await leggiMemoria();
    const preparata = await api.preparaRicaricaChip(p.b.braccialettoId, memoria, Number(p.importo));
    passo = { tipo: 'scrittura', b: p.b, preparata, importo: Number(p.importo), metodoId: p.metodoId, paganteId: p.paganteId, problema: '' };
    await scriviEConferma();
  });

  async function scriviEConferma() {
    const p = passo as Extract<Passo, { tipo: 'scrittura' }>;
    try {
      await lettore!.scrivi(p.b.codice, p.preparata.scrivi, stato.accesso!);
    } catch (e) {
      p.problema = `La scrittura non è riuscita (${e instanceof Error ? e.message : e}). Riappoggia il braccialetto e riprova, oppure annulla la ricarica.`;
      return;
    }
    await conferma();
  }

  async function conferma() {
    const p = passo as Extract<Passo, { tipo: 'scrittura' }>;
    try {
      const riletta = await leggiMemoria();
      await api.confermaRicaricaChip(p.preparata.operazione, { memoria: riletta, paganteId: p.paganteId, metodi: [{ metodoId: p.metodoId, importo: p.importo }] });
    } catch (e) {
      p.problema = `${testo(e)} La ricarica resta in sospeso finché non la registri o la annulli.`;
      await carica();
      return;
    }
    passo = null;
    await carica();
    cambiato();
    messaggio = `Ricarica di ${euro(p.importo)} scritta sul braccialetto: ${p.preparata.creditiDopo} crediti (+${p.preparata.comprati}).`;
  }

  /** Una ricarica rimasta in sospeso (ad esempio dopo aver ricaricato la pagina): si riprende da qui. */
  function riprendi(b: SaldoChip) {
    const s = b.inSospeso!;
    if (lettore?.tipo === 'simulato') lettoreSimulato.appoggia(b.codice);
    passo = { tipo: 'scrittura', b, importo: s.importo, metodoId: metodoPredefinito(), paganteId: id, problema: '',
      preparata: { operazione: s.operazione, creditiPrima: s.creditiPrima, creditiDopo: s.creditiDopo, comprati: s.creditiDopo - s.creditiPrima, scrivi: {} } };
  }

  const annulla = (operazione: string) => fai(async () => {
    let riletta: MemoriaChip | null = null;
    try {
      riletta = lettore ? await leggiMemoria() : null;
    } catch {
      riletta = null;
    }
    await api.annullaRicaricaChip(operazione, riletta);
    passo = null;
    await carica();
    messaggio = 'Ricarica annullata: niente è stato pagato.';
  });

  /** Dal pulsante «Ricarica» della scheda: la ricarica del primo braccialetto attivo. */
  export function ricaricaPrimo() {
    if (attivi[0] && scelta.lettore && !attivi[0].inSospeso) apriRicarica(attivi[0]);
    else errore = attivi[0] ? (scelta.lettore ? 'C\'è una ricarica in sospeso: registrala o annullala.' : scelta.perche) : 'Per ricaricare serve un braccialetto attivo.';
  }

  const fonte = (s: SaldoChip) => (s.fonte === 'SCRITTURA' ? 'scritto alla ricarica' : s.fonte === 'A_MANO' ? 'dal display della macchina' : 'letto sul braccialetto');
</script>

<div class="chip-b">
  <div class="testa-c">
    <span class="lbl grow">Crediti sui braccialetti</span>
    {#if lettore?.tipo === 'simulato'}
      <label class="small sim">Lettore simulato, appoggiato:
        <select class="inp mini" value={lettoreSimulato.appoggiato ?? ''} onchange={(e) => lettoreSimulato.appoggia(e.currentTarget.value || null)}>
          <option value="">nessuno</option>
          {#each saldi as s (s.braccialettoId)}<option value={s.codice}>{s.codice}</option>{/each}
        </select></label>
    {/if}
  </div>
  <p class="muted small nomargin">Le macchine non sono collegate: scalano i crediti sul braccialetto senza dirlo al gestionale. Qui c'è l'ultimo saldo visto, i crediti veri possono essere di meno.
    {#if !lettore}{scelta.perche}{/if}</p>
  {#if messaggio}<p class="msg ok nomargin" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  <ul class="righe">
    {#each saldi.filter((s) => s.attivo || s.inSospeso) as s (s.braccialettoId)}
      <li>
        <span class="m cod">{s.codice}</span>
        <span class="grow small">{#if s.crediti == null}<span class="muted">mai letto</span>
          {:else}<strong>{s.crediti} crediti</strong> <span class="muted">· {fonte(s)} il {dataBreve(dataDi(s.aggiornatoIl!))} alle {oraDi(s.aggiornatoIl!)}</span>{/if}</span>
        {#if !passo && s.attivo}
          <span class="gruppo-pulsanti">
            {#if lettore}<button type="button" class="mini-btn" disabled={lavoro} onclick={() => leggi(s)}><Icona nome="nfc" />Leggi</button>{/if}
            <button type="button" class="mini-btn" onclick={() => (passo = { tipo: 'display', b: s, crediti: '' })}>Dal display…</button>
            {#if puoRicaricare}<button type="button" class="mini-btn pieno" disabled={!lettore || !!s.inSospeso} title={lettore ? '' : scelta.perche} onclick={() => apriRicarica(s)}>Ricarica…</button>{/if}
          </span>
        {/if}
      </li>
      {#if s.inSospeso && passo?.b.braccialettoId !== s.braccialettoId}
        <li class="sospesa" role="alert">
          <Icona nome="avviso" />
          <span class="grow small">Ricarica di {euro(s.inSospeso.importo)} in sospeso dal {dataBreve(dataDi(s.inSospeso.creataIl))}: forse è già sul braccialetto ({s.inSospeso.creditiDopo} crediti), ma non è pagata.</span>
          {#if puoRicaricare}
            {#if lettore}<button type="button" class="mini-btn pieno" onclick={() => riprendi(s)}>Rileggi e registra…</button>{/if}
            <button type="button" class="mini-btn rosso" disabled={lavoro} onclick={() => annulla(s.inSospeso!.operazione)}>Annulla</button>
          {/if}
        </li>
      {/if}
    {:else}
      <li class="muted small">Nessun braccialetto attivo.</li>
    {/each}
  </ul>

  {#if passo?.tipo === 'display'}
    {@const p = passo}
    <form class="modulo" onsubmit={(e) => { e.preventDefault(); salvaDisplay(); }}>
      <label class="field"><span class="lbl">Crediti che mostra la macchina per {p.b.codice}</span>
        <input class="inp m corto" type="number" min="0" step="1" bind:value={p.crediti} required /></label>
      <span class="muted small">Avvicinando il braccialetto, la centralina dei phon e i lettori delle docce mostrano i crediti rimasti.</span>
      <div class="row"><button type="button" class="btn btn-secondary grow" onclick={() => (passo = null)}>Annulla</button>
        <button class="btn btn-primary grow" disabled={lavoro || p.crediti === '' || Number(p.crediti) < 0}>Salva il saldo</button></div>
    </form>
  {:else if passo?.tipo === 'ricarica'}
    {@const p = passo}
    <form class="modulo" onsubmit={(e) => { e.preventDefault(); ricarica(); }}>
      <div class="passo"><span class="num">1</span><span class="grow">
        {#if p.letto}Sul braccialetto <strong>{p.b.codice}</strong> ci sono <strong>{p.letto.crediti} crediti</strong>{p.letto.consumati ? ` (${p.letto.consumati} consumati alle macchine)` : ''}.
        {:else}Appoggia il braccialetto <strong>{p.b.codice}</strong> sul lettore e leggilo.{/if}</span>
        <button type="button" class="mini-btn" disabled={lavoro} onclick={leggiPerRicarica}><Icona nome="nfc" />{p.letto ? 'Rileggi' : 'Leggi'}</button></div>
      <div class="field">
        <span class="lbl">Quanto (un credito = {euro(regole.valoreCredito)}, minimo {euro(regole.ricaricaMinima)})</span>
        <div class="riga">
          <div class="chips">
            {#each [...new Set([Math.max(regole.ricaricaMinima, 5), 10, 20])] as v (v)}
              <button type="button" class="chip" aria-pressed={Number(p.importo) === v} onclick={() => (p.importo = String(v))}>{euro(v)}</button>
            {/each}
          </div>
          <input class="inp m importo" type="number" min={regole.ricaricaMinima} step={regole.valoreCredito} bind:value={p.importo} aria-label="Importo della ricarica" />
          <span class="small">= <strong>{comprati(p.importo)} crediti</strong>{p.letto ? `, sul braccialetto ${p.letto.crediti + comprati(p.importo)}` : ''}</span>
        </div>
        {#if Number(p.importo) > 0 && !multiplo(p.importo)}<span class="small rosso">Deve essere un multiplo di {euro(regole.valoreCredito)}.</span>{/if}
      </div>
      <div class="due">
        <label class="field"><span class="lbl">Come si paga</span>
          <select class="inp" bind:value={p.metodoId}>{#each metodi as m (m.id)}<option value={m.id}>{m.borsellino ? `Borsellino (credito: ${euro(euroDisponibili)})` : m.nome}</option>{/each}</select></label>
        <label class="field"><span class="lbl">Chi paga</span>
          <select class="inp" bind:value={p.paganteId}><option value={id}>Lui o lei</option>
            {#each pagantiPossibili as f (f.personaId)}<option value={f.personaId}>{f.nome}{f.relazione ? ` (${f.relazione.toLowerCase()})` : ''}</option>{/each}</select></label>
      </div>
      <span class="muted small">Il pagamento si registra solo dopo aver scritto e riletto il braccialetto: tienilo sul lettore fino alla fine.</span>
      <div class="row"><button type="button" class="btn btn-secondary grow" onclick={() => (passo = null)}>Annulla</button>
        <button class="btn btn-primary grow" disabled={lavoro || !p.letto || !(Number(p.importo) >= regole.ricaricaMinima && multiplo(p.importo) && p.metodoId)}>
          {lavoro ? 'Scrivo…' : `Ricarica ${euro(Number(p.importo) || 0)} e scrivi`}</button></div>
    </form>
  {:else if passo?.tipo === 'scrittura'}
    {@const p = passo}
    <div class="modulo">
      <div class="passo"><span class="num">2</span><span class="grow">Ricarica di <strong>{euro(p.importo)}</strong> sul braccialetto <strong>{p.b.codice}</strong>: da {p.preparata.creditiPrima} a {p.preparata.creditiDopo} crediti.</span></div>
      {#if p.problema}<p class="msg err nomargin" role="alert">{p.problema}</p>{/if}
      <label class="field"><span class="lbl">Come si paga</span>
        <select class="inp" bind:value={p.metodoId}>{#each metodi as m (m.id)}<option value={m.id}>{m.borsellino ? `Borsellino (credito: ${euro(euroDisponibili)})` : m.nome}</option>{/each}</select></label>
      <div class="row">
        <button type="button" class="btn btn-secondary grow" disabled={lavoro} onclick={() => annulla(p.preparata.operazione)}>Annulla la ricarica</button>
        {#if Object.keys(p.preparata.scrivi).length}
          <button type="button" class="btn btn-secondary grow" disabled={lavoro} onclick={() => fai(scriviEConferma)}>Riprova a scrivere</button>
        {/if}
        <button type="button" class="btn btn-primary grow" disabled={lavoro || !p.metodoId} onclick={() => fai(conferma)}>Rileggi e registra il pagamento</button>
      </div>
    </div>
  {/if}
</div>

<style>
  .chip-b { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-12); border-radius: var(--radius-md); border: 1px solid var(--line); }
  .testa-c { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .sim { display: flex; align-items: center; gap: var(--space-6); color: var(--muted); }
  .inp.mini { width: auto; height: 32px; padding: 0 var(--space-8); font-size: var(--text-sm); appearance: auto; }
  .righe { list-style: none; margin: 0; padding: 0; }
  .righe li { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-8) 0; border-top: 1px solid var(--line-soft); flex-wrap: wrap; }
  .righe li:first-child { border-top: none; }
  .cod { font-weight: 600; min-width: 88px; }
  .gruppo-pulsanti { display: flex; gap: var(--space-6); flex-wrap: wrap; }
  .sospesa { color: var(--warn); background: var(--warn-bg); border-radius: var(--radius-sm); padding: var(--space-8) var(--space-10) !important; }
  .sospesa :global(.ic) { width: 18px; height: 18px; flex-shrink: 0; }
  .modulo { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-12); border-radius: var(--radius-md); background: var(--surface-sunken); }
  .passo { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .num { width: 24px; height: 24px; border-radius: 50%; display: grid; place-items: center; background: var(--fill); color: var(--on-fill); font-size: var(--text-xs); font-weight: 700; flex-shrink: 0; }
  .riga { display: flex; gap: var(--space-8); align-items: center; flex-wrap: wrap; }
  .importo { width: 110px; }
  .corto { width: 120px; }
  select.inp { appearance: auto; }
  .rosso { color: var(--danger); }
</style>
