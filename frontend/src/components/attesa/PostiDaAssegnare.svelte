<script lang="ts">
  import Azione from '../Azione.svelte';
  import Icona from '../Icona.svelte';
  import AvvisoPosto from './AvvisoPosto.svelte';
  import { api, type PaginaAttesa, type TurnoPosti, type VoceAttesa } from '../../lib/api';
  import {
    alCompleto, descriviEvento, eta, giorniDiAttesa, notaVoce, postiDaAssegnare, quandoTurno, riepilogo, statoVoce, testoPoi, type AzioniAttesa,
  } from '../../lib/attesa';
  import { colore } from '../../lib/piano';

  /**
   * Vista «Posti da assegnare» (variante A del mockup): i quattro numeri, i turni dove c'è un posto con chi tocca e cosa
   * fare, i turni al completo in righe che si aprono, e a destra l'avviso da mandare e le ultime azioni.
   */
  let { pagina, cerca, schede, azioni }: { pagina: PaginaAttesa; cerca: string; schede: boolean; azioni: AzioniAttesa } = $props();

  let avviso = $state<{ voce: VoceAttesa; turno: TurnoPosti } | null>(null);
  let aperto = $state<string | null>(null);
  let tuttoStorico = $state(false);

  const q = $derived(cerca.trim().toLowerCase());
  const corrisponde = (t: TurnoPosti) => !q || t.corso.toLowerCase().includes(q)
    || t.attesa.some((v) => `${v.nome} ${v.cognome} ${v.cognome} ${v.nome}`.toLowerCase().includes(q));
  const posti = $derived(postiDaAssegnare(pagina).filter((p) => corrisponde(p.turno)));
  const pieni = $derived(alCompleto(pagina).filter(corrisponde));
  const kpi = $derived(riepilogo(pagina));
  const storico = $derived(pagina.storico.slice(0, tuttoStorico ? 30 : 4).map((e) => descriviEvento(e, pagina.oggi)));

  // se la pagina si ricarica e chi si stava avvisando non tocca più, il modulo si chiude
  $effect(() => {
    if (avviso && !postiDaAssegnare(pagina).some((p) => p.tocca?.id === avviso!.voce.id)) avviso = null;
  });

  const nome = (v: VoceAttesa) => `${v.nome} ${v.cognome}`;

  /** Chi non ha risposto resta in lista; il posto si propone al prossimo, con l'avviso già aperto. */
  async function proponi(v: VoceAttesa, t: TurnoPosti, prossimo: VoceAttesa) {
    const ok = await azioni.esegui(() => api.senzaRisposta(v.id), `${nome(v)} resta in lista; il posto ora è per ${nome(prossimo)}.`);
    if (ok) {
      const nuovo = pagina.turni.find((x) => x.lezioneId === t.lezioneId);
      const voce = nuovo?.attesa.find((x) => x.id === prossimo.id);
      if (nuovo && voce) avviso = { voce, turno: nuovo };
    }
  }
</script>

<div class="tiles">
  <div class="tile hero-t"><span class="lbl">Posti da assegnare</span><span class="v">{kpi.posti}</span><span class="n">{kpi.turniConPosto === 1 ? 'in 1 turno' : `in ${kpi.turniConPosto} turni`}</span></div>
  <div class="tile"><span class="lbl">In attesa</span><span class="v">{kpi.persone}</span><span class="n">{kpi.persone === 1 ? 'persona' : 'persone'}{kpi.turniPieni ? `, ${kpi.turniPieni} ${kpi.turniPieni === 1 ? 'turno' : 'turni'} al completo` : ''}</span></div>
  <div class="tile" class:arancio={kpi.scaduti > 0}><span class="lbl">Avvisi senza risposta</span><span class="v">{kpi.avvisi}</span>
    <span class="n">{kpi.scaduti ? `${kpi.scaduti} oltre i ${pagina.giorniRisposta} giorni` : `entro i ${pagina.giorniRisposta} giorni per rispondere`}</span></div>
  <div class="tile"><span class="lbl">Attesa media</span><span class="v">{kpi.attesaMedia === 1 ? '1 giorno' : `${kpi.attesaMedia} giorni`}</span><span class="n">la più lunga: {kpi.attesaMassima} giorni</span></div>
</div>

