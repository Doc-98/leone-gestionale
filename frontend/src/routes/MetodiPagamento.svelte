<script lang="ts">
  import { api, messaggioErrore, type MetodoPagamento } from '../lib/api';
  import { sessione } from '../lib/sessione.svelte';

  /** I metodi di pagamento della società: si aggiungono, si rinominano, si spengono (quelli usati restano nello storico). */
  let metodi = $state<MetodoPagamento[]>([]);
  let errore = $state('');
  let messaggio = $state('');
  let nuovo = $state({ nome: '', daIncassare: false });
  let modifica = $state<{ id: string; nome: string } | null>(null);
  const puo = $derived(sessione.puo('IMPOSTAZIONI'));

  async function carica() {
    try {
      metodi = await api.metodiPagamento();
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  carica();

  async function esegui(azione: () => Promise<unknown>, fatto: string) {
    errore = '';
    messaggio = '';
    try {
      await azione();
      await carica();
      messaggio = fatto;
      return true;
    } catch (e) {
      errore = messaggioErrore(e);
      return false;
    }
  }

  const salva = (m: MetodoPagamento, cambi: Partial<MetodoPagamento>, fatto: string) =>
    esegui(() => api.modificaMetodoPagamento(m.id, { nome: m.nome, daIncassare: m.daIncassare, attivo: m.attivo, ordine: m.ordine, ...cambi }), fatto);

  async function sposta(i: number, verso: -1 | 1) {
    const a = metodi[i], b = metodi[i + verso];
    await esegui(async () => {
      await api.modificaMetodoPagamento(a.id, { ...a, ordine: b.ordine });
      await api.modificaMetodoPagamento(b.id, { ...b, ordine: a.ordine === b.ordine ? a.ordine + verso : a.ordine });
    }, 'Ordine cambiato.');
  }
</script>

<div class="pagina">
  <p class="muted small nomargin">Compaiono quando si registra un pagamento, in quest'ordine. «Da incassare» (es. bonifico): il pagamento resta nell'elenco
    «Da incassare» finché qualcuno non lo segna arrivato sul conto. Un metodo spento non si propone più, ma resta nei pagamenti già registrati.</p>
  {#if messaggio}<p class="msg ok nomargin" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  <section class="card elenco">
    <ul class="righe">
      {#each metodi as m, i (m.id)}
        <li class:spento={!m.attivo}>
          {#if modifica?.id === m.id}
            <form class="riga" onsubmit={(e) => { e.preventDefault(); salva(m, { nome: modifica!.nome.trim() }, 'Nome cambiato.').then((ok) => ok && (modifica = null)); }}>
              <input class="inp" bind:value={modifica.nome} maxlength="60" aria-label="Nome" />
              <button class="btn btn-primary" disabled={!modifica.nome.trim()}>Salva</button>
              <button type="button" class="link small" onclick={() => (modifica = null)}>Annulla</button>
            </form>
          {:else}
            <strong class="grow">{m.nome}</strong>
            {#if m.daIncassare}<span class="tag t-warn">Da incassare</span>{/if}
            {#if m.borsellino}<span class="tag t-soft">Il credito in euro di chi paga</span>{/if}
            {#if !m.attivo}<span class="tag t-altro">Spento</span>{/if}
            {#if puo}
              <button class="link small" disabled={i === 0} onclick={() => sposta(i, -1)} aria-label="Sposta su">↑</button>
              <button class="link small" disabled={i === metodi.length - 1} onclick={() => sposta(i, 1)} aria-label="Sposta giù">↓</button>
              {#if !m.borsellino}
                <button class="link small" onclick={() => (modifica = { id: m.id, nome: m.nome })}>Rinomina</button>
                <button class="link small" onclick={() => salva(m, { daIncassare: !m.daIncassare }, m.daIncassare ? `${m.nome}: incassato subito.` : `${m.nome}: da incassare a mano.`)}>
                  {m.daIncassare ? 'Incassato subito' : 'Da incassare'}</button>
              {/if}
              <button class="link small muted" onclick={() => salva(m, { attivo: !m.attivo }, m.attivo ? `${m.nome} spento.` : `${m.nome} riacceso.`)}>{m.attivo ? 'Spegni' : 'Riaccendi'}</button>
            {/if}
          {/if}
        </li>
      {/each}
    </ul>
  </section>

  {#if puo}
    <form class="card nuovo" onsubmit={(e) => { e.preventDefault(); esegui(() => api.creaMetodoPagamento({ nome: nuovo.nome.trim(), daIncassare: nuovo.daIncassare }), `Metodo «${nuovo.nome.trim()}» aggiunto.`).then((ok) => ok && (nuovo = { nome: '', daIncassare: false })); }}>
      <input class="inp" bind:value={nuovo.nome} placeholder="Nuovo metodo (es. buono welfare)" maxlength="60" aria-label="Nuovo metodo" />
      <label class="small spunta"><input type="checkbox" bind:checked={nuovo.daIncassare} /> Da incassare a mano</label>
      <button class="btn btn-primary" disabled={!nuovo.nome.trim()}>Aggiungi</button>
    </form>
  {/if}
</div>

<style>
  .pagina { display: flex; flex-direction: column; gap: var(--space-14); max-width: 760px; }
  .elenco { padding: var(--space-6) var(--space-16); }
  .righe { list-style: none; margin: 0; padding: 0; }
  .righe li { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; padding: var(--space-10) 0; border-top: 1px solid var(--line-soft); }
  .righe li:first-child { border-top: none; }
  .riga { display: flex; gap: var(--space-8); align-items: center; flex: 1; }
  .riga .inp { flex: 1; }
  .spento { opacity: 0.6; }
  .nuovo { display: flex; gap: var(--space-10); align-items: center; flex-wrap: wrap; padding: var(--space-12) var(--space-16); }
  .nuovo .inp { flex: 1; min-width: 220px; }
  .spunta { display: flex; align-items: center; gap: var(--space-6); }
  .link { min-height: 32px; }
</style>
