<script lang="ts">
  import { api, messaggioErrore, TIPI_RICHIESTA, type Richiesta } from '../lib/api';
  import { descrivi } from '../lib/richieste';

  let { cambiate }: { cambiate?: (n: number) => void } = $props();
  let richieste = $state<Richiesta[]>([]);
  let note = $state<Record<string, string>>({});
  let caricamento = $state(true);
  let errore = $state('');
  let messaggio = $state('');
  let lavoro = $state<string | null>(null);

  async function carica() {
    caricamento = true;
    try {
      richieste = await api.richiesteDaApprovare();
      cambiate?.(richieste.length);
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      caricamento = false;
    }
  }
  carica();

  async function decidi(r: Richiesta, approva: boolean) {
    errore = messaggio = '';
    lavoro = r.id;
    try {
      await (approva ? api.approva(r.id, note[r.id]) : api.respingi(r.id, note[r.id]));
      messaggio = approva ? 'Approvata: i turni sono aggiornati.' : 'Richiesta respinta.';
      await carica();
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      lavoro = null;
    }
  }
</script>

<section class="page">
  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

  {#each richieste as r (r.id)}
    {@const d = descrivi(r)}
    <div class="card richiesta">
      <span class="tag t-soft">{TIPI_RICHIESTA[r.tipo]}</span>
      <div class="titolo">{d.titolo}</div>
      <div class="muted small">{d.dettaglio}</div>
      {#if r.messaggio}<div class="small citazione">«{r.messaggio}»</div>{/if}
      <input class="inp" placeholder="Nota per lo staff (facoltativa)" bind:value={note[r.id]} maxlength="500" />
      <div class="row">
        <button class="btn btn-primary grow" disabled={lavoro === r.id} onclick={() => decidi(r, true)}>Approva</button>
        <button class="btn btn-secondary grow" disabled={lavoro === r.id} onclick={() => decidi(r, false)}>Respingi</button>
      </div>
    </div>
  {:else}
    <p class="muted centro">{caricamento ? '…' : 'Nessuna richiesta da approvare.'}</p>
  {/each}
</section>

<style>
  .richiesta { padding: var(--space-14); display: flex; flex-direction: column; gap: var(--space-8); align-items: flex-start; }
  .richiesta .inp, .richiesta .row { align-self: stretch; }
  .titolo { font-weight: 600; }
  .citazione { font-style: italic; }
  .centro { text-align: center; margin-top: var(--space-20); }
</style>
