<script lang="ts">
  import type { Occorrenza } from '../lib/api';
  import { colore, minuti, STATI, tratti as dividi, type Banda, type Intestazione, type Riquadro, type Tratto } from '../lib/piano';
  import { ora } from '../lib/date';

  /**
   * Griglia oraria: colonne (spazi o giorni) e righe da 5 minuti. I riquadri occupano le loro colonne e si dividono
   * la larghezza quando si sovrappongono; le chiusure sono bande tratteggiate sotto i riquadri.
   * Con «comprimi» le ore senza attività (almeno un'ora e mezza) diventano una striscia stretta, così la giornata
   * intera sta in uno schermo.
   */
  let { colonne, riquadri, bande = [], finestra, adesso = null, etichetta, sotto, apri, vuoto, comprimi = false }: {
    colonne: Intestazione[];
    riquadri: Riquadro[];
    bande?: Banda[];
    finestra: [number, number];
    /** minuti dalla mezzanotte, per la linea dell'ora attuale */
    adesso?: number | null;
    etichetta: string;
    /** testo secondario del riquadro (es. spazi o istruttore) */
    sotto: (o: Occorrenza) => string;
    apri: (o: Occorrenza) => void;
    /** clic in uno spazio vuoto: colonna e minuto (arrotondato al quarto d'ora) */
    vuoto?: (colonna: number, minuto: number) => void;
    comprimi?: boolean;
  } = $props();

  const PASSO = 5;
  const ALTEZZA = 4; // px per 5 minuti → 48 px l'ora: una giornata intera entra nello schermo
  const RIGHE_VUOTO = 7; // un tratto senza attività: 28 px
  const pezzi = $derived<Tratto[]>(comprimi
    ? dividi([...riquadri.map((r) => [minuti(r.occorrenza.inizio), minuti(r.occorrenza.fine)] as [number, number])], finestra)
    : [{ da: finestra[0], a: finestra[1], vuoto: false }]);
  const righeDi = (t: Tratto) => (t.vuoto ? RIGHE_VUOTO : (t.a - t.da) / PASSO);
  /** la prima riga (dopo le intestazioni, contando da 0) di ogni tratto */
  const basi = $derived(pezzi.reduce<number[]>((acc, t, i) => [...acc, i ? acc[i - 1] + righeDi(pezzi[i - 1]) : 0], []));
  const righe = $derived(pezzi.reduce((n, t) => n + righeDi(t), 0));
  /** quante righe dall'inizio della griglia per il minuto m */
  function posizione(m: number): number {
    for (let i = 0; i < pezzi.length; i++) {
      const t = pezzi[i];
      if (m <= t.a || i === pezzi.length - 1) {
        const x = Math.min(Math.max(m, t.da), t.a);
        return basi[i] + (t.vuoto ? (x <= t.da ? 0 : RIGHE_VUOTO) : Math.round((x - t.da) / PASSO));
      }
    }
    return 0;
  }
  // etichette delle ore: ogni ora piena dentro i tratti con attività, e l'inizio di un tratto che non cade sull'ora
  const ore = $derived(pezzi.flatMap((t, i) => {
    if (t.vuoto) return [];
    const out: { m: number; righe: number; prima: boolean }[] = [];
    let m = t.da;
    while (m < t.a) {
      const dopo = m % 60 ? Math.ceil(m / 60) * 60 : m + 60;
      if (m % 60 === 0 || m === t.da) out.push({ m, righe: (Math.min(dopo, t.a) - m) / PASSO, prima: i === 0 && m === t.da });
      m = dopo;
    }
    return out;
  }));
  const conSopra = $derived(colonne.some((c) => c.sopra));
  const testa = $derived(conSopra ? 2 : 1);

  // intestazioni «sopra» raggruppate (es. il nome della vasca sopra le sue corsie)
  const gruppi = $derived.by(() => {
    const out: { titolo: string; da: number; ampiezza: number }[] = [];
    colonne.forEach((c, i) => {
      const ultimo = out[out.length - 1];
      if (ultimo && ultimo.titolo === (c.sopra ?? '')) ultimo.ampiezza++;
      else out.push({ titolo: c.sopra ?? '', da: i, ampiezza: 1 });
    });
    return out;
  });

  const riga = (m: number) => testa + 1 + posizione(m);
  const rigaFine = (m: number) => testa + 1 + posizione(m);
  const hhmm = (m: number) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;

  // All'apertura (e quando cambiano i dati) la vista scorre fino alla prima attività
  let contenitore = $state<HTMLDivElement>();
  $effect(() => {
    const primo = riquadri.length ? Math.min(...riquadri.map((r) => minuti(r.occorrenza.inizio))) : null;
    if (!contenitore || primo == null) return;
    const y = posizione(primo - 30) * ALTEZZA;
    contenitore.scrollTo({ top: y });
  });

  function cliccaVuoto(e: MouseEvent, colonna: number, t: Tratto) {
    if (!vuoto) return;
    const m = t.da + Math.floor(e.offsetY / ALTEZZA / 3) * 15;
    vuoto(colonna, Math.min(m, finestra[1] - 60));
  }
