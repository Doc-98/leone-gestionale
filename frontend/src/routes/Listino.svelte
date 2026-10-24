<script lang="ts">
  import Azione from '../components/Azione.svelte';
  import Icona from '../components/Icona.svelte';
  import {
    api, messaggioErrore,
    type Corso, type GruppoClienti, type NuovaOfferta, type Offerta, type PaginaListino, type RegolaPrezzo, type Stagione, type TipoOfferta, type TipoRegolaPrezzo,
  } from '../lib/api';
  import { dataBreve, iso } from '../lib/date';
  import { etichettaVolte, euro, VOLTE } from '../lib/listino';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Il listino di una stagione (ADR 0018): abbonamenti ai corsi con il prezzo per frequenza (e per gruppo di clienti),
   * carnet, altri importi; e le regole di prezzo. Il gestionale propone gli importi a chi vende; qui si decidono.
   */
  const TIPI: { id: TipoOfferta; nome: string; spiega: string }[] = [
    { id: 'CORSO', nome: 'Abbonamenti ai corsi', spiega: 'Per tutta la stagione, con il prezzo per frequenza (anche il nuoto libero).' },
    { id: 'CARNET', nome: 'Carnet', spiega: 'Pacchetti di ingressi con una validità, a entrata libera o negli orari di un corso.' },
    { id: 'ALTRO', nome: 'Altri importi', spiega: 'Importi che non danno accesso (es. quota della squadra agonistica): si useranno con i pagamenti.' },
  ];

  let stagioni = $state<Stagione[]>([]);
  let stagioneId = $state('');
  let pagina = $state<PaginaListino | null>(null);
  let corsi = $state<Corso[]>([]);
  let gruppi = $state<GruppoClienti[]>([]);
  let errore = $state('');
  let messaggio = $state('');
  const oggi = iso(new Date());
  const modifica = $derived(sessione.puo('IMPOSTAZIONI'));

  async function avvia() {
    try {
      [stagioni, corsi, gruppi] = await Promise.all([api.stagioni(), api.corsi(), api.gruppi().catch(() => [])]);
      const inCorso = stagioni.find((s) => s.dal <= oggi && oggi <= s.al) ?? stagioni[0];
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
      bozzeRegole = Object.fromEntries(pagina.regole.map((r) => [r.tipo, bozzaRegola(r)]));
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a caricare il listino.');
    }
  }
  $effect(() => {
    void stagioneId;
    offerta = null;
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
  const nomeGruppo = (id: string | null) => (id ? gruppi.find((g) => g.id === id)?.nome ?? 'gruppo' : null);

  function validita(o: Offerta) {
    if (o.validitaMesi) return o.validitaMesi === 1 ? 'valido 1 mese' : `valido ${o.validitaMesi} mesi`;
    if (o.validitaGiorni) return o.validitaGiorni === 1 ? 'valido il giorno stesso' : `valido ${o.validitaGiorni} giorni`;
    return o.tipo === 'CARNET' ? 'senza scadenza' : '';
  }

  // ── Offerte ───────────────────────────────────────────────────────────
  type Bozza = Omit<NuovaOfferta, 'prezzi' | 'ingressi' | 'validitaMesi' | 'validitaGiorni'> & {
    ingressi: string; validita: string; unita: 'mesi' | 'giorni'; prezzi: { volte: string; gruppoId: string; importo: string }[];
  };
  let offerta = $state<{ id: string | null; b: Bozza } | null>(null);

  function apri(o: Offerta | null, tipo: TipoOfferta = 'CORSO') {
    const v = (n: number | null) => (n == null ? '' : String(n));
    offerta = o
      ? { id: o.id, b: { ...o, descrizione: o.descrizione ?? '', ingressi: v(o.ingressi), validita: v(o.validitaMesi ?? o.validitaGiorni),
          unita: o.validitaGiorni ? 'giorni' : 'mesi', prezzi: o.prezzi.map((p) => ({ volte: p.volte == null ? '' : String(p.volte), gruppoId: p.gruppoId ?? '', importo: String(p.importo) })) } }
      : { id: null, b: { stagioneId, nome: '', tipo, descrizione: '', ingressi: tipo === 'CARNET' ? '10' : '', validita: tipo === 'CARNET' ? '3' : '', unita: 'mesi',
          corsoId: null, corsi: [], riduzioneTardiva: tipo === 'CORSO', attiva: true, ordine: 0,
          prezzi: tipo === 'CORSO' ? [{ volte: '1', gruppoId: '', importo: '' }, { volte: '2', gruppoId: '', importo: '' }] : [{ volte: '', gruppoId: '', importo: '' }] } };
  }

  function salvaOfferta() {
    const { id, b } = offerta!;
    const numero = (s: string) => (s.trim() === '' ? null : Number(s));
    const corpo: NuovaOfferta = {
      stagioneId, nome: b.nome, tipo: b.tipo, descrizione: b.descrizione || null, corsoId: b.tipo === 'CARNET' ? b.corsoId || null : null,
      corsi: b.tipo === 'CORSO' ? b.corsi : [], riduzioneTardiva: b.riduzioneTardiva, attiva: b.attiva, ordine: Number(b.ordine) || 0,
      ingressi: b.tipo === 'CARNET' ? numero(b.ingressi) : null,
      validitaMesi: b.unita === 'mesi' ? numero(b.validita) : null, validitaGiorni: b.unita === 'giorni' ? numero(b.validita) : null,
      prezzi: b.prezzi.filter((p) => p.importo.trim() !== '').map((p) => ({ volte: b.tipo === 'CORSO' ? numero(p.volte) : null, gruppoId: p.gruppoId || null, importo: Number(p.importo) })),
    };
    return esegui(() => (id ? api.modificaOfferta(id, corpo) : api.creaOfferta(corpo)), id ? 'Offerta salvata.' : `«${b.nome.trim()}» aggiunta al listino.`)
      .then((ok) => ok && (offerta = null));
  }

  function toggleCorso(id: string) {
    const b = offerta!.b;
    b.corsi = b.corsi.includes(id) ? b.corsi.filter((c) => c !== id) : [...b.corsi, id];
  }

  // ── Copia ─────────────────────────────────────────────────────────────
  let copia = $state<{ da: string; aumento: string } | null>(null);
  const copiaListino = () => esegui(async () => {
    const r = await api.copiaListino(copia!.da, stagioneId, Number(copia!.aumento) || 0);
    messaggio = `${r.offerte} offerte copiate.`;
  }, '').then((ok) => {
    if (ok) {
      messaggio = 'Listino e regole copiati.';
      copia = null;
    }
  });

  // ── Regole ────────────────────────────────────────────────────────────
  type Scaglione = { data: string; percentuale: string };
  type BozzaRegola = { attiva: boolean; scaglioni: Scaglione[]; nelBorsellino: boolean; numero: string; primaPercentuale: string; scadenze: string[];
    settimaneMinime: string; settimaneCertificato: string };
  let bozzeRegole = $state<Record<string, BozzaRegola>>({});

  function bozzaRegola(r: RegolaPrezzo): BozzaRegola {
    const p = r.parametri as Record<string, unknown>;
    const campo = r.tipo === 'RECESSO' ? 'finoAl' : 'dal';
    const sc = ((p.scaglioni as Record<string, unknown>[] | undefined) ?? []).map((x) => ({ data: String(x[campo] ?? ''), percentuale: String(x.percentuale ?? '') }));
    return { attiva: r.attiva, scaglioni: sc, nelBorsellino: p.nelBorsellino === true, numero: String(p.numero ?? 2), primaPercentuale: String(p.primaPercentuale ?? 50),
      scadenze: ((p.scadenze as (string | null)[] | undefined) ?? []).map((s) => s ?? ''), settimaneMinime: String(p.settimaneMinime ?? 6),
      settimaneCertificato: String(p.settimaneCertificato ?? 4) };
  }

  function salvaRegola(tipo: TipoRegolaPrezzo) {
    const b = bozzeRegole[tipo];
    let parametri: Record<string, unknown>;
    if (tipo === 'RIDUZIONE_TARDIVA' || tipo === 'RECESSO') {
      const campo = tipo === 'RECESSO' ? 'finoAl' : 'dal';
      parametri = { scaglioni: b.scaglioni.filter((s) => s.data).map((s) => ({ [campo]: s.data, percentuale: Number(s.percentuale) || 0 })) };
      if (tipo === 'RECESSO') parametri.nelBorsellino = b.nelBorsellino;
    } else if (tipo === 'RATE') {
      const n = Number(b.numero) || 2;
      parametri = { numero: n, primaPercentuale: Number(b.primaPercentuale) || 50, scadenze: Array.from({ length: n - 1 }, (_, i) => b.scadenze[i] || null) };
    } else parametri = { settimaneMinime: Number(b.settimaneMinime) || 0, settimaneCertificato: Number(b.settimaneCertificato) || 0 };
    return esegui(() => api.modificaRegolaPrezzo(stagioneId, tipo, b.attiva, parametri), 'Regola salvata.');
  }

  const REGOLE: { tipo: TipoRegolaPrezzo; nome: string; spiega: string }[] = [
    { tipo: 'RIDUZIONE_TARDIVA', nome: 'Riduzione per iscrizione a stagione iniziata', spiega: 'Da una data in poi, il prezzo delle offerte segnate si riduce della percentuale.' },
    { tipo: 'RATE', nome: 'Rate', spiega: 'Come si divide l\'importo: la prima rata all\'iscrizione, le altre alle scadenze.' },
    { tipo: 'RECESSO', nome: 'Recesso', spiega: 'Quanto si rimborsa di quanto pagato, secondo la data della richiesta. Dopo l\'ultima data, niente.' },
    { tipo: 'SOSPENSIONE', nome: 'Sospensione per malattia o infortunio', spiega: 'Quando la sospensione dà un credito. Si userà con i pagamenti e il borsellino.' },
  ];
</script>

<div class="barra">
  <label class="field stagione"><span class="lbl">Stagione</span>
    <select class="inp" bind:value={stagioneId}>
      {#each stagioni as s (s.id)}<option value={s.id}>{s.nome} · {dataBreve(s.dal)} – {dataBreve(s.al)}</option>{/each}
    </select>
  </label>
  <span class="grow"></span>
  {#if modifica && stagioneId}
    <button class="btn btn-secondary" onclick={() => (copia = { da: stagioni.find((s) => s.id !== stagioneId)?.id ?? '', aumento: '0' })}>Copia da un'altra stagione…</button>
  {/if}
</div>

{#if !stagioni.length && !errore}
  <p class="muted">Nessuna stagione: prima aggiungine una in <a href="#/societa">Società e stagioni</a>.</p>
{/if}
{#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

{#if copia}
  <form class="card blocco" onsubmit={(e) => { e.preventDefault(); copiaListino(); }}>
    <h3>Copia il listino in {pagina?.stagione.nome}</h3>
    <div class="riga">
      <label class="field"><span class="lbl">Da</span>
        <select class="inp" bind:value={copia.da}>{#each stagioni.filter((s) => s.id !== stagioneId) as s (s.id)}<option value={s.id}>{s.nome}</option>{/each}</select>
      </label>
      <label class="field"><span class="lbl">Aumento dei prezzi (%)</span><input class="inp m num" type="number" step="0.5" bind:value={copia.aumento} /></label>
    </div>
    <p class="muted small nomargin">Si copiano offerte, prezzi e regole; le date delle regole si spostano alla stagione nuova. Le offerte già presenti restano.</p>
    <div class="row">
      <button type="button" class="btn btn-secondary grow" onclick={() => (copia = null)}>Annulla</button>
      <button class="btn btn-primary grow" disabled={!copia.da}>Copia</button>
    </div>
  </form>
{/if}

{#if pagina}
  {#each TIPI as t (t.id)}
    {@const elenco = pagina.offerte.filter((o) => o.tipo === t.id)}
    <section class="gruppo-t">
      <div class="testa-t">
        <div><h3>{t.nome}</h3><p class="muted small nomargin">{t.spiega}</p></div>
        {#if modifica && !offerta}<button class="btn btn-secondary piccolo" onclick={() => apri(null, t.id)}>Aggiungi</button>{/if}
      </div>
      <ul class="card righe">
        {#each elenco as o (o.id)}
          <li>
            {#if offerta?.id === o.id}
              {@render modulo()}
            {:else}
              <div class="riga" class:spento={!o.attiva}>
                <div class="grow chi">
                  <strong>{o.nome}</strong>{#if !o.attiva}<span class="tag t-altro">Non attiva</span>{/if}
                  <span class="muted small">
                    {#if o.tipo === 'CORSO'}{o.corsi.map(nomeCorso).join(', ')}{/if}
                    {#if o.tipo === 'CARNET'}{o.ingressi === 1 ? '1 ingresso' : `${o.ingressi} ingressi`} · {validita(o)} · {o.corsoId ? `negli orari di ${nomeCorso(o.corsoId)}` : 'entrata libera'}{/if}
                    {#if o.riduzioneTardiva} · riduzione per iscrizione tardiva{/if}
                  </span>
                </div>
                <div class="prezzi">
                  {#each o.prezzi as p, i (i)}
                    <span class="prezzo" class:di-gruppo={p.gruppoId}>{#if p.gruppoId}<span class="small">{nomeGruppo(p.gruppoId)}</span>{/if}
                      {o.tipo === 'CORSO' ? `${etichettaVolte(p.volte)} ` : ''}<strong>{euro(p.importo)}</strong></span>
                  {/each}
                </div>
                {#if modifica && !offerta}
                  <span class="gruppo-azioni">
                    <Azione icona="matita" etichetta={`Modifica «${o.nome}»`} onclick={() => apri(o)} />
                    <Azione icona="cestino" tono="danger" etichetta={`Togli «${o.nome}» dal listino`} conferma="Elimina?" onclick={() => esegui(() => api.eliminaOfferta(o.id), `«${o.nome}» tolta dal listino.`)} />
                  </span>
                {/if}
              </div>
            {/if}
          </li>
        {:else}
          <li class="muted small">Niente in questa sezione.</li>
        {/each}
        {#if offerta && !offerta.id && offerta.b.tipo === t.id}<li>{@render modulo()}</li>{/if}
      </ul>
    </section>
  {/each}

  <section class="gruppo-t">
    <h3>Regole di prezzo</h3>
    <div class="regole">
      {#each REGOLE as r (r.tipo)}
        {@const b = bozzeRegole[r.tipo]}
        {#if b}
          <div class="card regola" class:spenta={!b.attiva}>
            <div class="riga">
              <button class="switch" role="switch" aria-checked={b.attiva} aria-label={r.nome} disabled={!modifica} onclick={() => (b.attiva = !b.attiva)}><span></span></button>
              <strong class="grow">{r.nome}</strong>
            </div>
            <p class="muted small nomargin">{r.spiega}</p>
            {#if r.tipo === 'RIDUZIONE_TARDIVA' || r.tipo === 'RECESSO'}
              {#each b.scaglioni as s, i (i)}
                <div class="riga">
                  <span class="small">{r.tipo === 'RECESSO' ? 'Fino al' : 'Dal'}</span>
                  <input class="inp data" type="date" bind:value={s.data} disabled={!modifica} aria-label="Data" />
                  <input class="inp m num" type="number" min="0" max="100" bind:value={s.percentuale} disabled={!modifica} aria-label="Percentuale" /><span>%</span>
                  {#if modifica}<Azione icona="chiudi" etichetta="Togli questa data" onclick={() => (b.scaglioni = b.scaglioni.filter((_, j) => j !== i))} />{/if}
                </div>
              {/each}
              {#if modifica}<button class="btn btn-secondary btn-sm sx" onclick={() => (b.scaglioni = [...b.scaglioni, { data: '', percentuale: '' }])}><Icona nome="piu" />Aggiungi una data</button>{/if}
              {#if r.tipo === 'RECESSO'}<label class="spunta small"><input type="checkbox" bind:checked={b.nelBorsellino} disabled={!modifica} /> Il rimborso va nel borsellino (credito), non in contanti</label>{/if}
            {:else if r.tipo === 'RATE'}
              <div class="riga">
                <input class="inp m num" type="number" min="2" max="12" bind:value={b.numero} disabled={!modifica} aria-label="Numero di rate" /><span>rate, la prima del</span>
                <input class="inp m num" type="number" min="1" max="99" bind:value={b.primaPercentuale} disabled={!modifica} aria-label="Percentuale della prima rata" /><span>% all'iscrizione</span>
              </div>
              {#each Array.from({ length: Math.max(0, (Number(b.numero) || 2) - 1) }) as _, i (i)}
                <div class="riga"><span class="small">Rata {i + 2} entro il</span>
                  <input class="inp data" type="date" bind:value={b.scadenze[i]} disabled={!modifica} aria-label={`Scadenza della rata ${i + 2}`} /></div>
              {/each}
            {:else}
              <div class="riga"><span class="small">Credito solo oltre</span><input class="inp m num" type="number" min="0" max="52" bind:value={b.settimaneMinime} disabled={!modifica} aria-label="Settimane minime" /><span class="small">settimane di stop</span></div>
              <div class="riga"><span class="small">Certificato entro</span><input class="inp m num" type="number" min="0" max="52" bind:value={b.settimaneCertificato} disabled={!modifica} aria-label="Settimane per il certificato" /><span class="small">settimane</span></div>
            {/if}
            {#if modifica}<div class="row"><button class="btn btn-primary" onclick={() => salvaRegola(r.tipo)}>Salva</button></div>{/if}
          </div>
        {/if}
      {/each}
    </div>
  </section>
{/if}

{#snippet modulo()}
  {@const b = offerta!.b}
  <form class="modulo" onsubmit={(e) => { e.preventDefault(); salvaOfferta(); }}>
    <div class="due">
      <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={b.nome} placeholder={b.tipo === 'CORSO' ? 'Es. Scuola nuoto' : b.tipo === 'CARNET' ? 'Es. 10 ingressi' : 'Es. Quota agonistica'} /></label>
      <label class="field"><span class="lbl">Descrizione (facoltativa)</span><input class="inp" bind:value={b.descrizione} /></label>
    </div>
    {#if b.tipo === 'CORSO'}
      <div class="field"><span class="lbl">Vale per i corsi</span>
        <div class="chips">{#each corsi.filter((c) => c.attivo || b.corsi.includes(c.id)) as c (c.id)}<button type="button" class="chip" aria-pressed={b.corsi.includes(c.id)} onclick={() => toggleCorso(c.id)}>{c.nome}</button>{/each}</div>
      </div>
    {:else if b.tipo === 'CARNET'}
      <div class="riga">
        <label class="field"><span class="lbl">Ingressi</span><input class="inp m num" type="number" min="1" max="500" bind:value={b.ingressi} /></label>
        <label class="field"><span class="lbl">Validità</span>
          <span class="riga"><input class="inp m num" type="number" min="1" bind:value={b.validita} placeholder="—" />
            <select class="inp unita" bind:value={b.unita}><option value="mesi">mesi</option><option value="giorni">giorni</option></select></span>
        </label>
        <label class="field grow"><span class="lbl">Quando si entra</span>
          <select class="inp" bind:value={b.corsoId}><option value={null}>Entrata libera</option>{#each corsi as c (c.id)}<option value={c.id}>Negli orari di {c.nome}</option>{/each}</select>
        </label>
      </div>
    {/if}
    <div class="field"><span class="lbl">Prezzi</span>
      {#each b.prezzi as p, i (i)}
        <div class="riga">
          {#if b.tipo === 'CORSO'}
            <select class="inp volte" bind:value={p.volte} aria-label="Frequenza">{#each VOLTE as v (v)}<option value={v == null ? '' : String(v)}>{etichettaVolte(v)}</option>{/each}</select>
          {/if}
          <select class="inp gr" bind:value={p.gruppoId} aria-label="Per chi"><option value="">Per tutti</option>{#each gruppi as g (g.id)}<option value={g.id}>{g.nome}</option>{/each}</select>
          <input class="inp m num" type="number" min="0" step="0.01" bind:value={p.importo} placeholder="€" aria-label="Importo" />
          <Azione icona="chiudi" etichetta="Togli questo prezzo" onclick={() => (b.prezzi = b.prezzi.filter((_, j) => j !== i))} />
        </div>
      {/each}
      <button type="button" class="btn btn-secondary btn-sm sx" onclick={() => (b.prezzi = [...b.prezzi, { volte: b.tipo === 'CORSO' ? '1' : '', gruppoId: '', importo: '' }])}><Icona nome="piu" />Aggiungi un prezzo</button>
      <p class="muted small nomargin">Un prezzo per un gruppo vale per chi ne fa parte, se è più basso di quello per tutti.</p>
    </div>
    <div class="riga">
      {#if b.tipo !== 'ALTRO'}<label class="spunta small"><input type="checkbox" bind:checked={b.riduzioneTardiva} /> Si riduce con l'iscrizione a stagione iniziata</label>{/if}
      <label class="spunta small"><input type="checkbox" bind:checked={b.attiva} /> Attiva (si può vendere)</label>
    </div>
    <div class="row">
      <button type="button" class="btn btn-secondary grow" onclick={() => (offerta = null)}>Annulla</button>
      <button class="btn btn-primary grow" disabled={!b.nome.trim() || !b.prezzi.some((x) => x.importo !== '') || (b.tipo === 'CORSO' && !b.corsi.length)}>Salva</button>
    </div>
  </form>
{/snippet}

<style>
  .barra { display: flex; align-items: flex-end; gap: var(--space-12); flex-wrap: wrap; }
  .stagione { min-width: 280px; }
  select.inp { appearance: auto; }
  .gruppo-t { display: flex; flex-direction: column; gap: var(--space-8); }
  .testa-t { display: flex; justify-content: space-between; align-items: flex-end; gap: var(--space-10); }
  h3 { margin: 0; font-size: var(--text-xl); }
  .blocco { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16); }
  .righe { list-style: none; margin: 0; padding: 0; overflow: hidden; }
  .righe > li { padding: var(--space-10) var(--space-14); border-top: 1px solid var(--line-soft); }
  .righe > li:first-child { border-top: none; }
  .riga { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .chi { display: flex; flex-direction: column; gap: 2px; min-width: 200px; }
  .chi .tag { margin-left: var(--space-8); }
  .prezzi { display: flex; flex-wrap: wrap; gap: var(--space-6); justify-content: flex-end; }
  .prezzo { display: inline-flex; align-items: baseline; gap: var(--space-4); padding: var(--space-4) var(--space-10); border-radius: var(--radius-pill);
    background: var(--surface-sunken); font-size: var(--text-sm); white-space: nowrap; }
  .prezzo.di-gruppo { background: var(--cloro-soft); color: var(--cloro-ink); }
  .spento { opacity: 0.6; }
  .modulo { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-4) 0; }
  .num { width: 96px; }
  .volte { width: 150px; }
  .gr { width: 200px; }
  .unita { width: 100px; }
  .data { width: 170px; }
  .regole { display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: var(--space-12); }
  .regola { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-12) var(--space-14); }
  .spenta { opacity: 0.75; }
  .spunta { display: flex; align-items: center; gap: var(--space-6); }
  .piccolo { min-height: 36px; padding: 0 var(--space-12); font-size: var(--text-sm); }
  .sx { align-self: flex-start; }
  @media (max-width: 700px) { .regole { grid-template-columns: 1fr; } .prezzi { justify-content: flex-start; } .stagione { min-width: 0; width: 100%; } }
</style>
