<script lang="ts">
  import Azione from '../components/Azione.svelte';
  import Icona from '../components/Icona.svelte';
  import { api, messaggioErrore, type Richiesta, type Turno } from '../lib/api';
  import { ora } from '../lib/date';
  import { leggiPreferenza, salvaPreferenza } from '../lib/preferenze';
  import { aperta, cosaCambia, esito, giornoCorto, NOMI_TIPO, ordinaDaCoprire, passi, VISTE_RICHIESTE, type VistaRichieste } from '../lib/richieste';
  import { sessione } from '../lib/sessione.svelte';
  import { areaDi } from '../lib/turni';

  /**
   * Le richieste (ADR 0022) in due viste a scelta: tutto in una pagina, o a schede (Per te, Da coprire, Le mie, Storico).
   * «Per te» sono le richieste rivolte proprio a te; i turni da coprire che si sovrappongono a un tuo turno stanno in fondo,
   * in piccolo; le richieste concluse si riducono a tipo, giorno e cosa è cambiato.
   */
  let mie = $state<Richiesta[]>([]);
  let aperte = $state<Richiesta[]>([]);
  let mieiTurni = $state<Turno[]>([]);
  let caricamento = $state(true);
  let errore = $state('');
  let messaggio = $state('');
  let lavoro = $state<string | null>(null);
  let vista = $state<VistaRichieste>(leggiPreferenza<VistaRichieste>('richieste.vista', 'pagina', ['pagina', 'schede']));
  let scegliVista = $state(false);
  let scheda = $state<'perte' | 'coprire' | 'mie' | 'storico'>('perte');
  let aperto = $state<string | null>(null);
  let tutteConcluse = $state(false);

  const io = $derived(sessione.utente?.personaId);

  async function carica() {
    caricamento = true;
    try {
      [mie, aperte] = await Promise.all([api.richiesteMie(), api.richiesteAperte()]);
      const date = aperte.map((r) => r.turno.data).sort();
      mieiTurni = date.length ? await api.turniMiei(date[0], date.at(-1)!).catch(() => []) : [];
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a caricare le richieste.');
    } finally {
      caricamento = false;
    }
  }
  carica();

  async function azione(id: string, fn: () => Promise<unknown>, ok: string) {
    errore = messaggio = '';
    lavoro = id;
    try {
      await fn();
      messaggio = ok;
      await carica();
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      lavoro = null;
    }
  }

  /** Il mio turno che si accavalla con quello da coprire. */
  const mioInTurno = (r: Richiesta) =>
    mieiTurni.find((t) => t.stato === 'PIANIFICATO' && t.data === r.turno.data && t.inizio < r.turno.fine && t.fine > r.turno.inizio) ?? null;

  const perTe = $derived(mie.filter((r) => r.stato === 'IN_ATTESA_COLLEGA' && r.collega?.id === io));
  const daCoprire = $derived(ordinaDaCoprire(aperte.filter((r) => r.richiedente.id !== io), (r) => !!mioInTurno(r)));
  const mieAperte = $derived(mie.filter((r) => r.richiedente.id === io && aperta(r)));
  const concluse = $derived(mie.filter((r) => !aperta(r) && !(r.stato === 'IN_ATTESA_COLLEGA' && r.collega?.id === io))
    .sort((a, b) => (b.decisaIl ?? b.creataIl).localeCompare(a.decisaIl ?? a.creataIl)));
  const recenti = $derived(concluse.filter((r) => r.richiedente.id === io).slice(0, 3));

  function cambiaVista(v: VistaRichieste) {
    vista = v;
    salvaPreferenza('richieste.vista', v);
  }

  const iniziali = (n: string) => n.split(' ').map((x) => x[0]).join('').slice(0, 2);
  const cosaChiede = (r: Richiesta) => (r.tipo === 'SCAMBIO' ? 'ti propone uno scambio' : r.propostaDa ? `ti propone di sostituire ${r.richiedente.nome}`
    : r.collega?.id === io ? 'ti chiede di sostituirlo' : 'chiede una sostituzione');
  // chi «parla» nella scheda: il coordinatore, se la sostituzione l'ha proposta lui
  const chiChiede = (r: Richiesta) => r.propostaDa ?? r.richiedente;
  const ICONA = { SOSTITUZIONE: 'persona', SCAMBIO: 'scambio', CAMBIO_ORARIO: 'ore' } as const;
</script>

