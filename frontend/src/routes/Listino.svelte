<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import Azione from '../components/Azione.svelte';
  import Foglio from '../components/Foglio.svelte';
  import Fumetto from '../components/Fumetto.svelte';
  import RiquadroAcceso from '../components/RiquadroAcceso.svelte';
  import SceltaStagione from '../components/SceltaStagione.svelte';
  import Schede from '../components/Schede.svelte';
  import StatoVuoto from '../components/StatoVuoto.svelte';
  import { api, ErroreApi, messaggioErrore, type Corso, type GruppoClienti, type NuovaOfferta, type Offerta, type PaginaListino, type Stagione, type TipoOfferta } from '../lib/api';
  import { righeBlocco } from '../lib/blocco';
  import { dataLunga } from '../lib/date';
  import { etichettaVolte, VOLTE } from '../lib/listino';
  import { euro, euroColonna, importoNonValido, numeroDa, perCampo } from '../lib/numeri';
  import { quanti } from '../lib/plurali';
  import { ordinaStagioni } from '../lib/stagioni';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Il listino di una stagione (ADR 0018, 0024, 0029): le offerte in tabella, con i prezzi per frequenza in colonna così
   * si confrontano, divise per tipo in schede; tocca un'offerta per cambiarla nel foglio (acceso o spento, campi, prezzi,
   * duplica, elimina). Le regole di prezzo hanno la loro pagina. Chi non ha IMPOSTAZIONI vede il listino e basta.
   */
  const TIPI: { id: TipoOfferta; nome: string; nuovo: string }[] = [
    { id: 'CORSO', nome: 'Abbonamenti ai corsi', nuovo: 'Nuovo abbonamento' },
    { id: 'CARNET', nome: 'Carnet', nuovo: 'Nuovo carnet' },
    { id: 'ALTRO', nome: 'Altri importi', nuovo: 'Nuovo importo' },
  ];

  let stagioni = $state<Stagione[]>([]);
  let stagioneId = $state('');
  let pagina = $state<PaginaListino | null>(null);
  let corsi = $state<Corso[]>([]);
  let gruppi = $state<GruppoClienti[]>([]);
  let tipo = $state<TipoOfferta>('CORSO');
  let errore = $state('');
  let messaggio = $state('');
  let daZero = $state(false);
  const modifica = $derived(sessione.puo('IMPOSTAZIONI'));

  async function avvia() {
    try {
      [stagioni, corsi, gruppi] = await Promise.all([api.stagioni(), api.corsi(), api.gruppi().catch(() => [])]);
      // la stagione di partenza la sceglie `SceltaStagione` (in corso, poi la futura più vicina, poi l'ultima finita)
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a caricare il listino.');
    }
  }
  avvia();

  async function carica() {
    if (!stagioneId) return;
    try {
      pagina = await api.listino(stagioneId);
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a caricare il listino.');
    }
  }
  $effect(() => {
    void stagioneId;
    offerta = null;
    daZero = false;
    copiaDa = ordinaStagioni(stagioni).filter((s) => s.id !== stagioneId).at(-1)?.id ?? '';
    carica();
  });

  async function esegui(azione: () => Promise<unknown>, fatto: string) {
    errore = '';
    messaggio = '';
    try {
      await azione();
      await carica();
      messaggio = fatto;
      return true;
    } catch (e) {
      errore = messaggioErrore(e);
      return false;
    }
  }

  const nomeCorso = (id: string | null) => corsi.find((c) => c.id === id)?.nome ?? 'corso';
  const nomeGruppo = (id: string | null) => (id ? gruppi.find((g) => g.id === id)?.nome ?? 'gruppo' : 'Tutti');
  const vuota = $derived(!!pagina && !pagina.offerte.length && !daZero);
  const schede = $derived(TIPI.map((t) => ({ id: t.id, nome: t.nome, segno: { testo: String(pagina?.offerte.filter((o) => o.tipo === t.id).length ?? 0) } })));

  // ── La tabella: una colonna per frequenza (abbonamenti), o ingressi/validità/prezzo ──
  function validita(o: Offerta) {
    if (o.validitaMesi) return o.validitaMesi === 1 ? '1 mese' : `${o.validitaMesi} mesi`;
    if (o.validitaGiorni) return o.validitaGiorni === 1 ? 'il giorno stesso' : `${o.validitaGiorni} giorni`;
    return 'senza scadenza';
  }
  function sotto(o: Offerta) {
    const parti = o.tipo === 'CORSO' ? [o.corsi.slice(0, 4).map(nomeCorso).join(', ') + (o.corsi.length > 4 ? ` e altri ${o.corsi.length - 4}` : '')] : o.tipo === 'CARNET' ? [o.corsoId ? `negli orari di ${nomeCorso(o.corsoId)}` : 'entrata libera'] : [o.descrizione ?? ''];
    if (o.tipo !== 'ALTRO') parti.push(o.riduzioneTardiva ? 'riduzione tardiva' : 'prezzo unico in tutta la stagione');
    return parti.filter(Boolean).join(' · ');
  }
  /** Il prezzo per tutti di quella frequenza, e quelli per i gruppi sotto. */
  const prezzo = (o: Offerta, volte: number | null) => ({
    tutti: o.prezzi.find((p) => p.volte === volte && !p.gruppoId)?.importo ?? null,
    gruppi: o.prezzi.filter((p) => p.volte === volte && p.gruppoId).map((p) => ({ id: p.gruppoId!, nome: nomeGruppo(p.gruppoId), importo: euroColonna(p.importo) })),
  });

  // ── Il foglio dell'offerta ────────────────────────────────────────────
  type Bozza = {
    nome: string; descrizione: string; tipo: TipoOfferta; corsi: string[]; corsoId: string | null; ingressi: string; validita: string; unita: 'mesi' | 'giorni';
    riduzioneTardiva: boolean; attiva: boolean; ordine: number; righe: string[]; colonne: string[]; prezzi: Record<string, string>;
  };
  let offerta = $state<{ id: string | null; b: Bozza } | null>(null);
  let scegliCorsi = $state(false);
  /** Gli errori del foglio stanno nel foglio (la pagina dietro li nasconderebbe); il blocco dell'eliminazione ha le sue due righe. */
  let erroreFoglio = $state('');
  let bloccoElimina = $state('');
  let inSalvataggio = $state(false);
  const chiave = (volte: string, gruppo: string) => `${volte}|${gruppo}`;
  const chiaveRiga = (v: number | null) => (v == null ? '' : String(v));

  function apri(o: Offerta | null, t: TipoOfferta = tipo) {
    scegliCorsi = false;
    erroreFoglio = '';
    bloccoElimina = '';
    const v = (n: number | null) => (n == null ? '' : String(n));
    if (o) {
      const righe = [...new Set(o.prezzi.map((p) => (o.tipo === 'CORSO' ? v(p.volte) : '')))];
      const colonne = ['', ...new Set(o.prezzi.filter((p) => p.gruppoId).map((p) => p.gruppoId!))];
      offerta = { id: o.id, b: { nome: o.nome, descrizione: o.descrizione ?? '', tipo: o.tipo, corsi: [...o.corsi], corsoId: o.corsoId, ingressi: v(o.ingressi),
        validita: v(o.validitaMesi ?? o.validitaGiorni), unita: o.validitaGiorni ? 'giorni' : 'mesi', riduzioneTardiva: o.riduzioneTardiva, attiva: o.attiva, ordine: o.ordine,
        righe: righe.length ? righe.sort((a, b) => VOLTE.indexOf(a === '' ? null : Number(a)) - VOLTE.indexOf(b === '' ? null : Number(b))) : [t === 'CORSO' ? '1' : ''],
        colonne, prezzi: Object.fromEntries(o.prezzi.map((p) => [chiave(o.tipo === 'CORSO' ? v(p.volte) : '', p.gruppoId ?? ''), perCampo(p.importo)])) } };
    } else {
      offerta = { id: null, b: { nome: '', descrizione: '', tipo: t, corsi: [], corsoId: null, ingressi: t === 'CARNET' ? '10' : '', validita: t === 'CARNET' ? '3' : '', unita: 'mesi',
        riduzioneTardiva: t === 'CORSO', attiva: true, ordine: 0, righe: t === 'CORSO' ? ['1', '2'] : [''], colonne: [''], prezzi: {} } };
      if (t === 'CORSO') scegliCorsi = true;
    }
  }
  const chiudi = () => (offerta = null);

  function corpo(b: Bozza): NuovaOfferta {
    const numero = (s: string) => (s.trim() === '' ? null : Number(s));
    const prezzi = b.righe.flatMap((r) => b.colonne.map((c) => ({ r, c, imp: b.prezzi[chiave(r, c)] ?? '' })))
      .filter((x) => String(x.imp).trim() !== '')
      .map((x) => ({ volte: b.tipo === 'CORSO' ? numero(x.r) : null, gruppoId: x.c || null, importo: numeroDa(String(x.imp)) ?? NaN }));
    return {
      stagioneId, nome: b.nome.trim(), tipo: b.tipo, descrizione: b.descrizione.trim() || null, corsoId: b.tipo === 'CARNET' ? b.corsoId || null : null,
      corsi: b.tipo === 'CORSO' ? b.corsi : [], riduzioneTardiva: b.tipo === 'ALTRO' ? false : b.riduzioneTardiva, attiva: b.attiva, ordine: b.ordine,
      ingressi: b.tipo === 'CARNET' ? numero(b.ingressi) : null,
      validitaMesi: b.tipo === 'CARNET' && b.unita === 'mesi' ? numero(b.validita) : null, validitaGiorni: b.tipo === 'CARNET' && b.unita === 'giorni' ? numero(b.validita) : null,
      prezzi,
    };
  }
  /** I dati salvati di un'offerta, come corpo di una modifica: l'interruttore cambia solo `attiva` e non porta con sé la bozza del foglio. */
  function corpoSalvato(o: Offerta, attiva: boolean): NuovaOfferta {
    const { id, eliminabile, motivoBlocco, ...resto } = o;
    return { ...resto, attiva };
  }
  const prezziErrati = $derived(!!offerta && corpo(offerta.b).prezzi.some((x) => !Number.isFinite(x.importo) || x.importo < 0));
  const salvabile = $derived(!!offerta && !prezziErrati && !!offerta.b.nome.trim() && corpo(offerta.b).prezzi.length > 0 && (offerta.b.tipo !== 'CORSO' || offerta.b.corsi.length > 0));
  /** L'offerta aperta come sta sul server (null per una nuova). */
  const salvata = $derived(offerta?.id ? pagina?.offerte.find((o) => o.id === offerta!.id) ?? null : null);
  const accesoFoglio = $derived(salvata ? salvata.attiva : offerta?.b.attiva ?? true);
  const titoloFoglio = $derived(salvata ? salvata.nome : offerta?.id ? offerta.b.nome : TIPI.find((t) => t.id === offerta?.b.tipo)?.nuovo ?? 'Nuova offerta');

  // il foglio è su un'offerta che non c'è più (eliminata da un altro): si chiude
  $effect(() => {
    if (offerta?.id && pagina && !salvata) {
      offerta = null;
      messaggio = "L'offerta non c'è più.";
    }
  });

  /** Un'azione dal foglio: l'errore resta nel foglio. */
  async function eseguiNelFoglio(azione: () => Promise<unknown>) {
    errore = '';
    messaggio = '';
    erroreFoglio = '';
    bloccoElimina = '';
    try {
      await azione();
      await carica();
      return true;
    } catch (e) {
      erroreFoglio = messaggioErrore(e);
      return false;
    }
  }

  async function salvaOfferta(e?: SubmitEvent) {
    e?.preventDefault();
    if (!offerta || !salvabile || inSalvataggio) return;
    const { id, b } = offerta;
    inSalvataggio = true;
    try {
      if (await eseguiNelFoglio(() => (id ? api.modificaOfferta(id, corpo(b)) : api.creaOfferta(corpo(b))))) {
        messaggio = id ? `«${b.nome.trim()}» salvata.` : `«${b.nome.trim()}» aggiunta al listino.`;
        chiudi();
      }
    } finally {
      inSalvataggio = false;
    }
  }
  async function duplica() {
    const b = offerta!.b;
    if (await eseguiNelFoglio(() => api.creaOfferta({ ...corpo(b), nome: `${b.nome.trim()} (copia)` }))) {
      messaggio = `Copia di «${b.nome.trim()}» aggiunta: cambiale nome e prezzi.`;
      chiudi();
    }
  }
  /** Acceso o spento: subito, sui dati salvati (la bozza del foglio non c'entra); in una nuova cambia solo la bozza. */
  async function accendi(attiva: boolean) {
    if (!offerta) return;
    const o = salvata;
    if (!o) {
      offerta.b.attiva = attiva;
      return;
    }
    if (pagina) pagina.offerte = pagina.offerte.map((x) => (x.id === o.id ? { ...x, attiva } : x));
    if (await eseguiNelFoglio(() => api.modificaOfferta(o.id, corpoSalvato(o, attiva)))) {
      messaggio = attiva ? `«${o.nome}» di nuovo in vendita.` : `«${o.nome}» spenta: non si vende più, resta su chi l'ha già.`;
    } else {
      await carica();
    }
  }
  /** Il secondo tocco sul cestino: se il listino sa già che è venduta, dice perché senza chiamare il server. */
  async function elimina() {
    const o = salvata;
    if (!o) return;
    errore = '';
    messaggio = '';
    erroreFoglio = '';
    bloccoElimina = '';
    if (o.eliminabile === false) {
      bloccoElimina = o.motivoBlocco ?? `Non si può eliminare: «${o.nome}» è stata venduta.\nPuoi spegnerla: chi l'ha comprata la tiene com'è, ma non si vende più.`;
      return;
    }
    try {
      await api.eliminaOfferta(o.id);
      chiudi();
      await carica();
      messaggio = `«${o.nome}» eliminata dal listino.`;
    } catch (e) {
      if (e instanceof ErroreApi && e.status === 409) bloccoElimina = e.message;
      else erroreFoglio = messaggioErrore(e);
    }
  }
  function toggleCorso(id: string) {
    const b = offerta!.b;
    b.corsi = b.corsi.includes(id) ? b.corsi.filter((c) => c !== id) : [...b.corsi, id];
  }
  /** La spunta di una frequenza: tolta, sparisce la riga con i suoi prezzi (dalla bozza; si salva con «Salva l'offerta»). */
  function toggleRiga(v: number | null) {
    const b = offerta!.b;
    const k = chiaveRiga(v);
    if (b.righe.includes(k)) {
      b.righe = b.righe.filter((r) => r !== k);
      for (const c of b.colonne) delete b.prezzi[chiave(k, c)];
    } else {
      b.righe = [...b.righe, k].sort((x, y) => VOLTE.indexOf(x === '' ? null : Number(x)) - VOLTE.indexOf(y === '' ? null : Number(y)));
    }
  }
  /** La spunta di un gruppo: tolta, sparisce la colonna con i suoi prezzi. */
  function toggleColonna(id: string) {
    const b = offerta!.b;
    if (b.colonne.includes(id)) {
      b.colonne = b.colonne.filter((c) => c !== id);
      for (const r of b.righe) delete b.prezzi[chiave(r, id)];
    } else {
      b.colonne = [...b.colonne, id];
    }
  }
  const gruppiScelte = $derived(offerta ? gruppi.filter((g) => g.attivo || offerta!.b.colonne.includes(g.id)) : []);
  const riduzioneTesto = $derived.by(() => {
    const r = pagina?.regole.find((x) => x.tipo === 'RIDUZIONE_TARDIVA');
    const sc = ((r?.parametri.scaglioni as { percentuale: number }[] | undefined) ?? []).map((s) => s.percentuale);
    if (!r?.attiva) return 'La regola di riduzione è spenta in questa stagione.';
    return sc.length ? `Dal ${Math.min(...sc)} % al ${Math.max(...sc)} %, dalle regole di prezzo.` : 'Con gli scaglioni delle regole di prezzo.';
  });
  const nomeFrequenza = (k: string, tipoOfferta: TipoOfferta) => (tipoOfferta === 'CORSO' ? etichettaVolte(k === '' ? null : Number(k)) : 'Prezzo');

  // ── Copia da un'altra stagione ────────────────────────────────────────
  let copiaDa = $state('');
  let aumento = $state('3');
  let daCopiare = $state<number | null>(null);
  $effect(() => {
    if (copiaDa) api.listino(copiaDa).then((l) => (daCopiare = l.offerte.length)).catch(() => (daCopiare = null));
  });
  const copia = () => esegui(async () => {
    const r = await api.copiaListino(copiaDa, stagioneId, Number(aumento) || 0);
    saltate = r.saltate ?? [];
  }, '').then((ok) => ok && (messaggio = saltate.length
    ? `Listino e regole copiati. Non copiate perché senza prezzi: ${saltate.map((n) => `«${n}»`).join(', ')}.`
    : 'Listino e regole copiati: ora cambia solo quello che serve.'));
  let saltate = $state<string[]>([]);
  const stagioneScelta = $derived(stagioni.find((s) => s.id === stagioneId));
