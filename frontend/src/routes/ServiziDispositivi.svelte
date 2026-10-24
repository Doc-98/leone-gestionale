<script lang="ts">
  import { api, messaggioErrore, type Dispositivo, type Servizio } from '../lib/api';
  import { dataBreve, dataDi, oraDi } from '../lib/date';
  import { sessione } from '../lib/sessione.svelte';
  import Azione from '../components/Azione.svelte';
  import Icona from '../components/Icona.svelte';

  /**
   * Servizi a crediti (phon, doccia) e dispositivi (le macchine e i lettori del saldo). Ogni dispositivo ha una chiave
   * che si vede una volta sola, alla creazione: va scritta nella configurazione della macchina.
   */
  let servizi = $state<Servizio[]>([]);
  let dispositivi = $state<Dispositivo[]>([]);
  let errore = $state('');
  let messaggio = $state('');
  let chiave = $state<{ nome: string; id: string; chiave: string } | null>(null);
  const puo = $derived(sessione.puo('IMPOSTAZIONI'));

  async function carica() {
    try {
      [servizi, dispositivi] = await Promise.all([api.servizi(), api.dispositivi()]);
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

  type BozzaServizio = { id: string | null; nome: string; crediti: string; gratuitiAlGiorno: string; minuti: string; attivo: boolean };
  let servizio = $state<BozzaServizio | null>(null);
  const salvaServizio = () => {
    const s = servizio!;
    const dati = { nome: s.nome.trim(), crediti: Number(s.crediti), gratuitiAlGiorno: Number(s.gratuitiAlGiorno || 0), minuti: s.minuti ? Number(s.minuti) : null, attivo: s.attivo };
    return esegui(() => (s.id ? api.modificaServizio(s.id, dati) : api.creaServizio(dati)), `Servizio «${dati.nome}» salvato.`).then((ok) => ok && (servizio = null));
  };

  let nuovo = $state<{ nome: string; tipo: 'SERVIZIO' | 'SALDO'; servizioId: string } | null>(null);
  async function creaDispositivo() {
    const n = nuovo!;
    errore = '';
    try {
      const c = await api.creaDispositivo({ nome: n.nome.trim(), tipo: n.tipo, servizioId: n.tipo === 'SERVIZIO' ? n.servizioId : null });
      chiave = { nome: c.dispositivo.nome, id: c.dispositivo.id, chiave: c.chiave };
      nuovo = null;
      await carica();
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  async function rigenera(d: Dispositivo) {
    errore = '';
    try {
      const c = await api.nuovaChiaveDispositivo(d.id);
      chiave = { nome: d.nome, id: d.id, chiave: c.chiave };
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  const quando = (i: string | null) => (i ? `${dataBreve(dataDi(i))} ${oraDi(i)}` : 'mai');
</script>

<div class="pagina">
  {#if messaggio}<p class="msg ok nomargin" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  <section class="card blocco">
    <h3>Servizi</h3>
    <p class="muted small nomargin">Quanti crediti costa ogni uso e quante volte al giorno è gratis (es. la prima doccia). I minuti sono solo da mostrare sulla macchina.</p>
    <ul class="righe">
      {#each servizi as s (s.id)}
        <li class:spento={!s.attivo}>
          <strong class="grow">{s.nome}</strong>
          <span class="small">{s.crediti === 0 ? 'gratis' : s.crediti === 1 ? '1 credito' : `${s.crediti} crediti`}{s.gratuitiAlGiorno ? ` · ${s.gratuitiAlGiorno} gratis al giorno` : ''}{s.minuti ? ` · ${s.minuti} min` : ''}</span>
          {#if !s.attivo}<span class="tag t-altro">Spento</span>{/if}
          {#if puo}<Azione icona="matita" etichetta={`Modifica «${s.nome}»`} onclick={() => (servizio = { id: s.id, nome: s.nome, crediti: String(s.crediti), gratuitiAlGiorno: String(s.gratuitiAlGiorno), minuti: s.minuti ? String(s.minuti) : '', attivo: s.attivo })} />{/if}
        </li>
      {:else}
        <li class="muted small">Nessun servizio.</li>
      {/each}
    </ul>
    {#if servizio}
      <form class="modulo" onsubmit={(e) => { e.preventDefault(); salvaServizio(); }}>
        <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={servizio.nome} maxlength="60" placeholder="Es. Phon" /></label>
        <div class="tre">
          <label class="field"><span class="lbl">Crediti per uso</span><input class="inp m" type="number" min="0" bind:value={servizio.crediti} /></label>
          <label class="field"><span class="lbl">Gratis al giorno</span><input class="inp m" type="number" min="0" bind:value={servizio.gratuitiAlGiorno} /></label>
          <label class="field"><span class="lbl">Minuti (facoltativo)</span><input class="inp m" type="number" min="1" bind:value={servizio.minuti} /></label>
        </div>
        <label class="spunta"><input type="checkbox" bind:checked={servizio.attivo} /> Attivo</label>
        <div class="row">
          <button type="button" class="btn btn-secondary grow" onclick={() => (servizio = null)}>Annulla</button>
          <button class="btn btn-primary grow" disabled={!servizio.nome.trim() || servizio.crediti === ''}>Salva</button>
        </div>
      </form>
    {:else if puo}
      <button class="btn btn-secondary sx" onclick={() => (servizio = { id: null, nome: '', crediti: '1', gratuitiAlGiorno: '0', minuti: '', attivo: true })}><Icona nome="piu" />Nuovo servizio…</button>
    {/if}
  </section>

  <section class="card blocco">
    <h3>Dispositivi</h3>
    <p class="muted small nomargin">Le macchine (phon, docce) scalano i crediti; i lettori del saldo mostrano solo quanti crediti restano. Ogni dispositivo si presenta al gestionale con la sua chiave (vedi il README per l'API).</p>
    {#if chiave}
      <div class="chiave" role="status">
        <strong>Chiave di «{chiave.nome}»</strong>
        <code class="m">{chiave.chiave}</code>
        <span class="small">Id del dispositivo: <code class="m">{chiave.id}</code>. Scrivila adesso nella configurazione della macchina: non si potrà più rivedere (se si perde, se ne crea una nuova).</span>
        <button class="btn btn-secondary btn-sm sx" onclick={() => (chiave = null)}><Icona nome="check" />L'ho scritta</button>
      </div>
    {/if}
    <ul class="righe">
      {#each dispositivi as d (d.id)}
        <li class:spento={!d.attivo}>
          <span class="grow"><strong>{d.nome}</strong>
            <span class="small muted riga">{d.tipo === 'SALDO' ? 'Mostra il saldo' : d.servizio} · ultimo contatto: {quando(d.ultimoContatto)} · oggi {d.utilizziOggi} letture</span></span>
          {#if !d.attivo}<span class="tag t-altro">Spento</span>{/if}
          {#if puo}
            <span class="gruppo-azioni">
              <Azione icona="chiave" etichetta={`Chiave nuova per «${d.nome}» (la vecchia smette di funzionare)`} conferma="Chiave nuova?" onclick={() => rigenera(d)} />
              <Azione icona="spegni" etichetta={d.attivo ? `Spegni «${d.nome}»` : `Riaccendi «${d.nome}»`}
                onclick={() => esegui(() => api.modificaDispositivo(d.id, { nome: d.nome, attivo: !d.attivo }), d.attivo ? `${d.nome} spento.` : `${d.nome} riacceso.`)} />
            </span>
          {/if}
        </li>
      {:else}
        <li class="muted small">Nessun dispositivo.</li>
      {/each}
    </ul>
    {#if nuovo}
      <form class="modulo" onsubmit={(e) => { e.preventDefault(); creaDispositivo(); }}>
        <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={nuovo.nome} maxlength="80" placeholder="Es. Phon spogliatoio maschile" /></label>
        <div class="seg" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Tipo">
          <button type="button" aria-pressed={nuovo.tipo === 'SERVIZIO'} onclick={() => (nuovo!.tipo = 'SERVIZIO')}>Macchina (scala i crediti)</button>
          <button type="button" aria-pressed={nuovo.tipo === 'SALDO'} onclick={() => (nuovo!.tipo = 'SALDO')}>Lettore del saldo</button>
        </div>
        {#if nuovo.tipo === 'SERVIZIO'}
          <label class="field"><span class="lbl">Servizio</span>
            <select class="inp" bind:value={nuovo.servizioId}>{#each servizi.filter((s) => s.attivo) as s (s.id)}<option value={s.id}>{s.nome}</option>{/each}</select>
          </label>
        {/if}
        <div class="row">
          <button type="button" class="btn btn-secondary grow" onclick={() => (nuovo = null)}>Annulla</button>
          <button class="btn btn-primary grow" disabled={!nuovo.nome.trim() || (nuovo.tipo === 'SERVIZIO' && !nuovo.servizioId)}>Crea e mostra la chiave</button>
        </div>
      </form>
    {:else if puo}
      <button class="btn btn-secondary sx" onclick={() => (nuovo = { nome: '', tipo: 'SERVIZIO', servizioId: servizi.find((s) => s.attivo)?.id ?? '' })}><Icona nome="piu" />Nuovo dispositivo…</button>
    {/if}
  </section>
</div>

<style>
  .pagina { display: flex; flex-direction: column; gap: var(--space-14); max-width: 860px; }
  .blocco { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16); }
  h3 { margin: 0; font-size: var(--text-xl); }
  .righe { list-style: none; margin: 0; padding: 0; }
  .righe li { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; padding: var(--space-8) 0; border-top: 1px solid var(--line-soft); }
  .righe li:first-child { border-top: none; }
  .riga { display: block; }
  .spento { opacity: 0.6; }
  .modulo { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-12); border-radius: var(--radius-md); background: var(--surface-sunken); }
  .tre { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-10); }
  .spunta { display: flex; align-items: center; gap: var(--space-6); }
  select.inp { appearance: auto; }
  .sx { align-self: flex-start; }
  .chiave { display: flex; flex-direction: column; gap: var(--space-6); padding: var(--space-12); border-radius: var(--radius-md); background: var(--cloro-soft); color: var(--cloro-ink); }
  .chiave code { font-size: var(--text-lg); word-break: break-all; user-select: all; }
</style>
