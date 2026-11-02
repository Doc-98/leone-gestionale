<script lang="ts">
  import { dataBreve, iso } from '../../lib/date';
  import { mesiStagione, posizione } from '../../lib/impostazioni';

  /** La stagione in corso su una linea, da un estremo all'altro: le tacche dei mesi e oggi. */
  let { dal, al }: { dal: string; al: string } = $props();
  const oggi = iso(new Date());
  const x = $derived(posizione(dal, al, oggi));
  const mesi = $derived(mesiStagione(dal, al));
</script>

<div class="linea-t" aria-label="La stagione dal {dataBreve(dal)} al {dataBreve(al)}: oggi è il {dataBreve(oggi)}">
  <span class="estremo">{dataBreve(dal)}</span>
  <span class="corpo">
    <span class="barra"><i style="width: {x}%"></i><span class="oggi" style="left: {x}%">oggi</span></span>
    <span class="tacche" aria-hidden="true">
      {#each mesi as m, i (m.mese)}<span class:g={m.gennaio} class:primo={i === 0} class:passato={m.mese < oggi.slice(0, 7)} style="left: {m.da}%; width: {m.largo}%">{m.nome}</span>{/each}
    </span>
  </span>
  <span class="estremo">{dataBreve(al)}</span>
</div>

<style>
  .linea-t { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: start; gap: var(--space-12); }
  .estremo { font-family: var(--font-mono); font-size: var(--text-xs); color: var(--muted); line-height: 8px; white-space: nowrap; }
  .corpo { display: flex; flex-direction: column; }
  .barra { position: relative; height: 8px; border-radius: 4px; background: var(--surface-sunken); }
  .barra i { position: absolute; top: 0; bottom: 0; left: 0; border-radius: 4px; background: var(--cloro); }
  .oggi { position: absolute; top: 50%; transform: translate(-50%, -50%); font-size: 11px; font-weight: 700; color: var(--cloro-ink); background: var(--surface);
    padding: 0 6px; border-radius: 999px; box-shadow: 0 0 0 1px var(--cloro); white-space: nowrap; }
  .tacche { position: relative; height: 24px; margin-top: -2px; }
  .tacche span { position: absolute; top: 0; height: 22px; font-size: 11px; font-weight: 700; color: var(--muted); padding: 9px 0 0 3px; line-height: 1; box-sizing: border-box;
    white-space: nowrap; overflow: hidden; }
  .tacche span::before { content: ''; position: absolute; left: 0; top: 0; width: 1px; height: 6px; background: var(--line-strong); }
  .tacche span.g::before { height: 10px; }
  .tacche span.primo::before { height: 14px; width: 2px; background: var(--ink); }
  .tacche span.primo { color: var(--ink); }
  .tacche span.passato { color: var(--line-strong); }
</style>