<div class="corpo">
  <div class="principale">
    <section class="sezione">
      <h2 class="lbl">Da assegnare</h2>
      {#each posti as p (p.turno.lezioneId)}
        {@const t = p.turno}
        <article class="card posto">
          <header class="testa-t">
            <span class="pallino-l {colore(t.tipo ?? 'ALTRO')}" style:background={t.colore ?? 'var(--c-line)'} aria-hidden="true"></span>
            <b class="corso">{t.corso}</b><span class="muted">{quandoTurno(t)}</span>
            <span class="grow"></span>
            <span class="libero">{p.posto.testo}</span>
          </header>
          {#if p.tocca}
            {@const v = p.tocca}
            {@const s = statoVoce(v, pagina.oggi)}
            {@const nota = notaVoce(v, pagina.oggi)}
            <div class="tocca">
              <span class="chi">
                <span><span class="muted">Tocca a&nbsp;</span>{#if schede}<a class="nome" href={`#/clienti/${v.personaId}`}>{nome(v)}</a>{:else}<b>{nome(v)}</b>{/if}{#if v.eta != null}<span class="muted">, {eta(v.eta)}</span>{/if}</span>
                <span class="nota {nota.tono}">{nota.testo}</span>
              </span>
              <span class="bottoni">
                {#if s === 'nuovo'}
                  <button class="btn btn-quieto btn-sm" onclick={() => azioni.iscrivi(v, t)}>Iscrivi</button>
                  <button class="btn btn-primary btn-sm" aria-pressed={avviso?.voce.id === v.id} onclick={() => (avviso = { voce: v, turno: t })}>Avvisa</button>
                {:else if s === 'avvisata'}
                  <button class="btn btn-quieto btn-sm" onclick={() => azioni.esegui(() => api.rinunciaAttesa(v.id), `${nome(v)} ha rinunciato: tolto dalla lista.`)}>Ha rinunciato</button>
                  <button class="btn btn-primary btn-sm" onclick={() => azioni.iscrivi(v, t)}>Iscrivi</button>
                {:else if p.poi.length}
                  <button class="btn btn-quieto btn-sm" onclick={() => azioni.iscrivi(v, t)}>Iscrivi</button>
                  <button class="btn btn-primary btn-sm" onclick={() => proponi(v, t, p.poi[0])}>Proponi a {p.poi[0].nome}</button>
                {:else}
                  <button class="btn btn-quieto btn-sm" onclick={() => azioni.esegui(() => api.rinunciaAttesa(v.id), `${nome(v)} tolto dalla lista.`)}>Ha rinunciato</button>
                  <button class="btn btn-primary btn-sm" onclick={() => azioni.iscrivi(v, t)}>Iscrivi</button>
                {/if}
              </span>
            </div>
            <span class="poi muted">{testoPoi(p.poi)}{p.saltati.length ? ` · ${p.saltati.length === 1 ? '1 non ha risposto' : `${p.saltati.length} non hanno risposto`}` : ''}</span>
          {:else}
            <p class="muted small nomargin">Nessuno da avvisare: chi resta in lista non ha risposto agli avvisi.</p>
          {/if}
        </article>
      {:else}
        <p class="vuoto muted small">{q ? 'Nessun posto libero con questa ricerca.' : 'Nessun posto libero nei turni con qualcuno in attesa.'}</p>
      {/each}
    </section>

    <section class="sezione">
      <h2 class="lbl">Al completo</h2>
      {#if pieni.length}
        <div class="card pieni">
          {#each pieni as t (t.lezioneId)}
            {@const primo = t.attesa[0]}
            <div class="riga-p">
              <button type="button" class="apri" aria-expanded={aperto === t.lezioneId} onclick={() => (aperto = aperto === t.lezioneId ? null : t.lezioneId)}>
                <span class="c"><span class="pallino-l {colore(t.tipo ?? 'ALTRO')}" style:background={t.colore ?? 'var(--c-line)'} aria-hidden="true"></span><b>{t.corso}</b><span class="muted">{quandoTurno(t)}</span></span>
                <span>{t.attesa.length} in attesa</span>
                <span class="muted small">{primo ? `il primo da ${giorniDiAttesa(primo, pagina.oggi)} gg` : ''}</span>
                <Icona nome="dx" />
              </button>
              {#if aperto === t.lezioneId}
                <ol class="lista">
                  {#each t.attesa as v (v.id)}
                    <li>
                      <span class="grow">{#if schede}<a class="nome" href={`#/clienti/${v.personaId}`}>{nome(v)}</a>{:else}<b>{nome(v)}</b>{/if}<span class="muted">{v.eta != null ? `, ${eta(v.eta)}` : ''} · da {giorniDiAttesa(v, pagina.oggi)} giorni{v.note ? ` · ${v.note}` : ''}</span></span>
                      <Azione icona="cestino" tono="danger" etichetta={`Togli ${nome(v)} dalla lista d'attesa`} conferma="Togli?"
                        onclick={() => azioni.esegui(() => api.rinunciaAttesa(v.id), `${nome(v)} tolto dalla lista d'attesa.`)} />
                    </li>
                  {/each}
                </ol>
              {/if}
            </div>
          {/each}
        </div>
      {:else}
        <p class="vuoto muted small">Nessun turno al completo con qualcuno in attesa.</p>
      {/if}
    </section>
  </div>

  <aside class="lato">
    {#if avviso}
      {#key avviso.voce.id}
        <AvvisoPosto voce={avviso.voce} turno={avviso.turno} {pagina} onchiudi={() => (avviso = null)}
          onfatto={(m) => { avviso = null; azioni.fatto(m); }} />
      {/key}
    {/if}
    <section class="storico">
      <h2 class="lbl">Ultime azioni</h2>
      {#each storico as s, i (i)}
        <span class="evento"><b>{s.chi}</b> {s.cosa} <span class="muted">· {s.quando}</span></span>
      {:else}
        <span class="muted small">Ancora niente.</span>
      {/each}
      {#if pagina.storico.length > 4}
        <button type="button" class="link-btn" onclick={() => (tuttoStorico = !tuttoStorico)}>{tuttoStorico ? 'Mostra meno' : 'Tutto lo storico →'}</button>
      {/if}
    </section>
  </aside>
</div>

<style>
  .tiles { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-10); }
  .tile { display: flex; flex-direction: column; gap: 2px; padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); background: var(--surface); border: 1px solid var(--line); min-width: 0; }
  .tile .v { font-family: var(--font-display); font-size: 26px; font-weight: 800; line-height: 1.1; white-space: nowrap; }
  .tile .n { font-size: var(--text-xs); color: var(--muted); }
  .tile.arancio { background: var(--warn-bg); border-color: transparent; } .tile.arancio .v { color: var(--warn); }
  .tile.hero-t { background: var(--hero); color: var(--on-fill); border-color: transparent; }
  .tile.hero-t .lbl, .tile.hero-t .n { color: color-mix(in srgb, var(--on-fill) 72%, transparent); }

  .corpo { display: flex; flex-wrap: wrap; gap: var(--space-20) 24px; align-items: flex-start; }
  .principale { flex: 7 1 560px; min-width: 0; display: flex; flex-direction: column; gap: 28px; }
  .lato { flex: 4 1 320px; min-width: 0; display: flex; flex-direction: column; gap: var(--space-14); position: sticky; top: var(--space-16); }
  .sezione { display: flex; flex-direction: column; gap: var(--space-10); }
  .sezione h2 { margin: 0; }

  .posto { padding: var(--space-16); display: flex; flex-direction: column; gap: var(--space-12); }
  .testa-t { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .corso { font-size: var(--text-lg); }
  .libero { font-size: var(--text-sm); font-weight: 700; color: var(--cloro-ink); }
  .pallino-l { width: 9px; height: 9px; border-radius: 3px; flex-shrink: 0; }
  .tocca { display: flex; align-items: center; gap: var(--space-16); flex-wrap: wrap; }
  .chi { flex: 1 1 260px; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
  .nome { font-weight: 700; color: var(--ink); text-decoration: none; }
  .nome:hover { text-decoration: underline; }
  .nota { font-size: var(--text-sm); color: var(--muted); }
  .nota.warn { color: var(--warn); font-weight: 600; }
  .bottoni { display: flex; gap: var(--space-8); }
  .poi { font-size: var(--text-sm); }

  .pieni { padding: 0 var(--space-16); }
  .riga-p + .riga-p { border-top: 1px solid var(--line-soft); }
  .apri { width: 100%; display: grid; grid-template-columns: minmax(0, 1fr) 130px 130px 20px; gap: var(--space-16); align-items: center; padding: var(--space-12) 0;
    border: none; background: none; font: inherit; color: inherit; text-align: left; cursor: pointer; }
  .apri .c { display: flex; align-items: center; gap: var(--space-10); min-width: 0; }
  .apri .c b { font-weight: 600; }
  .apri :global(.ic) { width: 18px; height: 18px; color: var(--muted); transition: transform 0.15s; }
  .apri[aria-expanded='true'] :global(.ic) { transform: rotate(90deg); }
  .lista { margin: 0 0 var(--space-14); padding: 0 0 0 var(--space-20); display: flex; flex-direction: column; gap: 2px; font-size: var(--text-md); }
  .lista li { display: flex; align-items: center; gap: var(--space-8); }

  .storico { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-4) 2px; }
  .storico h2 { margin: 0; }
  .evento { font-size: var(--text-sm); line-height: 1.35; }
  .link-btn { align-self: flex-start; border: none; background: none; padding: 0; font: inherit; font-size: var(--text-sm); font-weight: 700; color: var(--cloro); cursor: pointer; }
  .link-btn:hover { text-decoration: underline; }
  .vuoto { padding: var(--space-14); border: 1px dashed var(--line-dashed); border-radius: var(--radius-md); margin: 0; }
  .nomargin { margin: 0; }

  @media (max-width: 900px) { .tiles { grid-template-columns: repeat(2, minmax(0, 1fr)); } .lato { position: static; } }
  @media (max-width: 600px) {
    .apri { grid-template-columns: minmax(0, 1fr) auto 20px; }
    .apri > .small { display: none; }
    .bottoni { width: 100%; } .bottoni .btn { flex: 1; }
  }
</style>
