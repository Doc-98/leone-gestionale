<script lang="ts">
  import { onDestroy, untrack } from 'svelte';
  import {
    api, messaggioErrore,
    type Abbonamento, type Braccialetto, type Corso, type SchedaAccessi, type SchedaCliente,
  } from '../lib/api';
  import { dataBreve, dataDi, GIORNI_BREVI, iso, ora, oraDi } from '../lib/date';
  import { leggiConNfc, nfcDisponibile, normalizzaCodice } from '../lib/nfc';
  import { sessione } from '../lib/sessione.svelte';
  import EsitoTornello from '../components/EsitoTornello.svelte';

  /**
   * La scheda di un cliente per la segreteria: come andrebbe adesso al tornello, braccialetti, ban e golden pass,
   * certificato medico, tesseramento, abbonamenti, turni frequentati, ultimi passaggi. Ogni apertura si registra.
   */
  let { personaId }: { personaId: string } = $props();
  const id = untrack(() => personaId);
  const oggi = iso(new Date());

  let cliente = $state<SchedaCliente | null>(null);
  let accessi = $state<SchedaAccessi | null>(null);
  let corsi = $state<Corso[]>([]);
  let errore = $state('');
  let messaggio = $state('');
  let conferma = $state('');

  const daiGolden = $derived(sessione.puo('GOLDEN_PASS'));

  async function carica() {
    try {
      [cliente, accessi] = await Promise.all([api.schedaCliente(id), api.schedaAccessi(id)]);
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco ad aprire la scheda.');
    }
  }
  carica();
  api.corsi().then((c) => (corsi = c.filter((x) => x.attivo))).catch(() => {});

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

  /** Le azioni pericolose chiedono un secondo clic. */
  function confermato(chiave: string) {
    if (conferma === chiave) return true;
    conferma = chiave;
    return false;
  }

  // ── Braccialetti ──────────────────────────────────────────────────────
  let nuovoCodice = $state('');
  let banBraccialetto = $state<{ id: string; motivo: string } | null>(null);
  let nfcAttivo = $state(false);
  let fermaNfc: AbortController | null = null;

  async function leggiNfc() {
    fermaNfc = new AbortController();
    try {
      await leggiConNfc((c) => {
        nuovoCodice = c;
        fermaNfc?.abort();
        nfcAttivo = false;
      }, fermaNfc.signal);
      nfcAttivo = true;
    } catch {
      errore = "Non riesco ad avviare l'NFC.";
    }
  }
  onDestroy(() => fermaNfc?.abort());

  const assegna = async () => {
    const c = normalizzaCodice(nuovoCodice);
    if (await esegui(() => api.assegnaBraccialetto(id, c), `Braccialetto ${c} assegnato.`)) nuovoCodice = '';
  };

  const STATI: Record<Braccialetto['stato'], { nome: string; classe: string }> = {
    ATTIVO: { nome: 'Attivo', classe: 't-soft' },
    SMARRITO: { nome: 'Smarrito', classe: 't-warn' },
    DISATTIVATO: { nome: 'Disattivato', classe: 't-altro' },
  };

  // ── Ban e golden pass ─────────────────────────────────────────────────
  let banPersona = $state<{ tipo: 'BAN' | 'SORVEGLIANZA'; motivo: string; al: string } | null>(null);
  let permesso = $state<{ tipo: 'USCITA_AUTONOMA' | 'ACCOMPAGNATORE'; firmataDa: string; firmataIl: string; al: string; note: string } | null>(null);
  let sospensione = $state<{ abbonamentoId: string; dal: string; al: string; motivo: string } | null>(null);
  let golden = $state<{ motivo: string } | null>(null);

  // ── Certificato ───────────────────────────────────────────────────────
  let certificato = $state<{ tipo: 'NON_AGONISTICO' | 'AGONISTICO' | 'PROVVISORIO'; rilasciatoIl: string; scadenza: string; firmatoDa: string } | null>(null);
  const tra = (giorni: number) => iso(new Date(Date.now() + giorni * 86400000));
  const TIPI_CERT = { NON_AGONISTICO: 'Non agonistico', AGONISTICO: 'Agonistico', PROVVISORIO: 'Provvisorio' } as const;

  // ── Abbonamenti ───────────────────────────────────────────────────────
  let abbonamento = $state<{ tipo: 'CORSO' | 'CARNET'; corsoId: string; volte: number; ingressi: number | string; aOrario: boolean; dal: string; al: string; importo: number | string | null } | null>(null);

  function nuovoAbbonamento() {
    abbonamento = { tipo: 'CORSO', corsoId: cliente?.turni[0]?.corsoId ?? '', volte: Math.max(1, Math.min(3, cliente?.turni.length ?? 1)), ingressi: 10, aOrario: false,
      dal: oggi, al: cliente?.stagioneProposta.al ?? '', importo: null };
  }

  const numero = (v: number | string | null) => (v === null || v === '' ? null : Number(v));

  const salvaAbbonamento = async () => {
    const a = abbonamento!;
    const ok = await esegui(() => api.nuovoAbbonamento(id, a.tipo === 'CORSO'
      ? { tipo: 'CORSO', corsoId: a.corsoId, volteASettimana: a.volte, dal: a.dal, al: a.al || null, importo: numero(a.importo) }
      : { tipo: 'CARNET', ingressi: numero(a.ingressi), corsoId: a.aOrario ? a.corsoId : null, dal: a.dal, al: a.al || null, importo: numero(a.importo) }), 'Abbonamento registrato.');
    if (ok) abbonamento = null;
  };

  function statoAbbonamento(a: Abbonamento): { nome: string; classe: string } {
    if (a.sospesoOggi) return { nome: 'Sospeso', classe: 't-warn' };
    if (a.dal > oggi) return { nome: 'Non iniziato', classe: 't-altro' };
    if ((a.alEffettivo ?? a.al) && (a.alEffettivo ?? a.al)! < oggi) return { nome: 'Scaduto', classe: 't-warn' };
    if (a.tipo === 'CARNET' && (a.residui ?? 0) <= 0) return { nome: 'Esaurito', classe: 't-warn' };
    return { nome: 'Valido', classe: 't-soft' };
  }

  const attivi = $derived(accessi?.blocchi.filter((b) => b.attivo) ?? []);
  const gp = $derived(accessi?.goldenPass.find((g) => g.attivo));
  const eta = (n: number | null) => (n == null ? '' : n === 1 ? '1 anno' : `${n} anni`);
  const tesseramentoInCorso = $derived(cliente?.tesseramenti.some((t) => t.stagione === cliente!.stagioneProposta.stagione) ?? false);
