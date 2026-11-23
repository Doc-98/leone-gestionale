<script lang="ts">
  import Azione from '../components/Azione.svelte';
  import BarraSalva from '../components/BarraSalva.svelte';
  import Fumetto from '../components/Fumetto.svelte';
  import Icona from '../components/Icona.svelte';
  import SceltaStagione from '../components/SceltaStagione.svelte';
  import StatoVuoto from '../components/StatoVuoto.svelte';
  import { api, messaggioErrore, type Offerta, type PaginaListino, type RegolaPrezzo, type Stagione, type TipoRegolaPrezzo } from '../lib/api';
  import { dataBreve, iso } from '../lib/date';
  import { dataIndice, giorniStagione, indiceGiorno, mesiStagione, posizione, rateDel, recessoDel, riduzioneDel, testoRate, type Scaglione } from '../lib/impostazioni';
  import { etichettaVolte } from '../lib/listino';
  import { euro } from '../lib/numeri';
  import { quanti } from '../lib/plurali';
  import { contaModifiche } from '../lib/salvataggio';
  import { MSG_PERCENTUALE, MSG_PRIMA_RATA, primaRataNonValida, scaglioneNonValido } from '../lib/validita';
  import ErroreCampo from '../components/ErroreCampo.svelte';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Le regole di prezzo di una stagione (ADR 0018, 0024): riduzione per chi si iscrive tardi, rate, recesso e sospensione.
   * In alto la stagione su una linea: si tocca o si trascina per vedere cosa succede a chi si iscrive quel giorno.
   * Scaglioni e rate sono righe d'elenco dentro la bozza (cestino a due tocchi, riga «Aggiungi…»): le modifiche si salvano
   * tutte insieme con la barra, che conta anche le righe aggiunte e tolte.
   */
  let stagioni = $state<Stagione[]>([]);
  let stagioneId = $state('');
  let pagina = $state<PaginaListino | null>(null);
  let errore = $state('');
  let messaggio = $state('');
  const oggi = iso(new Date());
  const modifica = $derived(sessione.puo('IMPOSTAZIONI'));

  type Bozza = {
    riduzione: { attiva: boolean; scaglioni: { data: string; percentuale: string }[] };
    recesso: { attiva: boolean; scaglioni: { data: string; percentuale: string }[]; rimborso: string };
    rate: { attiva: boolean; numero: number; primaPercentuale: string; scadenze: string[] };
    sospensione: { attiva: boolean; settimaneMinime: string; settimaneCertificato: string; settimaneRestituite: string; restituzione: string };
  };
  let bozza = $state<Bozza | null>(null);
  let originale = $state('');
  /**
   * Una chiave per ogni riga (scaglioni di riduzione e di recesso, scadenze delle rate), per tenere ogni riga con i suoi
   * campi quando se ne toglie una e per contare le modifiche per riga. Sta fuori dalla bozza: non si salva.
   */
  type Chiavi = { riduzione: number[]; recesso: number[]; rate: number[] };
  let chiavi = $state<Chiavi>({ riduzione: [], recesso: [], rate: [] });
  /** Le chiavi com'erano alla lettura: servono a riconoscere le righe aggiunte e quelle tolte. */
  let chiaviOriginali: Chiavi = { riduzione: [], recesso: [], rate: [] };
  let contatore = 0;
  const nuoveChiavi = (n: number) => Array.from({ length: n }, () => ++contatore);
  function azzeraChiavi() {
    chiavi = { riduzione: nuoveChiavi(bozza?.riduzione.scaglioni.length ?? 0), recesso: nuoveChiavi(bozza?.recesso.scaglioni.length ?? 0), rate: nuoveChiavi(bozza?.rate.scadenze.length ?? 0) };
    chiaviOriginali = { riduzione: [...chiavi.riduzione], recesso: [...chiavi.recesso], rate: [...chiavi.rate] };
  }
  function aggiungiRata() {
    bozza!.rate.numero++;
    bozza!.rate.scadenze = [...bozza!.rate.scadenze, ''];
    chiavi.rate = [...chiavi.rate, ++contatore];
  }
  function togliRata(i: number) {
    bozza!.rate.scadenze = bozza!.rate.scadenze.filter((_, j) => j !== i);
    bozza!.rate.numero--;
    chiavi.rate = chiavi.rate.filter((_, j) => j !== i);
  }
  /**
   * Quante cose sono cambiate: una riga aggiunta, tolta o cambiata vale 1 (si confrontano per chiave, non per posizione),
   * ogni altro campo vale 1.
   */
  function contaModificheRighe(): number {
    if (!originale || !bozza) return 0;
    const o = JSON.parse(originale) as Bozza;
    const senza = (b: Bozza): Bozza => ({ ...b, riduzione: { ...b.riduzione, scaglioni: [] }, recesso: { ...b.recesso, scaglioni: [] }, rate: { ...b.rate, scadenze: [], numero: 0 } });
    let n = contaModifiche(senza(o), senza(bozza));
    const liste: [keyof Chiavi, unknown[], unknown[]][] = [
      ['riduzione', o.riduzione.scaglioni, bozza.riduzione.scaglioni],
      ['recesso', o.recesso.scaglioni, bozza.recesso.scaglioni],
      ['rate', o.rate.scadenze, bozza.rate.scadenze],
    ];
    for (const [sez, prima, dopo] of liste) {
      const vecchie = new Map(chiaviOriginali[sez].map((k, i) => [k, prima[i]]));
      const attuali = new Map(chiavi[sez].map((k, i) => [k, dopo[i]]));
      for (const [k, riga] of attuali) n += vecchie.has(k) ? (contaModifiche(vecchie.get(k), riga) > 0 ? 1 : 0) : 1;
      for (const k of vecchie.keys()) if (!attuali.has(k)) n++;
    }
    return n;
  }
  function aggiungiScaglione(sez: 'riduzione' | 'recesso') {
    bozza![sez].scaglioni = [...bozza![sez].scaglioni, { data: '', percentuale: '' }];
    chiavi[sez] = [...chiavi[sez], ++contatore];
  }
  function togliScaglione(sez: 'riduzione' | 'recesso', i: number) {
    bozza![sez].scaglioni = bozza![sez].scaglioni.filter((_, j) => j !== i);
    chiavi[sez] = chiavi[sez].filter((_, j) => j !== i);
  }
  const annulla = () => {
    bozza = JSON.parse(originale);
    azzeraChiavi();
  };
  /** Il nome della riga per il cestino: con la data («dal 01/10/2026»), senza («2»). */
  const nomeScaglione = (sez: 'riduzione' | 'recesso', s: { data: string }, i: number) =>
    s.data ? `${sez === 'riduzione' ? 'dal' : 'fino al'} ${dataBreve(s.data)}` : String(i + 1);

  async function avvia() {
    try {
      // la stagione di partenza la sceglie `SceltaStagione` (in corso, poi la futura più vicina, poi l'ultima finita)
      stagioni = await api.stagioni();
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  avvia();

  const regola = (tipo: TipoRegolaPrezzo): RegolaPrezzo => pagina?.regole.find((r) => r.tipo === tipo) ?? { tipo, attiva: false, parametri: {} };
  const scaglioniDi = (r: RegolaPrezzo, campo: string) =>
    ((r.parametri.scaglioni as Record<string, unknown>[] | undefined) ?? []).map((x) => ({ data: String(x[campo] ?? ''), percentuale: String(x.percentuale ?? '') }));

  async function carica() {
    if (!stagioneId) return;
    try {
      pagina = await api.listino(stagioneId);
      const rid = regola('RIDUZIONE_TARDIVA'), rec = regola('RECESSO'), rate = regola('RATE'), sos = regola('SOSPENSIONE');
      const p = sos.parametri;
      bozza = {
        riduzione: { attiva: rid.attiva, scaglioni: scaglioniDi(rid, 'dal') },
        recesso: { attiva: rec.attiva, scaglioni: scaglioniDi(rec, 'finoAl'), rimborso: String(rec.parametri.rimborso ?? (rec.parametri.nelBorsellino === true ? 'BORSELLINO' : 'DENARO')) },
        rate: { attiva: rate.attiva, numero: Number(rate.parametri.numero ?? 2), primaPercentuale: String(rate.parametri.primaPercentuale ?? 50),
          scadenze: Array.from({ length: Number(rate.parametri.numero ?? 2) - 1 }, (_, i) => ((rate.parametri.scadenze as (string | null)[] | undefined) ?? [])[i] ?? '') },
        sospensione: { attiva: sos.attiva, settimaneMinime: String(p.settimaneMinime ?? 6), settimaneCertificato: String(p.settimaneCertificato ?? 4),
          settimaneRestituite: String(p.settimaneRestituite ?? 'TUTTE'), restituzione: String(p.restituzione ?? 'ALLUNGA') },
      };
      originale = JSON.stringify(bozza);
      azzeraChiavi();
      giorno = null;
      if (!opzioni.some((o) => o.chiave === scelta)) scelta = opzioni[0]?.chiave ?? '';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  $effect(() => {
    void stagioneId;
    carica();
  });

  const cambiate = $derived.by(() => {
    if (!bozza) return [] as TipoRegolaPrezzo[];
    const o = JSON.parse(originale) as Bozza;
    const out: TipoRegolaPrezzo[] = [];
    if (JSON.stringify(bozza.riduzione) !== JSON.stringify(o.riduzione)) out.push('RIDUZIONE_TARDIVA');
    if (JSON.stringify(bozza.recesso) !== JSON.stringify(o.recesso)) out.push('RECESSO');
    if (JSON.stringify(bozza.rate) !== JSON.stringify(o.rate)) out.push('RATE');
    if (JSON.stringify(bozza.sospensione) !== JSON.stringify(o.sospensione)) out.push('SOSPENSIONE');
    return out;
  });

  /** Percentuali scritte male: negli scaglioni con una data (gli altri non si salvano) e nella 1ª rata (il backend la controlla sempre). */
  const valida = $derived(!bozza || !(bozza.riduzione.scaglioni.some(scaglioneNonValido) || bozza.recesso.scaglioni.some(scaglioneNonValido)
    || primaRataNonValida(bozza.rate.primaPercentuale)));

  async function salva() {
    const b = bozza!;
    errore = '';
    messaggio = '';
    const sc = (x: { data: string; percentuale: string }[], campo: string) => x.filter((s) => s.data).map((s) => ({ [campo]: s.data, percentuale: Number(s.percentuale) || 0 }));
    try {
      for (const t of cambiate) {
        if (t === 'RIDUZIONE_TARDIVA') await api.modificaRegolaPrezzo(stagioneId, t, b.riduzione.attiva, { scaglioni: sc(b.riduzione.scaglioni, 'dal') });
        if (t === 'RECESSO') await api.modificaRegolaPrezzo(stagioneId, t, b.recesso.attiva, { scaglioni: sc(b.recesso.scaglioni, 'finoAl'), rimborso: b.recesso.rimborso });
        if (t === 'RATE') await api.modificaRegolaPrezzo(stagioneId, t, b.rate.attiva, { numero: b.rate.numero, primaPercentuale: Number(b.rate.primaPercentuale) || 50,
          scadenze: Array.from({ length: b.rate.numero - 1 }, (_, i) => b.rate.scadenze[i] || null) });
        if (t === 'SOSPENSIONE') await api.modificaRegolaPrezzo(stagioneId, t, b.sospensione.attiva, { settimaneMinime: Number(b.sospensione.settimaneMinime) || 0,
          settimaneCertificato: Number(b.sospensione.settimaneCertificato) || 0, settimaneRestituite: b.sospensione.settimaneRestituite, restituzione: b.sospensione.restituzione });
      }
      await carica();
      messaggio = 'Regole salvate: valgono per le vendite nuove.';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  // ── La linea della stagione ───────────────────────────────────────────
  const stagione = $derived(stagioni.find((s) => s.id === stagioneId) ?? null);
  const n = $derived(stagione ? giorniStagione(stagione.dal, stagione.al) : 365);
  const mesi = $derived(stagione ? mesiStagione(stagione.dal, stagione.al) : []);
  /** Il giorno dell'esempio: null = oggi (senza linea tratteggiata). */
  let giorno = $state<string | null>(null);
  let trascina = false;
  const oggiDentro = $derived(!!stagione && stagione.dal <= oggi && oggi <= stagione.al);
  const riferimento = $derived(giorno ?? (oggiDentro ? oggi : stagione?.dal ?? oggi));
  const px = (d: string) => (stagione ? `${posizione(stagione.dal, stagione.al, d)}%` : '0%');

  function daPuntatore(e: PointerEvent, el: HTMLElement) {
    const r = el.getBoundingClientRect();
    const i = Math.max(0, Math.min(n - 1, Math.floor(((e.clientX - r.left) / r.width) * n)));
    giorno = dataIndice(stagione!.dal, i);
  }
  function giu(e: PointerEvent) {
    const el = e.currentTarget as HTMLElement;
    el.setPointerCapture?.(e.pointerId);
    trascina = true;
    daPuntatore(e, el);
  }
  const muovi = (e: PointerEvent) => trascina && daPuntatore(e, e.currentTarget as HTMLElement);
  const su = () => (trascina = false);
  function tastiLinea(e: KeyboardEvent) {
    if (!stagione) return;
    const passo = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : e.key === 'PageUp' ? 30 : e.key === 'PageDown' ? -30 : 0;
    if (!passo) return;
    e.preventDefault();
    const i = Math.max(0, Math.min(n - 1, indiceGiorno(stagione.dal, riferimento) + passo));
    giorno = dataIndice(stagione.dal, i);
  }
  const meseDi = (d: string) => d.slice(0, 7);

  /** I tratti di una corsia: da quando a quando vale ogni percentuale. */
  function tratti(scaglioni: { data: string; percentuale: string }[], verso: 'dal' | 'finoAl') {
    if (!stagione) return [];
    const s = scaglioni.filter((x) => x.data).map((x) => ({ data: x.data, p: Number(x.percentuale) || 0 })).sort((a, b) => a.data.localeCompare(b.data));
    const out: { da: number; a: number; testo: string; cls: string }[] = [];
    const idx = (d: string) => Math.max(0, Math.min(n - 1, indiceGiorno(stagione!.dal, d)));
    if (verso === 'dal') {
      const primo = s.length ? idx(s[0].data) : n;
      if (primo > 0) out.push({ da: 0, a: primo - 1, testo: 'prezzo pieno', cls: 'zero' });
      s.forEach((x, i) => {
        const a = i + 1 < s.length ? idx(s[i + 1].data) - 1 : n - 1;
        if (a >= idx(x.data)) out.push({ da: idx(x.data), a, testo: i + 1 < s.length ? `−${x.p} %` : `−${x.p} % fino a fine stagione`, cls: 'rid' });
      });
    } else {
      let da = 0;
      for (const x of s) {
        if (indiceGiorno(stagione.dal, x.data) < 0) continue; // finito prima che la stagione cominci
        const a = idx(x.data);
        if (a >= da) out.push({ da, a, testo: `${x.p} %`, cls: 'rec' });
        da = a + 1;
      }
      if (da < n) out.push({ da, a: n - 1, testo: s.length ? `dopo il ${dataBreve(s.at(-1)!.data)} niente rimborso` : 'nessun rimborso', cls: 'zero' });
    }
    return out.map((t) => ({ ...t, left: (t.da / n) * 100, width: ((t.a - t.da + 1) / n) * 100 }));
  }
  /** Le fasce delle due corsie; la scritta sta nella fascia solo se ci entra, altrimenti va nella legenda sotto. */
  const trattiRid = $derived(bozza ? (bozza.riduzione.attiva ? tratti(bozza.riduzione.scaglioni, 'dal') : [{ da: 0, a: n - 1, testo: 'regola spenta', cls: 'zero', left: 0, width: 100 }]) : []);
  const trattiRec = $derived(bozza ? tratti(bozza.recesso.attiva ? bozza.recesso.scaglioni : [], 'finoAl') : []);
  let largTracce = $state(0);
  /** La larghezza della linea, seguita se cambia (senza ResizeObserver, come nei test, resta 0 e le scritte stanno nelle fasce). */
  function misuraLarghezza(nodo: HTMLElement) {
    const leggi = () => (largTracce = nodo.clientWidth);
    leggi();
    if (typeof ResizeObserver === 'undefined') return;
    const ro = new ResizeObserver(leggi);
    ro.observe(nodo);
    return { destroy: () => ro.disconnect() };
  }
  const entra = (t: { testo: string; width: number }) => largTracce === 0 || (t.width / 100) * largTracce >= t.testo.length * 7.4 + 14;
  const legenda = $derived.by(() => {
    if (!stagione) return [] as { chiave: string; corsia: string; testo: string }[];
    const d = (i: number) => dataBreve(dataIndice(stagione.dal, i));
    const voce = (corsia: string, verso: 'dal' | 'finoAl', t: { da: number; a: number; testo: string; cls: string }) => ({
      chiave: `${corsia}${t.da}`, corsia,
      testo: t.cls === 'rec' || (verso === 'dal' && t.cls === 'zero') ? `${t.testo} fino al ${d(t.a)}` : verso === 'dal' ? `${t.testo} dal ${d(t.da)}` : t.testo,
    });
    return [
      ...trattiRid.filter((t) => !entra(t)).map((t) => voce('Riduzione', 'dal', t)),
      ...trattiRec.filter((t) => !entra(t)).map((t) => voce('Recesso', 'finoAl', t)),
    ];
  });
  const scadenzeRate = $derived(bozza?.rate.attiva ? bozza.rate.scadenze.slice(0, bozza.rate.numero - 1).filter(Boolean) : []);

  // ── L'esempio ─────────────────────────────────────────────────────────
  type Opzione = { chiave: string; nome: string; listino: number; riduzione: boolean };
  const opzioni = $derived<Opzione[]>(
    (pagina?.offerte ?? []).filter((o: Offerta) => o.tipo === 'CORSO' && o.attiva).flatMap((o) =>
      o.prezzi.filter((p) => !p.gruppoId).map((p) => ({ chiave: `${o.id}|${p.volte}`, nome: `${o.nome}${p.volte == null ? '' : ` · ${etichettaVolte(p.volte)}`}`, listino: p.importo, riduzione: o.riduzioneTardiva }))),
  );
  let scelta = $state('');
  const opzione = $derived(opzioni.find((o) => o.chiave === scelta) ?? opzioni[0]);
  const num = (x: { data: string; percentuale: string }[]): Scaglione[] => x.filter((s) => s.data).map((s) => ({ data: s.data, percentuale: Number(s.percentuale) || 0 }));
  const esempio = $derived.by(() => {
    if (!bozza || !opzione) return null;
    const rid = bozza.riduzione.attiva && opzione.riduzione ? riduzioneDel(num(bozza.riduzione.scaglioni), riferimento) : 0;
    const paga = Math.round(opzione.listino * (100 - rid)) / 100;
    const rate = bozza.rate.attiva ? rateDel(paga, bozza.rate.numero, Number(bozza.rate.primaPercentuale) || 50, bozza.rate.scadenze, riferimento) : { subito: paga, dopo: [] };
    const scadute = bozza.rate.attiva ? bozza.rate.scadenze.slice(0, bozza.rate.numero - 1).filter((s) => s && s < riferimento) : [];
    const rec = bozza.recesso.attiva ? recessoDel(num(bozza.recesso.scaglioni), riferimento) : null;
    const ultimoRec = num(bozza.recesso.scaglioni).sort((a, b) => a.data.localeCompare(b.data)).at(-1);
    return {
      paga: euro(paga), perche: rid ? `${rid} % di riduzione tardiva su ${euro(opzione.listino)}` : `prezzo pieno, ${euro(opzione.listino)}`,
      rata: bozza.rate.attiva ? testoRate(rate, euro) : `1 × ${euro(paga)}`,
      rateSotto: !bozza.rate.attiva ? 'rate spente in questa stagione'
        : rate.dopo.length ? (scadute.length ? `subito, poi entro il ${rate.dopo.map((r) => (r.scadenza ? dataBreve(r.scadenza) : '—')).join(', ')}` : `all'iscrizione, poi entro il ${rate.dopo.map((r) => (r.scadenza ? dataBreve(r.scadenza) : '—')).join(', ')}`)
        : `rate chiuse il ${scadute.length ? dataBreve(scadute.at(-1)!) : '—'}`,
      rimborso: rec ? euro(Math.round(paga * rec.percentuale) / 100) : '0 €',
      rimbSotto: !bozza.recesso.attiva ? 'il recesso non dà rimborsi' : rec ? `${rec.percentuale} % fino al ${dataBreve(rec.data)}` : `richieste di rimborso scadute il ${ultimoRec ? dataBreve(ultimoRec.data) : '—'}`,
    };
  });
  const esempioSospensione = $derived.by(() => {
    if (!bozza || !opzione) return '';
    // come il server: il prezzo diviso per i giorni dell'abbonamento (qui tutta la stagione), per i giorni restituiti
    const allaSettimana = Math.round(((opzione.listino * 7) / Math.max(7, n)) * 100) / 100;
    const min = Number(bozza.sospensione.settimaneMinime) || 0;
    const stop = Math.max(min + 3, 9);
    const date = bozza.sospensione.settimaneRestituite === 'OLTRE_SOGLIA' ? stop - min : stop;
    const dove = { BORSELLINO: ' nel borsellino', ALLUNGA: ': l\'abbonamento si allunga di altrettanto', A_SCELTA: ', nel borsellino o allungando l\'abbonamento' }[bozza.sospensione.restituzione] ?? '';
    return `Abbonamento da ${euro(opzione.listino)}, circa ${euro(allaSettimana)} a settimana. Stop di ${quanti(stop, 'settimana', 'settimane')} con il certificato in tempo: ${date} × ${euro(allaSettimana)} ≈ ${euro(Math.round(((opzione.listino * date * 7) / Math.max(7, n)) * 100) / 100)}${dove}.`;
  });
</script>

<div class="barra">
  <SceltaStagione {stagioni} bind:valore={stagioneId} />
  <span class="fumetti"><Fumetto tema="l'esempio">Tocca o trascina sulla linea per vedere un esempio in un giorno qualsiasi.{#if scadenzeRate.length} La linea rossa è la scadenza della 2ª rata: si imposta nella sezione Rate.{/if}</Fumetto>
  <Fumetto tema="quando valgono le regole">Le modifiche alle regole valgono dopo il salvataggio, per le iscrizioni e i recessi da quel momento in poi.</Fumetto></span>
</div>
{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
{#if messaggio && !cambiate.length}<p class="msg ok" role="status">{messaggio}</p>{/if}
{#if !stagioni.length && !errore}<p class="muted">Nessuna stagione: prima aggiungine una in <a href="#/societa">Dati, stagioni e gruppi</a>.</p>{/if}

{#if bozza && stagione}
  <section class="card linea-s" aria-label="La stagione">
    <button type="button" class="oggi-t" onclick={() => (giorno = null)} title="Torna a oggi">Oggi: {dataBreve(oggi)}</button>
    <div class="mesi" role="group" aria-label="Mesi della stagione">
      {#each mesi as m (m.mese)}
        <button type="button" class:dentro={meseDi(riferimento) === m.mese} style="width: {m.largo}%" onclick={() => (giorno = `${m.mese}-15` > stagione.al ? stagione.al : `${m.mese}-15`)}>{m.nome}</button>
      {/each}
    </div>
    <div class="nomi">
      <span class="corsia-n"><b>Riduzione tardiva</b><span>chi si iscrive a stagione iniziata</span></span>
      <span class="corsia-n"><b>Recesso</b><span>quanto si rimborsa</span></span>
    </div>
    <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
    <div class="tracce" role="slider" tabindex="0" aria-label="Giorno dell'esempio" aria-valuemin={0} aria-valuemax={n - 1} aria-valuenow={indiceGiorno(stagione.dal, riferimento)}
         use:misuraLarghezza aria-valuetext={dataBreve(riferimento)} onpointerdown={giu} onpointermove={muovi} onpointerup={su} onpointercancel={su} onkeydown={tastiLinea}>
      <div class="corsia">
        {#each trattiRid as t (t.da)}<span class="tratto {t.cls}" style="left: {t.left}%; width: {t.width}%" title={t.testo}>{entra(t) ? t.testo : ''}</span>{/each}
      </div>
      <div class="corsia">
        {#each trattiRec as t (t.da)}<span class="tratto {t.cls}" style="left: {t.left}%; width: {t.width}%" title={t.testo}>{entra(t) ? t.testo : ''}</span>{/each}
      </div>
      {#each scadenzeRate as d, i (d)}
        <span class="scad-l" style="left: {px(d)}"></span>
        <span class="scad-e" style="left: {px(d)}"><b>Scadenza {i + 2}ª rata</b>{dataBreve(d)}</span>
      {/each}
      {#if oggiDentro}
        <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
        <span class="oggi-l" style="left: {px(oggi)}" title="Torna a oggi" onpointerdown={(e) => { e.stopPropagation(); giorno = null; }}></span>
        <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
        <span class="oggi-e" style="left: {px(oggi)}" title="Torna a oggi" onpointerdown={(e) => { e.stopPropagation(); giorno = null; }}>Oggi</span>
      {/if}
      {#if giorno}
        <span class="scelto-l" style="left: {px(giorno)}"></span>
        <span class="scelto-e" style="left: {px(giorno)}">{dataBreve(giorno)}</span>
      {/if}
    </div>
    {#if legenda.length}
      <!-- le fasce troppo strette per la scritta non la portano: la dicono qui, per intero -->
      <ul class="legenda" aria-label="Le fasce della linea">{#each legenda as l (l.chiave)}<li><b>{l.corsia}</b> {l.testo}</li>{/each}</ul>
    {/if}
  </section>

  {#if esempio}
    <section class="card pann" aria-label="Esempio">
      <span class="testa-e"><h2 class="grow">Se un cliente si iscrive {giorno ? `il ${dataBreve(giorno)}` : `oggi, ${dataBreve(riferimento)}`}</h2>
        <label class="scegli"><span class="muted small">all'abbonamento</span>
          <select class="inp" bind:value={scelta} aria-label="Abbonamento dell'esempio">{#each opzioni as o (o.chiave)}<option value={o.chiave}>{o.nome} · {euro(o.listino)}</option>{/each}</select></label></span>
      {#if opzione && !opzione.riduzione}<span class="muted small">Questo abbonamento ha un prezzo unico: la riduzione tardiva non si applica.</span>{/if}
      <div class="esempio">
        <div class="es"><span class="lbl">Paga</span><span class="v">{esempio.paga}</span><span class="n">{esempio.perche}</span></div>
        <div class="es"><span class="lbl">Rate</span><span class="v">{esempio.rata}</span><span class="n">{esempio.rateSotto}</span></div>
        <div class="es"><span class="lbl">Rimborso in caso di recesso</span><span class="v">{esempio.rimborso}</span><span class="n">{esempio.rimbSotto}</span></div>
      </div>
    </section>
  {:else if pagina && !opzioni.length}
    <section class="card pann" aria-label="Esempio">
      <StatoVuoto testo="Nessun abbonamento nel listino." />
      {#if modifica}<div class="az-vuoto"><a class="btn btn-secondary btn-sm" href="#/listino"><Icona nome="piu" />Aggiungi un abbonamento</a></div>{/if}
    </section>
  {/if}

  <fieldset class="colonne-p" disabled={!modifica}>
    <div class="princ">
      <section class="card sez" id="imp-riduzione" aria-label="Riduzione tardiva">
        <div class="sez-t"><button type="button" class="switch tap44" role="switch" aria-checked={bozza.riduzione.attiva} aria-label="Riduzione tardiva accesa" onclick={() => (bozza!.riduzione.attiva = !bozza!.riduzione.attiva)}><span></span></button>
          <h2 class="grow">Riduzione tardiva</h2><Fumetto tema="la riduzione tardiva">Per chi si iscrive a stagione iniziata. Vale solo sulle offerte segnate nel listino.</Fumetto></div>
        {@render scaglioni('riduzione')}
      </section>

      <section class="card sez" id="imp-recesso" aria-label="Rimborso in caso di recesso">
        <div class="sez-t"><button type="button" class="switch tap44" role="switch" aria-checked={bozza.recesso.attiva} aria-label="Rimborso per recesso acceso" onclick={() => (bozza!.recesso.attiva = !bozza!.recesso.attiva)}><span></span></button>
          <h2 class="grow">Recesso</h2><Fumetto tema="il recesso">Il rimborso in caso di recesso: quanto si restituisce di un abbonamento già pagato.</Fumetto></div>
        {@render scaglioni('recesso')}
        <div class="riga-imp"><span class="t"><span class="con-i"><b>Dove va il rimborso</b><Fumetto tema="il rimborso">Sul prezzo pagato dell'abbonamento: la quota associativa non si rimborsa.</Fumetto></span></span>
          <span class="ctl"><select class="inp" bind:value={bozza.recesso.rimborso} aria-label="Dove va il rimborso"><option value="BORSELLINO">Nel borsellino del cliente</option><option value="DENARO">Restituito in denaro</option><option value="A_SCELTA">Lo sceglie il cliente</option></select></span></div>
      </section>
    </div>

    <aside class="lato-p">
      <section class="card sez" id="imp-sospensione" aria-label="Sospensione per malattia">
        <div class="sez-t"><button type="button" class="switch tap44" role="switch" aria-checked={bozza.sospensione.attiva} aria-label="Sospensione per malattia accesa" onclick={() => (bozza!.sospensione.attiva = !bozza!.sospensione.attiva)}><span></span></button>
          <h2 class="grow">Sospensione</h2><Fumetto tema="la sospensione">La sospensione dell'abbonamento per malattia o infortunio: chi si ferma riceve le settimane perse, se rispetta queste condizioni.</Fumetto></div>
        <div class="riga-imp"><span class="t"><b>Lo stop deve durare più di</b><span>sotto questa soglia non si restituisce niente</span></span>
          <span class="ctl"><input class="inp num" type="number" min="0" max="52" bind:value={bozza.sospensione.settimaneMinime} aria-label="Settimane minime" /><span class="unita">settimane</span></span></div>
        <div class="riga-imp"><span class="t"><b>Certificato medico entro</b><span>dall'inizio dello stop; dopo, lo stop non conta</span></span>
          <span class="ctl"><input class="inp num" type="number" min="0" max="52" bind:value={bozza.sospensione.settimaneCertificato} aria-label="Settimane per il certificato" /><span class="unita">settimane</span></span></div>
        <div class="riga-imp"><span class="t"><b>Settimane restituite</b></span>
          <span class="ctl"><select class="inp" bind:value={bozza.sospensione.settimaneRestituite} aria-label="Settimane restituite"><option value="TUTTE">Tutte quelle di stop</option><option value="OLTRE_SOGLIA">Solo quelle oltre la soglia</option></select></span></div>
        <div class="riga-imp"><span class="t"><b>Come si restituiscono</b></span>
          <span class="ctl"><select class="inp" bind:value={bozza.sospensione.restituzione} aria-label="Come si restituiscono"><option value="BORSELLINO">Credito nel borsellino</option><option value="ALLUNGA">Si allunga l'abbonamento</option><option value="A_SCELTA">Si sceglie ogni volta</option></select></span></div>
        {#if esempioSospensione}<div class="riga-imp"><span class="t"><span class="lbl">Esempio</span><span>{esempioSospensione}</span></span></div>{/if}
      </section>

      <section class="card sez" id="imp-rate" aria-label="Rate">
        <div class="sez-t"><button type="button" class="switch tap44" role="switch" aria-checked={bozza.rate.attiva} aria-label="Rate accese" onclick={() => (bozza!.rate.attiva = !bozza!.rate.attiva)}><span></span></button>
          <h2 class="grow">Rate</h2><Fumetto tema="le rate">Il resto dopo la prima rata si divide in parti uguali.</Fumetto></div>
        <div class="riga-imp"><span class="t"><b>1ª rata</b><span>all'iscrizione</span></span>
          <span class="ctl"><input class="inp num" type="number" min="1" max="99" bind:value={bozza.rate.primaPercentuale} aria-label="Percentuale della 1ª rata" aria-invalid={primaRataNonValida(bozza.rate.primaPercentuale)} aria-describedby={primaRataNonValida(bozza.rate.primaPercentuale) ? 'err-rata' : undefined} /><span class="unita">%</span></span>{#if primaRataNonValida(bozza.rate.primaPercentuale)}<ErroreCampo id="err-rata">{MSG_PRIMA_RATA}</ErroreCampo>{/if}</div>
        {#each bozza.rate.scadenze.slice(0, bozza.rate.numero - 1) as _, i (chiavi.rate[i])}
          <div class="riga-imp"><span class="t"><b>{i + 2}ª rata entro il</b>{#if i === bozza.rate.numero - 2}<span>dopo questa data le rate non si offrono più</span>{/if}</span>
            <span class="ctl"><input class="inp m data" type="date" bind:value={bozza.rate.scadenze[i]} aria-label="Scadenza della {i + 2}ª rata" />
              {#if bozza.rate.numero > 2 && modifica}
                <Azione icona="cestino" tono="danger" etichetta="Elimina la {i + 2}ª rata" conferma="Elimina?" onclick={() => togliRata(i)} />
              {:else}<span class="posto-azione" aria-hidden="true"></span>{/if}</span></div>
        {/each}
        {#if bozza.rate.numero < 12 && modifica}
          <div class="riga-imp riga-aggiungi"><button type="button" class="aggiungi" onclick={aggiungiRata}><Icona nome="piu" /><span>Aggiungi una rata</span></button></div>
        {/if}
      </section>
    </aside>
  </fieldset>

  <BarraSalva {valida} modifiche={contaModificheRighe()} onannulla={annulla} onsalva={salva} />
{/if}

{#snippet scaglioni(sez: 'riduzione' | 'recesso')}
  {@const lista = bozza![sez].scaglioni}
  {@const nomeCampo = sez === 'riduzione' ? 'Dal' : 'Fino al'}
  <ul class="passi">
    {#each lista as s, i (chiavi[sez][i])}
      {@const errato = scaglioneNonValido(s)}
      <li class="passo">
        <div class="riga-sc">
          <label class="campo-d"><span class="unita">{nomeCampo}</span><input class="inp m" type="date" bind:value={s.data} min={stagione!.dal} max={stagione!.al} aria-label={nomeCampo} /></label>
          <span class="campo-p"><input class="inp m perc" type="number" min="0" max="100" bind:value={s.percentuale} aria-label={sez === 'riduzione' ? 'Percentuale' : 'Percentuale rimborsata'} aria-invalid={errato} aria-describedby={errato ? `err-${sez}-${chiavi[sez][i]}` : undefined} /><span class="unita">%</span></span>
          {#if modifica}<Azione icona="cestino" tono="danger" etichetta="Elimina lo scaglione {nomeScaglione(sez, s, i)}" conferma="Elimina?" onclick={() => togliScaglione(sez, i)} />{/if}
        </div>
        {#if errato}<ErroreCampo id="err-{sez}-{chiavi[sez][i]}">{MSG_PERCENTUALE}</ErroreCampo>{/if}
      </li>
    {/each}
    {#if sez === 'recesso'}<li class="passo"><div class="riga-sc fissa"><span class="unita">Dopo</span><span class="muted small">niente rimborso</span></div></li>{/if}
    {#if modifica}<li class="ultima"><button type="button" class="aggiungi" onclick={() => aggiungiScaglione(sez)}><Icona nome="piu" /><span>Aggiungi uno scaglione</span></button></li>{/if}
  </ul>
{/snippet}

<style>
  /* il titolo di una sezione sta su una riga (si tronca se la colonna è stretta); fumetto e interruttore restano interi */
  .sez-t { flex-wrap: nowrap; }
  /* larghezza 0 + crescita: il titolo prende lo spazio che resta e non allarga la sezione (un testo senza a capo ha altrimenti la sua larghezza piena come minimo) */
  .sez-t h2 { flex: 1 1 0; width: 0; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  @media (max-width: 380px) {
    .sez-t { padding-inline: var(--space-12); gap: var(--space-6); }
    .sez-t h2 { font-size: var(--text-xl); }
  }
  .passo :global(.muted) { min-width: 0; }
  .barra { display: flex; align-items: center; gap: var(--space-12); flex-wrap: wrap; }
  .fumetti { display: inline-flex; align-items: center; gap: var(--space-8); flex-wrap: nowrap; }
  .legenda { grid-column: 1 / -1; list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--space-4); font-size: var(--text-sm); color: var(--muted); }
  .legenda b { color: var(--ink); }
  .az-vuoto { padding: var(--space-8) var(--space-12) var(--space-12); }
  select.inp { appearance: auto; }
  fieldset { border: none; margin: 0; padding: 0; min-width: 0; }
  /* la stagione su una linea: un mese per colonna, una corsia per regola */
  .linea-s { display: grid; grid-template-columns: 200px minmax(0, 1fr); row-gap: var(--space-10); padding: var(--space-16) var(--space-16) var(--space-12); overflow: visible; }
  .oggi-t { position: relative; justify-self: start; align-self: center; display: inline-flex; align-items: center; gap: 6px; height: 28px; padding: 0 12px; border-radius: 999px; border: none;
    background: var(--cloro); color: var(--on-cloro); font: inherit; font-size: var(--text-sm); font-weight: 700; white-space: nowrap; cursor: pointer; }
  .oggi-t::after { content: ''; position: absolute; left: 0; right: 0; top: 50%; height: 44px; transform: translateY(-50%); }
  .oggi-t::before { content: ''; width: 8px; height: 8px; border-radius: 50%; background: var(--on-cloro); }
  .mesi { display: flex; min-width: 0; }
  .mesi button { flex: 1 1 0; min-width: 0; }
  .mesi button { height: 30px; border: none; border-left: 1px solid var(--line-soft); background: none; font: inherit; font-size: 11px; font-weight: 700; letter-spacing: 0.06em;
    text-transform: uppercase; color: var(--muted); cursor: pointer; padding: 0; overflow: hidden; }
  .mesi button.dentro { background: var(--fill); color: var(--on-fill); border-radius: 6px; }
  .nomi { display: flex; flex-direction: column; gap: var(--space-10); padding-bottom: 22px; }
  .corsia-n { height: 44px; display: flex; flex-direction: column; justify-content: center; gap: 2px; padding-right: var(--space-12); }
  .corsia-n b { font-size: var(--text-md); }
  .corsia-n span { font-size: var(--text-xs); color: var(--muted); }
  /* le corsie in un solo blocco: le linee di oggi e del giorno scelto le attraversano tutte */
  .tracce { position: relative; display: flex; flex-direction: column; gap: var(--space-10); padding-bottom: 22px; cursor: crosshair; touch-action: none; user-select: none; }
  .tracce:focus-visible { outline: 2px solid var(--cloro); outline-offset: 4px; border-radius: 10px; }
  .corsia { position: relative; height: 44px; border-radius: 10px; background: var(--surface-sunken); overflow: hidden; }
  .tratto { position: absolute; top: 4px; bottom: 4px; border-radius: 7px; display: flex; align-items: center; justify-content: center; font-size: var(--text-xs); font-weight: 700;
    white-space: nowrap; overflow: hidden; }
  .tratto.rid { background: var(--cloro-soft); color: var(--cloro-ink); box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--cloro) 35%, transparent); }
  .tratto.rec { background: var(--warn-bg); color: var(--warn); box-shadow: inset 0 0 0 1px var(--warn-line); }
  .tratto.zero { background: none; color: var(--muted); font-weight: 600; }
  .oggi-l { position: absolute; top: 0; bottom: 19px; width: 3px; margin-left: -1.5px; border-radius: 2px; background: var(--cloro); z-index: 2; cursor: pointer; }
  .oggi-l::after { content: ''; position: absolute; top: 0; bottom: 0; left: -6px; right: -6px; }
  .oggi-e { position: absolute; bottom: 0; transform: translateX(-50%); padding: 2px 8px; border-radius: 999px; background: var(--cloro); color: var(--on-cloro); font-size: 11px;
    font-weight: 700; white-space: nowrap; cursor: pointer; z-index: 2; }
  /* scadenza delle rate: rossa, più sottile di oggi; l'etichetta compare solo passando sopra la linea */
  .scad-l { position: absolute; top: 0; bottom: 22px; width: 2px; margin-left: -1px; background: var(--ferma); z-index: 1; }
  .scad-l::after { content: ''; position: absolute; top: 0; bottom: 0; left: -6px; right: -6px; }
  .scad-e { position: absolute; top: 50%; margin-left: 10px; transform: translateY(-50%); padding: 5px 9px; border-radius: 8px; background: var(--surface); color: var(--ink);
    box-shadow: 0 0 0 1px var(--line), 0 6px 16px rgba(16, 33, 43, 0.14); font-size: var(--text-xs); line-height: 1.3; white-space: nowrap; pointer-events: none; z-index: 5;
    opacity: 0; transition: opacity 0.12s; display: flex; flex-direction: column; }
  .scad-e b { font-size: 11px; letter-spacing: 0.04em; text-transform: uppercase; color: var(--ferma); }
  .scad-l:hover + .scad-e { opacity: 1; }
  /* il giorno dell'esempio: tratteggiata, si sposta toccando o trascinando */
  .scelto-l { position: absolute; top: -4px; bottom: 22px; width: 0; margin-left: -1px; border-left: 2px dashed var(--ink); z-index: 3; pointer-events: none; }
  .scelto-e { position: absolute; bottom: 0; transform: translateX(-50%); padding: 2px 8px; border-radius: 999px; background: var(--ink); color: var(--surface); font-size: 11px;
    font-weight: 700; white-space: nowrap; pointer-events: none; z-index: 4; }
  .testa-e { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .scegli { display: flex; align-items: center; gap: var(--space-8); min-width: 0; max-width: 100%; }
  .scegli select { width: auto; max-width: 100%; height: 40px; field-sizing: content; padding-right: 32px; }
  .esempio { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-12); }
  .es { display: flex; flex-direction: column; gap: 4px; padding: var(--space-14); border-radius: var(--radius-md); background: var(--carta); }
  .es .v { font-family: var(--font-display); font-size: 26px; font-weight: 800; line-height: 1.1; }
  .es .n { font-size: var(--text-sm); color: var(--muted); }
  /* scaglioni: righe d'elenco; data, percentuale e cestino stanno sempre sulla stessa riga */
  .passi { list-style: none; margin: 0; display: flex; flex-direction: column; padding: var(--space-6) var(--space-16) var(--space-14); }
  .passo { border-top: 1px solid var(--line-soft); padding: var(--space-6) 0; }
  .passo:first-child { border-top: none; }
  .riga-sc { display: flex; align-items: center; gap: var(--space-8); min-height: 52px; min-width: 0; max-width: 480px; }
  .campo-d { flex: 1 1 0; min-width: 0; display: flex; align-items: center; gap: var(--space-10); max-width: 260px; }
  .campo-d .unita { min-width: 52px; white-space: nowrap; }
  .campo-d .inp { flex: 1 1 0; min-width: 0; height: 44px; }
  .campo-p { flex: 0 0 auto; display: flex; align-items: center; gap: var(--space-6); margin-left: auto; }
  .campo-p .unita { white-space: nowrap; }
  .riga-sc.fissa .unita { min-width: 52px; }
  .riga-sc > :global(.azione) { margin-left: 0; }
  .perc { width: 64px; height: 44px; text-align: center; }
  .ultima { list-style: none; padding: var(--space-8) 0 0; }
  .aggiungi { display: flex; align-items: center; justify-content: center; gap: var(--space-8); width: 100%; min-height: 48px; padding: 0 var(--space-12); background: transparent;
    border: 1px dashed var(--line-dashed); border-radius: var(--radius-md); color: var(--cloro-ink); font: inherit; font-weight: 600; font-size: var(--text-base); cursor: pointer; }
  .aggiungi span { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; min-width: 0; }
  .aggiungi :global(svg) { color: var(--cloro); }
  .aggiungi:hover { background: var(--cloro-soft); border-color: var(--cloro); }
  .riga-aggiungi { padding-top: var(--space-6); }
  .posto-azione { width: 40px; height: 40px; flex-shrink: 0; }
  .con-i { display: flex; align-items: center; gap: var(--space-8); min-width: 0; }
  .riga-imp > .t > .con-i { color: var(--ink); font-size: var(--text-base); }
  .data { width: 160px; height: 40px; }
  .lato-p :global(.riga-imp .t) { flex-basis: 170px; }
  .lato-p :global(.riga-imp .ctl) { margin: 0 auto; justify-content: center; }
  @media (max-width: 900px) {
    .linea-s { grid-template-columns: minmax(0, 1fr); }
    .nomi { display: none; }
    .esempio { grid-template-columns: 1fr; }
    .scegli { flex: 1 1 100%; flex-wrap: wrap; }
    .scegli select { field-sizing: fixed; width: 100%; }
  }
  /* telefono: l'etichetta «Dal» / «Fino al» sale sopra la data, così data, percentuale e cestino restano sulla riga */
  @media (max-width: 480px) {
    .passi { padding-inline: var(--space-8); }
    .riga-sc { gap: var(--space-6); }
    .campo-d { flex-direction: column; align-items: stretch; gap: 0; max-width: none; }
    .campo-d .unita { min-width: 0; font-size: var(--text-xs); color: var(--muted); }
    .campo-d .inp { flex: 0 0 auto; padding-inline: var(--space-8); }
    .perc { width: 56px; padding-inline: var(--space-4); }
    /* col cestino armato («Elimina?») la riga non basta più: la percentuale si nasconde finché non si conferma o si lascia stare */
    .riga-sc:has(:global(.azione.armata)) .campo-p { display: none; }
    .mesi button { font-size: 10px; letter-spacing: 0; height: 44px; }
  }
  @media (max-width: 360px) { .mesi button { font-size: 9px; letter-spacing: -0.02em; } }
  /* la percentuale ha 3 cifre al massimo: senza le frecce del campo numerico ci stanno anche a 64px */
  .inp.perc { -moz-appearance: textfield; appearance: textfield; }
  .inp.perc::-webkit-inner-spin-button, .inp.perc::-webkit-outer-spin-button { -webkit-appearance: none; margin: 0; }
</style>
