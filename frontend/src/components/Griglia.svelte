<script lang="ts">
  import type { Postazione } from '../lib/api';
  import { ora } from '../lib/date';
  import type { Colonna, VoceGriglia } from '../lib/griglia';

  /**
   * La settimana come nel vecchio Excel, ma viva: una riga per postazione (raggruppate per mansione),
   * una colonna per giorno. Un clic su una voce la apre, su una cella vuota ne crea una lì.
   * Serve sia ai turni (giorni del calendario) sia alla settimana tipo (lunedì … domenica).
   */
  let { colonne, postazioni, voci, evidenzia = '', etichetta, apri, nuovo }: {
    colonne: Colonna[];
    postazioni: Postazione[];
    voci: VoceGriglia[];
    /** Id della persona da mettere in evidenza. */
    evidenzia?: string;
    etichetta: string;
    apri: (id: string) => void;
    nuovo: (postazioneId: string, colonna: string) => void;
  } = $props();

  // Righe: le postazioni attive, più quelle (disattivate) che hanno ancora turni nella settimana
  const righe = $derived.by(() => {
    const note = new Set(postazioni.map((p) => p.id));
    const extra = new Map<string, Postazione>();
    for (const t of voci) {
      if (!note.has(t.postazioneId) && !extra.has(t.postazioneId))
        extra.set(t.postazioneId, { id: t.postazioneId, nome: t.postazione, mansione: t.mansione, impiantoId: t.impiantoId });
    }
    const tutte = [...postazioni, ...extra.values()];
    const gruppi: { mansione: string; postazioni: Postazione[] }[] = [];
    for (const p of tutte) {
      let g = gruppi.find((x) => x.mansione === p.mansione);
      if (!g) gruppi.push((g = { mansione: p.mansione, postazioni: [] }));
      g.postazioni.push(p);
    }
    return gruppi;
  });

  const perCella = $derived.by(() => {
    const m = new Map<string, VoceGriglia[]>();
    for (const t of voci) {
      const k = `${t.postazioneId}|${t.colonna}`;
      const l = m.get(k);
      if (l) l.push(t);
      else m.set(k, [t]);
    }
    return m;
  });

  const scopertiDel = (c: string) => voci.filter((t) => t.colonna === c && !t.annullato && !t.persona).length;
</script>