</script>

{#if cliente && accessi}
  {@const p = cliente.persona}
  <header class="testa">
    <div>
      <h2 class="d nome">{p.nome} {p.cognome}</h2>
      <div class="muted">{eta(p.eta)}{p.dataNascita ? ` · nato il ${dataBreve(p.dataNascita)}` : ''}</div>
    </div>
  </header>

  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

  <div class="adesso">
    <div class="lbl">Adesso al tornello</div>
    <EsitoTornello esito={accessi.adesso} simulato />
  </div>

  <div class="griglia">
    <!-- Braccialetti -->
    <section class="card blocco">
      <h3>Braccialetti</h3>
      <ul class="righe">
        {#each accessi.braccialetti as b (b.id)}
          <li class="riga-b" class:spento={b.stato === 'DISATTIVATO'}>
            <div class="riga-testa">
              <span class="m codice">{b.codice}</span>
              {#if b.decimale}<span class="m muted small" title="Lo stesso codice in decimale, come nel gestionale attuale">{b.decimale}</span>{/if}
              <span class="tag {STATI[b.stato].classe}">{STATI[b.stato].nome}</span>
              {#if b.bannato}<span class="tag bannato">Bannato</span>{/if}
              <span class="muted small">dal {dataBreve(dataDi(b.statoDal))}</span>
            </div>
            {#if banBraccialetto?.id === b.id}
              <form class="row" onsubmit={(e) => { e.preventDefault(); esegui(() => api.banna({ braccialettoId: b.id, motivo: banBraccialetto!.motivo, al: null }), `Braccialetto ${b.codice} bannato.`).then((ok) => ok && (banBraccialetto = null)); }}>
                <input class="inp grow" bind:value={banBraccialetto.motivo} placeholder="Motivo del ban" aria-label="Motivo del ban" />
                <button class="btn btn-danger" disabled={!banBraccialetto.motivo.trim()}>Banna</button>
                <button type="button" class="link small" onclick={() => (banBraccialetto = null)}>Annulla</button>
              </form>
            {:else if b.stato !== 'DISATTIVATO'}
              <div class="azioni">
                {#if b.stato === 'ATTIVO'}
                  <button class="link small" onclick={() => esegui(() => api.statoBraccialetto(b.id, 'SMARRITO'), `${b.codice} segnato come smarrito: al tornello darà rosso.`)}>Smarrito</button>
                  {#if !b.bannato}<button class="link small" onclick={() => (banBraccialetto = { id: b.id, motivo: '' })}>Banna</button>{/if}
                {:else}
                  <button class="link small" onclick={() => esegui(() => api.statoBraccialetto(b.id, 'ATTIVO'), `${b.codice} ritrovato: di nuovo attivo.`)}>Ritrovato</button>
                {/if}
                <button class="link small rosso" onclick={() => confermato('dis' + b.id) && esegui(() => api.statoBraccialetto(b.id, 'DISATTIVATO'), `${b.codice} disattivato.`)}>
                  {conferma === 'dis' + b.id ? 'Conferma: disattiva' : 'Disattiva'}
                </button>
              </div>
            {/if}
          </li>
        {:else}
          <li class="muted small">Nessun braccialetto.</li>
        {/each}
      </ul>
      <form class="aggiungi" onsubmit={(e) => { e.preventDefault(); assegna(); }}>
        <span class="lbl">Assegna un braccialetto</span>
        <div class="row">
          <input class="inp m grow" bind:value={nuovoCodice} placeholder="Codice o lettore" aria-label="Codice del nuovo braccialetto" autocomplete="off" />
          {#if nfcDisponibile()}
            <button type="button" class="btn btn-secondary" onclick={leggiNfc} disabled={nfcAttivo}>{nfcAttivo ? 'Avvicina…' : 'NFC'}</button>
          {/if}
          <button class="btn btn-primary" disabled={normalizzaCodice(nuovoCodice).length < 4}>Assegna</button>
        </div>
        <p class="muted small nomargin">Se uno è smarrito resta suo: se si ritrova basta segnarlo come ritrovato.</p>
      </form>
    </section>

    <!-- Ban e golden pass -->
    <section class="card blocco">
      <h3>Ban e golden pass</h3>
      {#if gp}
        <div class="oro">
          <span><strong>Golden pass</strong> dal {dataBreve(gp.dal)}{gp.al ? ` al ${dataBreve(gp.al)}` : ''}{gp.motivo ? ` · ${gp.motivo}` : ''}</span>
          {#if daiGolden}<button class="link small" onclick={() => confermato('gp') && esegui(() => api.revocaGoldenPass(gp!.id), 'Golden pass revocato.')}>{conferma === 'gp' ? 'Conferma' : 'Revoca'}</button>{/if}
        </div>
        <p class="muted small nomargin">Salta certificato, tesseramento, abbonamento e orario. Braccialetto smarrito e ban valgono lo stesso.</p>
      {/if}
      <ul class="righe">
        {#each attivi as b (b.id)}
          <li class="ban">
            <span>
              {#if b.tipo === 'SORVEGLIANZA'}<span class="tag oro-t">Sotto controllo</span>{/if}
              <strong>{b.braccialettoId ? `Braccialetto ${b.codice}` : 'Persona'}</strong>{b.tipo === 'BAN' ? (b.braccialettoId ? ' bannato' : ' bannata') : ''}
              dal {dataBreve(b.dal)}{b.al ? ` al ${dataBreve(b.al)}` : ''}: {b.motivo}</span>
            <button class="link small" onclick={() => confermato('ban' + b.id) && esegui(() => api.revocaBan(b.id), b.tipo === 'BAN' ? 'Ban revocato.' : 'Non è più sotto controllo.')}>{conferma === 'ban' + b.id ? 'Conferma' : 'Revoca'}</button>
          </li>
        {:else}
          <li class="muted small">Nessun ban, nessuna segnalazione.</li>
        {/each}
      </ul>
      {#if banPersona}
        <form class="modulo" onsubmit={(e) => { e.preventDefault(); esegui(() => api.banna({ tipo: banPersona!.tipo, personaId: id, motivo: banPersona!.motivo, al: banPersona!.al || null }), banPersona!.tipo === 'BAN' ? 'Persona bannata: tutti i suoi braccialetti danno rosso.' : 'Sotto controllo: a ogni passaggio la segreteria riceve un avviso.').then((ok) => ok && (banPersona = null)); }}>
          {#if banPersona.tipo === 'SORVEGLIANZA'}<p class="muted small nomargin">Entra normalmente, ma il tornello lo segnala e la segreteria riceve un avviso a ogni passaggio.</p>{/if}
          <input class="inp" bind:value={banPersona.motivo} placeholder="Motivo" aria-label="Motivo del ban" />
          <label class="field"><span class="lbl">Fino al (vuoto: finché non si revoca)</span><input class="inp" type="date" bind:value={banPersona.al} min={oggi} /></label>
          <div class="row">
            <button type="button" class="btn btn-secondary grow" onclick={() => (banPersona = null)}>Annulla</button>
            <button class="btn {banPersona.tipo === 'BAN' ? 'btn-danger' : 'btn-primary'} grow" disabled={!banPersona.motivo.trim()}>{banPersona.tipo === 'BAN' ? 'Banna la persona' : 'Metti sotto controllo'}</button>
          </div>
        </form>
      {:else if golden}
        <form class="modulo" onsubmit={(e) => { e.preventDefault(); esegui(() => api.goldenPass(id, golden!.motivo, null), 'Golden pass attivato.').then((ok) => ok && (golden = null)); }}>
          <input class="inp" bind:value={golden.motivo} placeholder="Motivo (es. tornellista)" aria-label="Motivo del golden pass" />
          <div class="row">
            <button type="button" class="btn btn-secondary grow" onclick={() => (golden = null)}>Annulla</button>
            <button class="btn btn-primary grow">Attiva il golden pass</button>
          </div>
        </form>
      {:else}
        <div class="row a-capo">
          <button class="btn btn-secondary grow" onclick={() => (banPersona = { tipo: 'BAN', motivo: '', al: '' })}>Banna la persona…</button>
          <button class="btn btn-secondary grow" onclick={() => (banPersona = { tipo: 'SORVEGLIANZA', motivo: '', al: '' })}>Sotto controllo…</button>
          {#if daiGolden && !gp}<button class="btn btn-secondary grow" onclick={() => (golden = { motivo: '' })}>Golden pass…</button>{/if}
        </div>
      {/if}
    </section>

    <!-- Permessi firmati -->
    <section class="card blocco">
      <h3>Permessi firmati</h3>
      <ul class="righe">
        {#each accessi.autorizzazioni.filter((a) => a.attiva) as a (a.id)}
          <li class="ban">
            <span><strong>{a.tipo === 'USCITA_AUTONOMA' ? 'Uscita autonoma' : 'Accompagnatore'}</strong>
              {a.firmataDa ? ` · firmato da ${a.firmataDa}` : ''}{a.firmataIl ? ` il ${dataBreve(a.firmataIl)}` : ''}
              <span class="muted small"> · dal {dataBreve(a.dal)}{a.al ? ` al ${dataBreve(a.al)}` : ''}</span></span>
            <button class="link small" onclick={() => confermato('aut' + a.id) && esegui(() => api.revocaAutorizzazione(a.id), 'Permesso revocato.')}>{conferma === 'aut' + a.id ? 'Conferma' : 'Revoca'}</button>
          </li>
        {:else}
          <li class="muted small">Nessun permesso.</li>
        {/each}
      </ul>
      {#if permesso}
        <form class="modulo" onsubmit={(e) => { e.preventDefault(); esegui(() => api.autorizza(id, { tipo: permesso!.tipo, firmataDa: permesso!.firmataDa || null, firmataIl: permesso!.firmataIl || null, dal: null, al: permesso!.al || null, note: permesso!.note || null }), 'Permesso registrato.').then((ok) => ok && (permesso = null)); }}>
          <div class="seg" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Tipo di permesso">
            <button type="button" aria-pressed={permesso.tipo === 'USCITA_AUTONOMA'} onclick={() => (permesso!.tipo = 'USCITA_AUTONOMA')}>Uscita autonoma</button>
            <button type="button" aria-pressed={permesso.tipo === 'ACCOMPAGNATORE'} onclick={() => (permesso!.tipo = 'ACCOMPAGNATORE')}>Accompagnatore</button>
          </div>
          <p class="muted small nomargin">{permesso.tipo === 'USCITA_AUTONOMA'
            ? 'Il minorenne può uscire da solo passando il braccialetto sul lato d\'uscita (se il controllo dell\'uscita è acceso).'
            : 'Può ripassare il braccialetto subito, per far entrare chi accompagna (se il cooldown è acceso).'}</p>
          <div class="due">
            <input class="inp" bind:value={permesso.firmataDa} placeholder={permesso.tipo === 'USCITA_AUTONOMA' ? 'Firmato da (genitore)' : 'Firmato da (facoltativo)'} aria-label="Firmato da" />
            <input class="inp" type="date" bind:value={permesso.firmataIl} max={oggi} aria-label="Firmato il" />
          </div>
          <label class="field"><span class="lbl">Valido fino al (vuoto: finché non si revoca)</span><input class="inp" type="date" bind:value={permesso.al} min={oggi} /></label>
          <div class="row">
            <button type="button" class="btn btn-secondary grow" onclick={() => (permesso = null)}>Annulla</button>
            <button class="btn btn-primary grow" disabled={permesso.tipo === 'USCITA_AUTONOMA' && !permesso.firmataDa.trim()}>Registra</button>
          </div>
        </form>
      {:else}
        <button class="btn btn-secondary" onclick={() => (permesso = { tipo: 'USCITA_AUTONOMA', firmataDa: '', firmataIl: oggi, al: '', note: '' })}>Nuovo permesso…</button>
      {/if}
    </section>

    <!-- Certificato medico -->
    <section class="card blocco">
      <h3>Certificato medico</h3>
      <ul class="righe">
        {#each cliente.certificati as c, i (c.id)}
          <li class="voce-l" class:spento={i > 0}>
            <span class="grow">{TIPI_CERT[c.tipo]} · scade il <strong>{dataBreve(c.scadenza)}</strong>{c.firmatoDa ? ` · firmato da ${c.firmatoDa}` : ''}</span>
            {#if i === 0}<span class="tag {c.valido ? 't-soft' : 't-warn'}">{c.valido ? 'Valido' : 'Scaduto'}</span>{/if}
            <button class="link small muted" onclick={() => confermato('cert' + c.id) && esegui(() => api.annullaVoce('certificati', c.id), 'Certificato annullato.')}>{conferma === 'cert' + c.id ? 'Conferma' : 'Annulla'}</button>
          </li>
        {:else}
          <li class="muted small">Nessun certificato.</li>
        {/each}
      </ul>
      {#if certificato}
        <form class="modulo" onsubmit={(e) => { e.preventDefault(); esegui(() => api.nuovoCertificato(id, { tipo: certificato!.tipo, rilasciatoIl: certificato!.rilasciatoIl || null, scadenza: certificato!.scadenza, firmatoDa: certificato!.tipo === 'PROVVISORIO' ? certificato!.firmatoDa : null }), 'Certificato registrato.').then((ok) => ok && (certificato = null)); }}>
          <div class="seg" style="grid-template-columns: 1fr 1fr 1fr" role="group" aria-label="Tipo">
            <button type="button" aria-pressed={certificato.tipo === 'NON_AGONISTICO'} onclick={() => (certificato!.tipo = 'NON_AGONISTICO')}>Non agonistico</button>
            <button type="button" aria-pressed={certificato.tipo === 'AGONISTICO'} onclick={() => (certificato!.tipo = 'AGONISTICO')}>Agonistico</button>
            <button type="button" aria-pressed={certificato.tipo === 'PROVVISORIO'} onclick={() => { certificato!.tipo = 'PROVVISORIO'; certificato!.scadenza = tra(14); }}>Provvisorio</button>
          </div>
          {#if certificato.tipo === 'PROVVISORIO'}
            <input class="inp" bind:value={certificato.firmatoDa} placeholder="Firmato da (l'interessato o un genitore)" aria-label="Firmato da" />
            <p class="muted small nomargin">Dichiarazione firmata in attesa del certificato: al tornello vale pochi giorni (vedi le regole) e passa con un avviso.</p>
          {/if}
          <div class="due">
            <label class="field"><span class="lbl">Rilasciato il</span><input class="inp" type="date" bind:value={certificato.rilasciatoIl} max={oggi} /></label>
            <label class="field"><span class="lbl">Scade il</span><input class="inp" type="date" bind:value={certificato.scadenza} /></label>
          </div>
          <div class="row">
            <button type="button" class="btn btn-secondary grow" onclick={() => (certificato = null)}>Annulla</button>
            <button class="btn btn-primary grow" disabled={!certificato.scadenza}>Registra</button>
          </div>
        </form>
      {:else}
        <button class="btn btn-secondary" onclick={() => (certificato = { tipo: 'NON_AGONISTICO', rilasciatoIl: oggi, scadenza: '', firmatoDa: '' })}>Nuovo certificato…</button>
      {/if}
    </section>

    <!-- Tesseramento -->
    <section class="card blocco">
      <h3>Tesseramento</h3>
      <ul class="righe">
        {#each cliente.tesseramenti as t (t.id)}
          <li class="voce-l">
            <span class="grow"><strong>{t.stagione}</strong> · {dataBreve(t.dal)} – {dataBreve(t.al)}{t.importo != null ? ` · ${t.importo} €` : ''}</span>
            <span class="tag {t.valido ? 't-soft' : 't-altro'}">{t.valido ? 'Valido' : t.dal > oggi ? 'Non iniziato' : 'Scaduto'}</span>
            <button class="link small muted" onclick={() => confermato('tes' + t.id) && esegui(() => api.annullaVoce('tesseramenti', t.id), 'Tesseramento annullato.')}>{conferma === 'tes' + t.id ? 'Conferma' : 'Annulla'}</button>
          </li>
        {:else}
          <li class="muted small">Nessun tesseramento.</li>
        {/each}
      </ul>
      {#if !tesseramentoInCorso}
        {@const s = cliente.stagioneProposta}
        <button class="btn btn-secondary" onclick={() => esegui(() => api.nuovoTesseramento(id, {}), `Tesseramento ${s.stagione} registrato.`)}>
          Registra il tesseramento {s.stagione} · {s.importo} €
        </button>
        <p class="muted small nomargin">Vale dal {dataBreve(s.dal)} al {dataBreve(s.al)}, pagato oggi.</p>
      {/if}
    </section>

    <!-- Abbonamenti -->
    <section class="card blocco largo">
      <h3>Abbonamenti</h3>
      <ul class="righe">
        {#each cliente.abbonamenti as a (a.id)}
          {@const st = statoAbbonamento(a)}
          <li class="voce-l">
            <span class="grow">
              {#if a.tipo === 'CORSO'}<strong>{a.corso}</strong> · {a.volteASettimana === 1 ? 'una volta' : `${a.volteASettimana} volte`} a settimana
              {:else}<strong>Carnet {a.ingressi} ingressi</strong> · {a.corso ? `negli orari di ${a.corso}` : 'entrata libera'} · {a.residui} rimasti{a.ultimoIngresso ? `, ultimo il ${dataBreve(dataDi(a.ultimoIngresso))}` : ''}{/if}
              <span class="muted small"> · dal {dataBreve(a.dal)}{a.al ? ` al ${dataBreve(a.alEffettivo ?? a.al)}` : ''}{a.alEffettivo && a.al && a.alEffettivo !== a.al ? ` (allungato per le sospensioni)` : ''}</span>
              {#each a.sospensioni as s (s.id)}
                <span class="sosp small">Sospeso dal {dataBreve(s.dal)} al {dataBreve(s.al)}{s.motivo ? ` · ${s.motivo}` : ''}
                  <button class="link small muted" onclick={() => confermato('sos' + s.id) && esegui(() => api.annullaSospensione(s.id), 'Sospensione annullata.')}>{conferma === 'sos' + s.id ? 'Conferma' : 'Annulla'}</button></span>
              {/each}
              {#if sospensione?.abbonamentoId === a.id}
                <form class="sospendi" onsubmit={(e) => { e.preventDefault(); esegui(() => api.sospendi(a.id, { dal: sospensione!.dal, al: sospensione!.al, motivo: sospensione!.motivo || null }), 'Abbonamento sospeso: la scadenza si allunga di altrettanti giorni.').then((ok) => ok && (sospensione = null)); }}>
                  <input class="inp" type="date" bind:value={sospensione.dal} aria-label="Sospeso dal" />
                  <input class="inp" type="date" bind:value={sospensione.al} min={sospensione.dal} aria-label="Sospeso fino al" />
                  <input class="inp" bind:value={sospensione.motivo} placeholder="Motivo (es. infortunio)" aria-label="Motivo" />
                  <button class="btn btn-primary" disabled={!sospensione.dal || !sospensione.al}>Sospendi</button>
                  <button type="button" class="link small" onclick={() => (sospensione = null)}>Annulla</button>
                </form>
              {/if}
            </span>
            <span class="tag {st.classe}">{st.nome}</span>
            {#if !sospensione}<button class="link small" onclick={() => (sospensione = { abbonamentoId: a.id, dal: oggi, al: tra(13), motivo: '' })}>Sospendi</button>{/if}
            {#if !a.al || a.al > oggi}
              <button class="link small" onclick={() => confermato('fin' + a.id) && esegui(() => api.terminaAbbonamento(a.id, oggi), 'Abbonamento terminato oggi.')}>{conferma === 'fin' + a.id ? 'Conferma' : 'Termina oggi'}</button>
            {/if}
            <button class="link small muted" onclick={() => confermato('abb' + a.id) && esegui(() => api.annullaVoce('abbonamenti', a.id), 'Abbonamento annullato.')}>{conferma === 'abb' + a.id ? 'Conferma' : 'Annulla'}</button>
          </li>
        {:else}
          <li class="muted small">Nessun abbonamento.</li>
        {/each}
      </ul>
      {#if abbonamento}
        <form class="modulo" onsubmit={(e) => { e.preventDefault(); salvaAbbonamento(); }}>
          <div class="seg" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Tipo di abbonamento">
            <button type="button" aria-pressed={abbonamento.tipo === 'CORSO'} onclick={() => { abbonamento!.tipo = 'CORSO'; abbonamento!.al = cliente!.stagioneProposta.al; }}>Corso</button>
            <button type="button" aria-pressed={abbonamento.tipo === 'CARNET'} onclick={() => { abbonamento!.tipo = 'CARNET'; abbonamento!.al = ''; }}>Carnet</button>
          </div>
          {#if abbonamento.tipo === 'CORSO'}
            <label class="field"><span class="lbl">Corso</span>
              <select class="inp" bind:value={abbonamento.corsoId}>
                <option value="" disabled>Scegli il corso</option>
                {#each corsi as c (c.id)}<option value={c.id}>{c.nome}</option>{/each}
              </select>
            </label>
            <div class="field">
              <span class="lbl">Volte a settimana</span>
              <div class="chips">
                {#each [1, 2, 3] as v (v)}<button type="button" class="chip" aria-pressed={abbonamento.volte === v} onclick={() => (abbonamento!.volte = v)}>{v === 1 ? 'Mono' : v === 2 ? 'Bi' : 'Tri'}settimanale</button>{/each}
              </div>
            </div>
          {:else}
            <label class="field"><span class="lbl">Ingressi</span><input class="inp" type="number" min="1" max="500" bind:value={abbonamento.ingressi} /></label>
            <div class="field">
              <span class="lbl">Quando si entra</span>
              <div class="seg" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Quando si entra">
                <button type="button" aria-pressed={!abbonamento.aOrario} onclick={() => (abbonamento!.aOrario = false)}>Entrata libera</button>
                <button type="button" aria-pressed={abbonamento.aOrario} onclick={() => (abbonamento!.aOrario = true)}>Negli orari di un corso</button>
              </div>
            </div>
            {#if abbonamento.aOrario}
              <label class="field"><span class="lbl">Corso (es. Nuoto libero)</span>
                <select class="inp" bind:value={abbonamento.corsoId}>
                  <option value="" disabled>Scegli il corso</option>
                  {#each corsi as c (c.id)}<option value={c.id}>{c.nome}</option>{/each}
                </select>
              </label>
              <p class="muted small nomargin">Al tornello vale solo nella fascia dei turni di quel corso, come un abbonamento.</p>
            {/if}
          {/if}
          <div class="due">
            <label class="field"><span class="lbl">Dal</span><input class="inp" type="date" bind:value={abbonamento.dal} /></label>
            <label class="field"><span class="lbl">Al {abbonamento.tipo === 'CARNET' ? '(facoltativo)' : ''}</span><input class="inp" type="date" bind:value={abbonamento.al} min={abbonamento.dal} /></label>
          </div>
          <label class="field"><span class="lbl">Importo (€, facoltativo)</span><input class="inp" type="number" min="0" step="0.01" bind:value={abbonamento.importo} /></label>
          <div class="row">
            <button type="button" class="btn btn-secondary grow" onclick={() => (abbonamento = null)}>Annulla</button>
            <button class="btn btn-primary grow" disabled={abbonamento.tipo === 'CORSO' || abbonamento.aOrario ? !abbonamento.corsoId : !numero(abbonamento.ingressi)}>Registra</button>
          </div>
        </form>
      {:else}
        <button class="btn btn-secondary" onclick={nuovoAbbonamento}>Nuovo abbonamento o carnet…</button>
      {/if}

      <div class="sotto">
        <span class="lbl">Turni frequentati</span>
        {#each cliente.turni as t (t.iscrizioneId)}
          <div class="small">{GIORNI_BREVI[t.giornoSettimana]} <span class="m">{ora(t.inizio)}–{ora(t.fine)}</span> · {t.corso}{t.al ? ` (fino al ${dataBreve(t.al)})` : ''}</div>
        {:else}
          <div class="muted small">Nessun turno: le iscrizioni ai turni si fanno dal piano vasca.</div>
        {/each}
      </div>
    </section>

    <!-- Passaggi -->
    <section class="card blocco largo">
      <h3>Ultimi passaggi</h3>
      <ul class="righe">
        {#each accessi.passaggi as x (x.id)}
          <li class="voce-l">
            <span class="punto" class:v={x.verde}></span>
            <span class="m small">{dataBreve(dataDi(x.istante))} {oraDi(x.istante)}</span>
            <span class="grow small">{x.verso === 'USCITA' ? (x.verde ? 'Uscito' : 'Uscita negata: ') : ''}{x.verde ? (x.verso === 'USCITA' ? '' : x.goldenPass ? 'Golden pass' : x.carnetScalato ? 'Entrato · carnet scalato' : 'Entrato') : x.motivi.filter((m) => m.blocca).map((m) => m.messaggio).join(' · ')}{x.segnalato ? ' · segnalato alla segreteria' : ''}</span>
            <span class="m small muted">{x.codice}</span>
          </li>
        {:else}
          <li class="muted small">Nessun passaggio.</li>
        {/each}
      </ul>
    </section>
  </div>
{:else if errore}
  <p class="msg err" role="alert">{errore}</p>
{:else}
  <p class="muted">Caricamento…</p>
{/if}

<style>
  .testa { display: flex; justify-content: space-between; align-items: flex-end; gap: var(--space-12); }
  .nome { margin: 0; font-size: 28px; font-weight: 800; line-height: 1.1; }
  .adesso { display: flex; flex-direction: column; gap: var(--space-6); }
  .griglia { display: grid; grid-template-columns: 1fr; gap: var(--space-14); }
  @media (min-width: 1180px) {
    .griglia { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    .blocco.largo { grid-column: 1 / -1; }
  }
  .blocco { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16); }
  h3 { margin: 0; font-size: var(--text-xl); }
  .righe { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
  .righe > li { padding: var(--space-8) 0; border-top: 1px solid var(--line-soft); }
  .righe > li:first-child { border-top: none; }
  .riga-b { display: flex; flex-direction: column; gap: var(--space-6); }
  .riga-testa { display: flex; align-items: center; gap: var(--space-8); flex-wrap: wrap; }
  .codice { font-size: var(--text-lg); }
  .spento { opacity: 0.6; }
  .bannato { background: var(--ferma-bg); color: var(--ferma-fg); }
  .azioni { display: flex; gap: var(--space-14); flex-wrap: wrap; }
  .azioni .link, .voce-l .link, .ban .link { min-height: 32px; }
  .rosso { color: var(--danger); }
  .aggiungi { display: flex; flex-direction: column; gap: var(--space-6); padding-top: var(--space-6); border-top: 1px solid var(--line); }
  .voce-l { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .ban { display: flex; align-items: center; justify-content: space-between; gap: var(--space-10); }
  .oro { display: flex; align-items: center; justify-content: space-between; gap: var(--space-10); padding: var(--space-8) var(--space-12);
    border-radius: var(--radius-md); background: var(--oro-bg); color: var(--oro-fg); border: 1px solid var(--oro); }
  .a-capo { flex-wrap: wrap; }
  .a-capo .btn { min-width: 180px; }
  .oro-t { background: var(--oro-bg); color: var(--oro-fg); margin-right: var(--space-4); }
  .sosp { display: flex; align-items: center; gap: var(--space-8); color: var(--warn); margin-top: 2px; }
  .sospendi { display: flex; flex-wrap: wrap; gap: var(--space-6); align-items: center; margin-top: var(--space-6); }
  .sospendi .inp { width: auto; min-width: 140px; }
  .modulo { display: flex; flex-direction: column; gap: var(--space-10); }
  select.inp { appearance: auto; }
  .sotto { display: flex; flex-direction: column; gap: var(--space-4); padding-top: var(--space-10); border-top: 1px solid var(--line); }
  .punto { width: 10px; height: 10px; border-radius: 50%; background: var(--ferma); flex-shrink: 0; }
  .punto.v { background: var(--passa); }
</style>
