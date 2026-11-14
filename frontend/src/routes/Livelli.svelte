<script lang="ts">
  import { api, messaggioErrore, type DettaglioLivelli, type EsitoLivello, type PaginaLivelli, type BambinoLivelli } from '../lib/api';
  import { conteggi, corsiDi, prossimoDaCompletare, quandoBreve, statoTurno, tuttiPassano } from '../lib/livelli';
  import { GIORNI_ESTESI } from '../lib/date';
  import { leggiPreferenza, salvaPreferenza } from '../lib/preferenze';
  import Icona from '../components/Icona.svelte';

  /**
   * Esiti di fine stagione e passaggi di livello (ADR 0026, mockup A3). L'istruttore vede i suoi turni; chi ha il permesso
   * «Livelli» sceglie anche un corso qualsiasi. Ogni scelta si salva subito.
   */
  let pagina = $state<PaginaLivelli | null>(null);
  let dettaglio = $state<DettaglioLivelli | null>(null);
  let aperto = $state<string | null>(null);
  let errore = $state('');
  let messaggio = $state('');
  let lavoro = $state(false);
  let vista = $state<'fine' | 'anno'>(leggiPreferenza<'fine' | 'anno'>('livelli.vista', 'fine', ['fine', 'anno']));
  $effect(() => salvaPreferenza('livelli.vista', vista));
  /** «miei» o l'id di un corso (solo per chi vede tutti) */
  let filtro = $state('miei');

  const miei = $derived(pagina?.turni.filter((t) => t.mio) ?? []);
  const corsi = $derived(pagina ? corsiDi(pagina.turni) : []);
  const elenco = $derived(!pagina ? [] : filtro === 'miei' ? miei : pagina.turni.filter((t) => t.corsoId === filtro));
  const turno = $derived(dettaglio?.turno ?? null);
  const conti = $derived(dettaglio ? conteggi(dettaglio.bambini) : { passano: 0, continuano: 0, mancano: 0 });
  const prossimo = $derived(prossimoDaCompletare(elenco, aperto));
  const completi = $derived(elenco.filter((t) => statoTurno(t).completo).length);

  async function carica() {
    try {
      pagina = await api.livelli();
      if (!miei.length && pagina.tutti) filtro = corsi[0]?.id ?? 'miei';
      const primo = elenco.find((t) => !statoTurno(t).completo && t.iscritti > 0) ?? elenco[0];
      if (primo) await apri(primo.lezioneId);
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a caricare i corsi.');
    }
  }
  carica();

  async function apri(id: string) {
    aperto = id;
    errore = '';
    messaggio = '';
    try {
      dettaglio = await api.turnoLivelli(id);
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  function scegliFiltro(f: string) {
    filtro = f;
    const primo = elenco[0];
    if (primo) apri(primo.lezioneId);
    else {
      aperto = null;
      dettaglio = null;
    }
  }

  /** Dopo ogni modifica il dettaglio torna dal server; i numeri della scheda del turno si aggiornano qui. */
  function aggiorna(d: DettaglioLivelli) {
    dettaglio = d;
    if (pagina) pagina.turni = pagina.turni.map((t) => (t.lezioneId === d.turno.lezioneId ? d.turno : t));
  }

  async function esegui(azione: () => Promise<DettaglioLivelli>, fatto = '') {
    errore = '';
    messaggio = '';
    lavoro = true;
    try {
      aggiorna(await azione());
      messaggio = fatto;
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      lavoro = false;
    }
  }

  const esito = (b: BambinoLivelli, e: EsitoLivello) =>
    esegui(() => api.esitiLivello(aperto!, [{ personaId: b.personaId, esito: b.esito === e ? null : e }]));
  const tutti = () => esegui(() => api.esitiLivello(aperto!, tuttiPassano(dettaglio!.bambini)));
  const passa = (b: BambinoLivelli) =>
    esegui(() => api.passaDiLivello(b.iscrizioneId), `${b.nome} è in lista d'attesa in ${turno?.successivo}, davanti a chi aspetta: intanto resta nel suo turno.`);
  const annulla = (b: BambinoLivelli) =>
    esegui(() => api.annullaPassaggio(b.passaggio!.id), `${b.nome} resta in ${turno?.corso}: tolto dalle liste d'attesa di ${turno?.successivo}.`);

  const iniziali = (b: BambinoLivelli) => `${b.nome.charAt(0)}${b.cognome.charAt(0)}`.toUpperCase();
  const colore = (c: string | null) => c ?? 'var(--line-strong)';
</script>

{#if pagina}
  <div class="testa">
    <p class="muted small">Stagione {pagina.stagione}</p>
    {#if pagina.tutti}
      <label class="scelta">
        <span class="lbl">Turni</span>
        <select class="inp" value={filtro} onchange={(e) => scegliFiltro(e.currentTarget.value)}>
          {#if miei.length}<option value="miei">I miei turni</option>{/if}
          {#each corsi as c (c.id)}<option value={c.id}>{c.nome}</option>{/each}
        </select>
      </label>
    {/if}
  </div>

  {#if !elenco.length}
    <p class="muted">{pagina.tutti ? 'Nessun turno: scegli un corso.' : 'Non tieni turni di corsi con un livello successivo.'}</p>
  {:else}
    <div class="miei" role="tablist" aria-label="Turni">
      {#each elenco as t (t.lezioneId)}
        {@const s = statoTurno(t)}
        <button type="button" role="tab" class="mio" aria-selected={t.lezioneId === aperto} style="--c: {colore(t.colore)}" onclick={() => apri(t.lezioneId)}>
          <span class="r1"><i></i><b>{t.corso}</b></span>
          <span class="r2">{quandoBreve(t)} · {t.iscritti} {t.iscritti === 1 ? 'iscritto' : 'iscritti'}</span>
          <span class="r3"><span class="mini-barra"><em style="width: {s.perc}%"></em></span><span class={s.completo ? 'st-ok' : 'st-da'}>{s.testo}</span></span>
        </button>
      {/each}
    </div>

    <div class="chips" role="group" aria-label="Quando">
      <button type="button" class="chip" aria-pressed={vista === 'fine'} onclick={() => (vista = 'fine')}>Fine stagione</button>
      <button type="button" class="chip" aria-pressed={vista === 'anno'} onclick={() => (vista = 'anno')}>Durante l'anno</button>
    </div>

    {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
    {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

    {#if turno && dettaglio}
      <div class="cols">
        <div class="sx">
          <div class="card livello">
            <i style="background: {colore(turno.colore)}"></i><b>{turno.corso}</b>
            <span class="muted quando">{GIORNI_ESTESI[turno.giornoSettimana]} alle {turno.inizio.slice(0, 5)}</span>
            <span class="freccia" aria-hidden="true">→</span>
            <i style="background: {colore(turno.coloreSuccessivo)}"></i><b>{turno.successivo}</b>
            <span class="grow"></span>
            {#if vista === 'fine' && conti.mancano > 0}
              <button type="button" class="btn btn-secondary btn-sm" disabled={lavoro} onclick={tutti}>Tutti passano</button>
            {/if}
          </div>
          {#if !dettaglio.bambini.length}
            <p class="card vuoto muted">Nessun iscritto in questo turno.</p>
          {:else}
            <ul class="card bimbi">
              {#each dettaglio.bambini as b (b.iscrizioneId)}
                <li class:manca={vista === 'fine' && !b.esito}>
                  <span class="av" aria-hidden="true">{iniziali(b)}</span>
                  <span class="chi"><b>{b.nome} {b.cognome}</b><small>{b.eta != null ? `${b.eta} anni` : ''}</small></span>
                  {#if vista === 'fine'}
                    <span class="seg esito" role="group" aria-label="Esito di {b.nome} {b.cognome}">
                      <button type="button" class="passa" aria-pressed={b.esito === 'PASSA'} disabled={lavoro} onclick={() => esito(b, 'PASSA')}><span class="lungo">Passa a {turno.successivo}</span><span class="breve">Passa</span></button>
                      <button type="button" aria-pressed={b.esito === 'CONTINUA'} disabled={lavoro} onclick={() => esito(b, 'CONTINUA')}><span class="lungo">Continua in {turno.corso}</span><span class="breve">Continua</span></button>
                    </span>
                  {:else if b.passaggio}
                    <span class="fatto">In attesa di un posto in {turno.successivo}{b.passaggio.posizione ? ` · ${b.passaggio.posizione}°` : ''}</span>
                    <button type="button" class="btn btn-quieto btn-sm" disabled={lavoro} onclick={() => annulla(b)}>Annulla</button>
                  {:else}
                    <button type="button" class="btn btn-secondary btn-sm" disabled={lavoro} onclick={() => passa(b)}><Icona nome="su" />Passa adesso a {turno.successivo}</button>
                  {/if}
                </li>
              {/each}
            </ul>
          {/if}
        </div>

        <div class="dx">
          <section class="card pann">
            <span class="lbl">Il turno</span>
            {#if vista === 'fine'}
              <div class="barra-e" role="img" aria-label="{conti.passano} su {dettaglio.bambini.length} passano">
                <i style="width: {dettaglio.bambini.length ? Math.round((conti.passano / dettaglio.bambini.length) * 100) : 0}%"></i>
              </div>
              <div class="leg">
                <span><i class="p"></i><b>{conti.passano}</b> passano a {turno.successivo}</span>
                <span><i></i><b>{conti.continuano}</b> continuano in {turno.corso}</span>
                {#if conti.mancano}<span><i class="m"></i><b>{conti.mancano}</b> senza esito</span>{/if}
              </div>
              {#if !conti.mancano && dettaglio.bambini.length}<span class="muted small">Tutti i bambini di questo turno hanno un esito.</span>{/if}
            {:else}
              {@const inPassaggio = dettaglio.bambini.filter((b) => b.passaggio).length}
              <div class="leg">
                <span><i class="p"></i><b>{inPassaggio}</b> in attesa di un posto in {turno.successivo}</span>
              </div>
            {/if}
          </section>
          {#if vista === 'fine'}
            <section class="card pann">
              <span class="lbl">{filtro === 'miei' ? 'I tuoi turni' : 'I turni del corso'}</span>
              <p class="completi"><b>{completi} su {elenco.length}</b> completi</p>
              {#if prossimo}
                {@const p = prossimo}
                <button type="button" class="btn btn-secondary btn-sm prossimo" onclick={() => apri(p.lezioneId)}>Vai al prossimo: {p.corsoId === turno.corsoId ? '' : `${p.corso} · `}{quandoBreve(p)}</button>
              {/if}
            </section>
          {/if}
        </div>
      </div>
    {/if}
  {/if}
{:else if errore}
  <p class="msg err" role="alert">{errore}</p>
{:else}
  <p class="muted">Caricamento…</p>
{/if}

<style>
  .testa { display: flex; flex-wrap: wrap; gap: var(--space-12); align-items: flex-end; justify-content: space-between; }
  .testa p { margin: 0; }
  .scelta { display: flex; flex-direction: column; gap: var(--space-6); min-width: 240px; }
  select.inp { appearance: auto; }

  .miei { display: flex; flex-wrap: wrap; gap: var(--space-10); }
  .mio { flex: 1 1 210px; max-width: 300px; display: flex; flex-direction: column; gap: var(--space-6); padding: 12px 14px; border: 1px solid var(--line);
    border-radius: var(--radius-md); background: var(--surface); text-align: left; cursor: pointer; color: var(--ink); font: inherit; }
  .mio:hover { border-color: var(--c); }
  .mio[aria-selected='true'] { border: 2px solid var(--c); padding: 11px 13px; background: color-mix(in srgb, var(--c) 10%, var(--surface)); }
  .r1 { display: flex; align-items: center; gap: var(--space-8); font-size: var(--text-md); }
  .r1 i { width: 12px; height: 12px; border-radius: 4px; background: var(--c); flex-shrink: 0; }
  .r2 { font-size: var(--text-sm); color: var(--muted); }
  .r3 { display: flex; align-items: center; gap: var(--space-8); }
  .mini-barra { flex: 1; height: 6px; border-radius: 999px; background: var(--surface-sunken); overflow: hidden; }
  .mini-barra em { display: block; height: 100%; background: var(--ink); border-radius: 999px; }
  .st-ok, .st-da { font-size: var(--text-xs); font-weight: 700; white-space: nowrap; }
  .st-ok { color: var(--passa); }
  .st-da { color: var(--warn); }

  .cols { display: flex; flex-wrap: wrap; gap: 18px; align-items: flex-start; }
  .sx { flex: 999 1 560px; min-width: 0; display: flex; flex-direction: column; gap: var(--space-12); }
  .dx { flex: 1 1 300px; max-width: 380px; display: flex; flex-direction: column; gap: var(--space-14); }
  .livello { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-10); padding: 12px 16px; font-size: var(--text-md); }
  .livello i { width: 14px; height: 14px; border-radius: 4px; flex-shrink: 0; }
  .freccia { color: var(--muted); }
  .vuoto { margin: 0; padding: var(--space-16); }

  .bimbi { list-style: none; margin: 0; padding: 0; }
  .bimbi li { display: flex; align-items: center; gap: var(--space-14); flex-wrap: wrap; padding: 12px 16px; border-top: 1px solid var(--line); }
  .bimbi li:first-child { border-top: none; }
  .bimbi li.manca { background: var(--warn-bg); box-shadow: inset 3px 0 0 var(--warn); }
  .av { width: 36px; height: 36px; border-radius: 50%; display: grid; place-items: center; background: var(--cloro-soft); color: var(--cloro-ink);
    font-size: var(--text-xs); font-weight: 700; flex-shrink: 0; }
  .chi { flex: 1 1 160px; min-width: 0; display: flex; flex-direction: column; }
  .chi small { font-size: var(--text-xs); color: var(--muted); }
  .esito { grid-template-columns: repeat(2, auto); }
  .esito button { padding: 0 var(--space-12); white-space: nowrap; }
  .esito button.passa[aria-pressed='true'] { background: var(--passa-bg); color: var(--passa-fg); }
  .fatto { display: inline-flex; align-items: center; height: 30px; padding: 0 var(--space-10); border-radius: 999px; background: var(--passa-bg); color: var(--passa-fg);
    font-size: var(--text-xs); font-weight: 700; white-space: nowrap; }

  .barra-e { height: 12px; border-radius: 999px; background: var(--surface-sunken); overflow: hidden; }
  .barra-e i { display: block; height: 100%; border-radius: 999px; background: var(--passa); transition: width 0.2s; }
  .leg { display: flex; flex-direction: column; gap: var(--space-6); font-size: var(--text-md); }
  .leg span { display: flex; align-items: center; gap: var(--space-8); }
  .leg b { font-family: var(--font-display); font-size: 20px; font-weight: 800; min-width: 18px; font-variant-numeric: tabular-nums; }
  .leg i { width: 12px; height: 12px; border-radius: 3px; background: var(--surface-sunken); border: 1px solid var(--line); box-sizing: border-box; }
  .leg i.p { background: var(--passa); border-color: transparent; }
  .leg i.m { background: transparent; border: 2px dashed var(--warn); }
  .completi { margin: 0; font-size: var(--text-md); }
  .prossimo { align-self: flex-start; max-width: 100%; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .breve { display: none; }
  @media (max-width: 700px) {
    /* sul telefono i turni scorrono in orizzontale, così la lista dei bambini resta vicina */
    .miei { flex-wrap: nowrap; overflow-x: auto; scroll-snap-type: x mandatory; scroll-padding-inline: var(--space-16); margin: 0 calc(-1 * var(--space-16)); padding: 2px var(--space-16) var(--space-4); }
    .mio { flex: 0 0 200px; max-width: none; scroll-snap-align: start; }
    .esito { width: 100%; grid-template-columns: 1fr 1fr; }
    .lungo { display: none; }
    .breve { display: inline; }
    .quando { display: none; }
    .dx { max-width: none; }
  }
</style>