</script>

<div class="pagina">
  <div class="testa">
    <SceltaStagione {stagioni} bind:valore={stagioneId} />
    <Fumetto tema="le regole di prezzo">Riduzioni, rate e recesso sono nelle regole di prezzo.</Fumetto>
    <a class="btn btn-sm btn-secondary a-regole" href="#/prezzi">Regole di prezzo</a>
  </div>

  {#if !stagioni.length && !errore}
    <p class="muted nomargin">Nessuna stagione: prima aggiungine una in <a href="#/societa">Dati, stagioni e gruppi</a>.</p>
  {/if}
  {#if messaggio}<p class="msg ok nomargin" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  {#if pagina && vuota}
    <section class="card vuota">
      <span class="pill warn">Da preparare</span>
      <h2>La stagione {pagina.stagione.nome} non ha ancora offerte</h2>
      {#if stagioneScelta}
        <span class="muted riga-f">Comincia il {dataLunga(stagioneScelta.dal)}.
          <Fumetto tema="il listino da preparare">Il modo più veloce: copiare il listino di un'altra stagione e aumentare tutti i prezzi in un colpo; poi cambi solo quello che serve.</Fumetto></span>
      {/if}
      {#if modifica}
        {#if stagioni.length > 1}
          <div class="copia">
            <span class="unita">Copia da</span>
            <select class="inp" bind:value={copiaDa} aria-label="Stagione da copiare">{#each ordinaStagioni(stagioni).filter((s) => s.id !== stagioneId) as s (s.id)}<option value={s.id}>{s.nome}</option>{/each}</select>
            <span class="unita">e aumenta i prezzi del</span><input class="inp m num" type="number" step="0.5" bind:value={aumento} aria-label="Aumento in percentuale" /><span class="unita">%</span>
          </div>
        {/if}
        <div class="az-vuota">
          {#if stagioni.length > 1}<button type="button" class="btn btn-primary" disabled={!copiaDa} onclick={copia}>Copia {daCopiare == null ? 'il listino' : daCopiare === 1 ? '1 offerta' : `${daCopiare} offerte`}</button>{/if}
          <button type="button" class="btn btn-secondary" onclick={() => { daZero = true; apri(null, 'CORSO'); }}>Comincia da zero</button>
        </div>
      {/if}
    </section>
  {:else if pagina}
    <Schede {schede} bind:attiva={tipo} etichetta="Tipi di offerta" id="listino">
      {#snippet children(t)}
        {@const elenco = pagina!.offerte.filter((o) => o.tipo === t)}
        {@const colonneVolte = VOLTE.filter((v) => elenco.some((o) => o.prezzi.some((p) => p.volte === v)))}
        <div class="card tabella">
          {#if elenco.length}
            <div class="scorri">
              <table class="tab-o" aria-label="Offerte">
                <thead><tr>
                  <th class="h primo" scope="col">{t === 'CORSO' ? 'Offerta' : t === 'CARNET' ? 'Carnet' : 'Importo'}</th>
                  {#if t === 'CORSO'}
                    {#each colonneVolte as v (v)}<th class="h" scope="col">{etichettaVolte(v)}</th>{/each}
                  {:else if t === 'CARNET'}
                    <th class="h" scope="col">Ingressi</th><th class="h" scope="col">Validità</th><th class="h" scope="col">Prezzo</th>
                  {:else}
                    <th class="h" scope="col">Prezzo</th>
                  {/if}
                </tr></thead>
                <tbody>
                  {#each elenco as o (o.id)}
                    <tr class="riga-o" class:on={offerta?.id === o.id} class:spenta={!o.attiva}>
                      <th class="c primo" scope="row"><button type="button" class="off" aria-haspopup="dialog" onclick={() => apri(o)}><b>{o.nome}{#if !o.attiva} <span class="pill">spenta</span>{/if}</b><span>{sotto(o)}</span></button></th>
                      {#if t === 'CORSO'}
                        {#each colonneVolte as v (v)}
                          {@const p = prezzo(o, v)}
                          <td class="c"><div class="cella"><span class="p" class:vuoto={p.tutti == null}>{p.tutti == null ? '—' : euroColonna(p.tutti)}</span>{#each p.gruppi as g (g.id)}<span class="g"><span class="gn" title={g.nome}>{g.nome}</span><span class="gp">{g.importo}</span></span>{/each}</div></td>
                        {/each}
                      {:else}
                        {@const p = prezzo(o, null)}
                        {#if t === 'CARNET'}
                          <td class="c"><div class="cella"><span class="p">{o.ingressi ?? '—'}</span></div></td>
                          <td class="c"><div class="cella"><span class="p testo">{validita(o)}</span></div></td>
                        {/if}
                        <td class="c"><div class="cella"><span class="p" class:vuoto={p.tutti == null}>{p.tutti == null ? '—' : euroColonna(p.tutti)}</span>{#each p.gruppi as g (g.id)}<span class="g"><span class="gn" title={g.nome}>{g.nome}</span><span class="gp">{g.importo}</span></span>{/each}</div></td>
                      {/if}
                    </tr>
                  {/each}
                </tbody>
              </table>
            </div>
          {:else}
            <StatoVuoto testo="Nessuna offerta." />
          {/if}
          {#if modifica}
            <div class="ultima"><button type="button" class="aggiungi" onclick={() => apri(null, t)}><Icona nome="piu" /><span>Aggiungi un'offerta…</span></button></div>
          {/if}
        </div>
      {/snippet}
      {#snippet azioni()}
        <Fumetto tema="il listino">Ogni riga è un'offerta che si vende al banco, con il prezzo per ogni frequenza; tocca un'offerta per cambiarla. In verde i prezzi dedicati a un gruppo di clienti. Il gestionale propone questi prezzi a chi vende, già ridotti o divisi in rate secondo le regole di prezzo.</Fumetto>
      {/snippet}
    </Schede>
  {/if}
</div>

{#snippet pillolaSpenta()}
  {#if !accesoFoglio}<span class="pill">spenta</span>{/if}
{/snippet}

{#if offerta}
  {@const b = offerta.b}
  <Foglio titolo={titoloFoglio} {chiudi} troncaTitolo dopoTitolo={pillolaSpenta}>
    <form class="modulo" onsubmit={salvaOfferta}>
      {#if modifica}<RiquadroAcceso acceso={accesoFoglio} onacceso={accendi} nome={salvata?.nome ?? b.nome.trim()} />{/if}
      <fieldset disabled={!modifica}>
        <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={b.nome} autocomplete="off" placeholder={b.tipo === 'CORSO' ? 'Es. Scuola nuoto' : b.tipo === 'CARNET' ? 'Es. Nuoto libero 10 ingressi' : 'Es. Quota squadra'} /></label>
        {#if b.tipo === 'ALTRO'}<label class="field"><span class="lbl">Descrizione</span><input class="inp" bind:value={b.descrizione} placeholder="Facoltativa" /></label>{/if}
        {#if b.tipo === 'CORSO'}
          <div class="field"><span class="lbl">Vale per i corsi</span>
            {#if scegliCorsi}
              <div class="chips">{#each corsi.filter((c) => c.attivo || b.corsi.includes(c.id)) as c (c.id)}<button type="button" class="chip" aria-pressed={b.corsi.includes(c.id)} onclick={() => toggleCorso(c.id)}>{#if b.corsi.includes(c.id)}<Icona nome="check" />{/if}{c.nome}</button>{/each}</div>
              <button type="button" class="btn btn-sm btn-secondary sx" onclick={() => (scegliCorsi = false)}>Fatto</button>
            {:else}
              <div class="corsi-c">{#each b.corsi.slice(0, 6) as id (id)}<span>{nomeCorso(id)}</span>{:else}<span class="manca">nessun corso</span>{/each}{#if b.corsi.length > 6}<span>e altri {b.corsi.length - 6}</span>{/if}</div>
              {#if modifica}<button type="button" class="btn btn-sm btn-secondary sx" onclick={() => (scegliCorsi = true)}>Scegli i corsi</button>{/if}
            {/if}
          </div>
        {:else if b.tipo === 'CARNET'}
          <div class="tre">
            <label class="field"><span class="lbl">Ingressi</span><input class="inp m" type="number" min="1" max="500" bind:value={b.ingressi} /></label>
            <label class="field"><span class="lbl">Validità</span><input class="inp m" type="number" min="1" bind:value={b.validita} placeholder="—" /></label>
            <label class="field"><span class="lbl">&nbsp;</span><select class="inp" bind:value={b.unita} aria-label="Unità della validità"><option value="mesi">mesi</option><option value="giorni">giorni</option></select></label>
          </div>
          <label class="field"><span class="lbl">Quando si entra</span>
            <select class="inp" bind:value={b.corsoId}><option value={null}>Entrata libera</option>{#each corsi as c (c.id)}<option value={c.id}>Negli orari di {c.nome}</option>{/each}</select>
          </label>
        {/if}

        {#if b.tipo === 'CORSO'}
          <div class="field"><span class="lbl" id="lbl-frequenze">Frequenze</span>
            <div class="chips" role="group" aria-labelledby="lbl-frequenze">{#each VOLTE as v (v)}{@const k = chiaveRiga(v)}<button type="button" class="chip" aria-pressed={b.righe.includes(k)} onclick={() => toggleRiga(v)}>{#if b.righe.includes(k)}<Icona nome="check" />{/if}{etichettaVolte(v)}</button>{/each}</div>
          </div>
        {/if}
        {#if gruppiScelte.length}
          <div class="field"><span class="lbl" id="lbl-gruppi">Prezzi per i gruppi</span>
            <div class="chips" role="group" aria-labelledby="lbl-gruppi">{#each gruppiScelte as g (g.id)}<button type="button" class="chip" aria-pressed={b.colonne.includes(g.id)} onclick={() => toggleColonna(g.id)}>{#if b.colonne.includes(g.id)}<Icona nome="check" />{/if}{g.nome}</button>{/each}</div>
          </div>
        {/if}

        <div class="field"><span class="con-i"><span class="lbl">{b.tipo === 'CORSO' ? 'Prezzi per frequenza' : 'Prezzo'}</span>
          <Fumetto tema="i prezzi">{#if b.tipo === 'CORSO'}Vuoto = non si vende con quella frequenza. {/if}Un prezzo per un gruppo vale per chi ne fa parte, se è più basso.{#if offerta.id} Cambiare i prezzi vale per le vendite nuove: chi l'ha già comprata resta com'è.{/if}</Fumetto></span>
          {#each b.colonne as c (c)}
            <div class="colonna-p">
              {#if b.colonne.length > 1}
                <div class="testa-c"><b class="nome-c">{c ? nomeGruppo(c) : 'Tutti'}</b>
                  {#if c && modifica}<Azione icona="cestino" tono="danger" etichetta="Elimina i prezzi per «{nomeGruppo(c)}»" conferma="Elimina?" onclick={() => toggleColonna(c)} />{/if}</div>
              {/if}
              {#each b.righe as r (r)}
                <label class="riga-p"><span class="r">{nomeFrequenza(r, b.tipo)}</span>
                  <input class="inp m" inputmode="decimal" placeholder="—" bind:value={b.prezzi[chiave(r, c)]} aria-invalid={importoNonValido(b.prezzi[chiave(r, c)] ?? '')} aria-label="{nomeFrequenza(r, b.tipo)}, {c ? nomeGruppo(c) : 'Tutti'}" /></label>
              {/each}
            </div>
          {/each}
        </div>

        {#if b.tipo !== 'ALTRO'}
          <div class="riga-switch"><span class="con-i" id="lbl-riduzione">Riduzione per iscrizione a stagione iniziata
            <Fumetto tema="la riduzione tardiva">{riduzioneTesto}</Fumetto></span>
            <button type="button" class="switch tap44" role="switch" aria-checked={b.riduzioneTardiva} aria-labelledby="lbl-riduzione" onclick={() => (b.riduzioneTardiva = !b.riduzioneTardiva)}><span></span></button></div>
        {/if}
      </fieldset>

      {#if prezziErrati}<p class="msg err nomargin" role="alert">Scrivi i prezzi in euro, es. 35 o 35,50</p>{/if}
      {#if erroreFoglio}<p class="msg err nomargin messaggio-b" role="alert">{#each righeBlocco(erroreFoglio) as r, k (k)}<span class="riga-b">{#if r.attacco}<b>{r.attacco}</b>{' '}{/if}{r.resto}{' '}</span>{/each}</p>{/if}
      {#if bloccoElimina}
        <p class="blocco" role="alert">{#each righeBlocco(bloccoElimina) as r, k (k)}<span class="riga-b">{#if r.attacco}<b>{r.attacco}</b>{' '}{/if}{r.resto}{' '}</span>{/each}</p>
      {/if}

      {#if modifica}
        {#if salvata}
          <div class="piu-azioni">
            <button type="button" class="btn btn-sm btn-secondary" onclick={duplica}><Icona nome="copia" />Duplica l'offerta</button>
            <Azione icona="cestino" tono="danger" etichetta="Elimina l'offerta" conferma="Elimina?" onclick={elimina} />
          </div>
        {/if}
        <div class="azioni-foglio">
          <button type="button" class="btn btn-secondary" onclick={chiudi}>Annulla</button>
          <button type="submit" class="btn btn-accent" disabled={!salvabile || inSalvataggio}>
            {#if offerta.id}<Icona nome="salva" />Salva l'offerta{:else}<Icona nome="piu" />Aggiungi al listino{/if}
          </button>
        </div>
      {/if}
    </form>
  </Foglio>
{/if}

<style>
  .pagina { display: flex; flex-direction: column; gap: var(--space-14); max-width: 900px; min-width: 0; }
  .testa { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-8) var(--space-14); }
  .tabella { padding: 0; overflow: hidden; }
  .scorri { overflow-x: auto; }
  /* una vera tabella: la colonna dei nomi resta ferma (sticky) per tutto lo scorrimento di lato, su un fondo pieno */
  .tab-o { border-collapse: separate; border-spacing: 0; width: 100%; min-width: max-content; font-size: var(--text-md); }
  .tab-o .h { background: var(--surface); padding: var(--space-10) var(--space-12); text-align: right; font-size: 11px; font-weight: 700; letter-spacing: 0.07em; text-transform: uppercase; color: var(--muted);
    border-bottom: 1px solid var(--line); white-space: nowrap; min-width: 112px; }
  .tab-o .h.primo { text-align: left; min-width: 170px; }
  .tab-o .primo { position: sticky; left: 0; z-index: 1; background: var(--surface); }
  .tab-o .c { background: var(--surface); padding: 0 var(--space-12); border-top: 1px solid var(--line-soft); text-align: right; font-weight: inherit; vertical-align: middle; white-space: nowrap; }
  .tab-o .c.primo { text-align: left; white-space: normal; }
  .cella { display: flex; flex-direction: column; align-items: flex-end; justify-content: center; padding: 11px 0; }
  .tab-o th.c.primo { padding-block: 0; }
  .riga-o.on .c { background: var(--cloro-soft); }
  .riga-o.spenta .c .off, .riga-o.spenta .c .cella { opacity: 0.6; }
  .off { display: flex; flex-direction: column; justify-content: center; gap: 2px; min-height: 44px; padding: 11px 0; background: none; border: none; font: inherit; color: var(--ink); text-align: left; cursor: pointer; }
  .off { width: 220px; }
  @media (max-width: 480px) { .off { width: 150px; } .tab-o .h.primo { min-width: 0; } }
  .off b { font-size: var(--text-base); }
  .off span { font-size: var(--text-xs); color: var(--muted); }
  .p { font-family: var(--font-mono); font-weight: 500; line-height: 1.3; }
  .p.testo { font-family: var(--font-body); }
  .p.vuoto { color: var(--line-strong); }
  /* il prezzo di un gruppo: il nome come etichetta piccola sopra, l'importo intero su una riga */
  .g { display: flex; flex-direction: column; align-items: flex-end; font-family: var(--font-mono); font-size: var(--text-xs); color: var(--cloro-ink); line-height: 1.3; }
  .g > span { white-space: nowrap; }
  .gn { font-family: var(--font-body); color: var(--muted); max-width: 100%; overflow: hidden; text-overflow: ellipsis; }
  .g { max-width: 100%; }
  .tabella > :global(.stato-vuoto) { margin: var(--space-8); border-radius: var(--radius-md); }
  .ultima { padding: var(--space-8) var(--space-12) var(--space-12); }
  .aggiungi { display: flex; align-items: center; justify-content: center; gap: var(--space-8); width: 100%; min-height: 48px; padding: 0 var(--space-12); background: transparent;
    border: 1px dashed var(--line-dashed); border-radius: var(--radius-md); color: var(--cloro-ink); font: inherit; font-weight: 600; font-size: var(--text-base); cursor: pointer; }
  .aggiungi span { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; min-width: 0; }
  .aggiungi :global(svg) { color: var(--cloro); }
  .aggiungi:hover { background: var(--cloro-soft); border-color: var(--cloro); }
  .vuota { display: flex; flex-direction: column; align-items: flex-start; gap: var(--space-12); padding: 22px; }
  .vuota h2 { margin: 0; font-family: var(--font-display); font-size: 22px; font-weight: 800; }
  .riga-f { display: inline-flex; align-items: center; gap: var(--space-8); }
  .az-vuota { display: flex; flex-wrap: wrap; gap: var(--space-10); }
  .az-vuota .btn, .a-regole { white-space: nowrap; text-decoration: none; }
  .copia { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .copia .unita { white-space: nowrap; }
  .copia select { width: 140px; height: 40px; appearance: auto; }
  .num { width: 70px; height: 40px; text-align: right; }

  /* il foglio */
  .modulo { display: flex; flex-direction: column; gap: var(--space-14); min-width: 0; }
  fieldset { border: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--space-14); min-width: 0; }
  select.inp { appearance: auto; }
  .tre { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-8); }
  .corsi-c { display: flex; flex-wrap: wrap; gap: var(--space-6); }
  .corsi-c span { display: inline-flex; align-items: center; height: 28px; padding: 0 var(--space-10); border-radius: var(--radius-pill); background: var(--area-m-bg); color: var(--area-m-fg);
    font-size: var(--text-xs); font-weight: 600; }
  .corsi-c .manca { background: var(--warn-bg); color: var(--warn); }
  .sx { align-self: flex-start; }
  .chip { height: 44px; display: inline-flex; align-items: center; gap: var(--space-6); max-width: 100%; }
  .chip :global(.ic) { width: 16px; height: 16px; }
  .con-i { display: flex; align-items: center; gap: var(--space-8); min-width: 0; }
  .con-i .lbl { margin: 0; }
  .colonna-p { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); background: var(--surface-sunken); }
  .testa-c { display: flex; align-items: center; justify-content: space-between; gap: var(--space-8); min-width: 0; min-height: 40px; }
  .nome-c { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: var(--text-base); }
  .riga-p { display: flex; align-items: center; justify-content: space-between; gap: var(--space-12); min-width: 0; }
  .riga-p .r { font-size: var(--text-sm); white-space: nowrap; }
  .riga-p .inp { width: 128px; height: 44px; font-family: var(--font-mono); text-align: right; flex-shrink: 0; }
  .riga-switch { cursor: default; }
  .piu-azioni { display: flex; align-items: center; justify-content: space-between; gap: var(--space-10); }
  .azioni-foglio { display: flex; justify-content: flex-end; gap: var(--space-10); flex-wrap: wrap; }
  .blocco { margin: 0; padding: var(--space-8) var(--space-12); border-radius: var(--radius-md); background: var(--ferma-bg); color: var(--ferma-fg); font-size: var(--text-sm); display: flex; flex-direction: column; gap: var(--space-4); }
  .messaggio-b { display: flex; flex-direction: column; gap: var(--space-4); }
  .blocco b { color: inherit; font-weight: 700; }
  @media (max-width: 360px) { .tre { gap: var(--space-6); } }
</style>