</script>

<div class="scorre card" bind:this={contenitore}>
  <div class="griglia" role="grid" aria-label={etichetta}
       style="--colonne: {colonne.length}; --righe: {righe}; --testa: {testa}; --alt: {ALTEZZA}px">
    {#if conSopra}
      <div class="angolo" style="grid-row: 1 / span 2"></div>
      {#each gruppi as g (g.da)}
        <div class="sopra" style="grid-column: {g.da + 2} / span {g.ampiezza}">{g.titolo}</div>
      {/each}
    {:else}
      <div class="angolo"></div>
    {/if}
    {#each colonne as c, i (c.chiave)}
      <div class="intesta" class:oggi={c.oggi} style="grid-row: {testa}; grid-column: {i + 2}" role="columnheader">{c.titolo}</div>
    {/each}

    {#each ore as h (h.m)}
      <div class="ora m" class:prima={h.prima} style="grid-row: {riga(h.m)} / span {h.righe}">{ora(hhmm(h.m))}</div>
    {/each}

    {#each pezzi as t, k (t.da)}
      {#if t.vuoto}
        <div class="pausa" style="grid-column: 1 / -1; grid-row: {testa + 1 + basi[k]} / span {RIGHE_VUOTO}">
          <span>Nessuna attività dalle {ora(hhmm(t.da))} alle {ora(hhmm(t.a))}</span>
        </div>
      {:else}
        {#each colonne as c, i (c.chiave)}
          <!-- svelte-ignore a11y_click_events_have_key_events -->
          <div class="fondo" class:oggi={c.oggi} class:cliccabile={!!vuoto} role="presentation"
               style="grid-column: {i + 2}; grid-row: {testa + 1 + basi[k]} / span {righeDi(t)}; background-position-y: {(((60 - (t.da % 60)) % 60) / PASSO) * ALTEZZA}px"
               onclick={(e) => cliccaVuoto(e, i, t)}></div>
        {/each}
      {/if}
    {/each}

    {#each bande as b (b.chiave)}
      <div class="banda" style="grid-column: {b.colonna + 2} / span {b.ampiezza}; grid-row: {riga(b.inizio)} / {rigaFine(b.fine)}" title={b.titolo}>
        <span>{b.titolo}</span>
      </div>
    {/each}

    {#each riquadri as r (r.occorrenza.chiave + ':' + r.colonna)}
      {@const o = r.occorrenza}
      {@const spento = o.stato !== 'REGOLARE' && o.stato !== 'SPOSTATA_QUI'}
      {@const breve = minuti(o.fine) - minuti(o.inizio) <= 45}
      <button
        class="riquadro {colore(o.categoria)}"
        class:spento
        class:conflitto={o.conflitti.length > 0}
        class:breve
        style="grid-column: {r.colonna + 2} / span {r.ampiezza}; grid-row: {riga(minuti(o.inizio))} / {rigaFine(minuti(o.fine))};
               width: calc((100% - 4px) / {r.corsie}); margin-left: calc(2px + (100% - 4px) * {r.corsia} / {r.corsie})"
        onclick={() => apri(o)}
        title="{o.titolo} · {ora(o.inizio)}–{ora(o.fine)}{o.conflitti.length ? ' · si sovrappone' : ''}{spento ? ' · ' + STATI[o.stato] : ''}"
      >
        <span class="m orario">{ora(o.inizio)}–{ora(o.fine)}{#if o.conflitti.length}<span class="avviso" aria-label="si sovrappone"></span>{/if}</span>
        <span class="titolo">{o.titolo}</span>
        {#if !breve}<span class="sotto">{spento ? STATI[o.stato] : sotto(o)}</span>{/if}
      </button>
    {/each}

    {#if adesso != null && adesso > finestra[0] && adesso < finestra[1]}
      <div class="adesso" style="grid-column: 2 / -1; grid-row: {riga(adesso)}" aria-hidden="true"></div>
    {/if}
  </div>
</div>

<style>
  .scorre { overflow: auto; max-height: calc(100vh - 210px); }
  .griglia {
    display: grid; position: relative;
    grid-template-columns: 52px repeat(var(--colonne), minmax(96px, 1fr));
    grid-template-rows: repeat(var(--testa), auto) repeat(var(--righe), var(--alt));
    min-width: calc(52px + var(--colonne) * 96px);
  }
  .angolo, .sopra, .intesta { position: sticky; top: 0; z-index: 3; background: var(--surface); }
  .angolo { grid-column: 1; left: 0; z-index: 4; border-bottom: 1px solid var(--line); }
  .sopra { grid-row: 1; padding: var(--space-6) var(--space-8) 0; font-size: var(--text-2xs); font-weight: 700; letter-spacing: .06em; text-transform: uppercase; color: var(--muted); border-left: 1px solid var(--line-soft); }
  .intesta { padding: var(--space-6) var(--space-8) var(--space-8); font-size: var(--text-sm); font-weight: 600; border-bottom: 1px solid var(--line); border-left: 1px solid var(--line-soft); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .sopra + .intesta, .intesta { top: 0; }
  .griglia:has(.sopra) .intesta { top: 22px; }
  .intesta.oggi { color: var(--cloro); }
  .ora { grid-column: 1; position: sticky; left: 0; z-index: 2; background: var(--surface); font-size: var(--text-2xs); color: var(--muted); padding: 0 var(--space-6); transform: translateY(-6px); text-align: right; align-self: start; }
  .ora.prima { transform: none; padding-top: 2px; }
  .fondo {
    grid-row-end: span 1; border-left: 1px solid var(--line-soft);
    background-image: repeating-linear-gradient(to bottom, var(--line-soft) 0 1px, transparent 1px calc(var(--alt) * 12));
  }
  .fondo.oggi { background-color: color-mix(in srgb, var(--cloro-soft) 30%, transparent); }
  .fondo.cliccabile { cursor: cell; }
  .banda {
    z-index: 1; margin: 0 1px; border-radius: var(--radius-xs); pointer-events: none; overflow: hidden;
    background: repeating-linear-gradient(135deg, var(--surface-sunken) 0 8px, transparent 8px 16px); border: 1px dashed var(--line-dashed);
    display: flex; justify-content: center; padding-top: var(--space-8);
  }
  .banda span { font-size: var(--text-xs); font-weight: 700; color: var(--muted); background: var(--surface); padding: 2px var(--space-8); border-radius: var(--radius-pill); height: fit-content; }
  .riquadro {
    z-index: 2; justify-self: start; min-width: 0; margin-top: 1px; margin-bottom: 1px; overflow: hidden;
    display: flex; flex-direction: column; align-items: flex-start; gap: 0; padding: var(--space-4) var(--space-6);
    border: 1px solid rgba(16, 33, 43, 0.06); border-radius: var(--radius-xs); background: var(--c-bg); color: var(--c-fg);
    font: inherit; text-align: left; cursor: pointer; line-height: 1.2;
  }
  .riquadro:hover { filter: brightness(0.97); box-shadow: 0 1px 4px rgba(16, 33, 43, 0.18); }
  .riquadro.breve { flex-direction: row; flex-wrap: wrap; column-gap: var(--space-6); gap: var(--space-6); align-items: center; padding-top: 0; padding-bottom: 0; }
  .orario { font-size: var(--text-2xs); opacity: 0.85; display: inline-flex; align-items: center; gap: var(--space-4); white-space: nowrap; }
  .titolo { font-size: var(--text-xs); font-weight: 700; color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
  .sotto { font-size: var(--text-2xs); opacity: 0.85; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
  .spento { opacity: 0.55; background: repeating-linear-gradient(135deg, var(--c-bg) 0 6px, var(--surface) 6px 12px); }
  .spento .titolo { text-decoration: line-through; }
  .conflitto { box-shadow: inset 0 0 0 2px var(--warn); }
  .avviso { width: 11px; height: 11px; background: var(--warn); -webkit-mask: var(--icona-avviso) center / contain no-repeat; mask: var(--icona-avviso) center / contain no-repeat; }
  .pausa { z-index: 1; display: flex; align-items: center; padding-left: 60px; border-top: 1px solid var(--line); border-bottom: 1px solid var(--line);
    background: repeating-linear-gradient(135deg, var(--carta) 0 6px, var(--surface) 6px 12px); }
  .pausa span { font-size: var(--text-xs); color: var(--muted); background: var(--surface); padding: 1px var(--space-8); border-radius: var(--radius-pill); }
  .adesso { z-index: 3; height: 2px; background: var(--danger); pointer-events: none; align-self: start; }
</style>