{#snippet miniTurno(t: Turno, di: string)}
  <span class="mini ar-{areaDi(t.postazione)}">{#if di}<span class="lbl di">{di}</span>{/if}<span class="segno-area"></span><b class="m">{giornoCorto(t.data)} · {ora(t.inizio)}–{ora(t.fine)}</b><span class="muted nome">{t.postazione}</span></span>
{/snippet}

{#snippet passiDi(r: Richiesta)}
  <span class="passi">
    {#each passi(r) as p, i (i)}{#if i}<b></b>{/if}<span class={p.stato}><i></i> {p.nome}</span>{/each}
  </span>
{/snippet}

{#snippet pulsantiPerTe(r: Richiesta)}
  <div class="bottoni">
    <button class="btn btn-secondary" disabled={lavoro === r.id} onclick={() => azione(r.id, () => api.rifiuta(r.id),
      r.tipo === 'SOSTITUZIONE' ? 'Hai risposto che non puoi: la richiesta torna aperta agli altri.' : 'Hai risposto che non puoi.')}>Non posso</button>
    <button class="btn btn-primary" disabled={lavoro === r.id} onclick={() => azione(r.id, () => api.accetta(r.id),
      r.propostaDa ? 'Accettata: il turno ora è tuo.' : 'Accettata: ora decide il coordinatore.')}>Accetto</button>
  </div>
{/snippet}

{#snippet schedaPerTe(r: Richiesta)}
  <article class="card scheda">
    <span class="riga"><span class="av">{iniziali(chiChiede(r).nome)}</span><span class="grow"><b>{chiChiede(r).nome}</b>{r.propostaDa ? ' (coordinatore)' : ''} {cosaChiede(r)}</span><span class="tipo">{NOMI_TIPO[r.tipo]}</span></span>
    {#if r.tipo === 'SCAMBIO' && r.turnoCollega}
      {@render miniTurno(r.turno, 'il suo')}{@render miniTurno(r.turnoCollega, 'il tuo')}
    {:else}
      {@render miniTurno(r.turno, '')}
    {/if}
    {#if r.messaggio}<span class="muted citazione">«{r.messaggio}»</span>{/if}
    {@render pulsantiPerTe(r)}
  </article>
{/snippet}

{#snippet schedaDaCoprire(r: Richiesta)}
  <article class="card scheda ar-{areaDi(r.turno.postazione)}">
    <span class="riga"><b class="m">{giornoCorto(r.turno.data)} · {ora(r.turno.inizio)}–{ora(r.turno.fine)}</b><span class="grow"></span><span class="m muted small">{Number(r.turno.ore).toLocaleString('it-IT')} h</span></span>
    <span class="riga small"><span class="segno-area"></span>{r.turno.postazione}</span>
    <span class="muted small">Lo chiede {r.richiedente.nome}{r.messaggio ? ` · «${r.messaggio}»` : ''}</span>
    <span class="riga"><span class="grow ok small">Sei libero in quell'orario</span>
      <button class="btn btn-accent btn-sm" disabled={lavoro === r.id} onclick={() => azione(r.id, () => api.accetta(r.id), 'Grazie! Ora decide il coordinatore.')}>Lo copro io</button></span>
  </article>
{/snippet}

{#snippet rigaOccupato(r: Richiesta)}
  <div class="occupato ar-{areaDi(r.turno.postazione)}">
    <span class="segno-area"></span><span class="m nowrap">{giornoCorto(r.turno.data)} {ora(r.turno.inizio)}–{ora(r.turno.fine)}</span>
    <span class="grow nome">· {r.richiedente.nome}</span><span class="nowrap">sei in turno</span>
  </div>
{/snippet}

{#snippet schedaMia(r: Richiesta)}
  {@const e = esito(r, io)}
  <article class="card scheda">
    <span class="riga"><span class="tipo">{NOMI_TIPO[r.tipo]}</span><span class="grow"></span><span class="stato {e.tono}">{e.testo}</span></span>
    {@render miniTurno(r.turno, '')}
    {@render passiDi(r)}
    {#if r.messaggio}<span class="muted small">«{r.messaggio}»</span>{/if}
    <button class="btn btn-quieto btn-sm sx danger" disabled={lavoro === r.id} onclick={() => azione(r.id, () => api.annullaRichiesta(r.id), 'Richiesta annullata.')}>Annulla la richiesta</button>
  </article>
{/snippet}

{#snippet conclusa(r: Richiesta)}
  {@const e = esito(r, io)}
  <div class="conclusa ar-{areaDi(r.turno.postazione)}">
    <span class="riga"><Icona nome={e.tono === 'ok' ? 'check' : 'chiudi'} /><b class="grow">{NOMI_TIPO[r.tipo]}</b><span class="stato {e.tono}">{e.testo}</span></span>
    <span class="riga dopo"><span class="segno-area"></span><span class="nome">{cosaCambia(r, io)}</span></span>
    {#if r.notaDecisione}<span class="muted small dopo">Nota: {r.notaDecisione}</span>{/if}
  </div>
{/snippet}

<section class="page">
  {#snippet ingranaggio()}
    <Azione icona="impostazioni" etichetta="Scegli come mostrare le richieste" premuto={scegliVista} onclick={() => (scegliVista = !scegliVista)} />
  {/snippet}
  {#if vista === 'schede'}<div class="testa">
      <div class="seg schede" role="group" aria-label="Quali richieste">
        <button type="button" aria-pressed={scheda === 'perte'} onclick={() => (scheda = 'perte')}>Per te{#if perTe.length}<span class="n">{perTe.length}</span>{/if}</button>
        <button type="button" aria-pressed={scheda === 'coprire'} onclick={() => (scheda = 'coprire')}>Da coprire{#if daCoprire.liberi.length}<span class="n">{daCoprire.liberi.length}</span>{/if}</button>
        <button type="button" aria-pressed={scheda === 'mie'} onclick={() => (scheda = 'mie')}>Le mie</button>
        <button type="button" aria-pressed={scheda === 'storico'} onclick={() => (scheda = 'storico')}>Storico</button>
      </div>
    <span class="grow"></span>
    {@render ingranaggio()}
  </div>{/if}

  {#if scegliVista}
    <section class="card viste" aria-label="Come mostrare le richieste">
      <span class="lbl">Come mostrare le richieste</span>
      <div class="chips">{#each VISTE_RICHIESTE as v (v.id)}<button type="button" class="chip" aria-pressed={vista === v.id} onclick={() => cambiaVista(v.id)}>{v.nome}</button>{/each}</div>
      <p class="muted small nomargin">La scelta resta per il tuo account su questo dispositivo.</p>
    </section>
  {/if}
  {#if messaggio}<p class="msg ok nomargin" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  {#if vista === 'pagina'}
    <section class="sezione">
      <div class="testa"><h2 class="lbl warn grow">Per te{perTe.length ? ` · ${perTe.length}` : ''}</h2>{@render ingranaggio()}</div>
      {#each perTe as r (r.id)}{@render schedaPerTe(r)}{:else}<p class="muted small nomargin">{caricamento ? '…' : 'Nessun collega ti ha chiesto niente.'}</p>{/each}
    </section>

    <section class="sezione">
      <h2 class="lbl">Turni da coprire{daCoprire.liberi.length ? ` · ${daCoprire.liberi.length}` : ''}</h2>
      {#each daCoprire.liberi as r (r.id)}{@render schedaDaCoprire(r)}{/each}
      {#each daCoprire.occupati as r (r.id)}{@render rigaOccupato(r)}{/each}
      {#if !daCoprire.liberi.length && !daCoprire.occupati.length}<p class="muted small nomargin">{caricamento ? '…' : 'Nessun collega cerca un sostituto.'}</p>{/if}
    </section>

    <section class="sezione">
      <h2 class="lbl">Le tue richieste</h2>
      {#each mieAperte as r (r.id)}{@render schedaMia(r)}{/each}
      {#each tutteConcluse ? concluse.filter((r) => r.richiedente.id === io) : recenti as r (r.id)}{@render conclusa(r)}{/each}
      {#if !mieAperte.length && !recenti.length}<p class="muted small nomargin">{caricamento ? '…' : 'Nessuna richiesta. Per farne una, tocca un tuo turno.'}</p>{/if}
      {#if concluse.filter((r) => r.richiedente.id === io).length > recenti.length}
        <button type="button" class="altre" onclick={() => (tutteConcluse = !tutteConcluse)}>
          <span class="grow">{tutteConcluse ? 'Mostra meno' : `Tutte le richieste concluse (${concluse.filter((r) => r.richiedente.id === io).length})`}</span><Icona nome={tutteConcluse ? 'su' : 'giu'} />
        </button>
      {/if}
    </section>
  {:else}
    <p class="muted small nomargin spiega">
      {scheda === 'perte' ? 'Colleghi che chiedono proprio a te: rispondi, poi decide il coordinatore.'
        : scheda === 'coprire' ? 'Richieste aperte a tutti. Prima quelle che puoi prendere; in fondo, in grigio, quelle che si sovrappongono a un tuo turno.'
        : scheda === 'mie' ? 'Quelle che hai chiesto tu, con il punto in cui si trovano.' : 'Le richieste concluse in cui sei coinvolto.'}
    </p>
    {@const righe = scheda === 'perte' ? perTe : scheda === 'coprire' ? [...daCoprire.liberi, ...daCoprire.occupati] : scheda === 'mie' ? mieAperte : concluse}
    <div class="card elenco">
      {#each righe as r (r.id)}
        {@const occ = scheda === 'coprire' && !!mioInTurno(r)}
        {@const e = esito(r, io)}
        <div class="voce" class:spento={occ}>
          <button type="button" class="riga-r" aria-expanded={aperto === r.id} onclick={() => (aperto = aperto === r.id ? null : r.id)}>
            <span class="ico" class:warn-i={scheda === 'perte' || (scheda === 'mie' && e.tono === 'warn')} class:cloro-i={scheda === 'coprire' && !occ}><Icona nome={scheda === 'coprire' ? 'piu' : ICONA[r.tipo]} /></span>
            <span class="grow testo">
              <b>{scheda === 'perte' ? `${chiChiede(r).nome.split(' ')[0]} ${cosaChiede(r)}` : scheda === 'coprire' ? `${giornoCorto(r.turno.data)} · ${ora(r.turno.inizio)}–${ora(r.turno.fine)}` : NOMI_TIPO[r.tipo]}</b>
              <span class="muted small">{scheda === 'coprire' ? `${r.turno.postazione} · chiede ${r.richiedente.nome}` : cosaCambia(r, io)}</span>
            </span>
            <span class="stato {occ ? 'muted' : e.tono}">{occ ? 'sei in turno' : scheda === 'coprire' ? 'sei libero' : e.testo}</span>
          </button>
          {#if aperto === r.id}
            <div class="dett">
              {@render miniTurno(r.turno, r.tipo === 'SCAMBIO' ? (r.richiedente.id === io ? 'il tuo' : 'il suo') : '')}
              {#if r.tipo === 'SCAMBIO' && r.turnoCollega}{@render miniTurno(r.turnoCollega, r.richiedente.id === io ? 'il suo' : 'il tuo')}{/if}
              {#if r.messaggio}<span class="muted small">«{r.messaggio}»</span>{/if}
              {#if r.notaDecisione}<span class="muted small">Nota: {r.notaDecisione}</span>{/if}
              {#if scheda === 'mie' || (scheda === 'storico' && r.richiedente.id === io)}{@render passiDi(r)}{/if}
              {#if scheda === 'perte'}{@render pulsantiPerTe(r)}
              {:else if scheda === 'coprire'}
                {#if occ}<span class="muted small">Quell'orario si sovrappone al tuo turno delle {ora(mioInTurno(r)!.inizio)}.</span>
                {:else}<button class="btn btn-accent btn-sm sx" disabled={lavoro === r.id} onclick={() => azione(r.id, () => api.accetta(r.id), 'Grazie! Ora decide il coordinatore.')}>Lo copro io</button>{/if}
              {:else if scheda === 'mie'}
                <button class="btn btn-quieto btn-sm sx danger" disabled={lavoro === r.id} onclick={() => azione(r.id, () => api.annullaRichiesta(r.id), 'Richiesta annullata.')}>Annulla la richiesta</button>
              {/if}
            </div>
          {/if}
        </div>
      {:else}
        <p class="muted small vuoto">{caricamento ? '…' : scheda === 'perte' ? 'Nessun collega ti ha chiesto niente.' : scheda === 'coprire' ? 'Nessun collega cerca un sostituto.' : scheda === 'mie' ? 'Nessuna richiesta aperta. Per farne una, tocca un tuo turno.' : 'Ancora niente.'}</p>
      {/each}
    </div>
    {#if scheda === 'mie'}<a class="btn btn-secondary nuova" href="#/turni"><Icona nome="piu" />Nuova richiesta su un mio turno</a>{/if}
  {/if}
</section>

<style>
  .testa { display: flex; align-items: center; gap: var(--space-8); }
  .schede { grid-template-columns: repeat(4, auto); }
  .schede button { display: inline-flex; align-items: center; justify-content: center; white-space: nowrap; padding: 0 var(--space-8); }
  .schede .n { display: inline-grid; place-items: center; min-width: 17px; height: 17px; padding: 0 4px; margin-left: 4px; border-radius: 9px; box-sizing: border-box;
    background: var(--warn); color: #fff; font-size: 11px; font-weight: 700; }
  .viste { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-12) var(--space-14); }
  .sezione { display: flex; flex-direction: column; gap: var(--space-8); }
  .sezione h2 { margin: 0; }
  .warn { color: var(--warn); }
  .scheda { padding: var(--space-14); display: flex; flex-direction: column; gap: var(--space-10); }
  .riga { display: flex; align-items: center; gap: var(--space-8); min-width: 0; }
  .riga :global(.ic) { width: 16px; height: 16px; }
  .av { width: 32px; height: 32px; border-radius: 50%; display: grid; place-items: center; background: var(--cloro-soft); color: var(--cloro-ink); font-size: 12px; font-weight: 700; flex-shrink: 0; }
  .tipo { display: inline-flex; align-items: center; height: 22px; padding: 0 9px; border-radius: var(--radius-pill); font-size: 11px; font-weight: 700; background: var(--surface-sunken); color: var(--seg-fg); white-space: nowrap; }
  .mini { display: flex; align-items: center; gap: var(--space-8); padding: var(--space-8) var(--space-10); border-radius: 10px; background: var(--carta); font-size: var(--text-sm); min-width: 0; }
  .mini .di { min-width: 34px; }
  .mini b { font-weight: 600; white-space: nowrap; }
  .nome { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0; }
  .nowrap { white-space: nowrap; }
  .citazione { font-size: var(--text-sm); font-style: italic; }
  .bottoni { display: flex; gap: var(--space-8); }
  .bottoni .btn { flex: 1; }
  .ok { color: var(--passa); font-weight: 700; }
  .occupato { display: flex; align-items: center; gap: var(--space-8); padding: var(--space-8) var(--space-12); border-radius: var(--radius-md); border: 1px dashed var(--line);
    font-size: var(--text-sm); color: var(--muted); }
  .occupato .segno-area { opacity: 0.6; }
  .stato { font-size: var(--text-xs); font-weight: 700; white-space: nowrap; }
  .stato.warn { color: var(--warn); } .stato.ok { color: var(--passa); } .stato.ferma { color: var(--ferma); } .stato.muted { color: var(--muted); }
  .passi { display: flex; align-items: center; gap: 6px; font-size: var(--text-xs); font-weight: 600; color: var(--muted); flex-wrap: wrap; }
  .passi i { width: 8px; height: 8px; border-radius: 50%; background: var(--line-strong); display: inline-block; }
  .passi .fatto i { background: var(--cloro); }
  .passi .ora { color: var(--ink); }
  .passi .ora i { background: var(--warn); box-shadow: 0 0 0 3px var(--warn-bg); }
  .passi .no i { background: var(--ferma); }
  .passi > b { flex: 0 0 14px; height: 1px; background: var(--line-strong); }
  .sx { align-self: flex-start; }
  .danger { color: var(--danger); }
  .conclusa { display: flex; flex-direction: column; gap: 4px; padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); background: var(--surface); border: 1px solid var(--line-soft); }
  .conclusa b { font-weight: 600; font-size: var(--text-md); }
  .conclusa .dopo { padding-left: 24px; font-size: var(--text-sm); }
  .altre { display: flex; align-items: center; gap: var(--space-8); padding: var(--space-10) var(--space-4); border: none; background: none; font: inherit; color: var(--muted); font-size: var(--text-md); cursor: pointer; }
  .spiega { padding: 0 2px; }
  .elenco { padding: 0; overflow: hidden; }
  .voce + .voce { border-top: 1px solid var(--line-soft); }
  .voce.spento .riga-r { opacity: 0.6; }
  .riga-r { width: 100%; display: flex; align-items: center; gap: var(--space-12); padding: var(--space-12) var(--space-14); border: none; background: none; font: inherit; color: var(--ink);
    text-align: left; cursor: pointer; }
  .ico { width: 36px; height: 36px; border-radius: 10px; display: grid; place-items: center; flex-shrink: 0; background: var(--surface-sunken); color: var(--seg-fg); }
  .ico.warn-i { background: var(--warn-bg); color: var(--warn); }
  .ico.cloro-i { background: var(--cloro-soft); color: var(--cloro-ink); }
  .testo { min-width: 0; display: flex; flex-direction: column; line-height: 1.3; }
  .testo b { font-size: var(--text-md); }
  .testo .small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .dett { padding: 0 var(--space-14) var(--space-14) 62px; display: flex; flex-direction: column; gap: var(--space-10); }
  .vuoto { padding: var(--space-14); margin: 0; }
  .nuova { min-height: 48px; text-decoration: none; }
  .nomargin { margin: 0; }
</style>
