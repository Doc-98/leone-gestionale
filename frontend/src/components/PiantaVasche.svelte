<script lang="ts">
  import type { FasciaOraria, Occorrenza, SpazioPiano } from '../lib/api';
  import { ora } from '../lib/date';
  import { colore, minuti, nomeCategoria, pianta, STATI, type Fascia } from '../lib/piano';
  import Icona from './Icona.svelte';

  /**
   * Le vasche viste dall'alto in una fascia: una striscia per corsia con il corso che la occupa, nella tinta della sua
   * categoria (con il pallino del colore del livello, istruttore, età, iscritti), le corsie libere, la vasca piccola come
   * riquadro a parte. Chi si sovrappone lo dice a parole.
   */
  let { spazi, occorrenze, fascia, definite = new Map(), compatta = false, apri, libera, iscritti, vedeIscritti = () => true, cambiaAssetto }: {
    spazi: SpazioPiano[];
    occorrenze: Occorrenza[];
    fascia: Fascia;
    /** per ogni vasca, la fascia definita dai coordinatori (dà l'assetto delle corsie) */
    definite?: Map<string, FasciaOraria>;
    /** nel pannello laterale o sul telefono: niente intestazioni grandi */
    compatta?: boolean;
    apri: (o: Occorrenza) => void;
    /** clic su una corsia libera (per i coordinatori: nuovo turno o evento lì) */
    libera?: (spazioId: string) => void;
    /** apre la lista degli iscritti (solo per chi la può vedere) */
    iscritti?: (o: Occorrenza) => void;
    /** per quali turni mostrare il pulsante della lista */
    vedeIscritti?: (o: Occorrenza) => boolean;
    /** coordinatori: passa la fascia a un altro assetto */
    cambiaAssetto?: (f: FasciaOraria, assetto: number) => void;
  } = $props();

  const disegno = $derived(pianta(spazi, occorrenze, new Map([...definite].filter(([, f]) => f.assetto != null).map(([v, f]) => [v, f.assetto!]))));
  const assettiDi = (vascaId: string) => [...new Set(spazi.filter((s) => s.padreId === vascaId && s.assetto != null).map((s) => s.assetto!))].sort((a, b) => b - a);
  const chiusure = $derived(occorrenze.filter((o) => o.categoria === 'CHIUSURA' && o.stato !== 'ANNULLATO'));
  const fuoriOrario = (o: Occorrenza) => minuti(o.inizio) !== fascia.inizio || minuti(o.fine) !== fascia.fine;
  const spento = (o: Occorrenza) => o.stato !== 'REGOLARE' && o.stato !== 'SPOSTATA_QUI';
</script>

