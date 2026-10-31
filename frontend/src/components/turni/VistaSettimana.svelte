<script lang="ts">
  import type { Turno } from '../../lib/api';
  import { aggiungiGiorni, formatOre } from '../../lib/date';
  import { areaDi, attivo, giornoBreve, orario } from '../../lib/turni';

  /**
   * La settimana (variante A del mockup): la striscia dei sette giorni colorati per area con le ore, poi solo i giorni in
   * cui si lavora; i giorni liberi in una riga sola. Un turno futuro si tocca per chiedere un cambio.
   */
  let { lun, turni, oggi, nota, onapri }: {
    lun: string;
    turni: Turno[];
    oggi: string;
    nota: (t: Turno) => string | null;
    onapri: (t: Turno) => void;
  } = $props();

  const giorni = $derived(Array.from({ length: 7 }, (_, i) => aggiungiGiorni(lun, i)));
  const delGiorno = (g: string) => turni.filter((t) => t.data === g);
  const ordinati = $derived([...turni].sort((a, b) => a.data.localeCompare(b.data) || a.inizio.localeCompare(b.inizio)));
  const liberi = $derived(giorni.filter((g) => !delGiorno(g).some(attivo)));
  const modificabile = (t: Turno) => attivo(t) && t.data >= oggi;
</script>

<div class="card striscia">
  {#each giorni as g (g)}
    {@const suoi = delGiorno(g).filter(attivo)}
    {@const b = giornoBreve(g)}
    <span class="giorno ar-{suoi[0] ? areaDi(suoi[0].postazione) : 'x'}" class:oggi={g === oggi} class:passato={g < oggi} class:vuoto={!suoi.length}>
      <span class="l">{b.l}</span><span class="n">{b.n}</span><span class="t"></span>
      <span class="h">{suoi.length ? `${formatOre(suoi.reduce((s, t) => s + Number(t.ore), 0))} h` : ''}</span>
    </span>
  {/each}
</div>

<div class="lista">
  {#each ordinati as t (t.id)}
    {@const b = giornoBreve(t.data)}
    {@const n = nota(t)}
    <svelte:element this={modificabile(t) ? 'button' : 'div'} class="card riga ar-{areaDi(t.postazione)}" class:passato={t.data < oggi || !attivo(t)}
      type={modificabile(t) ? 'button' : undefined} onclick={modificabile(t) ? () => onapri(t) : undefined} role={modificabile(t) ? undefined : 'listitem'}>
      <span class="data"><span>{b.l}</span><b>{b.n}</b></span>
      <span class="dett">
        <span class="sopra"><span class="m orario" class:annullato={!attivo(t)}>{orario(t)}</span><span class="grow"></span>
          <span class="m muted ore">{attivo(t) ? `${formatOre(Number(t.ore))} h` : 'annullato'}</span></span>
        <span class="dove"><span class="segno-area"></span><span class="nome">{t.postazione}</span></span>
        {#if t.data === oggi && attivo(t)}<span class="nota cloro">Oggi</span>{/if}
        {#if n}<span class="nota warn">{n}</span>{/if}
      </span>
    </svelte:element>
  {:else}
    <p class="muted small nomargin">Nessun turno questa settimana.</p>
  {/each}
  {#if liberi.length && ordinati.length}
    <span class="liberi muted small">Libero: {liberi.map((g) => `${giornoBreve(g).l} ${giornoBreve(g).n}`).join(', ')}</span>
  {/if}
</div>

<style>
  .striscia { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 2px; padding: var(--space-4); }
  .giorno { display: flex; flex-direction: column; align-items: center; gap: 3px; padding: var(--space-8) 0 7px; border-radius: 12px; }
  .giorno .l { font-size: 11px; font-weight: 700; color: var(--muted); text-transform: uppercase; }
  .giorno .n { font-family: var(--font-display); font-size: 18px; font-weight: 800; line-height: 1; }
  .giorno .t { width: 22px; height: 4px; border-radius: 2px; background: var(--a); }
  .giorno.vuoto .t { background: transparent; }
  .giorno .h { font-size: 10px; color: var(--muted); min-height: 13px; white-space: nowrap; }
  .giorno.oggi { background: var(--cloro-soft); box-shadow: inset 0 0 0 2px var(--cloro); }
  .giorno.passato { opacity: 0.55; }
  .lista { display: flex; flex-direction: column; gap: var(--space-8); }
  .riga { display: flex; align-items: center; gap: var(--space-12); padding: var(--space-12) var(--space-14); width: 100%; text-align: left; color: var(--ink); font: inherit; }
  button.riga { cursor: pointer; }
  .riga.passato { opacity: 0.55; }
  .data { display: flex; flex-direction: column; align-items: center; width: 38px; flex-shrink: 0; line-height: 1.1; }
  .data b { font-family: var(--font-display); font-size: 20px; font-weight: 800; }
  .data span { font-size: 11px; font-weight: 700; color: var(--muted); text-transform: uppercase; }
  .dett { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
  .sopra { display: flex; align-items: center; gap: var(--space-8); }
  .orario { font-size: var(--text-base); font-weight: 600; white-space: nowrap; }
  .annullato { text-decoration: line-through; color: var(--muted); }
  .ore { font-size: var(--text-sm); white-space: nowrap; }
  .dove { display: flex; align-items: center; gap: 7px; font-size: var(--text-md); min-width: 0; }
  .nome { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .nota { font-size: var(--text-xs); font-weight: 700; }
  .nota.cloro { color: var(--cloro-ink); }
  .nota.warn { color: var(--warn); }
  .liberi { padding: 2px var(--space-4); }
  .nomargin { margin: 0; }
</style>
