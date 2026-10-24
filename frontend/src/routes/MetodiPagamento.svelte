<script lang="ts">
  import { api, messaggioErrore, type MetodoPagamento } from '../lib/api';
  import { sessione } from '../lib/sessione.svelte';
  import Azione from '../components/Azione.svelte';
  import Icona from '../components/Icona.svelte';

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
              <span class="gruppo-azioni">
                <Azione type="submit" icona="check" tono="cloro" etichetta="Salva il nome" disabled={!modifica.nome.trim()} />
                <Azione icona="chiudi" etichetta="Annulla" onclick={() => (modifica = null)} />
              </span>
            </form>
          {:else}
            <strong class="grow">{m.nome}</strong>
            {#if m.daIncassare}<span class="tag t-warn">Da incassare</span>{/if}
            {#if m.borsellino}<span class="tag t-soft">Il credito in euro di chi paga</span>{/if}
            {#if !m.attivo}<span class="tag t-altro">Spento</span>{/if}
            {#if puo}
              <span class="gruppo-azioni">
                <Azione icona="su" etichetta="Sposta su" disabled={i === 0} onclick={() => sposta(i, -1)} />
                <Azione icona="giu" etichetta="Sposta giù" disabled={i === metodi.length - 1} onclick={() => sposta(i, 1)} />
                {#if !m.borsellino}
                  <Azione icona="matita" etichetta={`Rinomina «${m.nome}»`} onclick={() => (modifica = { id: m.id, nome: m.nome })} />
                  <Azione icona="attesa" etichetta="Da incassare a mano" premuto={m.daIncassare}
                    onclick={() => salva(m, { daIncassare: !m.daIncassare }, m.daIncassare ? `${m.nome}: incassato subito.` : `${m.nome}: da incassare a mano.`)} />
                {/if}
                <Azione icona="spegni" etichetta={m.attivo ? `Spegni «${m.nome}»` : `Riaccendi «${m.nome}»`}
                  onclick={() => salva(m, { attivo: !m.attivo }, m.attivo ? `${m.nome} spento.` : `${m.nome} riacceso.`)} />
              </span>
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
      <button class="btn btn-primary" disabled={!nuovo.nome.trim()}><Icona nome="piu" />Aggiungi</button>
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
</style>
