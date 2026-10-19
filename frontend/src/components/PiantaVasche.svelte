<script lang="ts">
  import type { FasciaOraria, Occorrenza, SpazioPiano } from '../lib/api';
  import { ora } from '../lib/date';
  import { colore, minuti, pianta, STATI, type Fascia } from '../lib/piano';

  /**
   * Le vasche viste dall'alto in una fascia: una striscia per corsia con il corso che la occupa (colore del
   * livello, istruttore, età), le corsie libere, la vasca piccola come riquadro a parte.
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
  <button class="scheda {colore(o.categoria)}" class:spento={spento(o)} class:conflitto={o.conflitti.length > 0}
          style={o.colore ? `--livello: ${o.colore}` : ''} onclick={() => apri(o)}>
    <span class="riga1">
      <span class="tit">{o.titolo}</span>
      {#if o.conflitti.length}<span class="avviso" aria-label="si sovrappone"></span>{/if}
    </span>
    <span class="riga2">
      {#if spento(o)}{STATI[o.stato]}{:else}{[o.istruttore?.nome, o.dettaglio].filter(Boolean).join(' · ')}{/if}
    </span>
    {#if fuoriOrario(o) || o.iscritti != null}
      <span class="riga3">
        {#if o.iscritti != null}
          <span class="posti m" class:pieno={o.posti != null && o.iscritti >= o.posti}>{o.iscritti}{o.posti != null ? `/${o.posti}` : ''} iscritti</span>
        {/if}
        {#if fuoriOrario(o)}<span class="m orario">{ora(o.inizio)}–{ora(o.fine)}</span>{/if}
      </span>
    {/if}
  </button>
  {#if iscritti && o.lezioneId && vedeIscritti(o)}
    <button class="lista" onclick={() => iscritti(o)} aria-label="Iscritti di {o.titolo}" title="Iscritti e presenze">
      <svg class="ic" viewBox="0 0 24 24" aria-hidden="true"><path d="M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM2 21a7 7 0 0 1 14 0M17 11h5M17 15h5M19 7h3" /></svg>
    </button>
  {/if}
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
                  <button class="libera" onclick={() => libera(c.id)}>Corsia libera <span aria-hidden="true">+</span></button>
                {:else}
                  <span class="libera">Corsia libera</span>
                {/if}
              {/if}
            </div>
          {/each}
          {#each v.pezzi as p (p.occorrenza.chiave + ':' + p.da)}
            <div class="pezzo" class:con-lista={!!iscritti && !!p.occorrenza.lezioneId && vedeIscritti(p.occorrenza)} style="grid-row: {p.da + 1} / span {p.quante}; grid-column: {2 + p.colonna} / span {p.colonne === 1 ? 'var(--colonne)' : 1}">
              {@render scheda(p.occorrenza)}
            </div>
          {/each}
        </div>
      {:else}
        <div class="acqua piccola">
          {#each v.pezzi as p (p.occorrenza.chiave)}<div class="pezzo">{@render scheda(p.occorrenza)}</div>{:else}
            {#if libera}<button class="libera" onclick={() => libera(v.id)}>Libera <span aria-hidden="true">+</span></button>{:else}<span class="libera">Libera</span>{/if}
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
  .corsia { grid-column: 2 / -1; border-bottom: 2px dashed color-mix(in srgb, var(--cloro) 35%, transparent); display: flex; align-items: center; padding: 0 var(--space-12); }
  .corsia.ultima, .acqua > .numero:nth-last-child(n) { border-bottom-style: dashed; }
  .libera { font-size: var(--text-sm); color: var(--muted); background: none; border: none; font: inherit; font-size: var(--text-sm); padding: var(--space-6) 0; }
  button.libera { cursor: pointer; display: inline-flex; gap: var(--space-6); }
  button.libera:hover { color: var(--cloro); }
  .pezzo { padding: var(--space-6); display: flex; gap: 2px; z-index: 1; min-width: 0; }
  .lista {
    flex-shrink: 0; width: 38px; border: none; border-radius: var(--radius-sm); background: var(--surface); color: var(--cloro);
    box-shadow: 0 1px 2px rgba(16, 33, 43, 0.12); display: flex; align-items: center; justify-content: center; cursor: pointer;
  }
  .lista:hover { background: var(--cloro-soft); }
  .lista .ic { width: 18px; height: 18px; }
  .assetto { padding: 2px; }
  .assetto button { min-height: 28px; padding: 0 var(--space-10); font-size: var(--text-xs); }
  .riga3 { display: flex; gap: var(--space-6); align-items: center; margin-top: 2px; }
  .posti { font-size: var(--text-2xs); color: var(--cloro-ink); background: var(--cloro-soft); padding: 0 var(--space-6); border-radius: var(--radius-pill); }
  .posti.pieno { color: var(--warn); background: var(--warn-bg); }
  .acqua.piccola { display: flex; flex-wrap: wrap; gap: var(--space-8); padding: var(--space-8); min-height: 64px; align-items: stretch; }
  .acqua.piccola > .pezzo { flex: 1 1 260px; padding: 0; }
  .altrove { display: flex; flex-wrap: wrap; gap: var(--space-8); }
  .altrove > .pezzo { flex: 1 1 260px; padding: 0; }

  .scheda {
    --livello: var(--c-line);
    flex: 1; display: flex; flex-direction: column; align-items: flex-start; justify-content: center; gap: 1px; min-width: 0; min-height: 50px;
    padding: var(--space-6) var(--space-10) var(--space-6) var(--space-14); border: none; border-radius: var(--radius-sm);
    background: var(--surface); color: var(--ink); font: inherit; text-align: left; cursor: pointer; position: relative;
    box-shadow: 0 1px 2px rgba(16, 33, 43, 0.12), inset 6px 0 0 var(--livello);
  }
  .scheda:hover { box-shadow: 0 2px 6px rgba(16, 33, 43, 0.18), inset 6px 0 0 var(--livello); }
  .riga1 { display: flex; align-items: center; gap: var(--space-6); max-width: 100%; }
  .tit { font-weight: 700; font-size: var(--text-md); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .riga2 { font-size: var(--text-xs); color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
  .orario { font-size: var(--text-2xs); color: var(--c-fg); background: var(--c-bg); padding: 0 var(--space-6); border-radius: var(--radius-pill); }
  .spento { opacity: 0.6; }
  .spento .tit { text-decoration: line-through; }
  .conflitto { outline: 2px solid var(--danger); outline-offset: -2px; }
  .avviso { width: 13px; height: 13px; flex-shrink: 0; background: var(--danger); -webkit-mask: var(--icona-avviso) center / contain no-repeat; mask: var(--icona-avviso) center / contain no-repeat; }
</style>
