<script lang="ts">
  import Azione from '../components/Azione.svelte';
  import Icona from '../components/Icona.svelte';
  import { api, messaggioErrore, type Cassa, type ChiusuraModo, type FondoModo, type ImpostazioniCassa, type MetodoPagamento, type PaginaCassa } from '../lib/api';
  import { CONTROLLI, numeroDa, spiegaFondo } from '../lib/cassa';
  import { euro } from '../lib/listino';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Impostazioni della cassa (ADR 0018, parte 11): una cassa o una per postazione, come funziona il fondo e quando si
   * chiude. Le casse si aggiungono e si cambiano subito; le scelte si salvano dalla barra in basso.
   */
  type Bozza = Omit<ImpostazioniCassa, 'importoFondo' | 'tolleranza' | 'oreAvviso'> & { importoFondo: string; tolleranza: string; oreAvviso: string };
  let pagina = $state<PaginaCassa | null>(null);
  let metodi = $state<MetodoPagamento[]>([]);
  let bozza = $state<Bozza | null>(null);
  let originale = $state('');
  let errore = $state('');
  let messaggio = $state('');
  let nuova = $state<string | null>(null);
  let rinomina = $state<{ id: string; nome: string } | null>(null);
  const puo = $derived(sessione.puo('IMPOSTAZIONI'));

  async function carica() {
    try {
      [pagina, metodi] = await Promise.all([api.cassa(), api.metodiPagamento()]);
      const i = pagina.impostazioni;
      bozza = { ...i, importoFondo: String(i.importoFondo).replace('.', ','), tolleranza: String(i.tolleranza).replace('.', ','), oreAvviso: String(i.oreAvviso) };
      originale = JSON.stringify(bozza);
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  carica();

  const sporco = $derived(!!bozza && JSON.stringify(bozza) !== originale);
  const fondoN = $derived(bozza ? numeroDa(bozza.importoFondo) : null);
  const tolleranzaN = $derived(bozza ? numeroDa(bozza.tolleranza) : null);
  const oreN = $derived(bozza && /^\d{1,2}$/.test(bozza.oreAvviso.trim()) ? Number(bozza.oreAvviso) : null);
  const valida = $derived(fondoN != null && tolleranzaN != null && tolleranzaN >= 0 && oreN != null && oreN <= 72);
  const pagabili = $derived(metodi.filter((m) => m.attivo && !m.contanti && !m.daIncassare && !m.borsellino));
  const aperte = $derived(pagina?.stati.filter((s) => s.aperta).map((s) => s.cassa.nome) ?? []);
  const nelCassetto = $derived(metodi.filter((m) => m.contanti && m.attivo).map((m) => m.nome));

  async function salva() {
    if (!bozza) return;
    errore = '';
    messaggio = '';
    try {
      await api.modificaImpostazioniCassa({ ...bozza, importoFondo: fondoN ?? -1, tolleranza: tolleranzaN ?? -1, oreAvviso: oreN ?? -1 });
      await carica();
      messaggio = 'Salvato: vale dalla prossima apertura.';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  async function esegui(azione: () => Promise<unknown>, fatto: string) {
    errore = '';
    messaggio = '';
    try {
      await azione();
      const b = bozza;
      await carica();
      if (b && sporco) bozza = b; // le scelte non salvate restano
      messaggio = fatto;
      return true;
    } catch (e) {
      errore = messaggioErrore(e);
      return false;
    }
  }

  const aggiungi = () => esegui(() => api.creaCassa(nuova!.trim()), `«${nuova!.trim()}» aggiunta.`).then((ok) => ok && (nuova = null));
  const cambia = (c: Cassa, m: Parameters<typeof api.modificaCassa>[1], fatto: string) => esegui(() => api.modificaCassa(c.id, m), fatto);

  type Scelte = { valore: string; opzioni: { id: string; nome: string }[]; scegli: (v: string) => void };
  const seg = <T extends string>(valore: T, opzioni: { id: T; nome: string }[], scegli: (v: T) => void): Scelte => ({ valore, opzioni, scegli: (v) => scegli(v as T) });
  const FONDI: { id: FondoModo; nome: string }[] = [{ id: 'RIPORTATO', nome: 'Riportato' }, { id: 'FISSO', nome: 'Fisso' }, { id: 'CONTATO', nome: 'Contato ogni volta' }];
  const CHIUSURE: { id: ChiusuraModo; nome: string }[] = [{ id: 'TURNO', nome: 'A fine turno' }, { id: 'GIORNATA', nome: 'A fine giornata' }];
</script>

<div class="pagina">
  {#if messaggio}<p class="msg ok nomargin" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  {#if bozza && pagina}
    <div class="colonne">
      <div class="princ">
        <section class="card sez" id="imp-casse" aria-label="Le casse">
          <div class="sez-t"><h2 class="grow">Le casse</h2><span class="spiega">un cassetto con il suo fondo e la sua chiusura</span></div>
          {@render scelte('Quante casse', 'Una sola per tutta la segreteria, o una per postazione: ogni computer dice di quale cassa è (si sceglie nella pagina Incassi) e chiude la sua.',
            seg(bozza.casse, [{ id: 'UNICA', nome: 'Una sola' }, { id: 'PER_POSTAZIONE', nome: 'Una per postazione' }], (v) => (bozza!.casse = v)))}
          {#if aperte.length && bozza.casse !== pagina.impostazioni.casse}
            <p class="hint nomargin avviso-r"><span>Per cambiare prima si chiude: {aperte.length === 1 ? `è aperta «${aperte[0]}»` : `sono aperte ${aperte.map((x) => `«${x}»`).join(', ')}`}.</span></p>
          {/if}
          {#if bozza.casse === 'PER_POSTAZIONE'}
            <ul class="casse">
              {#each pagina.casse as c (c.id)}
                <li class:spenta={!c.attiva}>
                  {#if rinomina?.id === c.id}
                    <form class="riga-c" onsubmit={(e) => { e.preventDefault(); cambia(c, { nome: rinomina!.nome.trim() }, 'Nome cambiato.').then((ok) => ok && (rinomina = null)); }}>
                      <input class="inp grow" bind:value={rinomina.nome} maxlength="40" aria-label="Nome della cassa" />
                      <Azione type="submit" icona="check" tono="cloro" etichetta="Salva il nome" disabled={!rinomina.nome.trim()} />
                      <Azione icona="chiudi" etichetta="Annulla" onclick={() => (rinomina = null)} />
                    </form>
                  {:else}
                    <span class="ico-c" aria-hidden="true"><Icona nome="incassi" /></span>
                    <span class="t grow"><b>{c.nome}</b><span>{c.principale ? 'Principale: prende i pagamenti dei computer che non hanno scelto' : c.attiva ? 'In uso' : 'Spenta'}</span></span>
                    {#if c.principale}<span class="pill passa">Principale</span>{/if}
                    {#if puo}
                      <span class="gruppo-azioni">
                        <Azione icona="matita" etichetta={`Rinomina «${c.nome}»`} onclick={() => (rinomina = { id: c.id, nome: c.nome })} />
                        {#if !c.principale}
                          <button type="button" class="mini-btn" onclick={() => cambia(c, { principale: true }, `«${c.nome}» è la principale.`)}>Rendi principale</button>
                          <button type="button" class="switch" role="switch" aria-checked={c.attiva} aria-label={c.attiva ? `Spegni «${c.nome}»` : `Accendi «${c.nome}»`}
                            onclick={() => cambia(c, { attiva: !c.attiva }, c.attiva ? `«${c.nome}» spenta.` : `«${c.nome}» accesa.`)}><span></span></button>
                        {/if}
                      </span>
                    {/if}
                  {/if}
                </li>
              {/each}
            </ul>
            {#if puo}
              {#if nuova === null}
                <div class="aggiungi"><button type="button" class="mini-btn" onclick={() => (nuova = '')}><Icona nome="piu" />Aggiungi una cassa</button></div>
              {:else}
                <form class="aggiungi" onsubmit={(e) => { e.preventDefault(); aggiungi(); }}>
                  <input class="inp grow" bind:value={nuova} maxlength="40" placeholder="Nome (es. Segreteria piscina)" aria-label="Nome della nuova cassa" />
                  <button class="btn btn-primary" disabled={!nuova.trim()}>Aggiungi</button>
                  <button type="button" class="btn btn-secondary" onclick={() => (nuova = null)}>Annulla</button>
                </form>
              {/if}
            {/if}
          {/if}
        </section>

        <section class="card sez" id="imp-fondo" aria-label="Il fondo cassa">
          <div class="sez-t"><h2 class="grow">Il fondo cassa</h2><span class="spiega">i contanti per dare il resto</span></div>
          {@render scelte('Da dove si parte', spiegaFondo({ fondo: bozza.fondo, importoFondo: fondoN ?? 0 }, euro), seg(bozza.fondo, FONDI, (v) => (bozza!.fondo = v)))}
          <div class="riga-imp"><span class="t"><b>{bozza.fondo === 'FISSO' ? 'Il fondo' : 'Il fondo di solito'}</b>
            <span>{bozza.fondo === 'FISSO' ? 'Alla chiusura resta nel cassetto, il resto si versa.' : 'Il primo giorno, e quello proposto da lasciare alla chiusura.'}</span></span>
            <span class="ctl"><input class="inp m corto" inputmode="decimal" bind:value={bozza.importoFondo} disabled={!puo} aria-label="Importo del fondo" /><span class="unita">€</span></span></div>
        </section>

        <section class="card sez" id="imp-chiusura" aria-label="La chiusura">
          <div class="sez-t"><h2 class="grow">La chiusura</h2><span class="spiega">contati, differenza, quanto si lascia</span></div>
          {@render scelte('Quando si chiude', bozza.chiusura === 'TURNO'
            ? 'Chi entra in cassa la apre, e la chiude a fine turno: ogni chiusura ha il suo nome. Più chiusure al giorno.'
            : 'Una chiusura a fine giornata, senza apertura (tranne con il fondo da contare). I pagamenti dopo la chiusura vanno nel giorno dopo.',
            seg(bozza.chiusura, CHIUSURE, (v) => (bozza!.chiusura = v)))}
          <div class="riga-imp"><span class="t"><b>Cosa finisce nel cassetto</b>
            <span>Contano nei contanti attesi solo i metodi segnati «nel cassetto».{#if !nelCassetto.length}{' '}<strong class="manca">Nessuno lo è.</strong>{/if}</span></span>
            <span class="ctl"><span class="esempio">{nelCassetto.join(', ') || '—'}</span><a class="mini-btn" href="#/metodi">Metodi di pagamento →</a></span></div>
        </section>

        <section class="card sez" id="imp-conteggio" aria-label="Il conteggio e il controllo">
          <div class="sez-t"><h2 class="grow">Il conteggio e il controllo</h2><span class="spiega">come si usa nel settore</span></div>
          <div class="riga-imp"><span class="t"><b>Conta alla cieca</b>
            <span>Chi chiude conta senza vedere i contanti attesi; il confronto arriva dopo. È la pratica consigliata: il conto non si «aggiusta» sul numero atteso.</span></span>
            <span class="ctl"><button type="button" class="switch" role="switch" aria-checked={bozza.cieca} aria-label="Conta alla cieca" disabled={!puo} onclick={() => (bozza!.cieca = !bozza!.cieca)}><span></span></button></span></div>
          <div class="riga-imp"><span class="t"><b>Differenza tollerata</b>
            <span>Fino a questa cifra, in più o in meno, si chiude e basta. Oltre: si propone di ricontare, la nota diventa obbligatoria e la chiusura può andare controllata. 0 = ogni differenza.</span></span>
            <span class="ctl"><input class="inp m corto" inputmode="decimal" bind:value={bozza.tolleranza} disabled={!puo} aria-label="Differenza tollerata" /><span class="unita">€</span></span></div>
          {@render scelte('Quali chiusure controlla un responsabile', 'Chi può annullare i pagamenti le segna come controllate (non chi le ha chiuse); sul riepilogo stampato compare chi e quando.',
            seg(bozza.controllo, CONTROLLI, (v) => (bozza!.controllo = v)))}
          <div class="riga-imp"><span class="t"><b>Riscontro con il POS</b>
            <span>Per i metodi scelti, alla chiusura si scrive il totale dello scontrino di chiusura del POS e si vede se torna con i pagamenti registrati. Si cambia subito, senza salvare.</span></span>
            <span class="ctl chips-m">
              {#each pagabili as m (m.id)}
                <button type="button" class="chip" aria-pressed={m.riscontro} disabled={!puo}
                  onclick={() => esegui(() => api.modificaMetodoPagamento(m.id, { nome: m.nome, daIncassare: m.daIncassare, contanti: m.contanti, attivo: m.attivo, ordine: m.ordine, riscontro: !m.riscontro }),
                    m.riscontro ? `${m.nome}: non si riscontra più.` : `${m.nome}: si riscontra con il POS.`)}>{m.nome}</button>
              {:else}<span class="muted small">Nessun metodo con carta o app.</span>{/each}
            </span></div>
        </section>

        <section class="card sez" id="imp-tolti" aria-label="I soldi tolti dal cassetto">
          <div class="sez-t"><h2 class="grow">I soldi tolti dal cassetto</h2><span class="spiega">spese, cassaforte, banca</span></div>
          <div class="riga-imp"><span class="t"><b>Spese pagate con la cassa</b>
            <span>Spento, il cassetto serve solo per incassi e resto: le piccole spese passano da una cassa a parte (come si consiglia di solito).</span></span>
            <span class="ctl"><button type="button" class="switch" role="switch" aria-checked={bozza.spese} aria-label="Spese pagate con la cassa" disabled={!puo} onclick={() => (bozza!.spese = !bozza!.spese)}><span></span></button></span></div>
          <div class="riga-imp"><span class="t"><b>Tieni il conto della cassaforte e della banca</b>
            <span>Quello versato alle chiusure resta «in cassaforte» finché qualcuno non segna che è stato portato in banca, con il giorno e la distinta. Il conto parte da quando si accende.</span></span>
            <span class="ctl"><button type="button" class="switch" role="switch" aria-checked={bozza.banca} aria-label="Conto della cassaforte e della banca" disabled={!puo} onclick={() => (bozza!.banca = !bozza!.banca)}><span></span></button></span></div>
          <div class="riga-imp"><span class="t"><b>Avvisa se una cassa resta aperta</b>
            <span>Dopo queste ore dall'apertura, un avviso a chi entra in cassa e, sulla voce Incassi, a chi usa la cassa. 0 = mai.</span></span>
            <span class="ctl"><input class="inp m corto" inputmode="numeric" bind:value={bozza.oreAvviso} disabled={!puo} aria-label="Ore dopo cui avvisare" /><span class="unita">ore</span></span></div>
        </section>
      </div>

      <aside class="lato">
        <section class="card pann">
          <span class="lbl">Come si fanno i conti</span>
          <div class="conto" aria-label="Esempio di chiusura">
            <span class="r"><span>Fondo cassa</span><span class="m">{euro(fondoN ?? 0)}</span></span>
            <span class="r"><span>+ contanti incassati</span><span class="m">{euro(420)}</span></span>
            <span class="r"><span>− contanti restituiti</span><span class="m">{euro(20)}</span></span>
            <span class="r"><span>− versamenti e spese</span><span class="m">{euro(200)}</span></span>
            <span class="r tot"><span>Attesi</span><span class="m">{euro((fondoN ?? 0) + 200)}</span></span>
            <span class="r"><span>Contati</span><span class="m">{euro((fondoN ?? 0) + 198)}</span></span>
            <span class="r meno"><span>Differenza</span><span class="m">−{euro(2)}</span></span>
          </div>
          <ul class="cosa">
            <li>{bozza.cieca ? 'Si conta alla cieca: gli attesi compaiono dopo il conteggio, e resta il primo conteggio se si riconta.' : 'Gli attesi si vedono mentre si conta.'}</li>
            <li>{tolleranzaN ? `Fino a ${euro(tolleranzaN)} di differenza si chiude e basta; oltre serve la nota.` : 'Ogni differenza chiede la nota.'}
              {bozza.controllo === 'TUTTE' ? ' Ogni chiusura va controllata da un responsabile.' : bozza.controllo === 'OLTRE' ? ' Quelle oltre vanno controllate da un responsabile.' : ''}</li>
            {#if pagabili.some((m) => m.riscontro)}<li>Si riscontrano con il POS: {pagabili.filter((m) => m.riscontro).map((m) => m.nome).join(', ')}.</li>{/if}
          </ul>
          <p class="muted small nomargin">Alla chiusura si dice quanto si lascia per la volta dopo; il resto è da versare. Il riepilogo si stampa da firmare. L'ultima chiusura si può riaprire, con il motivo, da chi può annullare i pagamenti.</p>
          <p class="muted small nomargin">Il gestionale non sostituisce il registratore telematico: il riepilogo della chiusura è un documento interno.</p>
        </section>
      </aside>
    </div>

    {#if sporco && puo}
      <div class="salva-bar" role="status">
        <span>Ci sono modifiche non salvate</span>
        <button type="button" class="vuoto" onclick={() => (bozza = JSON.parse(originale))}>Annulla</button>
        <button type="button" class="chiaro" disabled={!valida} onclick={salva}>Salva</button>
      </div>
    {/if}
  {:else if !errore}
    <p class="muted small">Caricamento…</p>
  {/if}
</div>

{#snippet scelte(titolo: string, spiega: string, s: Scelte)}
  <div class="riga-imp"><span class="t"><b>{titolo}</b><span>{spiega}</span></span>
    <span class="ctl"><span class="seg" style:grid-template-columns={`repeat(${s.opzioni.length}, minmax(0, 1fr))`} role="group" aria-label={titolo}>
      {#each s.opzioni as o (o.id)}<button type="button" aria-pressed={s.valore === o.id} disabled={!puo} onclick={() => s.scegli(o.id)}>{o.nome}</button>{/each}
    </span></span></div>
{/snippet}

<style>
  .pagina { display: flex; flex-direction: column; gap: var(--space-14); }
  .colonne { display: flex; flex-wrap: wrap; gap: var(--space-18, 18px); align-items: flex-start; }
  .princ { flex: 999 1 600px; min-width: 0; display: flex; flex-direction: column; gap: var(--space-14); }
  .lato { flex: 1 1 300px; max-width: 380px; position: sticky; top: var(--space-16); }
  .ctl { display: flex; align-items: center; gap: var(--space-8); flex-wrap: wrap; justify-content: flex-end; }
  .ctl .inp { height: 40px; }
  .corto { width: 110px; text-align: right; }
  .unita { font-size: var(--text-md); color: var(--muted); }
  .seg { min-width: 280px; }
  .cosa { margin: 0; padding-left: 18px; display: flex; flex-direction: column; gap: var(--space-6); font-size: var(--text-sm); }
  .chips-m { display: flex; gap: var(--space-6); flex-wrap: wrap; justify-content: flex-end; max-width: 360px; }
  .esempio { font-size: var(--text-md); font-weight: 600; padding: var(--space-6) var(--space-10); border-radius: var(--radius-sm); background: var(--carta); }
  .manca { color: var(--warn); font-weight: 600; }
  .avviso-r { margin: 0 var(--space-16) var(--space-12); }
  .casse { list-style: none; margin: 0; padding: 0; border-top: 1px solid var(--line-soft); }
  .casse li { display: flex; align-items: center; gap: var(--space-12); flex-wrap: wrap; padding: var(--space-12) var(--space-16); border-top: 1px solid var(--line-soft); }
  .casse li:first-child { border-top: none; }
  .casse li.spenta .t b { color: var(--muted); }
  .ico-c { width: 38px; height: 38px; border-radius: var(--radius-sm); display: grid; place-items: center; background: var(--carta); color: var(--seg-fg); flex-shrink: 0; }
  .casse .t { display: flex; flex-direction: column; gap: 1px; min-width: 200px; }
  .casse .t span { font-size: var(--text-sm); color: var(--muted); }
  .riga-c { display: flex; gap: var(--space-8); align-items: center; flex: 1 1 auto; }
  .riga-c .inp, .aggiungi .inp { height: 40px; }
  .grow { flex: 1 1 auto; min-width: 0; }
  .gruppo-azioni { display: flex; align-items: center; gap: var(--space-6); }
  .aggiungi { display: flex; gap: var(--space-8); flex-wrap: wrap; padding: var(--space-12) var(--space-16); border-top: 1px solid var(--line-soft); }
  .pann { padding: var(--space-16); display: flex; flex-direction: column; gap: var(--space-10); }
  .conto { display: flex; flex-direction: column; gap: var(--space-6); padding: var(--space-14); border-radius: var(--radius-md); background: var(--surface-sunken); font-size: var(--text-sm); }
  .conto .r { display: flex; justify-content: space-between; gap: var(--space-10); }
  .conto .tot { border-top: 1px solid var(--line); padding-top: var(--space-6); font-weight: 700; }
  .conto .meno { color: var(--ferma-fg); font-weight: 700; }
</style>
