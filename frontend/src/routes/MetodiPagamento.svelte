<script lang="ts">
  import { api, messaggioErrore, type DatiSocieta, type MetodoPagamento, type PagamentiFamiglia } from '../lib/api';
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

  // come paga una famiglia (sta nei dati della società; si cambia con il permesso «Impostazioni della società»)
  let societa = $state<DatiSocieta | null>(null);
  api.societa().then((d) => (societa = d)).catch(() => {});
  async function famiglia(v: PagamentiFamiglia) {
    errore = '';
    try {
      societa = await api.modificaSocieta({ ...societa!, pagamentiFamiglia: v });
      messaggio = 'Salvato: vale dal prossimo pagamento.';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

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
    esegui(() => api.modificaMetodoPagamento(m.id, { nome: m.nome, daIncassare: m.daIncassare, contanti: m.contanti, attivo: m.attivo, ordine: m.ordine, ...cambi }), fatto);

  async function sposta(i: number, verso: -1 | 1) {
    const a = metodi[i], b = metodi[i + verso];
    await esegui(async () => {
      await api.modificaMetodoPagamento(a.id, { ...a, ordine: b.ordine });
      await api.modificaMetodoPagamento(b.id, { ...b, ordine: a.ordine === b.ordine ? a.ordine + verso : a.ordine });
    }, 'Ordine cambiato.');
  }
</script>

<div class="pagina">
  {#if messaggio}<p class="msg ok nomargin" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  <section class="card sez">
    <div class="sez-t"><h2 class="grow">Metodi</h2><span class="spiega">in quest'ordine quando si registra un pagamento</span></div>
    <p class="muted small nomargin spiega-m">«Da incassare» (es. bonifico): il pagamento resta nell'elenco «Da incassare» finché qualcuno non lo segna arrivato sul conto.
      «Nel cassetto» (di solito i contanti): conta nei contanti attesi alla chiusura di cassa.
      Un metodo spento non si propone più, ma resta nei pagamenti già registrati.</p>
    <div class="elenco">
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
            {#if m.contanti}<span class="tag t-soft">Nel cassetto</span>{/if}
            {#if m.borsellino}<span class="tag t-soft">Il credito in euro di chi paga</span>{/if}
            {#if !m.attivo}<span class="tag t-altro">Spento</span>{/if}
            {#if puo}
              <span class="gruppo-azioni">
                <Azione icona="su" etichetta="Sposta su" disabled={i === 0} onclick={() => sposta(i, -1)} />
                <Azione icona="giu" etichetta="Sposta giù" disabled={i === metodi.length - 1} onclick={() => sposta(i, 1)} />
                {#if !m.borsellino}
                  <Azione icona="matita" etichetta={`Rinomina «${m.nome}»`} onclick={() => (modifica = { id: m.id, nome: m.nome })} />
                  <Azione icona="attesa" etichetta="Da incassare a mano" premuto={m.daIncassare}
                    onclick={() => salva(m, { daIncassare: !m.daIncassare, contanti: false }, m.daIncassare ? `${m.nome}: incassato subito.` : `${m.nome}: da incassare a mano.`)} />
                  <Azione icona="euro" etichetta="Finisce nel cassetto (conta alla chiusura di cassa)" premuto={m.contanti}
                    onclick={() => salva(m, { contanti: !m.contanti, daIncassare: false }, m.contanti ? `${m.nome}: non conta più nel cassetto.` : `${m.nome}: conta nei contanti attesi della cassa.`)} />
                {/if}
                <Azione icona="spegni" etichetta={m.attivo ? `Spegni «${m.nome}»` : `Riaccendi «${m.nome}»`}
                  onclick={() => salva(m, { attivo: !m.attivo }, m.attivo ? `${m.nome} spento.` : `${m.nome} riacceso.`)} />
              </span>
            {/if}
          {/if}
        </li>
      {/each}
    </ul>
    </div>
  </section>

  {#if societa}
    <section class="card sez" id="imp-famiglia" aria-label="Una famiglia paga per più persone">
      <div class="sez-t"><h2 class="grow">Una famiglia paga per più persone</h2><span class="spiega">es. due fratelli</span></div>
      <div class="riga-imp"><span class="t"><b>Quando si paga per più familiari insieme</b><span>Un pagamento unico per la famiglia, uno per persona, o si sceglie ogni volta al banco.</span></span>
        <span class="ctl"><select class="inp" value={societa.pagamentiFamiglia} disabled={!puo} aria-label="Come paga una famiglia"
          onchange={(e) => famiglia(e.currentTarget.value as PagamentiFamiglia)}>
          <option value="A_SCELTA">Si sceglie ogni volta</option>
          <option value="INSIEME">Sempre un pagamento unico</option>
          <option value="PER_PERSONA">Sempre uno per persona</option>
        </select></span></div>
    </section>
  {/if}

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
  .spiega-m { padding: var(--space-12) var(--space-16) 0; }
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