<div class="griglia card" role="grid" aria-label={etichetta} style="--giorni: {colonne.length}">
  <div class="riga testa" role="row">
    <div class="cella nome" role="columnheader"><span class="lbl">Postazione</span></div>
    {#each colonne as c (c.chiave)}
      {@const s = scopertiDel(c.chiave)}
      <div class="cella giorno" class:oggi={c.oggi} role="columnheader">
        <span class="lbl">{c.etichetta}</span>{#if c.numero}<span class="d num">{c.numero}</span>{/if}
        {#if s}<span class="tag t-warn scop" title="{s} da assegnare">{s}</span>{/if}
      </div>
    {/each}
  </div>

  {#each righe as gruppo (gruppo.mansione)}
    <div class="riga mansione" role="row"><div class="cella" role="rowheader">{gruppo.mansione}</div></div>
    {#each gruppo.postazioni as p (p.id)}
      <div class="riga" role="row">
        <div class="cella nome" role="rowheader" title={p.nome}>{p.nome}</div>
        {#each colonne as c (c.chiave)}
          {@const qui = perCella.get(`${p.id}|${c.chiave}`) ?? []}
          <div class="cella turni" class:oggi={c.oggi} role="gridcell">
            {#each qui as t (t.id)}
              {@const scoperto = !t.persona && !t.annullato}
              <button
                class="turno"
                class:scoperto
                class:annullato={t.annullato}
                class:evidenziato={!!evidenzia && t.persona?.id === evidenzia}
                class:spento={!!evidenzia && t.persona?.id !== evidenzia}
                disabled={t.annullato}
                onclick={() => apri(t.id)}
                title={t.note ?? undefined}
              >
                <span class="m orario">{ora(t.inizio)}–{ora(t.fine)}</span>
                <span class="chi">{t.annullato ? 'annullato' : scoperto ? 'Da assegnare' : t.persona?.nome}</span>
              </button>
            {/each}
            <button class="aggiungi" aria-label="Aggiungi: {p.nome}, {c.etichetta} {c.numero ?? ''}" onclick={() => nuovo(p.id, c.chiave)}>+</button>
          </div>
        {/each}
      </div>
    {/each}
  {:else}
    <p class="muted small vuoto">Nessuna postazione: aggiungile da «Mansioni e postazioni».</p>
  {/each}
</div>

<style>
  .griglia { overflow-x: auto; }
  .riga { display: grid; grid-template-columns: 210px repeat(var(--giorni), minmax(124px, 1fr)); min-width: calc(210px + var(--giorni) * 124px); }
  .cella { padding: var(--space-6) var(--space-8); border-top: 1px solid var(--line-soft); border-left: 1px solid var(--line-soft); min-width: 0; }
  .cella:first-child { border-left: none; }
  .testa .cella { border-top: none; position: sticky; top: 0; background: var(--surface); z-index: 1; display: flex; align-items: baseline; gap: var(--space-6); padding-top: var(--space-10); padding-bottom: var(--space-10); }
  .num { font-size: var(--text-xl); font-weight: 800; }
  .giorno.oggi .num { color: var(--cloro); }
  .scop { height: 20px; margin-left: auto; }
  .mansione .cella { grid-column: 1 / -1; background: var(--surface-sunken); font-size: var(--text-xs); font-weight: 700; letter-spacing: 0.04em; text-transform: uppercase; color: var(--muted); padding: var(--space-4) var(--space-8); }
  .nome { font-size: var(--text-sm); font-weight: 600; line-height: 1.25; overflow-wrap: anywhere; align-self: center; border-top: none; }
  .riga:not(.testa):not(.mansione) .nome { border-top: 1px solid var(--line-soft); align-self: stretch; display: flex; align-items: center; }
  .turni { display: flex; flex-direction: column; gap: var(--space-4); min-height: 50px; position: relative; }
  .turni.oggi { background: color-mix(in srgb, var(--cloro-soft) 35%, transparent); }
  .turno {
    display: flex; flex-direction: column; align-items: flex-start; gap: 1px; width: 100%; padding: var(--space-4) var(--space-6);
    border: 1px solid var(--line); border-radius: var(--radius-xs); background: var(--surface); color: var(--ink); font: inherit; text-align: left; cursor: pointer;
  }
  .turno:hover:not(:disabled) { border-color: var(--line-strong); }
  .orario { font-size: var(--text-xs); color: var(--muted); }
  .chi { font-size: var(--text-sm); font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
  .scoperto { border-style: dashed; border-color: var(--warn-line); background: var(--warn-bg); }
  .scoperto .chi { color: var(--warn); }
  .annullato { opacity: 0.55; cursor: default; }
  .annullato .orario, .annullato .chi { text-decoration: line-through; }
  .evidenziato { border-color: var(--cloro); box-shadow: inset 3px 0 0 var(--cloro); }
  .spento { opacity: 0.4; }
  /* «+»: nella cella vuota la occupa tutta; accanto a un turno è un tastino in basso a destra. Compare al passaggio del mouse. */
  .aggiungi {
    position: absolute; right: 4px; bottom: 4px; width: 22px; height: 22px; padding: 0; border: 1px dashed var(--line-dashed);
    border-radius: var(--radius-xs); background: var(--surface); color: var(--muted); font-size: var(--text-md); line-height: 1; cursor: pointer; opacity: 0;
  }
  .turni:not(:has(.turno)) .aggiungi { inset: 4px; width: auto; height: auto; background: none; }
  .turni:hover .aggiungi, .aggiungi:focus-visible { opacity: 1; }
  .aggiungi:hover { border-color: var(--cloro); color: var(--cloro); }
  .vuoto { padding: var(--space-16); margin: 0; }
</style>
