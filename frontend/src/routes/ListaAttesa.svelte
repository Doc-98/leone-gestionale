<script lang="ts">
  import { api, ErroreApi, messaggioErrore, type PaginaAttesa, type TurnoPosti, type VoceAttesa } from '../lib/api';
  import { dataBreve, dataDi, GIORNI_ESTESI, ora } from '../lib/date';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Le liste d'attesa dei turni pieni, in ordine di arrivo. In cima i turni dove c'è un posto (subito o da una data,
   * perché qualcuno si ritira o cambia turno): si avvisa il primo della lista con un'email modificabile e lo si iscrive.
   */
  let pagina = $state<PaginaAttesa | null>(null);
  let errore = $state('');
  let messaggio = $state('');
  let soloLiberi = $state(false);
  let cerca = $state('');
  let conferma = $state('');
  let soprannumero = $state<{ voce: VoceAttesa; turno: TurnoPosti } | null>(null);
  let avviso = $state<{ id: string; oggetto: string; testo: string } | null>(null);

  const schede = $derived(sessione.puo('CLIENTI'));

  async function carica() {
    try {
      pagina = await api.paginaAttesa();
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a caricare le liste d\'attesa.');
    }
  }
  carica();

  /** C'è un posto oggi o da una data futura. */
  const stato = (t: TurnoPosti) => {
    if (!pagina || !t.liberoDal) return { testo: 'Al completo', classe: 'neutro', libero: false };
    if (t.liberoDal <= pagina.oggi) {
      const n = t.liberi == null ? null : Math.max(1, t.liberi);
      return { testo: n == null ? 'Posti liberi' : n === 1 ? '1 posto libero' : `${n} posti liberi`, classe: 'verde', libero: true };
    }
    return { testo: `Si libera un posto dal ${dataBreve(t.liberoDal)}`, classe: 'arancio', libero: true };
  };

  const visibili = $derived.by(() => {
    if (!pagina) return [];
    const q = cerca.trim().toLowerCase();
    return pagina.turni.filter((t) => (!soloLiberi || stato(t).libero)
      && (!q || t.corso.toLowerCase().includes(q) || t.attesa.some((v) => `${v.nome} ${v.cognome} ${v.cognome} ${v.nome}`.toLowerCase().includes(q))));
  });
  const inAttesa = $derived(pagina?.turni.reduce((n, t) => n + t.attesa.length, 0) ?? 0);
  const conPosto = $derived(pagina?.turni.filter((t) => stato(t).libero).length ?? 0);

  const quando = (t: TurnoPosti) => `${GIORNI_ESTESI[t.giornoSettimana]} ${ora(t.inizio)}–${ora(t.fine)}`;
  const eta = (n: number | null) => (n == null ? '' : n === 1 ? '1 anno' : `${n} anni`);
  const dalGiorno = (t: TurnoPosti) => (t.liberoDal && pagina && t.liberoDal > pagina.oggi ? t.liberoDal : pagina!.oggi);

  async function esegui(azione: () => Promise<unknown>, fatto: string) {
    errore = '';
    messaggio = '';
    conferma = '';
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

  async function iscrivi(v: VoceAttesa, t: TurnoPosti, forza = false) {
    errore = '';
    messaggio = '';
    soprannumero = null;
    const dal = dalGiorno(t);
    try {
      await api.iscriviDaAttesa(v.id, { dal, forza });
      await carica();
      messaggio = `${v.nome} ${v.cognome} iscritto a ${t.corso} (${quando(t)}) dal ${dataBreve(dal)}${forza ? ', in soprannumero' : ''}.`;
    } catch (e) {
      if (e instanceof ErroreApi && e.status === 409 && e.message.includes('al completo')) {
        errore = e.message;
        soprannumero = { voce: v, turno: t };
      } else errore = messaggioErrore(e);
    }
  }

  function apriAvviso(v: VoceAttesa, t: TurnoPosti) {
    const dal = t.liberoDal && pagina && t.liberoDal > pagina.oggi ? ` dal ${dataBreve(t.liberoDal)}` : '';
    avviso = {
      id: v.id,
      oggetto: `Un posto per ${v.nome} nel corso ${t.corso}`,
      testo: `Buongiorno,\n\nsi è liberato un posto per ${v.nome} nel corso ${t.corso}, ${quando(t)}${dal}.\n`
        + 'Se vi interessa, rispondete a questa email o passate in segreteria entro 3 giorni: dopo lo proporremo al prossimo in lista.\n\n'
        + `${pagina?.societa ?? ''}`,
    };
  }

  async function inviaAvviso(v: VoceAttesa) {
    const a = avviso!;
    errore = '';
    messaggio = '';
    try {
      const r = await api.avvisaAttesa(a.id, { oggetto: a.oggetto, testo: a.testo });
      avviso = null;
      await carica();
      messaggio = r.stato === 'INVIATA' ? `Avviso inviato a ${v.contatti.join(', ')}.`
        : r.stato === 'NON_INVIATA' ? 'Avviso registrato, ma non partito: il server di posta non è configurato. Telefona alla famiglia.'
        : r.stato === 'SENZA_EMAIL' ? `${v.nome} ${v.cognome} non ha un'email (né un tutore che riceve le comunicazioni): telefona alla famiglia.`
        : `Invio non riuscito: ${r.errore ?? 'errore del server di posta'}.`;
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
</script>

<div class="barra">
  <label class="small spunta"><input type="checkbox" bind:checked={soloLiberi} /> Solo dove c'è un posto</label>
  <input class="inp cerca" type="search" bind:value={cerca} placeholder="Cerca un corso o un nome" aria-label="Cerca un corso o un nome" />
  <span class="grow"></span>
  {#if pagina}<span class="muted small">{inAttesa} in attesa · {conPosto} {conPosto === 1 ? 'turno con un posto' : 'turni con un posto'}</span>{/if}
</div>

{#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
{#if errore}
  <p class="msg err" role="alert">{errore}</p>
  {#if soprannumero}
    {@const s = soprannumero}
    <button class="btn btn-secondary sx" onclick={() => iscrivi(s.voce, s.turno, true)}>Iscrivi comunque in soprannumero</button>
  {/if}
{/if}

{#if pagina}
  <div class="turni">
    {#each visibili as t (t.lezioneId)}
      {@const st = stato(t)}
      <section class="card turno" class:libero={st.libero}>
        <header class="testa">
          <div class="grow">
            <h3>{t.corso}</h3>
            <div class="muted small cap">{quando(t)}{t.etaDa != null || t.etaA != null ? ` · ${t.etaDa ?? 0}–${t.etaA ?? '…'} anni` : ''}{t.posti != null ? ` · ${t.occupati}/${t.posti} iscritti` : ''}</div>
          </div>
          <span class="badge-s {st.classe}">{st.testo}</span>
        </header>
        <ol class="righe">
          {#each t.attesa as v (v.id)}
            {@const primo = v.posizione === 1 && st.libero}
            <li class:primo>
              <div class="riga">
                <span class="pos m">{v.posizione}</span>
                <span class="chi grow">
                  {#if schede}<a class="nome" href={`#/clienti/${v.personaId}`}>{v.cognome} {v.nome}</a>{:else}<span class="nome">{v.cognome} {v.nome}</span>{/if}
                  <span class="muted small">{eta(v.eta)}{v.eta != null ? ' · ' : ''}in attesa dal {dataBreve(dataDi(v.aggiuntaIl))}{v.note ? ` · ${v.note}` : ''}</span>
                  <span class="small">{#if v.contatti.length}<span class="muted">{v.contatti.join(', ')}</span>{:else}<span class="manca">Nessuna email</span>{/if}
                    {#if v.avvisataIl}<span class="tag t-soft avv">Avvisato il {dataBreve(dataDi(v.avvisataIl))}</span>{/if}</span>
                </span>
                <span class="azioni">
                  {#if st.libero}<button class="link small" onclick={() => (avviso?.id === v.id ? (avviso = null) : apriAvviso(v, t))}>Avvisa</button>{/if}
                  <button class="link small" onclick={() => iscrivi(v, t)}>Iscrivi{st.libero && t.liberoDal && t.liberoDal > pagina.oggi ? ` dal ${dataBreve(t.liberoDal)}` : ''}</button>
                  <button class="link small rosso" onclick={() => (conferma === v.id ? esegui(() => api.rinunciaAttesa(v.id), `${v.nome} ${v.cognome} tolto dalla lista d'attesa.`) : (conferma = v.id))}>
                    {conferma === v.id ? 'Conferma: togli' : 'Togli'}</button>
                </span>
              </div>
              {#if avviso?.id === v.id}
                <form class="avviso" onsubmit={(e) => { e.preventDefault(); inviaAvviso(v); }}>
                  <label class="field"><span class="lbl">Oggetto</span><input class="inp" bind:value={avviso.oggetto} /></label>
                  <label class="field"><span class="lbl">Testo</span><textarea class="inp testo" rows="7" bind:value={avviso.testo}></textarea></label>
                  <p class="muted small nomargin">{v.contatti.length ? `Va a: ${v.contatti.join(', ')}` : 'Non c\'è un indirizzo: l\'avviso si registra ma non parte.'}</p>
                  <div class="row">
                    <button type="button" class="btn btn-secondary grow" onclick={() => (avviso = null)}>Annulla</button>
                    <button class="btn btn-primary grow" disabled={!avviso.oggetto.trim() || !avviso.testo.trim()}>Invia l'avviso</button>
                  </div>
                </form>
              {/if}
            </li>
          {/each}
        </ol>
      </section>
    {:else}
      <p class="muted vuoto">{pagina.turni.length ? 'Nessun turno con questi filtri.' : 'Nessuno in lista d\'attesa. Quando un turno è pieno, dalla lista degli iscritti si può mettere in attesa.'}</p>
    {/each}
  </div>
{:else if !errore}
  <p class="muted">Caricamento…</p>
{/if}

<style>
  .barra { display: flex; flex-wrap: wrap; gap: var(--space-12); align-items: center; }
  .spunta { display: flex; align-items: center; gap: var(--space-6); }
  .cerca { width: 240px; }
  .sx { align-self: flex-start; }
  .turni { display: grid; grid-template-columns: repeat(auto-fill, minmax(440px, 1fr)); gap: var(--space-14); align-items: start; }
  .turno { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-14) var(--space-16); }
  .turno.libero { border-color: var(--cloro); }
  .testa { display: flex; align-items: flex-start; gap: var(--space-10); }
  h3 { margin: 0; font-size: var(--text-xl); }
  .cap::first-letter { text-transform: uppercase; }
  .badge-s { display: inline-flex; align-items: center; height: 26px; padding: 0 var(--space-10); border-radius: var(--radius-pill); font-size: var(--text-sm);
    font-weight: 600; white-space: nowrap; }
  .verde { background: var(--cloro-soft); color: var(--cloro-ink); }
  .arancio { background: var(--oro-bg); color: var(--oro-fg); }
  .neutro { background: var(--surface-sunken); color: var(--ink); }
  .righe { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
  .righe > li { padding: var(--space-8) 0; border-top: 1px solid var(--line-soft); display: flex; flex-direction: column; gap: var(--space-8); }
  .righe > li:first-child { border-top: none; }
  .primo .pos { background: var(--cloro); color: var(--on-cloro); }
  .riga { display: flex; align-items: flex-start; gap: var(--space-10); }
  .pos { width: 26px; height: 26px; border-radius: 50%; background: var(--surface-sunken); display: inline-flex; align-items: center; justify-content: center;
    font-size: var(--text-sm); font-weight: 700; flex-shrink: 0; }
  .chi { display: flex; flex-direction: column; min-width: 0; gap: 1px; }
  .nome { font-weight: 600; color: var(--ink); }
  .avv { margin-left: var(--space-6); }
  .manca { color: var(--warn); font-weight: 600; }
  .azioni { display: flex; gap: var(--space-12); flex-wrap: wrap; justify-content: flex-end; }
  .azioni .link { min-height: 32px; }
  .rosso { color: var(--danger); }
  .avviso { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-10) var(--space-12); background: var(--carta); border-radius: var(--radius-md); }
  .testo { min-height: 150px; font-family: var(--font-body); line-height: 1.45; resize: vertical; }
  .vuoto { padding: var(--space-20); text-align: center; grid-column: 1 / -1; }
  @media (max-width: 700px) {
    .turni { grid-template-columns: 1fr; }
    .cerca { width: 100%; }
    .testa { flex-direction: column; gap: var(--space-6); }
    .riga { flex-wrap: wrap; }
    .azioni { width: 100%; justify-content: flex-start; padding-left: 36px; }
  }
</style>
