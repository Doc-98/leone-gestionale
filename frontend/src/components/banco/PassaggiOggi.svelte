<script lang="ts">
  import type { GiornataBanco, Passaggio } from '../../lib/api';
  import { motivoBreve, perCorso, perQuarto, tipoPassaggio, turnoDelPassaggio, type TipoPassaggio, type VistaPassaggi } from '../../lib/banco';
  import { oraDi } from '../../lib/date';
  import { colore } from '../../lib/piano';
  import PillolaCorso from './PillolaCorso.svelte';

  /**
   * «Passaggi di oggi» al banco, in quattro viste a scelta (preferenza dell'account): tessere colorate dall'esito,
   * tabella compatta con i filtri, raggruppati per corso, linea del tempo. Un clic su un passaggio ne riapre l'esito.
   */
  let {
    giornata,
    vista,
    scelto,
    adesso,
    onapri,
    altri,
  }: {
    giornata: GiornataBanco;
    vista: VistaPassaggi;
    scelto: string | null;
    adesso: Date;
    onapri: (p: Passaggio) => void;
    /** carica altri passaggi (più indietro nella giornata), se ce ne sono */
    altri: (() => void) | null;
  } = $props();

  const nome = (p: Passaggio) => (p.persona ? `${p.persona.nome} ${p.persona.cognome}` : `Braccialetto ${p.codice}`);
  const iniziali = (p: Passaggio) => (p.persona ? `${p.persona.nome[0] ?? ''}${p.persona.cognome[0] ?? ''}`.toUpperCase() : '?');
  const turno = (p: Passaggio) => (p.verde && p.verso === 'ENTRATA' && p.persona ? turnoDelPassaggio(giornata.turni[p.persona.id], p.istante) : null);
  /** La nota sotto il nome: il motivo del rosso, l'uscita, il carnet; niente se c'è il corso. */
  function nota(p: Passaggio): string {
    if (!p.verde) return motivoBreve(p.motivi);
    if (p.verso === 'USCITA') return 'Uscita';
    if (p.carnetScalato) return 'Carnet: scalato un ingresso';
    if (p.segnalato) return 'Sotto controllo: segnalato';
    return '';
  }
  const esito = (p: Passaggio) => (!p.verde ? 'Fermato' : p.verso === 'USCITA' ? 'Uscito' : 'Entrato');

  // ── tabella: i filtri ──
  let filtro = $state<'tutti' | TipoPassaggio>('tutti');
  const FILTRI = $derived<{ id: 'tutti' | TipoPassaggio; nome: string; n: number }[]>([
    { id: 'tutti', nome: 'Tutti', n: giornata.linea.length },
    { id: 'entrate', nome: 'Entrate', n: giornata.conteggi.entrate },
    { id: 'uscite', nome: 'Uscite', n: giornata.conteggi.uscite },
    { id: 'fermati', nome: 'Fermati', n: giornata.conteggi.fermati },
  ]);
  const righe = $derived(filtro === 'tutti' ? giornata.passaggi : giornata.passaggi.filter((p) => tipoPassaggio(p) === filtro));

  // ── per corso ──
  const oraAdesso = $derived(oraDi(adesso.toISOString()));
  const gruppi = $derived(vista === 'corsi' ? perCorso(giornata, oraAdesso) : []);
  const minuti = (hhmm: string) => Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3, 5));
  function sotto(g: (typeof gruppi)[number]): string {
    if (g.tipo !== 'turno' || !g.turno) return g.passaggi.length === 1 ? '1 persona' : `${g.passaggi.length} persone`;
    return `${g.arrivati ?? g.passaggi.length} arrivati su ${g.turno.iscritti}`;
  }
  function notaGruppo(g: (typeof gruppi)[number]): string {
    if (g.tipo !== 'turno' || !g.turno) return '';
    const tra = minuti(g.turno.inizio) - minuti(oraAdesso);
    const mancano = g.turno.iscritti - (g.arrivati ?? 0);
    if (tra < 0 || tra > 60) return '';
    return `Inizia tra ${tra} min${mancano > 0 ? `: ne mancano ancora ${mancano}` : ''}.`;
  }

  // ── linea del tempo ──
  const quarti = $derived(vista === 'tempo' ? perQuarto(giornata.passaggi) : []);
