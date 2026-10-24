<script lang="ts">
  import { api, messaggioErrore, type DatiSocieta, type GruppoClienti, type MembroGruppo, type NuovaStagione, type Stagione } from '../lib/api';
  import { dataBreve, iso } from '../lib/date';

  /**
   * Le impostazioni della società (ADR 0018): i dati che compaiono su email e ricevute, le stagioni con la quota
   * associativa e i gruppi di clienti. Il gestionale serve a qualsiasi società: qui sta ciò che cambia dall'una all'altra.
   */
  let dati = $state<DatiSocieta | null>(null);
  let stagioni = $state<Stagione[]>([]);
  let gruppi = $state<GruppoClienti[]>([]);
  let errore = $state('');
  let messaggio = $state('');
  let conferma = $state('');

  type BozzaStagione = { nome: string; dal: string; al: string; quota: string; quotaRimborsabile: boolean };
  let stagione = $state<{ id: string | null; b: BozzaStagione } | null>(null);
  let gruppo = $state<{ id: string | null; nome: string; descrizione: string; attivo: boolean } | null>(null);
  let aperto = $state('');
  let membri = $state<MembroGruppo[]>([]);
  const oggi = iso(new Date());

  async function carica() {
    try {
      [dati, stagioni, gruppi] = await Promise.all([api.societa(), api.stagioni(), api.gruppi()]);
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a caricare le impostazioni.');
    }
  }
  carica();

  async function esegui(azione: () => Promise<unknown>, fatto: string) {
    errore = '';
    messaggio = '';
    conferma = '';
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

  const salvaDati = () => esegui(() => api.modificaSocieta({ ...dati!, giorniAnticipo: Number(dati!.giorniAnticipo) }), 'Dati della società salvati.');

  // ── Stagioni ──────────────────────────────────────────────────────────
  const piuUnGiorno = (d: string) => iso(new Date(Date.parse(d + 'T12:00:00') + 86400000));
  const piuUnAnno = (d: string) => `${Number(d.slice(0, 4)) + 1}${d.slice(4)}`.replace(/-02-29$/, '-02-28');
  /** «2026/27» → «2027/28»; «2026» → «2027»; altrimenti vuoto. */
  function nomeSuccessivo(n: string): string {
    const m = /^(\d{4})\/(\d{2})$/.exec(n);
    if (m) return `${Number(m[1]) + 1}/${String((Number(m[2]) + 1) % 100).padStart(2, '0')}`;
    return /^\d{4}$/.test(n) ? String(Number(n) + 1) : '';
  }

  function nuovaStagione() {
    const ultima = stagioni[0];
    if (ultima) {
      stagione = { id: null, b: { nome: nomeSuccessivo(ultima.nome), dal: piuUnGiorno(ultima.al), al: piuUnAnno(ultima.al),
        quota: ultima.quota == null ? '' : String(ultima.quota), quotaRimborsabile: ultima.quotaRimborsabile } };
    } else {
      const a = new Date().getMonth() >= 8 ? new Date().getFullYear() : new Date().getFullYear() - 1;
      stagione = { id: null, b: { nome: `${a}/${String((a + 1) % 100).padStart(2, '0')}`, dal: `${a}-09-01`, al: `${a + 1}-08-31`, quota: '', quotaRimborsabile: false } };
    }
  }

  function salvaStagione() {
    const s = stagione!;
    const corpo: NuovaStagione = { nome: s.b.nome, dal: s.b.dal, al: s.b.al, quota: s.b.quota === '' ? null : Number(s.b.quota), quotaRimborsabile: s.b.quotaRimborsabile };
    return esegui(() => (s.id ? api.modificaStagione(s.id, corpo) : api.creaStagione(corpo)), s.id ? 'Stagione salvata.' : `Stagione ${s.b.nome} aggiunta.`)
      .then((ok) => ok && (stagione = null));
  }

  const statoStagione = (s: Stagione) => (oggi < s.dal ? { nome: 'Futura', classe: 't-altro' } : oggi > s.al ? { nome: 'Finita', classe: 't-altro' } : { nome: 'In corso', classe: 't-soft' });

  // ── Gruppi ────────────────────────────────────────────────────────────
  function salvaGruppo() {
    const g = gruppo!;
    const descrizione = g.descrizione.trim() || null;
    return esegui(() => (g.id ? api.modificaGruppo(g.id, { nome: g.nome, descrizione, attivo: g.attivo }) : api.creaGruppo({ nome: g.nome, descrizione })),
      g.id ? 'Gruppo salvato.' : `Gruppo «${g.nome.trim()}» creato.`).then((ok) => ok && (gruppo = null));
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
</script>

{#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

{#if dati}
  <div class="colonne">
    <!-- Dati della società -->
    <form class="card blocco" onsubmit={(e) => { e.preventDefault(); salvaDati(); }}>
      <h3>La società</h3>
      <p class="muted small nomargin">Il nome compare nelle email e nelle pagine; gli altri dati serviranno per le ricevute.</p>
      <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={dati.nome} required /></label>
      <label class="field"><span class="lbl">Ragione sociale</span><input class="inp" bind:value={dati.ragioneSociale} placeholder="Es. Società Sportiva S.S.D. a r.l." /></label>
      <div class="due">
        <label class="field"><span class="lbl">Partita IVA</span><input class="inp m" bind:value={dati.partitaIva} inputmode="numeric" maxlength="13" /></label>
        <label class="field"><span class="lbl">Codice fiscale</span><input class="inp m" bind:value={dati.codiceFiscale} maxlength="16" /></label>
      </div>
      <label class="field"><span class="lbl">Indirizzo</span><input class="inp" bind:value={dati.indirizzo} /></label>
      <div class="tre">
        <label class="field"><span class="lbl">CAP</span><input class="inp m" bind:value={dati.cap} maxlength="10" /></label>
        <label class="field"><span class="lbl">Città</span><input class="inp" bind:value={dati.citta} /></label>
        <label class="field"><span class="lbl">Prov.</span><input class="inp m" bind:value={dati.provincia} maxlength="2" /></label>
      </div>
      <div class="due">
        <label class="field"><span class="lbl">Email</span><input class="inp" type="email" bind:value={dati.email} /></label>
        <label class="field"><span class="lbl">Telefono</span><input class="inp" type="tel" bind:value={dati.telefono} /></label>
      </div>
      <label class="field"><span class="lbl">Tesseramento della stagione nuova: proponilo da</span>
        <span class="riga"><input class="inp m num" type="number" min="0" max="365" bind:value={dati.giorniAnticipo} /> <span>giorni prima del suo inizio</span></span>
      </label>
      <label class="field"><span class="lbl">Una famiglia paga per più persone insieme (es. due fratelli)</span>
        <select class="inp" bind:value={dati.pagamentiFamiglia}>
          <option value="A_SCELTA">Si sceglie ogni volta: un pagamento unico o uno per persona</option>
          <option value="INSIEME">Sempre un pagamento unico per la famiglia</option>
          <option value="PER_PERSONA">Sempre un pagamento per ogni persona</option>
        </select>
      </label>
      <div class="row"><button class="btn btn-primary">Salva</button></div>
    </form>

    <!-- Stagioni -->
    <section class="card blocco">
      <h3>Stagioni</h3>
      <p class="muted small nomargin">Le date della stagione e la quota associativa (il tesseramento) le decide la società. Due stagioni non si accavallano.</p>
      <ul class="righe">
        {#each stagioni as s (s.id)}
          {@const st = statoStagione(s)}
          <li>
            {#if stagione?.id === s.id}
              {@render moduloStagione()}
            {:else}
              <div class="riga">
                <strong>{s.nome}</strong>
                <span class="tag {st.classe}">{st.nome}</span>
                <span class="grow muted small">{dataBreve(s.dal)} – {dataBreve(s.al)} · quota {s.quota == null ? 'da decidere' : `${s.quota} €`}{s.quotaRimborsabile ? ' (rimborsabile)' : ''}</span>
                <button class="link small" onclick={() => (stagione = { id: s.id, b: { nome: s.nome, dal: s.dal, al: s.al, quota: s.quota == null ? '' : String(s.quota), quotaRimborsabile: s.quotaRimborsabile } })}>Modifica</button>
                <button class="link small rosso" onclick={() => (conferma === 'st' + s.id ? esegui(() => api.eliminaStagione(s.id), `Stagione ${s.nome} eliminata.`) : (conferma = 'st' + s.id))}>
                  {conferma === 'st' + s.id ? 'Conferma: elimina' : 'Elimina'}</button>
              </div>
            {/if}
          </li>
        {:else}
          <li class="muted small">Nessuna stagione: senza, non si possono registrare i tesseramenti.</li>
        {/each}
      </ul>
      {#if stagione && !stagione.id}
        {@render moduloStagione()}
      {:else if !stagione}
        <button class="btn btn-secondary sx" onclick={nuovaStagione}>{stagioni.length ? 'Aggiungi la stagione successiva' : 'Aggiungi la stagione'}</button>
      {/if}
      {#if conferma.startsWith('st')}<p class="muted small nomargin">Eliminando una stagione i tesseramenti già registrati restano com'erano.</p>{/if}
    </section>

    <!-- Gruppi di clienti -->
    <section class="card blocco largo">
      <h3>Gruppi di clienti</h3>
      <p class="muted small nomargin">Etichette per le persone (es. alunni della scuola, convenzioni, squadra agonistica): serviranno per prezzi
        diversi e per la precedenza nei rinnovi. Si danno dalla scheda del cliente.</p>
      <ul class="righe">
        {#each gruppi as g (g.id)}
          <li>
            {#if gruppo?.id === g.id}
              {@render moduloGruppo()}
            {:else}
              <div class="riga" class:spento={!g.attivo}>
                <strong>{g.nome}</strong>
                {#if !g.attivo}<span class="tag t-altro">Non attivo</span>{/if}
                <span class="grow muted small">{g.descrizione ?? ''}</span>
                <button class="link small" onclick={() => apriMembri(g)}>{g.persone === 1 ? '1 persona' : `${g.persone} persone`}</button>
                <button class="link small" onclick={() => (gruppo = { id: g.id, nome: g.nome, descrizione: g.descrizione ?? '', attivo: g.attivo })}>Modifica</button>
              </div>
              {#if aperto === g.id}
                <div class="membri small">
                  {#each membri as m (m.appartenenzaId)}
                    <a href={`#/clienti/${m.personaId}`}>{m.cognome} {m.nome}</a>
                  {:else}
                    <span class="muted">Nessuno.</span>
                  {/each}
                </div>
              {/if}
            {/if}
          </li>
        {:else}
          <li class="muted small">Nessun gruppo.</li>
        {/each}
      </ul>
      {#if gruppo && !gruppo.id}
        {@render moduloGruppo()}
      {:else if !gruppo}
        <button class="btn btn-secondary sx" onclick={() => (gruppo = { id: null, nome: '', descrizione: '', attivo: true })}>Nuovo gruppo</button>
      {/if}
    </section>
  </div>
{:else if !errore}
  <p class="muted">Caricamento…</p>
{/if}

{#snippet moduloStagione()}
  {@const s = stagione!}
  <form class="modulo" onsubmit={(e) => { e.preventDefault(); salvaStagione(); }}>
    <div class="tre-s">
      <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={s.b.nome} placeholder="2026/27" maxlength="20" /></label>
      <label class="field"><span class="lbl">Dal</span><input class="inp" type="date" bind:value={s.b.dal} /></label>
      <label class="field"><span class="lbl">Al</span><input class="inp" type="date" bind:value={s.b.al} min={s.b.dal} /></label>
    </div>
    <div class="riga">
      <label class="field quota"><span class="lbl">Quota associativa (€)</span><input class="inp m" type="number" min="0" step="0.01" bind:value={s.b.quota} placeholder="Da decidere" /></label>
      <label class="spunta small"><input type="checkbox" bind:checked={s.b.quotaRimborsabile} /> Rimborsabile</label>
    </div>
    <div class="row">
      <button type="button" class="btn btn-secondary grow" onclick={() => (stagione = null)}>Annulla</button>
      <button class="btn btn-primary grow" disabled={!s.b.nome.trim() || !s.b.dal || !s.b.al}>Salva</button>
    </div>
  </form>
{/snippet}

{#snippet moduloGruppo()}
  {@const g = gruppo!}
  <form class="modulo" onsubmit={(e) => { e.preventDefault(); salvaGruppo(); }}>
    <div class="due">
      <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={g.nome} maxlength="80" /></label>
      <label class="field"><span class="lbl">Descrizione</span><input class="inp" bind:value={g.descrizione} maxlength="300" /></label>
    </div>
    {#if g.id}<label class="spunta small"><input type="checkbox" bind:checked={g.attivo} /> Attivo (un gruppo non attivo resta sulle persone che ci sono, ma non se ne aggiungono altre)</label>{/if}
    <div class="row">
      <button type="button" class="btn btn-secondary grow" onclick={() => (gruppo = null)}>Annulla</button>
      <button class="btn btn-primary grow" disabled={!g.nome.trim()}>Salva</button>
    </div>
  </form>
{/snippet}

<style>
  .colonne { display: grid; grid-template-columns: repeat(auto-fit, minmax(420px, 1fr)); gap: var(--space-14); align-items: start; }
  .largo { grid-column: 1 / -1; }
  .blocco { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16); }
  h3 { margin: 0; font-size: var(--text-xl); }
  .tre { display: grid; grid-template-columns: 110px minmax(0, 1fr) 80px; gap: var(--space-10); }
  .tre-s { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(0, 1fr); gap: var(--space-10); }
  .riga { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .num { width: 90px; }
  .quota { width: 200px; }
  .righe { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
  .righe > li { padding: var(--space-8) 0; border-top: 1px solid var(--line-soft); }
  .righe > li:first-child { border-top: none; }
  .modulo { display: flex; flex-direction: column; gap: var(--space-10); }
  .spunta { display: flex; align-items: center; gap: var(--space-6); }
  .membri { display: flex; flex-wrap: wrap; gap: var(--space-4) var(--space-12); padding: var(--space-6) 0 0 var(--space-4); }
  .spento { opacity: 0.65; }
  .sx { align-self: flex-start; }
  .link { min-height: 32px; }
  .rosso { color: var(--danger); }
  @media (max-width: 700px) {
    .colonne { grid-template-columns: 1fr; }
    .tre-s { grid-template-columns: 1fr; }
  }
</style>
