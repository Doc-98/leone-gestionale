<script lang="ts">
  import type { TurnoAlBanco } from '../../lib/api';
  import { etichettaTurno } from '../../lib/banco';
  import { colore } from '../../lib/piano';

  /**
   * Il corso di oggi di chi passa, nella tinta della sua categoria. Il pallino ha il colore del livello (Delfino
   * Giallo, Verde…), se il corso ne ha uno; altrimenti quello della categoria.
   */
  let { turno }: { turno: TurnoAlBanco } = $props();
</script>

<span class="pillola {colore(turno.tipo ?? 'ALTRO')}">
  <span class="liv" style:background={turno.colore ?? 'var(--c-line)'} aria-hidden="true"></span>{etichettaTurno(turno)}
</span>

<style>
  .pillola { display: inline-flex; align-items: center; gap: var(--space-6); max-width: 100%; padding: 2px 9px; border-radius: var(--radius-pill);
    background: var(--c-bg); color: var(--c-fg); font-size: var(--text-xs); font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .liv { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; box-shadow: 0 0 0 1px color-mix(in srgb, var(--c-fg) 25%, transparent); }
</style>
