<script lang="ts">
  import type { PersonaBreve, Postazione } from '../lib/api';
  import { formatH, gruppi, nomeCorto, type Gruppo } from '../lib/gestione';
  import { oreTra } from '../lib/date';

  /**
   * La settimana per postazione: una riga per postazione, raggruppate per area (con la fascia colorata), una colonna
   * per giorno. Una postazione è una persona alla volta: il «+» compare solo nelle caselle libere e, per una persona in
   * più, si aggiunge una postazione al gruppo. Serve alla pianificazione e alla settimana tipo.
   */
  export interface Colonna {
    chiave: string;
    /** «lun» */
    giorno: string;
    /** il numero del giorno (nella settimana tipo non c'è) */
    numero?: number;
    oggi?: boolean;
  }
  export interface Blocco {
    id: string;
    postazioneId: string;
    colonna: string;
    inizio: string;
    fine: string;
    persona: PersonaBreve | null;
    /** C'è una richiesta aperta su questo turno: un pallino da notifica sull'angolo. */
    notifica?: string;
  }

  let { colonne, postazioni, blocchi, scelto = '', evidenzia = '', oreRiga = false, etichetta, apri, nuovo, nuovaPostazione }: {
    colonne: Colonna[];
    postazioni: Postazione[];
    blocchi: Blocco[];
    scelto?: string;
    /** Id della persona da evidenziare: gli altri turni si spengono. */
    evidenzia?: string;
    /** Le ore della settimana sotto il nome della postazione. */
    oreRiga?: boolean;
    etichetta: string;
    apri: (id: string) => void;
    nuovo: (postazioneId: string, colonna: string) => void;
    nuovaPostazione?: (g: Gruppo) => void;
  } = $props();

  const elenco = $derived(gruppi(postazioni));
  const perCella = $derived.by(() => {
    const m = new Map<string, Blocco[]>();
    for (const b of blocchi) {
      const k = `${b.postazioneId}|${b.colonna}`;
      m.set(k, [...(m.get(k) ?? []), b].sort((x, y) => x.inizio.localeCompare(y.inizio)));
    }
    return m;
  });
  const hhmm = (s: string) => s.slice(0, 5);
  const oreDi = (l: Blocco[]) => l.reduce((s, b) => s + oreTra(b.inizio, b.fine), 0);
  const oreColonna = (c: string) => oreDi(blocchi.filter((b) => b.colonna === c));
  const orePostazione = (id: string) => oreDi(blocchi.filter((b) => b.postazioneId === id));
</script>

