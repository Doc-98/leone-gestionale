<script lang="ts">
  import MenuAltro from '../MenuAltro.svelte';
  import Icona from '../Icona.svelte';
  import AvvisoPosto from './AvvisoPosto.svelte';
  import { api, messaggioErrore, type PaginaAttesa, type TurnoPosti, type VoceAttesa } from '../../lib/api';
  import { quanti as nQuanti } from '../../lib/plurali';
  import {
    azionePersona, FILTRI_PERSONE, nelFiltro, ordinaPersone, personeInAttesa, postiDaAssegnare, principale, quandoTurnoBreve, statoPersona, statoVoce,
    testoConferma, type AzioniAttesa, type FiltroPersone, type OrdinePersone, type PersonaInAttesa,
  } from '../../lib/attesa';
  import { colore } from '../../lib/piano';
  import { leggiPreferenza, salvaPreferenza } from '../../lib/preferenze';
  import type { VoceMenu } from '../../lib/menu';

  /**
   * Vista «Persone in attesa» (variante C del mockup): una riga per persona con tutti i turni che aspetta e la posizione,
   * da quanto aspetta, un'azione dove serve e le altre nel menu. Si ordina per nome, corso o attesa; selezionando più
   * persone si chiede a tutte se aspettano ancora.
   */
  let { pagina, cerca, schede, azioni }: { pagina: PaginaAttesa; cerca: string; schede: boolean; azioni: AzioniAttesa } = $props();

  let filtro = $state<FiltroPersone>('tutti');
  let ordine = $state<OrdinePersone>(leggiPreferenza<OrdinePersone>('attesa.ordine', 'attesa', ['nome', 'corso', 'attesa']));
  $effect(() => salvaPreferenza('attesa.ordine', ordine));
  let scelti = $state<Set<string>>(new Set());
  let quanti = $state(40);
  let avviso = $state<{ personaId: string; voce: VoceAttesa; turno: TurnoPosti } | null>(null);
  let conferma = $state<{ oggetto: string; testo: string } | null>(null);
  let errore = $state('');

  const tutte = $derived(personeInAttesa(pagina));
  const q = $derived(cerca.trim().toLowerCase());
  const visibili = $derived(ordinaPersone(tutte.filter((p) => nelFiltro(p, filtro, pagina.oggi)
    && (!q || `${p.nome} ${p.cognome} ${p.cognome} ${p.nome}`.toLowerCase().includes(q) || p.turni.some((t) => t.turno.corso.toLowerCase().includes(q)))), ordine));
  const conteggi = $derived(Object.fromEntries(FILTRI_PERSONE.map((f) => [f.id, tutte.filter((p) => nelFiltro(p, f.id, pagina.oggi)).length])));
  const conPosto = $derived(conteggi.posto ?? 0);
  // le voci dei selezionati (una persona può aspettare più turni: si chiede una volta sola)
  const vociScelte = $derived(tutte.filter((p) => scelti.has(p.personaId)).flatMap((p) => p.turni.map((t) => t.voce.id)));

  $effect(() => {
    // chi non è più in lista esce dalla selezione
    const ids = new Set(tutte.map((p) => p.personaId));
    if ([...scelti].some((id) => !ids.has(id))) scelti = new Set([...scelti].filter((id) => ids.has(id)));
  });

  const nome = (p: { nome: string; cognome: string }) => `${p.nome} ${p.cognome}`;
  function scegli(id: string) {
    const s = new Set(scelti);
    if (s.has(id)) s.delete(id); else s.add(id);
    scelti = s;
  }

  function apriAvviso(p: PersonaInAttesa, voce: VoceAttesa, turno: TurnoPosti) {
    avviso = { personaId: p.personaId, voce, turno };
  }

  /** Le voci del menu «⋯» per un turno in cui la persona aspetta (le stesse di sempre); la rimozione è l'ultima, a due tocchi. */
  function vociTurno(p: PersonaInAttesa, t: PersonaInAttesa['turni'][number]): VoceMenu[] {
    const s = statoVoce(t.voce, pagina.oggi);
    return [
      ...(t.tocca && s !== 'saltata'
        ? [{ testo: s === 'nuovo' ? 'Avvisa del posto…' : 'Avvisa di nuovo…', icona: 'campana', onclick: () => apriAvviso(p, t.voce, t.turno) } satisfies VoceMenu]
        : []),
      { testo: 'Iscrivi al turno', icona: 'persona-piu', onclick: () => azioni.iscrivi(t.voce, t.turno) },
      { testo: 'Aspetta ancora', icona: 'check', onclick: () => azioni.esegui(() => api.confermataAttesa(t.voce.id), `${nome(p)} aspetta ancora: segnato.`) },
      { testo: 'Rimuovi dalla lista', icona: 'cestino', elimina: 'Rimuovi?',
        onclick: () => azioni.esegui(() => api.rinunciaAttesa(t.voce.id), `${nome(p)} rimosso dalla lista di ${t.turno.corso}.`) },
    ];
  }

  async function azione(p: PersonaInAttesa) {
    const t = principale(p);
    const a = azionePersona(p, pagina.oggi);
    if (!a) return;
    if (a.tipo === 'avvisa') apriAvviso(p, t.voce, t.turno);
    else if (a.tipo === 'iscrivi') await azioni.iscrivi(t.voce, t.turno);
    else {
      const ok = await azioni.esegui(() => api.senzaRisposta(t.voce.id), `${nome(p)} resta in lista; il posto passa al prossimo.`);
      const prossimo = ok ? postiDaAssegnare(pagina).find((x) => x.turno.lezioneId === t.turno.lezioneId)?.tocca : null;
      if (prossimo) {
        const turno = pagina.turni.find((x) => x.lezioneId === t.turno.lezioneId)!;
        avviso = { personaId: prossimo.personaId, voce: prossimo, turno };
      }
    }
  }

  async function chiedi() {
    errore = '';
    try {
      const r = await api.chiediConfermaAttesa({ ids: vociScelte, oggetto: conferma!.oggetto, testo: conferma!.testo });
      conferma = null;
      scelti = new Set();
      azioni.fatto(`${r.inviate === 1 ? '1 email partita' : `${r.inviate} email partite`}.`
        + (r.daChiamare.length ? ` Da chiamare (senza email o invio non riuscito): ${r.daChiamare.join(', ')}.` : ''));
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
</script>

<div class="barra">
  <div class="chips" role="group" aria-label="Chi mostrare">
    {#each FILTRI_PERSONE as f (f.id)}
      <button type="button" class="chip" aria-pressed={filtro === f.id} onclick={() => (filtro = f.id)}>{f.nome}<span class="m n">{conteggi[f.id]}</span></button>
    {/each}
  </div>
  <span class="grow"></span>
  {#if scelti.size}
    <span class="sel">
      <b>{scelti.size === 1 ? '1 selezionato' : `${scelti.size} selezionati`}</b>
      <button type="button" class="btn btn-quieto btn-sm" onclick={() => { scelti = new Set(); conferma = null; }}>Annulla</button>
      <button type="button" class="btn btn-secondary btn-sm" aria-pressed={!!conferma} onclick={() => (conferma = conferma ? null : testoConferma(pagina.societa))}>Chiedi se sono interessati</button>
    </span>
  {/if}
</div>

{#if conferma}
  <form class="card conferma" onsubmit={(e) => { e.preventDefault(); chiedi(); }}>
    <span class="muted small">Un'email per persona ({scelti.size}), anche se aspetta più turni. «{'{nome}'}» diventa il nome. Chi non ha un'email finisce nell'elenco da chiamare.</span>
    <label class="field"><span class="lbl">Oggetto</span><input class="inp" bind:value={conferma.oggetto} /></label>
    <label class="field"><span class="lbl">Testo</span><textarea class="inp testo" rows="6" bind:value={conferma.testo}></textarea></label>
    {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
    <div class="row"><button type="button" class="btn btn-secondary" onclick={() => (conferma = null)}>Annulla</button><button class="btn btn-primary">Manda le email</button></div>
  </form>
{/if}

<div class="card tabella">
  <div class="griglia testa">
    <span></span>
    <button type="button" class="ord lbl" aria-pressed={ordine === 'nome'} onclick={() => (ordine = 'nome')}>Persona{#if ordine === 'nome'}<Icona nome="su" /><span class="sr">, in ordine</span>{/if}</button>
    <button type="button" class="ord lbl" aria-pressed={ordine === 'corso'} onclick={() => (ordine = 'corso')}>Aspetta · per corso{#if ordine === 'corso'}<Icona nome="su" /><span class="sr">, in ordine</span>{/if}</button>
    <button type="button" class="ord lbl" aria-pressed={ordine === 'attesa'} onclick={() => (ordine = 'attesa')}>Da quanto{#if ordine === 'attesa'}<Icona nome="giu" /><span class="sr">, dal più lungo</span>{/if}</button>
    <span></span>
  </div>
  {#each visibili.slice(0, quanti) as p, i (p.personaId)}
    {@const st = statoPersona(p, pagina.oggi)}
    {@const a = azionePersona(p, pagina.oggi)}
    <div class="riga" class:pari={i % 2 === 1} class:scelta={scelti.has(p.personaId)}>
      <div class="griglia">
        <input class="spunta" type="checkbox" checked={scelti.has(p.personaId)} onchange={() => scegli(p.personaId)} aria-label={`Seleziona ${nome(p)}`} />
        <span class="chi">
          {#if schede}<a class="nome" href={`#/clienti/${p.personaId}`}>{nome(p)}</a>{:else}<b class="nome">{nome(p)}</b>{/if}
          <span class="muted small">{p.eta != null ? `${p.eta} ${p.eta === 1 ? 'anno' : 'anni'}` : ''}{p.eta != null && !p.contatti.length ? ' · ' : ''}{p.contatti.length ? '' : 'solo telefono'}</span>
        </span>
        <span class="turni">
          {#each p.turni as t (t.voce.id)}
            <span class="turno">
              <span class="turno-testo">
              <span class="pallino-l {colore(t.turno.tipo ?? 'ALTRO')}" style:background={t.turno.colore ?? 'var(--c-line)'} aria-hidden="true"></span>
                <span><b>{t.turno.corso}</b> <span class="muted">{quandoTurnoBreve(t.turno)}</span></span>
                <span class="pos" class:posto={t.tocca}>{t.tocca ? `${t.voce.posizione}° · c'è posto` : `${t.voce.posizione}° di ${t.di}`}</span>
              </span>
              {#if p.turni.length > 1}
                <!-- più turni: ognuno ha il suo menu (al massimo sette voci per menu) -->
                <span class="menu-turno"><MenuAltro voci={vociTurno(p, t)} etichetta={`${nome(p)} · ${t.turno.corso} ${quandoTurnoBreve(t.turno)}`} pulsante={`Altre azioni per ${nome(p)}, ${t.turno.corso} ${quandoTurnoBreve(t.turno)}`} /></span>
              {/if}
            </span>
          {/each}
        </span>
        <span class="quanto">
          <span class:lunga={p.giorni > 45}>{nQuanti(p.giorni, 'giorno', 'giorni')}</span>
          <span class="stato {st.tono}">{st.testo}</span>
        </span>
        <span class="azioni">
          {#if a}<button type="button" class="btn btn-sm" class:btn-primary={a.tipo === 'avvisa'} class:btn-secondary={a.tipo !== 'avvisa'} onclick={() => azione(p)}>{a.testo}</button>{/if}
          {#if p.turni.length === 1}<MenuAltro voci={vociTurno(p, p.turni[0])} etichetta={`Azioni per ${nome(p)}`} pulsante={`Altre azioni per ${nome(p)}`} />{/if}
        </span>
      </div>
      {#if avviso?.personaId === p.personaId}
        <div class="avviso-riga">
          {#key avviso.voce.id}
            <AvvisoPosto voce={avviso.voce} turno={avviso.turno} {pagina} onchiudi={() => (avviso = null)} onfatto={(m) => { avviso = null; azioni.fatto(m); }} />
          {/key}
        </div>
      {/if}
    </div>
  {:else}
    <p class="vuoto muted small">{tutte.length ? 'Nessuno con questi filtri.' : 'Nessuno in lista d\'attesa. Quando un turno è pieno, dalla lista degli iscritti si può mettere in attesa.'}</p>
  {/each}
  <div class="piede">
    <span class="muted small">{visibili.length === tutte.length ? `${tutte.length} ${tutte.length === 1 ? 'persona' : 'persone'}` : `${visibili.length} di ${tutte.length} persone`}{conPosto ? ` · ${conPosto} con un posto libero` : ''}</span>
    <span class="grow"></span>
    {#if visibili.length > quanti}<button type="button" class="btn btn-quieto btn-sm" onclick={() => (quanti = visibili.length)}>Mostra tutte le {visibili.length}</button>{/if}
  </div>
</div>

<style>
  .barra { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-10); }
  .chips { gap: var(--space-6); } /* più stretto del .chips globale (8px) */
  .chip .n { margin-left: var(--space-6); opacity: 0.6; }
  .sel { display: flex; align-items: center; gap: var(--space-8); font-size: var(--text-md); }
  .conferma { padding: var(--space-14) var(--space-16); display: flex; flex-direction: column; gap: var(--space-10); }
  .conferma .testo { min-height: 130px; font-family: var(--font-body); line-height: 1.45; resize: vertical; }
  .conferma .row { display: flex; gap: var(--space-8); justify-content: flex-end; }

  .tabella { padding: 0; overflow: visible; }
  .griglia { display: grid; grid-template-columns: 24px minmax(0, 1fr) minmax(0, 1.3fr) 170px 220px; gap: var(--space-20); align-items: center; padding: var(--space-14) var(--space-16); }
  .testa { padding-top: var(--space-12); padding-bottom: var(--space-12); border-bottom: 1px solid var(--line); }
  .ord { display: inline-flex; align-items: center; gap: var(--space-4); border: none; background: none; padding: 0; font-family: inherit; cursor: pointer; text-align: left; color: var(--muted); }
  .ord[aria-pressed='true'] { color: var(--ink); }
  .riga { background: var(--surface); }
  .riga + .riga { border-top: 1px solid var(--line); }
  .riga.pari { background: color-mix(in srgb, var(--cloro) 7%, var(--surface)); }
  .riga.scelta { background: var(--cloro-soft); }
  .riga:last-of-type { border-bottom: 1px solid var(--line-soft); }
  .spunta { width: 18px; height: 18px; accent-color: var(--fill); }
  .chi { display: flex; flex-direction: column; min-width: 0; line-height: 1.35; }
  .nome { font-weight: 700; color: var(--ink); text-decoration: none; }
  a.nome:hover { text-decoration: underline; }
  .turni { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
  .turno { display: flex; align-items: center; justify-content: space-between; gap: var(--space-8); font-size: var(--text-md); }
  .turno-testo { display: flex; align-items: center; gap: var(--space-8); flex-wrap: wrap; min-width: 0; }
  .turno b { font-weight: 600; }
  .pallino-l { width: 8px; height: 8px; border-radius: 3px; flex-shrink: 0; }
  .pos { font-size: var(--text-xs); color: var(--muted); font-variant-numeric: tabular-nums; white-space: nowrap; }
  .pos.posto { color: var(--cloro-ink); font-weight: 700; }
  .quanto { display: flex; flex-direction: column; line-height: 1.35; font-size: var(--text-md); }
  .quanto .lunga { color: var(--warn); }
  .stato { font-size: var(--text-xs); color: var(--muted); }
  .stato.warn { color: var(--warn); font-weight: 600; }
  .stato.cloro { color: var(--cloro-ink); font-weight: 600; }
  .azioni { display: flex; justify-content: flex-end; align-items: center; gap: var(--space-4); }
  .azioni .btn { white-space: nowrap; }
  .avviso-riga { padding: 0 var(--space-16) var(--space-16) 60px; max-width: 760px; }

  .menu-turno { margin-block: calc(var(--space-6) * -1); flex-shrink: 0; }

  .piede { display: flex; align-items: center; padding: var(--space-10) var(--space-16); }
  .vuoto { margin: var(--space-16); padding: var(--space-14); border: 1px dashed var(--line-dashed); border-radius: var(--radius-md); }

  @media (max-width: 900px) {
    .griglia { grid-template-columns: 24px minmax(0, 1fr) minmax(0, 1.2fr); }
    .testa > :nth-child(4), .testa > :nth-child(5) { display: none; }
    .quanto { grid-column: 2; }
    .azioni { grid-column: 3; justify-content: flex-start; }
    .avviso-riga { padding-left: var(--space-16); }
  }
  @media (max-width: 600px) {
    .griglia { grid-template-columns: 24px minmax(0, 1fr); gap: var(--space-8) var(--space-12); }
    .testa > :nth-child(3) { display: none; }
    .turni, .quanto, .azioni { grid-column: 2; }
  }
</style>
