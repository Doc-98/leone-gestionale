<script lang="ts">
  import ElencoModificabile from '../components/ElencoModificabile.svelte';

  /** Solo per i test: un elenco non riordinabile che toglie davvero la riga eliminata. */
  let elementi = $state([{ id: 'a', nome: 'Contanti' }, { id: 'b', nome: 'Carta' }]);
</script>

<ElencoModificabile {elementi} riordinabile={false} nome={(v) => v.nome} nomeElemento="metodo" riga={contenuto} onelimina={(v) => { elementi = elementi.filter((x) => x.id !== v.id); }} />

{#snippet contenuto(v: { nome: string })}<strong>{v.nome}</strong>{/snippet}
