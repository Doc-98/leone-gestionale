<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import { api, messaggioErrore, type Corso, type GruppoClienti, type NuovaOfferta, type Offerta, type PaginaListino, type Stagione, type TipoOfferta } from '../lib/api';
  import { dataLunga, iso } from '../lib/date';
  import { etichettaVolte, euro, VOLTE } from '../lib/listino';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Il listino di una stagione (ADR 0018, 0024): le offerte in tabella, con i prezzi per frequenza in colonna così si
   * confrontano; tocca un'offerta per cambiarla nel pannello a destra. Le regole di prezzo hanno la loro pagina.
   */
  const TIPI: { id: TipoOfferta; nome: string; nuova: string; etichetta: string }[] = [
    { id: 'CORSO', nome: 'Abbonamenti ai corsi', nuova: 'Nuovo abbonamento', etichetta: 'Abbonamento ai corsi' },
    { id: 'CARNET', nome: 'Carnet', nuova: 'Nuovo carnet', etichetta: 'Carnet' },
    { id: 'ALTRO', nome: 'Altri importi', nuova: 'Nuovo importo', etichetta: 'Altro importo (non dà accesso)' },
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
  const oggi = iso(new Date());
  const modifica = $derived(sessione.puo('IMPOSTAZIONI'));

  async function avvia() {
    try {
      [stagioni, corsi, gruppi] = await Promise.all([api.stagioni(), api.corsi(), api.gruppi().catch(() => [])]);
      stagioni = [...stagioni].sort((a, b) => a.dal.localeCompare(b.dal));
      const inCorso = stagioni.find((s) => s.dal <= oggi && oggi <= s.al) ?? stagioni.at(-1);
      if (inCorso) stagioneId = inCorso.id;
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
    copiaDa = stagioni.filter((s) => s.id !== stagioneId).at(-1)?.id ?? '';
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

  const statoStagione = (s: Stagione) => (oggi > s.al ? 'finita' : s.dal <= oggi ? 'in corso' : 'futura');
  const nomeCorso = (id: string | null) => corsi.find((c) => c.id === id)?.nome ?? 'corso';
  const nomeGruppo = (id: string | null) => (id ? gruppi.find((g) => g.id === id)?.nome ?? 'gruppo' : 'Tutti');
  const vuota = $derived(!!pagina && !pagina.offerte.length && !daZero);
  const elenco = $derived(pagina?.offerte.filter((o) => o.tipo === tipo) ?? []);
  const tipoCorrente = $derived(TIPI.find((t) => t.id === tipo)!);

  // ── La tabella: una colonna per frequenza (abbonamenti), o ingressi/validità/prezzo ──
  const colonneVolte = $derived(VOLTE.filter((v) => elenco.some((o) => o.prezzi.some((p) => p.volte === v))));
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
    gruppi: o.prezzi.filter((p) => p.volte === volte && p.gruppoId).map((p) => `${nomeGruppo(p.gruppoId).split(' ')[0].toLowerCase()} ${euro(p.importo)}`),
  });

  // ── Il pannello dell'offerta ──────────────────────────────────────────
  type Bozza = {
    nome: string; descrizione: string; tipo: TipoOfferta; corsi: string[]; corsoId: string | null; ingressi: string; validita: string; unita: 'mesi' | 'giorni';
    riduzioneTardiva: boolean; attiva: boolean; ordine: number; righe: string[]; colonne: string[]; prezzi: Record<string, string>;
  };
  let offerta = $state<{ id: string | null; b: Bozza } | null>(null);
  let scegliCorsi = $state(false);
  const chiave = (volte: string, gruppo: string) => `${volte}|${gruppo}`;

  function apri(o: Offerta | null, t: TipoOfferta = tipo) {
    scegliCorsi = false;
    const v = (n: number | null) => (n == null ? '' : String(n));
    if (o) {
      const righe = [...new Set(o.prezzi.map((p) => (o.tipo === 'CORSO' ? v(p.volte) : '')))];
      const colonne = ['', ...new Set(o.prezzi.filter((p) => p.gruppoId).map((p) => p.gruppoId!))];
      offerta = { id: o.id, b: { nome: o.nome, descrizione: o.descrizione ?? '', tipo: o.tipo, corsi: [...o.corsi], corsoId: o.corsoId, ingressi: v(o.ingressi),
        validita: v(o.validitaMesi ?? o.validitaGiorni), unita: o.validitaGiorni ? 'giorni' : 'mesi', riduzioneTardiva: o.riduzioneTardiva, attiva: o.attiva, ordine: o.ordine,
        righe: righe.length ? righe.sort((a, b) => VOLTE.indexOf(a === '' ? null : Number(a)) - VOLTE.indexOf(b === '' ? null : Number(b))) : [t === 'CORSO' ? '1' : ''],
        colonne, prezzi: Object.fromEntries(o.prezzi.map((p) => [chiave(o.tipo === 'CORSO' ? v(p.volte) : '', p.gruppoId ?? ''), String(p.importo)])) } };
    } else {
      offerta = { id: null, b: { nome: '', descrizione: '', tipo: t, corsi: [], corsoId: null, ingressi: t === 'CARNET' ? '10' : '', validita: t === 'CARNET' ? '3' : '', unita: 'mesi',
        riduzioneTardiva: t === 'CORSO', attiva: true, ordine: 0, righe: t === 'CORSO' ? ['1', '2'] : [''], colonne: [''], prezzi: {} } };
      if (t === 'CORSO') scegliCorsi = true;
    }
  }

  function corpo(b: Bozza): NuovaOfferta {
    const numero = (s: string) => (s.trim() === '' ? null : Number(s));
    const prezzi = b.righe.flatMap((r) => b.colonne.map((c) => ({ r, c, imp: b.prezzi[chiave(r, c)] ?? '' })))
      .filter((x) => String(x.imp).trim() !== '')
      .map((x) => ({ volte: b.tipo === 'CORSO' ? numero(x.r) : null, gruppoId: x.c || null, importo: Number(String(x.imp).replace(',', '.')) }));
    return {
      stagioneId, nome: b.nome.trim(), tipo: b.tipo, descrizione: b.descrizione.trim() || null, corsoId: b.tipo === 'CARNET' ? b.corsoId || null : null,
      corsi: b.tipo === 'CORSO' ? b.corsi : [], riduzioneTardiva: b.tipo === 'ALTRO' ? false : b.riduzioneTardiva, attiva: b.attiva, ordine: b.ordine,
      ingressi: b.tipo === 'CARNET' ? numero(b.ingressi) : null,
      validitaMesi: b.tipo === 'CARNET' && b.unita === 'mesi' ? numero(b.validita) : null, validitaGiorni: b.tipo === 'CARNET' && b.unita === 'giorni' ? numero(b.validita) : null,
      prezzi,
    };
  }
  const salvabile = $derived(!!offerta && !!offerta.b.nome.trim() && corpo(offerta.b).prezzi.length > 0 && (offerta.b.tipo !== 'CORSO' || offerta.b.corsi.length > 0));

  function salvaOfferta() {
    const { id, b } = offerta!;
    return esegui(() => (id ? api.modificaOfferta(id, corpo(b)) : api.creaOfferta(corpo(b))), id ? `«${b.nome.trim()}» salvata.` : `«${b.nome.trim()}» aggiunta al listino.`)
      .then((ok) => ok && (offerta = null));
  }
  const duplica = () => {
    const b = offerta!.b;
    return esegui(() => api.creaOfferta({ ...corpo(b), nome: `${b.nome.trim()} (copia)` }), `Copia di «${b.nome.trim()}» aggiunta: cambiale nome e prezzi.`);
  };
  const accendi = () => {
    const { id, b } = offerta!;
    b.attiva = !b.attiva;
    return esegui(() => api.modificaOfferta(id!, corpo(b)), b.attiva ? `«${b.nome}» di nuovo in vendita.` : `«${b.nome}» spenta: non si vende più, resta su chi l'ha già.`);
  };
  async function elimina() {
    const { id, b } = offerta!;
    if (!confirm(`Togliere «${b.nome}» dal listino?`)) return;
    if (await esegui(() => api.eliminaOfferta(id!), `«${b.nome}» tolta dal listino.`)) offerta = null;
  }
  function toggleCorso(id: string) {
    const b = offerta!.b;
    b.corsi = b.corsi.includes(id) ? b.corsi.filter((c) => c !== id) : [...b.corsi, id];
  }
  const righeDisponibili = $derived(offerta ? VOLTE.filter((v) => !offerta!.b.righe.includes(v == null ? '' : String(v))) : []);
  const colonneDisponibili = $derived(offerta ? gruppi.filter((g) => g.attivo && !offerta!.b.colonne.includes(g.id)) : []);
  const riduzioneTesto = $derived.by(() => {
    const r = pagina?.regole.find((x) => x.tipo === 'RIDUZIONE_TARDIVA');
    const sc = ((r?.parametri.scaglioni as { percentuale: number }[] | undefined) ?? []).map((s) => s.percentuale);
    if (!r?.attiva) return 'La regola di riduzione è spenta in questa stagione.';
    return sc.length ? `Dal ${Math.min(...sc)} % al ${Math.max(...sc)} %, dalle regole di prezzo.` : 'Con gli scaglioni delle regole di prezzo.';
  });

  // ── Copia da un'altra stagione ────────────────────────────────────────
  let copiaDa = $state('');
  let aumento = $state('3');
  let daCopiare = $state<number | null>(null);
  $effect(() => {
    if (copiaDa) api.listino(copiaDa).then((l) => (daCopiare = l.offerte.length)).catch(() => (daCopiare = null));
  });
  const copia = () => esegui(async () => {
    const r = await api.copiaListino(copiaDa, stagioneId, Number(aumento) || 0);
    messaggio = `${r.offerte} offerte copiate.`;
  }, '').then((ok) => ok && (messaggio = 'Listino e regole copiati: ora cambia solo quello che serve.'));
  const stagioneScelta = $derived(stagioni.find((s) => s.id === stagioneId));
</script>

<p class="muted nomargin">Riduzioni, rate e recesso sono nelle <a href="#/prezzi">regole di prezzo</a>.</p>

<div class="stag-c" role="group" aria-label="Stagione">
  {#each stagioni as s (s.id)}
    <button type="button" aria-pressed={s.id === stagioneId} onclick={() => (stagioneId = s.id)}>{s.nome}<span class="s">{statoStagione(s)}</span></button>
  {/each}
</div>

{#if !stagioni.length && !errore}
  <p class="muted">Nessuna stagione: prima aggiungine una in <a href="#/societa">Dati, stagioni e gruppi</a>.</p>
{/if}
{#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

{#if pagina && vuota}
  <section class="card vuota">
    <span class="pill warn">Da preparare</span>
    <h2>La stagione {pagina.stagione.nome} non ha ancora offerte</h2>
    {#if stagioneScelta}<span class="muted">Comincia il {dataLunga(stagioneScelta.dal)}. Il modo più veloce: copiare il listino di un'altra stagione e aumentare tutti i prezzi in un colpo; poi cambi solo quello che serve.</span>{/if}
    {#if modifica}
      {#if stagioni.length > 1}
        <div class="copia">
          <span class="unita">Copia da</span>
          <select class="inp" bind:value={copiaDa} aria-label="Stagione da copiare">{#each stagioni.filter((s) => s.id !== stagioneId) as s (s.id)}<option value={s.id}>{s.nome}</option>{/each}</select>
          <span class="unita">e aumenta i prezzi del</span><input class="inp m num" type="number" step="0.5" bind:value={aumento} aria-label="Aumento in percentuale" /><span class="unita">%</span>
        </div>
      {/if}
      <div class="row">
        {#if stagioni.length > 1}<button type="button" class="btn btn-primary" disabled={!copiaDa} onclick={copia}>Copia {daCopiare == null ? 'il listino' : daCopiare === 1 ? '1 offerta' : `${daCopiare} offerte`}</button>{/if}
        <button type="button" class="btn btn-secondary" onclick={() => { daZero = true; apri(null, 'CORSO'); }}>Comincia da zero</button>
      </div>
    {/if}
  </section>
{:else if pagina}
  <div class="colonne-p">
    <div class="princ">
      <div class="tab-c" role="group" aria-label="Tipo di offerta">
        {#each TIPI as t (t.id)}
          <button type="button" aria-pressed={tipo === t.id} onclick={() => { tipo = t.id; offerta = null; }}>{t.nome}<span class="n">{pagina.offerte.filter((o) => o.tipo === t.id).length}</span></button>
        {/each}
        <span class="grow"></span>
        {#if modifica}<button type="button" class="mini-btn" onclick={() => apri(null)}><Icona nome="piu" />{tipoCorrente.nuova}</button>{/if}
      </div>
      <div class="card tabella">
        {#if elenco.length}
          <div class="tab-o" role="table" aria-label="Offerte" style="--n: {tipo === 'CORSO' ? Math.max(1, colonneVolte.length) : tipo === 'CARNET' ? 3 : 1}">
            <div class="h primo" role="columnheader">{tipo === 'CORSO' ? 'Offerta' : tipo === 'CARNET' ? 'Carnet' : 'Importo'}</div>
            {#if tipo === 'CORSO'}
              {#each colonneVolte as v (v)}<div class="h" role="columnheader">{etichettaVolte(v)}</div>{/each}
            {:else if tipo === 'CARNET'}
              <div class="h" role="columnheader">Ingressi</div><div class="h" role="columnheader">Validità</div><div class="h" role="columnheader">Prezzo</div>
            {:else}
              <div class="h" role="columnheader">Prezzo</div>
            {/if}
            {#each elenco as o (o.id)}
              <div class="riga-o" class:on={offerta?.id === o.id} class:spenta={!o.attiva} role="row">
                <div class="c primo" role="cell"><button type="button" class="off" onclick={() => apri(o)}><b>{o.nome}{#if !o.attiva} <span class="pill">spenta</span>{/if}</b><span>{sotto(o)}</span></button></div>
                {#if tipo === 'CORSO'}
                  {#each colonneVolte as v (v)}
                    {@const p = prezzo(o, v)}
                    <div class="c" role="cell"><span class="p" class:vuoto={p.tutti == null}>{p.tutti == null ? '—' : euro(p.tutti)}</span>{#each p.gruppi as g (g)}<span class="g">{g}</span>{/each}</div>
                  {/each}
                {:else}
                  {@const p = prezzo(o, null)}
                  {#if tipo === 'CARNET'}
                    <div class="c" role="cell"><span class="p">{o.ingressi ?? '—'}</span></div>
                    <div class="c" role="cell"><span class="p testo">{validita(o)}</span></div>
                  {/if}
                  <div class="c" role="cell"><span class="p" class:vuoto={p.tutti == null}>{p.tutti == null ? '—' : euro(p.tutti)}</span>{#each p.gruppi as g (g)}<span class="g">{g}</span>{/each}</div>
                {/if}
              </div>
            {/each}
          </div>
        {:else}
          <p class="muted small vuoto-t">Niente in questa sezione.</p>
        {/if}
      </div>
      <span class="muted small">In verde i prezzi dedicati a un gruppo di clienti. Tocca un'offerta per cambiarla.</span>
    </div>

    <aside class="lato-p">
      {#if offerta}
        {@const b = offerta.b}
        <form class="card pann" onsubmit={(e) => { e.preventDefault(); salvaOfferta(); }}>
          <span class="testa-p"><span class="lbl grow">{offerta.id ? TIPI.find((t) => t.id === b.tipo)?.etichetta : `${TIPI.find((t) => t.id === b.tipo)?.nuova}`}</span>
            {#if offerta.id && modifica}<button type="button" class="mini-btn" onclick={duplica}>Duplica</button>{/if}
            <button type="button" class="x-btn" aria-label="Chiudi" onclick={() => (offerta = null)}><Icona nome="chiudi" /></button></span>
          <fieldset disabled={!modifica}>
            <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={b.nome} placeholder={b.tipo === 'CORSO' ? 'Es. Scuola nuoto' : b.tipo === 'CARNET' ? 'Es. Nuoto libero 10 ingressi' : 'Es. Quota squadra'} /></label>
            {#if b.tipo === 'ALTRO'}<label class="field"><span class="lbl">Descrizione</span><input class="inp" bind:value={b.descrizione} placeholder="Facoltativa" /></label>{/if}
            {#if b.tipo === 'CORSO'}
              <div class="field"><span class="lbl">Vale per i corsi</span>
                {#if scegliCorsi}
                  <div class="chips">{#each corsi.filter((c) => c.attivo || b.corsi.includes(c.id)) as c (c.id)}<button type="button" class="chip" aria-pressed={b.corsi.includes(c.id)} onclick={() => toggleCorso(c.id)}>{c.nome}</button>{/each}</div>
                  <button type="button" class="mini-btn sx" onclick={() => (scegliCorsi = false)}>Fatto</button>
                {:else}
                  <div class="corsi-c">{#each b.corsi.slice(0, 6) as id (id)}<span>{nomeCorso(id)}</span>{:else}<span class="manca">nessun corso</span>{/each}{#if b.corsi.length > 6}<span>+ {b.corsi.length - 6}</span>{/if}</div>
                  <button type="button" class="mini-btn tratt sx" onclick={() => (scegliCorsi = true)}>Scegli i corsi</button>
                {/if}
              </div>
            {:else if b.tipo === 'CARNET'}
              <div class="tre">
                <label class="field"><span class="lbl">Ingressi</span><input class="inp m" type="number" min="1" max="500" bind:value={b.ingressi} /></label>
                <label class="field"><span class="lbl">Validità</span><input class="inp m" type="number" min="1" bind:value={b.validita} placeholder="—" /></label>
                <label class="field"><span class="lbl">&nbsp;</span><select class="inp" bind:value={b.unita}><option value="mesi">mesi</option><option value="giorni">giorni</option></select></label>
              </div>
              <label class="field"><span class="lbl">Quando si entra</span>
                <select class="inp" bind:value={b.corsoId}><option value={null}>Entrata libera</option>{#each corsi as c (c.id)}<option value={c.id}>Negli orari di {c.nome}</option>{/each}</select>
              </label>
            {/if}
            <div class="field"><span class="lbl">{b.tipo === 'CORSO' ? 'Prezzi per frequenza' : 'Prezzo'}</span>
              <div class="prezzi-g" style="--c: {b.colonne.length}">
                <span></span>
                {#each b.colonne as c (c)}<span class="h" title={nomeGruppo(c)}><span class="nome-c">{nomeGruppo(c).split(' ')[0]}</span>{#if c}<button type="button" class="x-btn piccolo" aria-label="Togli i prezzi per {nomeGruppo(c)}" onclick={() => (b.colonne = b.colonne.filter((x) => x !== c))}><Icona nome="chiudi" /></button>{/if}</span>{/each}
                {#each b.righe as r (r)}
                  <span class="r">{b.tipo === 'CORSO' ? etichettaVolte(r === '' ? null : Number(r)) : 'Prezzo'}</span>
                  {#each b.colonne as c (c)}<input class="inp m" inputmode="decimal" placeholder="—" bind:value={b.prezzi[chiave(r, c)]} aria-label="{b.tipo === 'CORSO' ? etichettaVolte(r === '' ? null : Number(r)) : 'Prezzo'}, {nomeGruppo(c)}" />{/each}
                {/each}
              </div>
              <span class="aggiungi">
                {#if b.tipo === 'CORSO' && righeDisponibili.length}
                  <select class="inp mini" aria-label="Aggiungi una frequenza" onchange={(e) => { const v = (e.currentTarget as HTMLSelectElement).value; if (v !== 'x') b.righe = [...b.righe, v]; (e.currentTarget as HTMLSelectElement).value = 'x'; }}>
                    <option value="x">+ frequenza</option>{#each righeDisponibili as v (v)}<option value={v == null ? '' : String(v)}>{etichettaVolte(v)}</option>{/each}
                  </select>
                {/if}
                {#if colonneDisponibili.length}
                  <select class="inp mini" aria-label="Aggiungi i prezzi per un gruppo" onchange={(e) => { const v = (e.currentTarget as HTMLSelectElement).value; if (v) b.colonne = [...b.colonne, v]; (e.currentTarget as HTMLSelectElement).value = ''; }}>
                    <option value="">+ prezzo per un gruppo</option>{#each colonneDisponibili as g (g.id)}<option value={g.id}>{g.nome}</option>{/each}
                  </select>
                {/if}
              </span>
              <span class="muted small">Vuoto = non si vende con quella frequenza. Un prezzo per un gruppo vale per chi ne fa parte, se è più basso.</span>
            </div>
            {#if b.tipo !== 'ALTRO'}
              <label class="riga-switch"><span>Riduzione per iscrizione a stagione iniziata<br /><span class="muted small">{riduzioneTesto}</span></span>
                <button type="button" class="switch" role="switch" aria-checked={b.riduzioneTardiva} aria-label="Riduzione per iscrizione tardiva" onclick={() => (b.riduzioneTardiva = !b.riduzioneTardiva)}><span></span></button></label>
            {/if}
          </fieldset>
          {#if offerta.id}<span class="muted small">Cambiare i prezzi vale per le vendite nuove: chi l'ha già comprata resta com'è.</span>{/if}
          {#if modifica}
            <div class="row">
              <button class="btn btn-primary grow" disabled={!salvabile}>{offerta.id ? 'Salva' : 'Aggiungi al listino'}</button>
              {#if offerta.id}
                <button type="button" class="btn btn-secondary" onclick={accendi}>{b.attiva ? 'Spegni' : 'Riaccendi'}</button>
                <button type="button" class="btn btn-danger" aria-label="Elimina" onclick={elimina}><Icona nome="cestino" /></button>
              {/if}
            </div>
          {/if}
        </form>
      {:else}
        <section class="card pann">
          <h2>Come funziona</h2>
          <span class="muted small">Ogni riga è un'offerta che si vende al banco, con il prezzo per ogni frequenza. Tocca un'offerta per cambiarne nome, corsi e prezzi, o per spegnerla.</span>
          <span class="muted small">Il gestionale propone questi prezzi a chi vende, già ridotti o divisi in rate secondo le <a href="#/prezzi">regole di prezzo</a>.</span>
        </section>
      {/if}
    </aside>
  </div>
{/if}

<style>
  .stag-c { display: flex; gap: var(--space-6); flex-wrap: wrap; }
  .stag-c button { display: flex; align-items: center; gap: var(--space-8); height: 40px; padding: 0 var(--space-14); border-radius: var(--radius-pill); border: 1px solid var(--line);
    background: var(--surface); font: inherit; font-size: var(--text-md); font-weight: 600; color: var(--ink); cursor: pointer; }
  .stag-c button[aria-pressed='true'] { background: var(--fill); border-color: var(--fill); color: var(--on-fill); }
  .stag-c .s { font-size: var(--text-xs); font-weight: 500; opacity: 0.75; }
  .tab-c { display: flex; gap: var(--space-4); border-bottom: 1px solid var(--line); align-items: center; flex-wrap: wrap; }
  .tab-c > button:not(.mini-btn) { position: relative; height: 42px; padding: 0 var(--space-14); border: none; background: none; font: inherit; font-size: var(--text-md); font-weight: 600; color: var(--muted); cursor: pointer; }
  .tab-c > button[aria-pressed='true'] { color: var(--ink); }
  .tab-c > button[aria-pressed='true']::after { content: ''; position: absolute; left: 10px; right: 10px; bottom: -1px; height: 3px; border-radius: 3px 3px 0 0; background: var(--cloro); }
  .tab-c .n { margin-left: 6px; font-family: var(--font-mono); font-size: var(--text-xs); color: var(--muted); }
  .tabella { padding: 0; overflow-x: auto; }
  .tab-o { display: grid; grid-template-columns: minmax(220px, 1fr) repeat(var(--n), 96px); min-width: 520px; font-size: var(--text-md); }
  .tab-o .h { padding: var(--space-10) var(--space-12); text-align: right; font-size: 11px; font-weight: 700; letter-spacing: 0.07em; text-transform: uppercase; color: var(--muted);
    border-bottom: 1px solid var(--line); white-space: nowrap; }
  .tab-o .h.primo { text-align: left; }
  .riga-o { display: contents; }
  .tab-o .c { padding: 11px var(--space-12); border-top: 1px solid var(--line-soft); display: flex; flex-direction: column; align-items: flex-end; justify-content: center; white-space: nowrap; }
  .tab-o .c.primo { align-items: stretch; white-space: normal; }
  .riga-o.on .c { background: var(--cloro-soft); }
  .riga-o.spenta .c { opacity: 0.6; }
  .off { display: flex; flex-direction: column; gap: 2px; background: none; border: none; padding: 0; font: inherit; color: var(--ink); text-align: left; cursor: pointer; }
  .off b { font-size: var(--text-base); }
  .off span { font-size: var(--text-xs); color: var(--muted); }
  .p { font-family: var(--font-mono); font-weight: 500; line-height: 1.3; }
  .p.testo { font-family: var(--font-body); }
  .p.vuoto { color: var(--line-strong); }
  .g { font-family: var(--font-mono); font-size: var(--text-xs); color: var(--cloro-ink); line-height: 1.3; }
  .vuoto-t { padding: var(--space-14) var(--space-16); margin: 0; }
  .vuota { display: flex; flex-direction: column; align-items: flex-start; gap: var(--space-12); padding: 22px; }
  .vuota h2 { margin: 0; font-family: var(--font-display); font-size: 22px; font-weight: 800; }
  .copia { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .copia select { width: 140px; height: 40px; appearance: auto; }
  .num { width: 70px; height: 40px; text-align: right; }
  .testa-p { display: flex; align-items: center; gap: var(--space-8); }
  fieldset { border: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--space-12); min-width: 0; }
  select.inp { appearance: auto; }
  .tre { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-8); }
  .corsi-c { display: flex; flex-wrap: wrap; gap: var(--space-6); }
  .corsi-c span { display: inline-flex; align-items: center; height: 28px; padding: 0 var(--space-10); border-radius: var(--radius-pill); background: var(--area-m-bg); color: var(--area-m-fg);
    font-size: var(--text-xs); font-weight: 600; }
  .corsi-c .manca { background: var(--warn-bg); color: var(--warn); }
  .sx { align-self: flex-start; }
  .prezzi-g { display: grid; grid-template-columns: 96px repeat(var(--c), minmax(0, 1fr)); gap: var(--space-8); align-items: center; }
  .prezzi-g .h { display: flex; align-items: center; justify-content: flex-end; gap: 2px; font-size: 11px; font-weight: 700; letter-spacing: 0.07em; text-transform: uppercase; color: var(--muted);
    overflow: hidden; text-overflow: ellipsis; }
  .prezzi-g .r { font-size: var(--text-sm); }
  .nome-c { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .prezzi-g .inp { height: 38px; font-family: var(--font-mono); text-align: right; }
  .piccolo { width: 22px; height: 22px; }
  .aggiungi { display: flex; gap: var(--space-8); flex-wrap: wrap; }
  .mini { width: auto; height: 32px; font-size: var(--text-sm); padding: 0 var(--space-8); }
</style>
