<script lang="ts">
  import { api, messaggioErrore, type PaginaPromemoria, type RegolaPromemoria, type StatoEmail, type TipoPromemoria } from '../lib/api';
  import { dataBreve, dataDi, iso, oraDi } from '../lib/date';

  /**
   * I promemoria email delle scadenze: quando partono (giorni prima della scadenza, o ogni N giorni per il tesseramento
   * mancante), con quale testo, e il registro delle ultime email.
   */
  const TIPI: { tipo: TipoPromemoria; nome: string; spiega: string }[] = [
    { tipo: 'CERTIFICATO', nome: 'Certificato medico', spiega: 'Prima che scada il certificato.' },
    { tipo: 'ABBONAMENTO', nome: 'Abbonamento', spiega: 'Prima che scada l\'abbonamento (se non è già stato rinnovato).' },
    { tipo: 'TESSERAMENTO_MANCANTE', nome: 'Tesseramento mancante', spiega: 'Chi ha l\'abbonamento valido ma non ha pagato il tesseramento della stagione.' },
    { tipo: 'TESSERAMENTO', nome: 'Tesseramento in scadenza', spiega: 'Prima che scada il tesseramento della stagione.' },
  ];
  const SEGNAPOSTO = ['{nome}', '{cognome}', '{scadenza}', '{giorni}', '{corso}', '{stagione}', '{societa}'];
  const STATI: Record<StatoEmail, { nome: string; classe: string }> = {
    INVIATA: { nome: 'Inviata', classe: 't-soft' },
    NON_INVIATA: { nome: 'Non partita', classe: 't-altro' },
    SENZA_EMAIL: { nome: 'Senza email', classe: 't-warn' },
    ERRORE: { nome: 'Errore', classe: 't-warn' },
  };

  let pagina = $state<PaginaPromemoria | null>(null);
  let bozze = $state<Record<string, RegolaPromemoria>>({});
  let errore = $state('');
  let messaggio = $state('');
  let aperta = $state('');
  let conferma = $state('');

  async function carica() {
    try {
      pagina = await api.promemoria();
      bozze = Object.fromEntries(pagina.regole.map((r) => [r.id, { ...r }]));
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  carica();

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

  const cambiata = (r: RegolaPromemoria) => JSON.stringify(r) !== JSON.stringify(bozze[r.id]);
  const salva = (r: RegolaPromemoria) => {
    const b = bozze[r.id];
    return esegui(() => api.modificaPromemoria(r.id, { giorni: Number(b.giorni), maxInvii: b.maxInvii == null ? null : Number(b.maxInvii), attivo: b.attivo, oggetto: b.oggetto, testo: b.testo }),
      'Promemoria salvato.');
  };

  async function eseguiOra() {
    errore = '';
    messaggio = '';
    try {
      const r = await api.eseguiPromemoria();
      await carica();
      messaggio = r.email === 0 ? 'Oggi non c\'era nessun promemoria da mandare.'
        : `${r.email} promemoria: ${r.inviate} inviati${r.nonInviate ? `, ${r.nonInviate} registrati senza partire (posta non configurata)` : ''}${r.senzaEmail ? `, ${r.senzaEmail} senza email` : ''}${r.errori ? `, ${r.errori} con errore` : ''}.`;
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  /** Come apparirà, con dati di esempio. */
  function anteprima(t: string, r: RegolaPromemoria): string {
    const g = r.tipo === 'TESSERAMENTO_MANCANTE' ? 0 : Number(r.giorni);
    const scad = iso(new Date(Date.now() + g * 86400000));
    return t.replaceAll('{nome}', 'Giulia').replaceAll('{cognome}', 'Rossi').replaceAll('{scadenza}', dataBreve(scad)).replaceAll('{giorni}', String(g))
      .replaceAll('{corso}', 'Delfino Giallo').replaceAll('{stagione}', '2026/27').replaceAll('{societa}', 'Leone XIII Sport');
  }
</script>

<div class="stato card">
  <div class="grow">
    <div class="riga-stato">
      <span class="tag {pagina?.postaConfigurata ? 't-soft' : 't-warn'}">{pagina?.postaConfigurata ? 'Posta configurata' : 'Posta non configurata'}</span>
      <span class="muted small">Promemoria automatici: {pagina?.orario ?? '…'}</span>
    </div>
    {#if pagina && !pagina.postaConfigurata}
      <p class="muted small nomargin">Finché il server di posta non è configurato (sul server, dal tecnico) le email si registrano ma non partono: puoi
        comunque controllare testi e destinatari qui sotto.</p>
    {/if}
  </div>
  <button class="btn btn-secondary" onclick={eseguiOra}>Manda adesso quelli di oggi</button>
</div>

{#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

{#if pagina}
  <div class="tipi">
    {#each TIPI as t (t.tipo)}
      <section class="tipo">
        <div class="testa-t">
          <div><h3>{t.nome}</h3><p class="muted small nomargin">{t.spiega}</p></div>
          <button class="link small" onclick={() => esegui(() => api.creaPromemoria(t.tipo), 'Promemoria aggiunto (spento): scrivi il testo e attivalo.')}>Aggiungi</button>
        </div>
        {#each pagina.regole.filter((r) => r.tipo === t.tipo) as r (r.id)}
          {@const b = bozze[r.id]}
          {#if b}
            <div class="card regola" class:spenta={!b.attivo}>
              <div class="riga">
                <button class="switch" role="switch" aria-checked={b.attivo} aria-label="Attivo" onclick={() => (b.attivo = !b.attivo)}><span></span></button>
                {#if r.tipo === 'TESSERAMENTO_MANCANTE'}
                  <span>Ripeti ogni</span><input class="inp m num" type="number" min="1" max="365" bind:value={b.giorni} aria-label="Ogni quanti giorni" /><span>giorni,</span>
                  <span>al massimo</span><input class="inp m num" type="number" min="1" max="20" bind:value={b.maxInvii} aria-label="Quante volte al massimo" /><span>volte</span>
                {:else}
                  <input class="inp m num" type="number" min="0" max="365" bind:value={b.giorni} aria-label="Giorni prima" /><span>giorni prima della scadenza</span>
                {/if}
                <span class="grow"></span>
                <button class="link small" onclick={() => (aperta = aperta === r.id ? '' : r.id)}>{aperta === r.id ? 'Chiudi il testo' : 'Testo'}</button>
              </div>
              <div class="oggetto small"><strong>{anteprima(b.oggetto, b)}</strong></div>
              {#if aperta === r.id}
                <label class="field"><span class="lbl">Oggetto</span><input class="inp" bind:value={b.oggetto} /></label>
                <label class="field"><span class="lbl">Testo</span><textarea class="inp testo" rows="9" bind:value={b.testo}></textarea></label>
                <p class="muted small nomargin">Si sostituiscono da soli: {SEGNAPOSTO.join(' ')}</p>
                <div class="anteprima"><div class="lbl">Anteprima con dati di esempio</div><pre>{anteprima(b.testo, b)}</pre></div>
                <div class="row">
                  <span class="grow"></span>
                  <button class="link small rosso" onclick={() => conferma === r.id ? esegui(() => api.eliminaPromemoria(r.id), 'Promemoria eliminato.') : (conferma = r.id)}>
                    {conferma === r.id ? 'Conferma: elimina' : 'Elimina'}</button>
                </div>
              {/if}
              {#if cambiata(r)}
                <div class="row"><button class="btn btn-primary" onclick={() => salva(r)}>Salva</button><button class="btn btn-secondary" onclick={carica}>Annulla</button></div>
              {/if}
            </div>
          {/if}
        {:else}
          <p class="muted small nomargin">Nessun promemoria di questo tipo.</p>
        {/each}
      </section>
    {/each}
  </div>

  <section class="registro">
    <h3>Ultime email</h3>
    <ul class="card righe">
      {#each pagina.ultimeEmail as e (e.id)}
        <li>
          <span class="tag {STATI[e.stato].classe}">{STATI[e.stato].nome}</span>
          <span class="grow"><strong>{e.oggetto}</strong> <span class="muted small">· {e.destinatario ?? 'nessun indirizzo'}</span></span>
          <span class="muted small">{dataBreve(dataDi(e.creataIl))} {oraDi(e.creataIl)}</span>
          {#if e.personaId}<a class="link small" href={`#/clienti/${e.personaId}`}>Scheda</a>{/if}
        </li>
      {:else}
        <li class="muted small">Nessuna email finora.</li>
      {/each}
    </ul>
  </section>
{/if}

<style>
  .stato { display: flex; align-items: center; gap: var(--space-14); padding: var(--space-12) var(--space-16); flex-wrap: wrap; }
  .riga-stato { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .tipi { display: grid; grid-template-columns: repeat(auto-fill, minmax(420px, 1fr)); gap: var(--space-18, 18px); }
  .tipo { display: flex; flex-direction: column; gap: var(--space-8); }
  .testa-t { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-10); }
  h3 { margin: 0; font-size: var(--text-xl); }
  .regola { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-12) var(--space-14); }
  .spenta { opacity: 0.7; }
  .riga { display: flex; align-items: center; gap: var(--space-8); flex-wrap: wrap; }
  .num { width: 76px; }
  .testo { min-height: 180px; font-family: var(--font-body); line-height: 1.45; resize: vertical; }
  .anteprima { background: var(--carta); border-radius: var(--radius-md); padding: var(--space-10) var(--space-12); }
  .anteprima pre { margin: var(--space-6) 0 0; white-space: pre-wrap; font-family: var(--font-body); font-size: var(--text-md); }
  .rosso { color: var(--danger); }
  .registro { display: flex; flex-direction: column; gap: var(--space-8); }
  .righe { list-style: none; margin: 0; padding: 0; overflow: hidden; }
  .righe li { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-8) var(--space-14); border-top: 1px solid var(--line-soft); flex-wrap: wrap; }
  .righe li:first-child { border-top: none; }
  @media (max-width: 700px) { .tipi { grid-template-columns: 1fr; } }
</style>