{#snippet scheda(o: Occorrenza)}
  {@const pieno = o.posti != null && o.iscritti != null && o.iscritti >= o.posti}
  <div class="lez {colore(o.categoria)}" class:spento={spento(o)} class:conflitto={o.conflitti.length > 0}>
    <button class="apri" onclick={() => apri(o)}>
      <span class="riga1">
        {#if o.colore}<span class="livello" style="background: {o.colore}" aria-hidden="true"></span>{/if}
        <span class="tit">{o.titolo}</span>
      </span>
      <span class="riga2">
        {#if spento(o)}{STATI[o.stato]}{:else}{[compatta ? '' : nomeCategoria(o), o.istruttore?.nome, o.dettaglio].filter(Boolean).join(' · ')}{/if}
      </span>
      {#if o.conflitti.length}<span class="insieme"><span class="avviso" aria-hidden="true"></span>Insieme a {o.conflitti.join(', ')}</span>{/if}
    </button>
    {#if fuoriOrario(o)}<span class="m orario">{ora(o.inizio)}–{ora(o.fine)}</span>{/if}
    {#if o.iscritti != null}
      <span class="posti m" class:pieno title={pieno ? 'Al completo' : undefined}>{o.iscritti}{o.posti != null ? `/${o.posti}` : ''}</span>
    {/if}
    {#if iscritti && o.lezioneId && vedeIscritti(o)}
      <button class="azione" onclick={() => iscritti(o)} aria-label="Iscritti di {o.titolo}" title="Iscritti e presenze"><Icona nome="persone" /></button>
    {/if}
  </div>
{/snippet}

<div class="pianta" class:compatta>
  {#each chiusure as c (c.chiave)}<p class="chiusura">{c.titolo}</p>{/each}

  {#each disegno.vasche as v (v.id)}
    <section class="vasca" class:con-corsie={v.corsie.length > 0}>
      <header>
        <span class="nome">{v.nome}</span>
        {#if v.assetto && cambiaAssetto && definite.get(v.id) && assettiDi(v.id).length > 1}
          {@const def = definite.get(v.id)!}
          <div class="seg assetto" style="grid-template-columns: repeat({assettiDi(v.id).length}, auto)" role="group" aria-label="Corsie in questa fascia">
            {#each assettiDi(v.id) as a (a)}
              <button aria-pressed={def.assetto === a} onclick={() => def.assetto !== a && cambiaAssetto(def, a)}>{a} corsie</button>
            {/each}
          </div>
        {:else if v.assetto}<span class="tag t-soft">{v.assetto} corsie</span>{/if}
        {#if v.assettiMisti}<span class="tag t-warn">corsie divise in due modi diversi</span>{/if}
      </header>
      {#if v.corsie.length}
        <div class="acqua" style="--corsie: {v.corsie.length}; --colonne: {Math.max(1, ...v.pezzi.map((p) => p.colonne))}">
          {#each v.corsie as c, i (c.id)}
            <div class="numero" style="grid-row: {i + 1}">{c.numero}</div>
            <div class="corsia" style="grid-row: {i + 1}" class:ultima={i === v.corsie.length - 1}>
              {#if !v.pezzi.some((p) => i >= p.da && i < p.da + p.quante)}
                {#if libera}
                  <button class="libera" onclick={() => libera(c.id)}><Icona nome="piu" />Corsia libera: aggiungi un turno o un evento</button>
                {:else}
                  <span class="libera">Corsia libera</span>
                {/if}
              {/if}
            </div>
          {/each}
          {#each v.pezzi as p (p.occorrenza.chiave + ':' + p.da)}
            <div class="pezzo" style="grid-row: {p.da + 1} / span {p.quante}; grid-column: {2 + p.colonna} / span {p.colonne === 1 ? 'var(--colonne)' : 1}">
              {@render scheda(p.occorrenza)}
            </div>
          {/each}
        </div>
      {:else}
        <div class="acqua piccola">
          {#each v.pezzi as p (p.occorrenza.chiave)}<div class="pezzo">{@render scheda(p.occorrenza)}</div>{:else}
            {#if libera}<button class="libera" onclick={() => libera(v.id)}><Icona nome="piu" />Libera: aggiungi un turno o un evento</button>{:else}<span class="libera">Libera</span>{/if}
          {/each}
        </div>
      {/if}
    </section>
  {/each}

  {#if disegno.altrove.length}
    <section class="vasca">
      <header><span class="nome">Senza uno spazio indicato</span></header>
      <div class="altrove">{#each disegno.altrove as o (o.chiave)}<div class="pezzo">{@render scheda(o)}</div>{/each}</div>
    </section>
  {/if}
</div>

<style>
  .pianta { display: flex; flex-direction: column; gap: var(--space-16); }
  .chiusura { margin: 0; padding: var(--space-10) var(--space-14); border-radius: var(--radius-md); border: 1px dashed var(--line-dashed);
    background: repeating-linear-gradient(135deg, var(--surface-sunken) 0 8px, transparent 8px 16px); font-weight: 700; color: var(--muted); }
  .vasca { display: flex; flex-direction: column; gap: var(--space-8); }
  header { display: flex; align-items: center; gap: var(--space-8); flex-wrap: wrap; }
  .nome { font-family: var(--font-display); font-weight: 800; font-size: var(--text-xl); }
  .compatta .nome { font-size: var(--text-lg); }
  header .tag { height: 22px; }

  /* L'acqua: corsie orizzontali con i galleggianti tratteggiati in mezzo */
  .acqua {
    --acqua: color-mix(in srgb, var(--cloro-soft) 70%, var(--surface));
    display: grid; grid-template-columns: 34px repeat(var(--colonne), minmax(0, 1fr)); grid-template-rows: repeat(var(--corsie), minmax(64px, auto));
    background: var(--acqua); border: 2px solid color-mix(in srgb, var(--cloro) 45%, var(--line)); border-radius: var(--radius-md); overflow: hidden;
  }
  .compatta .acqua { grid-template-rows: repeat(var(--corsie), minmax(54px, auto)); }
  .numero {
    grid-column: 1; display: flex; align-items: center; justify-content: center; font-family: var(--font-mono); font-weight: 600;
    color: var(--cloro-ink); background: color-mix(in srgb, var(--cloro) 18%, var(--acqua)); border-bottom: 2px dashed color-mix(in srgb, var(--cloro) 35%, transparent);
  }
  .corsia { grid-column: 2 / -1; border-bottom: 2px dashed color-mix(in srgb, var(--cloro) 35%, transparent); display: flex; align-items: center; padding: var(--space-6); }
  .corsia.ultima, .acqua > .numero:nth-last-child(n) { border-bottom-style: dashed; }
  .libera { font-size: var(--text-sm); color: var(--muted); background: none; border: none; font: inherit; font-size: var(--text-sm); padding: var(--space-6) 0; }
  button.libera { cursor: pointer; display: flex; align-items: center; gap: var(--space-6); width: 100%; min-height: 44px; padding: 0 var(--space-12);
    border: 1.5px dashed color-mix(in srgb, var(--cloro) 40%, transparent); border-radius: var(--radius-md); color: var(--cloro-ink); }
  button.libera:hover { background: color-mix(in srgb, var(--surface) 60%, transparent); }
  .pezzo { padding: var(--space-6); display: flex; gap: 2px; z-index: 1; min-width: 0; }
  .assetto { padding: 2px; }
  .assetto button { min-height: 28px; padding: 0 var(--space-10); font-size: var(--text-xs); }
  .acqua.piccola { display: flex; flex-wrap: wrap; gap: var(--space-8); padding: var(--space-8); min-height: 64px; align-items: stretch; }
  .acqua.piccola > .pezzo { flex: 1 1 260px; padding: 0; }
  .altrove { display: flex; flex-wrap: wrap; gap: var(--space-8); }
  .altrove > .pezzo { flex: 1 1 260px; padding: 0; }

  /* La lezione: tutta nella tinta della sua categoria (non una barra a sinistra) */
  .lez { flex: 1; display: flex; align-items: center; gap: var(--space-8); min-width: 0; min-height: 50px; padding: 0 var(--space-4) 0 0;
    border-radius: var(--radius-md); background: var(--c-bg); color: var(--c-fg); border: 1px solid rgba(16, 33, 43, 0.06); }
  .apri { flex: 1; min-width: 0; align-self: stretch; display: flex; flex-direction: column; justify-content: center; gap: 1px;
    padding: var(--space-6) 0 var(--space-6) var(--space-12); border: none; background: none; color: inherit; font: inherit; text-align: left; cursor: pointer; border-radius: var(--radius-md); }
  .apri:hover .tit { text-decoration: underline; }
  .riga1 { display: flex; align-items: center; gap: var(--space-6); max-width: 100%; }
  .livello { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; box-shadow: 0 0 0 1px rgba(16, 33, 43, 0.25); }
  .tit { font-weight: 700; font-size: var(--text-md); color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .riga2 { font-size: var(--text-xs); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
  .insieme { display: inline-flex; align-items: center; gap: var(--space-4); font-size: var(--text-xs); font-weight: 600; color: var(--warn); }
  .posti { flex-shrink: 0; font-size: var(--text-xs); padding: 2px var(--space-8); border-radius: var(--radius-pill); background: color-mix(in srgb, var(--surface) 75%, transparent); color: var(--ink); }
  .posti.pieno { background: var(--warn-bg); color: var(--warn); }
  .orario { flex-shrink: 0; font-size: var(--text-2xs); padding: 2px var(--space-6); border-radius: var(--radius-pill); background: color-mix(in srgb, var(--surface) 75%, transparent); color: var(--ink); }
  .lez .azione { color: var(--c-fg); }
  .lez .azione:hover { background: color-mix(in srgb, var(--surface) 65%, transparent); color: var(--ink); }
  .spento { opacity: 0.6; }
  .spento .tit { text-decoration: line-through; }
  .conflitto { border: 2px solid var(--warn); }
  .avviso { width: 13px; height: 13px; flex-shrink: 0; background: var(--warn); -webkit-mask: var(--icona-avviso) center / contain no-repeat; mask: var(--icona-avviso) center / contain no-repeat; }
</style>
