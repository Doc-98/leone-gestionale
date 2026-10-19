<script lang="ts">
  import { untrack } from 'svelte';
  import { api, messaggioErrore, type Corso, type PersonaBreve, type TipoCorso } from '../lib/api';
  import { TIPI_CORSO } from '../lib/piano';

  let { corso, impiantoId, personale, fatto }: {
    corso: Corso | null;
    impiantoId: string;
    personale: PersonaBreve[];
    fatto: (c: Corso, messaggio: string) => void;
  } = $props();

  const c = untrack(() => $state.snapshot(corso));
  let nome = $state(c?.nome ?? '');
  let tipo = $state<TipoCorso>(c?.tipo ?? 'SCUOLA_NUOTO');
  let livello = $state(c?.livello ?? '');
  let etaDa = $state(c?.etaDa?.toString() ?? '');
  let etaA = $state(c?.etaA?.toString() ?? '');
  let istruttoreId = $state(c?.istruttore?.id ?? '');
  let note = $state(c?.note ?? '');
  let attivo = $state(c?.attivo ?? true);
  let colore = $state(c?.colore ?? '');
  // Colori dei livelli più comuni (Delfino Giallo, Verde, Azzurro, Rosso, Nero…) e qualche altro
  const COLORI = ['#E07A5F', '#E6B400', '#2E9E5B', '#2F8FD8', '#D23B3B', '#2B2B2B', '#5B6C7D', '#8E5BB5', '#E57BB0', '#1FA3A3'];
  let errore = $state('');
  let lavoro = $state(false);

  const numero = (s: string) => (s.trim() === '' ? null : Number(s));

  async function salva(e: SubmitEvent) {
    e.preventDefault();
    errore = '';
    lavoro = true;
    const dati = { impiantoId: c?.impiantoId ?? impiantoId, nome, tipo, livello: livello || null, etaDa: numero(etaDa), etaA: numero(etaA),
      istruttoreId: istruttoreId || null, note: note || null, attivo, colore: colore || null };
    try {
      const salvato = corso ? await api.modificaCorso(corso.id, dati) : await api.creaCorso(dati);
      fatto(salvato, corso ? 'Corso aggiornato.' : 'Corso creato: ora aggiungi le lezioni.');
    } catch (err) {
      errore = messaggioErrore(err);
    } finally {
      lavoro = false;
    }
  }
</script>

<form class="form" onsubmit={salva}>
  <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={nome} maxlength="120" required placeholder="Es. Scuola nuoto ragazzi · delfini" /></label>
  <div class="due">
    <label class="field"><span class="lbl">Tipo</span>
      <select class="inp" bind:value={tipo}>{#each Object.entries(TIPI_CORSO) as [k, v] (k)}<option value={k}>{v}</option>{/each}</select>
    </label>
    <label class="field"><span class="lbl">Livello (facoltativo)</span><input class="inp" bind:value={livello} maxlength="60" /></label>
  </div>
  <div class="due">
    <label class="field"><span class="lbl">Età da</span><input class="inp m" type="number" min="0" max="120" bind:value={etaDa} /></label>
    <label class="field"><span class="lbl">Età fino a</span><input class="inp m" type="number" min="0" max="120" bind:value={etaA} /></label>
  </div>
  <label class="field"><span class="lbl">Istruttore</span>
    <select class="inp" bind:value={istruttoreId}>
      <option value="">—</option>
      {#each personale as p (p.id)}<option value={p.id}>{p.nome}</option>{/each}
    </select>
  </label>
  <div class="field">
    <span class="lbl">Colore del livello (facoltativo)</span>
    <div class="colori" role="radiogroup" aria-label="Colore">
      <button type="button" class="col nessuno" role="radio" aria-checked={!colore} aria-label="Nessun colore" onclick={() => (colore = '')}>—</button>
      {#each COLORI as c (c)}
        <button type="button" class="col" role="radio" aria-checked={colore.toUpperCase() === c} aria-label={c} style="background: {c}" onclick={() => (colore = c)}></button>
      {/each}
      <input class="inp m hex" bind:value={colore} maxlength="7" placeholder="#RRGGBB" aria-label="Colore in esadecimale" />
    </div>
  </div>
  <label class="field"><span class="lbl">Note (facoltative)</span><input class="inp" bind:value={note} maxlength="500" /></label>
  {#if corso}
    <label class="riga-switch">
      <span>Attivo<br /><span class="muted small">Un corso non attivo sparisce dal piano; lezioni e storico restano.</span></span>
      <button type="button" class="switch" role="switch" aria-checked={attivo} aria-label="Attivo" onclick={() => (attivo = !attivo)}><span></span></button>
    </label>
  {/if}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
  <button class="btn btn-primary" disabled={lavoro}>{corso ? 'Salva' : 'Crea il corso'}</button>
</form>

<style>
  .form { display: flex; flex-direction: column; gap: var(--space-14); }
  select.inp { appearance: auto; }
  .colori { display: flex; flex-wrap: wrap; gap: var(--space-6); align-items: center; }
  .col { width: 30px; height: 30px; border-radius: 50%; border: 2px solid var(--surface); box-shadow: 0 0 0 1px var(--line-strong); cursor: pointer; padding: 0; color: var(--muted); background: var(--surface); }
  .col[aria-checked='true'] { box-shadow: 0 0 0 3px var(--cloro); }
  .hex { width: 110px; height: 36px; font-size: var(--text-sm); }
</style>
