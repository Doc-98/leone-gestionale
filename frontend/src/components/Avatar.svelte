<script lang="ts">
  /** Le iniziali in un cerchio (come nel riquadro dell'account della barra laterale). */
  let { iniziali, misura = 24 }: { iniziali: string; misura?: number } = $props();
</script>

<span class="avatar" aria-hidden="true" style="--m: {misura}px">{iniziali}</span>

<style>
  .avatar { width: var(--m); height: var(--m); border-radius: 50%; background: var(--cloro-soft); color: var(--cloro-ink); display: inline-flex; align-items: center;
    justify-content: center; font-weight: 700; font-size: var(--text-2xs); flex-shrink: 0; }
</style>