</script>

{#snippet sottoNome(p: Passaggio)}
  {@const t = turno(p)}
  {@const n = nota(p)}
  {#if t}<PillolaCorso turno={t} />{/if}
  {#if n}<span class="nota" class:rossa={!p.verde}>{n}</span>{/if}
  {#if p.goldenPass}<span class="tag-o">golden pass</span>{/if}
{/snippet}

{#if vista === 'tessere'}
  <ul class="tessere">
    {#each giornata.passaggi as p (p.id)}
      {@const tipo = tipoPassaggio(p)}
      <li>
        <button type="button" class="tessera {tipo}" class:oro={p.goldenPass && p.verde} class:scelto={p.id === scelto} onclick={() => onapri(p)}>
          <span class="testa">
            <span class="iniziali" aria-hidden="true">{iniziali(p)}</span>
            <span class="chi">
              <span class="nome">{nome(p)}</span>
              <span class="esito">{esito(p)} <span class="m ora">· {oraDi(p.istante)}</span></span>
            </span>
          </span>
          <span class="piede">{@render sottoNome(p)}</span>
        </button>
      </li>
    {:else}
      <li class="vuoto muted small">Nessun passaggio oggi.</li>
    {/each}
  </ul>
{:else if vista === 'tabella'}
  <div class="filtri" role="group" aria-label="Quali passaggi mostrare">
    {#each FILTRI as f (f.id)}
      <button type="button" class="chip" aria-pressed={filtro === f.id} onclick={() => (filtro = f.id)}>{f.nome}<span class="m conto">{f.n}</span></button>
    {/each}
  </div>
  <div class="card tabella">
    <div class="riga intest" aria-hidden="true"><span>Ora</span><span>Chi · corso di oggi</span><span class="dx">Esito</span></div>
    {#each righe as p (p.id)}
      {@const tipo = tipoPassaggio(p)}
      <button type="button" class="riga {tipo}" class:scelto={p.id === scelto} onclick={() => onapri(p)}>
        <span class="m ora">{oraDi(p.istante)}</span>
        <span class="chi-riga"><span class="nome">{nome(p)}</span>{@render sottoNome(p)}</span>
        <span class="dx"><span class="esito-tag {tipo}" class:oro={p.goldenPass && p.verde}>{tipo === 'fermati' ? 'fermato' : tipo === 'uscite' ? 'uscita' : 'entrata'}</span></span>
      </button>
    {:else}
      <p class="muted small vuoto">Nessun passaggio di questo tipo oggi.</p>
    {/each}
  </div>
{:else if vista === 'corsi'}
  <div class="gruppi">
    {#each gruppi as g (g.chiave)}
      <div class="card gruppo {g.tipo} {g.turno ? colore(g.turno.tipo ?? 'ALTRO') : ''}">
        <div class="g-testa">
          {#if g.turno}<span class="liv" style:background={g.turno.colore ?? 'var(--c-line)'} aria-hidden="true"></span>{/if}
          <span class="g-titolo">{g.titolo}</span>
          <span class="g-sotto">{sotto(g)}</span>
        </div>
        <div class="g-persone">
          {#each g.passaggi as p (p.id)}
            <button type="button" class="persona" class:scelto={p.id === scelto} title={nota(p) || nome(p)} onclick={() => onapri(p)}>
              <span class="iniziali" aria-hidden="true">{iniziali(p)}</span>
              <span class="nome">{nome(p)}</span>
              <span class="m ora">{oraDi(p.istante)}</span>
            </button>
          {/each}
        </div>
        {#if notaGruppo(g)}<p class="g-nota">{notaGruppo(g)}</p>{/if}
      </div>
    {:else}
      <p class="muted small">Nessun passaggio oggi.</p>
    {/each}
  </div>
{:else}
  <div class="card tempo">
    {#each quarti as q (q.ora + q.voci[0].id)}
      <div class="quarto">
        <span class="m q-ora">{q.ora}</span>
        <div class="q-voci">
          {#each q.voci as p (p.id)}
            {@const tipo = tipoPassaggio(p)}
            <div class="q-voce">
              <span class="nodo {tipo}" aria-hidden="true"></span>
              <button type="button" class="q-btn {tipo}" class:scelto={p.id === scelto} onclick={() => onapri(p)}>
                <span class="q-riga"><span class="nome">{nome(p)}</span><span class="m ora">{oraDi(p.istante)}</span></span>
                <span class="q-sotto">{@render sottoNome(p)}</span>
              </button>
            </div>
          {/each}
        </div>
      </div>
    {:else}
      <p class="muted small">Nessun passaggio oggi.</p>
    {/each}
  </div>
{/if}

{#if altri}<button type="button" class="btn btn-sm altri" onclick={altri}>Mostra i passaggi più indietro</button>{/if}

<style>
  .tessera, .riga, .persona, .q-btn { font: inherit; color: inherit; text-align: left; }
  .nome { font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; min-width: 0; }
  .ora { font-size: var(--text-xs); color: var(--muted); font-weight: 400; }
  .nota { font-size: var(--text-xs); color: var(--muted); }
  .nota.rossa { color: var(--ferma); font-weight: 600; }
  .tag-o { padding: 1px 8px; border-radius: var(--radius-pill); background: var(--oro-bg); color: var(--oro-fg); font-size: 11px; font-weight: 600; white-space: nowrap; }
  .iniziali { width: 34px; height: 34px; border-radius: 50%; display: grid; place-items: center; flex-shrink: 0; font-size: 12px; font-weight: 700; }
  .vuoto { padding: var(--space-12); }
  .altri { align-self: center; }
  .scelto { outline: 2px solid var(--cloro); outline-offset: -2px; background: var(--cloro-soft) !important; }

  /* tessere */
  .tessere { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: var(--space-8); }
  .tessera { --e: var(--passa); width: 100%; height: 100%; display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-12); border: none;
    border-radius: var(--radius-md); background: var(--surface); cursor: pointer; box-shadow: inset 0 3px 0 var(--e), inset 0 0 0 1px var(--line); }
  .tessera.fermati { --e: var(--ferma); background: var(--ferma-bg); box-shadow: inset 0 3px 0 var(--e), inset 0 0 0 1px color-mix(in srgb, var(--ferma) 35%, transparent); }
  .tessera.uscite { --e: var(--line-strong); }
  .tessera.oro { --e: var(--oro); }
  .tessera .testa { display: flex; align-items: center; gap: var(--space-10); min-width: 0; }
  .tessera .iniziali { background: var(--surface-sunken); color: var(--ink); box-shadow: inset 0 0 0 2px var(--e); }
  .tessera.fermati .iniziali { background: var(--ferma); color: var(--surface); }
  .tessera.uscite .iniziali { background: var(--surface); color: var(--muted); }
  .tessera .chi { display: flex; flex-direction: column; min-width: 0; line-height: 1.25; }
  .tessera .esito { font-size: var(--text-xs); font-weight: 600; color: var(--e); }
  .tessera.uscite .esito { color: var(--muted); }
  .piede { display: flex; align-items: center; gap: var(--space-6); flex-wrap: wrap; min-height: 22px; }

  /* tabella */
  .filtri { display: flex; gap: var(--space-6); flex-wrap: wrap; }
  .conto { margin-left: var(--space-6); opacity: 0.65; }
  .tabella { padding: 0; overflow: hidden; }
  .riga { width: 100%; display: grid; grid-template-columns: 48px minmax(0, 1fr) 82px; gap: var(--space-10); align-items: center;
    padding: var(--space-8) var(--space-14) var(--space-8) 11px; border: none; border-top: 1px solid var(--line-soft); border-left: 3px solid transparent;
    background: transparent; font-size: var(--text-sm); cursor: pointer; }
  .riga.intest { cursor: default; border-top: none; font-size: 11px; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted); }
  .riga.fermati { border-left-color: var(--ferma); background: var(--ferma-bg); }
  .riga:not(.intest):hover { background: var(--carta); }
  .chi-riga { display: flex; align-items: center; gap: var(--space-8); min-width: 0; overflow: hidden; }
  .chi-riga :global(.pillola) { flex-shrink: 0; }
  .chi-riga .nome { flex: 0 0 auto; max-width: 60%; }
  .chi-riga .nota { flex: 1 1 0; min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .dx { justify-self: end; }
  .esito-tag { padding: 1px 8px; border-radius: var(--radius-pill); font-size: 11px; font-weight: 700; color: var(--passa); }
  .esito-tag.fermati { background: var(--ferma); color: var(--surface); }
  .esito-tag.uscite { background: var(--surface-sunken); color: var(--muted); }
  .esito-tag.oro { background: var(--oro-bg); color: var(--oro-fg); }

  /* per corso */
  .gruppi { display: flex; flex-direction: column; gap: var(--space-8); }
  .gruppo { padding: 0; overflow: hidden; box-shadow: inset 4px 0 0 var(--c-line, var(--line-strong)); }
  .gruppo.fermati { --c-bg: var(--ferma-bg); --c-fg: var(--ferma); --c-line: var(--ferma); }
  .gruppo.senza, .gruppo.uscite { --c-bg: var(--surface-sunken); --c-fg: var(--muted); --c-line: var(--line-strong); }
  .g-testa { display: flex; align-items: center; gap: var(--space-8); padding: var(--space-8) var(--space-14) var(--space-8) 18px; background: var(--c-bg); color: var(--c-fg); }
  .g-titolo { flex: 1; min-width: 0; font-weight: 700; font-size: var(--text-sm); }
  .g-sotto { font-size: var(--text-xs); font-weight: 600; }
  .liv { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
  .g-persone { display: flex; flex-wrap: wrap; gap: var(--space-6); padding: var(--space-10) var(--space-14) var(--space-10) 18px; }
  .persona { display: inline-flex; align-items: center; gap: 7px; padding: 3px 10px 3px 3px; border: none; border-radius: var(--radius-pill); background: var(--surface);
    box-shadow: inset 0 0 0 1px var(--line); font-size: var(--text-sm); cursor: pointer; max-width: 100%; }
  .persona .iniziali { width: 24px; height: 24px; font-size: 10px; background: var(--c-line, var(--line-strong)); color: var(--surface); }
  .g-nota { margin: 0; padding: 0 var(--space-14) var(--space-10) 18px; font-size: var(--text-xs); color: var(--muted); }

  /* linea del tempo */
  .tempo { padding: var(--space-12) var(--space-16) var(--space-8); }
  .quarto { display: grid; grid-template-columns: 46px minmax(0, 1fr); column-gap: var(--space-10); }
  .q-ora { font-size: var(--text-xs); font-weight: 700; color: var(--muted); padding-top: 9px; text-align: right; }
  .q-voci { border-left: 2px solid var(--line); padding: 2px 0 var(--space-12) var(--space-16); display: flex; flex-direction: column; gap: 3px; }
  .q-voce { position: relative; }
  .nodo { position: absolute; left: -23px; top: 11px; width: 12px; height: 12px; border-radius: 50%; background: var(--passa); box-shadow: 0 0 0 3px var(--surface); }
  .nodo.fermati { background: var(--ferma); }
  .nodo.uscite { background: var(--surface); box-shadow: 0 0 0 3px var(--surface), inset 0 0 0 2px var(--line-strong); }
  .q-btn { width: 100%; display: flex; flex-direction: column; gap: 4px; padding: 6px 10px; border: none; border-radius: var(--radius-sm); background: transparent; cursor: pointer; }
  .q-btn:hover { background: var(--carta); }
  .q-btn.fermati { background: var(--ferma-bg); box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--ferma) 35%, transparent); }
  .q-btn.uscite .nome { color: var(--muted); }
  .q-riga { display: flex; align-items: baseline; gap: var(--space-8); }
  .q-riga .nome { flex: 1; font-size: var(--text-sm); }
  .q-sotto { display: flex; align-items: center; gap: var(--space-6); flex-wrap: wrap; }
  .q-sotto:empty { display: none; }
</style>
