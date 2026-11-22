<script lang="ts">
  /**
   * Il riquadro «acceso / spento» in cima al foglio di un elemento complesso (design system, «Elenco modificabile»).
   * Acceso: fondo `cloro-soft`. Spento: fondo `surface-sunken` e la scritta «Spento». L'interruttore è lo stesso della riga:
   * lo stato si cambia da qui o dalla riga, subito, e i due restano uguali (lo stato sta nel genitore).
   * `nome` entra nell'etichetta per lo screen reader («Acceso: Satispay»); senza, solo «Acceso».
   */
  let { acceso, onacceso, nome = '', disabled = false }: {
    acceso: boolean;
    onacceso: (acceso: boolean) => unknown;
    nome?: string;
    disabled?: boolean;
  } = $props();

  const etichetta = $derived(nome ? `Acceso: «${nome}»` : 'Acceso');
</script>

<!-- tutto il riquadro si tocca: l'etichetta porta il clic all'interruttore; la scritta è a vista soltanto (il nome e lo stato li dà l'interruttore, una volta sola) -->
<label class="riquadro" class:spento={!acceso}>
  <span class="scritta" aria-hidden="true">{acceso ? 'Acceso' : 'Spento'}</span>
  <button type="button" class="switch tap44" role="switch" aria-checked={acceso} aria-label={etichetta} {disabled} onclick={() => onacceso(!acceso)}><span></span></button>
</label>

<style>
  .riquadro {
    display: flex; align-items: center; justify-content: space-between; gap: var(--space-12); min-height: 56px;
    padding: var(--space-8) var(--space-14); border-radius: var(--radius-lg); background: var(--cloro-soft); color: var(--cloro-ink); cursor: pointer;
  }
  .riquadro.spento { background: var(--surface-sunken); color: var(--muted); }
  .scritta { font-weight: 700; font-size: var(--text-base); white-space: nowrap; }
</style>