<div class="card scorre">
  <div class="gr" role="grid" aria-label={etichetta} style="--n: {colonne.length}">
    <div class="riga-gr" role="row">
      <span class="th primo" role="columnheader"><span class="lbl">Postazione</span></span>
      {#each colonne as c (c.chiave)}
        <span class="th" class:oggi={c.oggi} role="columnheader">
          {#if c.numero}<span class="lbl">{c.giorno}</span><b>{c.numero}</b>{:else}<b class="solo">{c.giorno}</b>{/if}
          <span class="muted m ore-g">{formatH(oreColonna(c.chiave))}</span>
        </span>
      {/each}
    </div>
    {#each elenco as g (g.chiave)}
      <div class="area-t ar-{g.area}" role="row"><span role="rowheader"><i></i>{g.nome}</span></div>
      {#each g.righe as r (r.postazione.id)}
        <div class="riga-gr" role="row">
          <div class="nome-r" role="rowheader" title={r.postazione.nome}>
            <span><b>{r.etichetta}</b></span>
            {#if oreRiga}<span class="muted m ore-r">{formatH(orePostazione(r.postazione.id))}</span>{/if}
          </div>
          {#each colonne as c (c.chiave)}
            {@const qui = perCella.get(`${r.postazione.id}|${c.chiave}`) ?? []}
            <div class="cella" class:oggi={c.oggi} role="gridcell">
              {#each qui as b (b.id)}
                <button type="button" class="blk ar-{g.area}" class:vuoto={!b.persona} class:scelto={scelto === b.id}
                  class:spento={!!evidenzia && b.persona?.id !== evidenzia} onclick={() => apri(b.id)}
                  aria-label={`${r.postazione.nome}, ${c.giorno} ${c.numero ?? ''}, ${hhmm(b.inizio)}–${hhmm(b.fine)}, ${b.persona?.nome ?? 'scoperto'}${b.notifica ? `, ${b.notifica}` : ''}`}>
                  <span class="o">{hhmm(b.inizio)}–{hhmm(b.fine)}</span>
                  <span class="p">{b.persona ? nomeCorto(b.persona.nome) : 'Scoperto'}</span>
                  {#if b.notifica}<span class="notifica" title={b.notifica}></span>{/if}
                </button>
              {/each}
              {#if !qui.length}
                <button type="button" class="piu-c" aria-label={`Nuovo turno: ${r.postazione.nome}, ${c.giorno} ${c.numero ?? ''}`} onclick={() => nuovo(r.postazione.id, c.chiave)}>+</button>
              {/if}
            </div>
          {/each}
        </div>
      {/each}
      {#if nuovaPostazione}
        <div class="riga-gr nuova" role="row">
          <button type="button" class="nuova-post" onclick={() => nuovaPostazione(g)}><b>+</b>Aggiungi una postazione a {g.nome[0].toLowerCase() + g.nome.slice(1)}</button>
        </div>
      {/if}
    {:else}
      <p class="muted small nessuna">Nessuna postazione: aggiungile da «Mansioni e postazioni».</p>
    {/each}
  </div>
</div>

<style>
  .scorre { padding: 0; overflow-x: auto; }
  .gr { min-width: calc(124px + var(--n) * 86px); }
  .riga-gr { display: grid; grid-template-columns: 124px repeat(var(--n), minmax(0, 1fr)); }
  .th { padding: var(--space-10) var(--space-8); display: flex; flex-direction: column; align-items: center; line-height: 1.15; border-bottom: 1px solid var(--line); }
  .th.primo { align-items: flex-start; justify-content: flex-end; padding-left: var(--space-12); }
  .th b { font-family: var(--font-display); font-size: 20px; font-weight: 800; }
  .th b.solo { font-size: var(--text-lg); }
  .th.oggi { background: var(--cloro-soft); color: var(--cloro-ink); }
  .th.oggi .lbl { color: var(--cloro-ink); }
  .ore-g { font-size: 11px; }
  /* fascia dell'area: tinta del suo colore, con il filo a sinistra */
  .area-t { display: flex; align-items: center; padding: var(--space-10) var(--space-12) var(--space-6); font-size: var(--text-xs); font-weight: 700; letter-spacing: 0.06em;
    text-transform: uppercase; color: var(--a-fg); background: var(--a-bg); border-top: 1px solid var(--line); box-shadow: inset 4px 0 0 var(--a); }
  .area-t span { display: inline-flex; align-items: center; gap: var(--space-8); }
  .area-t i { width: 10px; height: 10px; border-radius: 3px; background: var(--a); display: inline-block; }
  .nome-r { padding: var(--space-8) var(--space-12); font-size: var(--text-md); display: flex; flex-direction: column; justify-content: center; gap: 1px; border-top: 1px solid var(--line-soft); min-width: 0; }
  .nome-r span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .ore-r { font-size: var(--text-xs); }
  .cella { padding: 3px; border-top: 1px solid var(--line-soft); border-left: 1px solid var(--line-soft); display: flex; flex-direction: column; gap: 4px; min-height: 52px; min-width: 0; }
  .cella.oggi { background: color-mix(in srgb, var(--cloro) 5%, transparent); }
  .blk { position: relative; display: flex; flex-direction: column; gap: 1px; padding: 5px 6px; border-radius: 8px; border: none; background: var(--a-bg); color: var(--a-fg);
    font: inherit; text-align: left; cursor: pointer; min-width: 0; }
  .blk .o { font-family: var(--font-mono); font-size: 10.5px; letter-spacing: -0.02em; white-space: nowrap; overflow: hidden; }
  .blk .p { font-size: var(--text-sm); font-weight: 700; color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  /* scoperto: bianco a righe rosse con il bordo rosso, per non confondersi con nessun colore di area */
  .blk.vuoto { background: repeating-linear-gradient(135deg, var(--surface) 0 7px, var(--ferma-bg) 7px 10px); color: var(--ferma-fg); box-shadow: inset 0 0 0 2px var(--ferma); }
  .blk.vuoto .p { color: var(--ferma-fg); }
  .blk.scelto { box-shadow: inset 0 0 0 2px var(--a-fg); }
  .blk.vuoto.scelto { box-shadow: inset 0 0 0 3px var(--ferma); }
  .blk.spento { opacity: 0.28; }
  .notifica { position: absolute; top: -4px; right: -4px; width: 11px; height: 11px; border-radius: 50%; background: var(--warn); box-shadow: 0 0 0 2px var(--surface); }
  .piu-c { flex: 1; min-height: 20px; border: 1px dashed transparent; border-radius: 8px; background: none; color: transparent; font: inherit; font-size: 18px; cursor: pointer; }
  .cella:hover .piu-c, .piu-c:focus-visible { border-color: var(--line-dashed); color: var(--muted); }
  .riga-gr.nuova { display: block; }
  .nuova-post { display: inline-flex; align-items: center; gap: var(--space-6); margin: var(--space-6) var(--space-12) var(--space-10); padding: 5px 12px 5px 8px;
    border: 1px dashed color-mix(in srgb, var(--cloro) 55%, transparent); border-radius: var(--radius-pill); background: var(--surface); font: inherit; font-size: var(--text-sm);
    font-weight: 600; color: var(--cloro); cursor: pointer; white-space: nowrap; }
  .nuova-post:hover { background: var(--cloro-soft); border-style: solid; }
  .nuova-post b { width: 18px; height: 18px; border-radius: 50%; display: grid; place-items: center; background: var(--cloro-soft); font-size: 14px; line-height: 1; }
  .nessuna { padding: var(--space-16); margin: 0; }
</style>
