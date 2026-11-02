<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import { api, messaggioErrore, type Dispositivo, type RegoleBorsellino, type Servizio } from '../lib/api';
  import { dataBreve, dataDi, oraDi } from '../lib/date';
  import { euro } from '../lib/listino';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Phon e docce (ADR 0024): tutto quello che riguarda il braccialetto come borsellino, in una pagina. Quanto vale un
   * credito, quanto costa ogni servizio, il credito in euro, la cauzione; a destra le macchine con la loro chiave.
   * Regole e servizi si salvano tutti insieme; le macchine subito.
   */
  let regole = $state<RegoleBorsellino | null>(null);
  let scade = $state(true);
  let servizi = $state<(Servizio & { nuovo?: boolean })[]>([]);
  let dispositivi = $state<Dispositivo[]>([]);
  let originale = '';
  let errore = $state('');
  let messaggio = $state('');
  let chiave = $state<{ nome: string; id: string; chiave: string } | null>(null);
  const puo = $derived(sessione.puo('IMPOSTAZIONI'));

  const istantanea = () => JSON.stringify({ regole, scade, servizi });
  async function carica() {
    try {
      const [r, s, d] = await Promise.all([api.regoleBorsellino(), api.servizi(), api.dispositivi()]);
      regole = r;
      scade = r.validitaEuroMesi != null;
      servizi = s;
      dispositivi = d;
      originale = istantanea();
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  carica();

  const sporco = $derived(!!regole && istantanea() !== originale);

  async function salva() {
    errore = '';
    messaggio = '';
    try {
      const r = regole!;
      await api.modificaRegoleBorsellino({ ...r, valoreCredito: Number(r.valoreCredito), ricaricaMinima: Number(r.ricaricaMinima), cauzione: Number(r.cauzione),
        validitaEuroMesi: scade ? Number(r.validitaEuroMesi ?? 12) : null });
      const prima = JSON.parse(originale).servizi as Servizio[];
      for (const s of servizi) {
        const dati = { nome: s.nome.trim(), crediti: Number(s.crediti), gratuitiAlGiorno: Number(s.gratuitiAlGiorno || 0), minuti: s.minuti ? Number(s.minuti) : null, attivo: s.attivo };
        if (s.nuovo) { if (dati.nome) await api.creaServizio(dati); }
        else if (JSON.stringify(prima.find((p) => p.id === s.id)) !== JSON.stringify(s)) await api.modificaServizio(s.id, dati);
      }
      await carica();
      messaggio = 'Modifiche salvate.';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  const nuovoServizio = () => (servizi = [...servizi, { id: `nuovo-${servizi.length}`, nome: '', crediti: 1, gratuitiAlGiorno: 0, minuti: null, attivo: true, ordine: servizi.length, nuovo: true }]);
  const ricaricaCrediti = $derived(regole && Number(regole.valoreCredito) > 0 ? Math.floor(Number(regole.ricaricaMinima) / Number(regole.valoreCredito)) : 0);

  // ── Macchine ──────────────────────────────────────────────────────────
  let nuova = $state<{ nome: string; tipo: 'SERVIZIO' | 'SALDO'; servizioId: string } | null>(null);
  async function creaDispositivo() {
    const n = nuova!;
    errore = '';
    try {
      const c = await api.creaDispositivo({ nome: n.nome.trim(), tipo: n.tipo, servizioId: n.tipo === 'SERVIZIO' ? n.servizioId : null });
      chiave = { nome: c.dispositivo.nome, id: c.dispositivo.id, chiave: c.chiave };
      nuova = null;
      dispositivi = await api.dispositivi();
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  async function rigenera(d: Dispositivo) {
    if (!confirm(`Una chiave nuova per «${d.nome}»? Quella vecchia smette subito di funzionare.`)) return;
    errore = '';
    try {
      const c = await api.nuovaChiaveDispositivo(d.id);
      chiave = { nome: d.nome, id: d.id, chiave: c.chiave };
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  async function accendi(d: Dispositivo) {
    try {
      await api.modificaDispositivo(d.id, { nome: d.nome, attivo: !d.attivo });
      dispositivi = await api.dispositivi();
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  const quando = (i: string | null) => (i ? `ultimo contatto ${dataBreve(dataDi(i))} ${oraDi(i)}` : 'mai collegata');
  const maiCollegate = $derived(dispositivi.filter((d) => d.attivo && !d.ultimoContatto).length);
  const gruppiMacchine = $derived([
    { nome: 'Macchine', voci: dispositivi.filter((d) => d.tipo === 'SERVIZIO') },
    { nome: 'Lettori del saldo', voci: dispositivi.filter((d) => d.tipo === 'SALDO') },
  ].filter((g) => g.voci.length));
</script>

{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
{#if messaggio && !sporco}<p class="msg ok" role="status">{messaggio}</p>{/if}

{#if regole}
  <div class="colonne-p">
    <fieldset class="princ" disabled={!puo}>
      <section class="card sez" id="imp-crediti" aria-label="Crediti e servizi">
        <div class="sez-t"><h2 class="grow">Crediti e servizi</h2></div>
        <div class="riga-imp"><span class="t"><b>Un credito vale</b><span>Si comprano al banco o alla cassa automatica.</span></span>
          <span class="ctl"><input class="inp num" type="number" min="0.01" step="0.01" bind:value={regole.valoreCredito} aria-label="Un credito vale" /><span class="unita">€</span></span></div>
        <div class="riga-imp"><span class="t"><b>Ricarica minima</b></span>
          <span class="ctl"><input class="inp num" type="number" min="0" step="0.01" bind:value={regole.ricaricaMinima} aria-label="Ricarica minima" /><span class="unita">€</span></span></div>
        <div class="tabella" id="imp-servizi">
          <table class="serv">
            <thead><tr><th>Servizio</th><th>Crediti per uso</th><th>Gratis al giorno</th><th>Minuti</th><th><span class="sr">Acceso</span></th></tr></thead>
            <tbody>
              {#each servizi as s (s.id)}
                <tr class:spento={!s.attivo}>
                  <td class="nome">{#if s.nuovo}<input class="inp nome-n" bind:value={s.nome} placeholder="Es. Phon" aria-label="Nome del servizio" />{:else}{s.nome}{/if}</td>
                  <td><input class="inp" type="number" min="0" bind:value={s.crediti} aria-label="Crediti per uso, {s.nome}" /></td>
                  <td><input class="inp" type="number" min="0" bind:value={s.gratuitiAlGiorno} aria-label="Gratis al giorno, {s.nome}" /></td>
                  <td><input class="inp" type="number" min="1" bind:value={s.minuti} placeholder="—" aria-label="Minuti, {s.nome}" /></td>
                  <td><button type="button" class="switch" role="switch" aria-checked={s.attivo} aria-label="{s.nome || 'Servizio'} acceso" onclick={() => (s.attivo = !s.attivo)}><span></span></button></td>
                </tr>
              {:else}
                <tr><td colspan="5" class="muted small">Nessun servizio.</td></tr>
              {/each}
            </tbody>
          </table>
        </div>
        <div class="conto-c">
          {#if ricaricaCrediti}<span class="grow">Con la ricarica minima di {euro(Number(regole.ricaricaMinima))}: <b>{ricaricaCrediti} crediti</b>{#each servizi.filter((s) => s.attivo && Number(s.crediti) > 0).slice(0, 2) as s (s.id)}, {Math.floor(ricaricaCrediti / Number(s.crediti))} usi di {s.nome.toLowerCase()}{/each}.</span>{:else}<span class="grow"></span>{/if}
          <button type="button" class="mini-btn tratt" onclick={nuovoServizio}><Icona nome="piu" />Servizio</button>
        </div>
      </section>

      <section class="card sez" id="imp-euro" aria-label="Credito in euro">
        <div class="sez-t"><h2 class="grow">Credito in euro</h2><span class="spiega">rimborsi di recesso e sospensione, cauzioni restituite</span></div>
        <div class="riga-imp"><span class="t"><b>Scade</b><span>Dopo quanto il credito non usato si perde. Si usa per pagare con il metodo «Borsellino».</span></span>
          <span class="ctl"><button type="button" class="switch" role="switch" aria-checked={scade} aria-label="Il credito scade" onclick={() => (scade = !scade)}><span></span></button>
            <input class="inp num" type="number" min="1" max="120" bind:value={regole.validitaEuroMesi} disabled={!scade} aria-label="Mesi" /><span class="unita">mesi</span></span></div>
        <div class="riga-imp"><span class="t"><b>Si può trasferire a un familiare</b><span>Genitori, figli, fratelli.</span></span>
          <span class="ctl"><button type="button" class="switch" role="switch" aria-checked={regole.trasferibile} aria-label="Trasferibile" onclick={() => (regole!.trasferibile = !regole!.trasferibile)}><span></span></button></span></div>
        <div class="riga-imp"><span class="t"><b>Si può restituire in denaro</b><span>Serve anche il permesso «Rimborsi e correzioni del borsellino».</span></span>
          <span class="ctl"><button type="button" class="switch" role="switch" aria-checked={regole.restituibile} aria-label="Restituibile in denaro" onclick={() => (regole!.restituibile = !regole!.restituibile)}><span></span></button></span></div>
      </section>

      <section class="card sez" id="imp-braccialetto" aria-label="Braccialetto">
        <div class="sez-t"><h2 class="grow">Braccialetto</h2></div>
        <div class="riga-imp"><span class="t"><b>Cauzione alla consegna</b><span>0 = nessuna cauzione.</span></span>
          <span class="ctl"><input class="inp num" type="number" min="0" step="0.01" bind:value={regole.cauzione} aria-label="Cauzione" /><span class="unita">€</span></span></div>
        <div class="riga-imp"><span class="t"><b>Alla riconsegna, cauzione e crediti tornano</b></span>
          <span class="ctl"><span class="seg seg-s" role="group" aria-label="Alla riconsegna">
            {#each [['DENARO', 'In denaro'], ['BORSELLINO', 'Nel borsellino'], ['A_SCELTA', 'A scelta']] as [v, t] (v)}<button type="button" aria-pressed={regole.cauzioneRiconsegna === v} onclick={() => (regole!.cauzioneRiconsegna = v as RegoleBorsellino['cauzioneRiconsegna'])}>{t}</button>{/each}
          </span></span></div>
        <div class="riga-imp"><span class="t"><b>Riconsegnando l'ultimo braccialetto</b><span>Si propone di rimborsare i crediti di phon e docce.</span></span>
          <span class="ctl"><button type="button" class="switch" role="switch" aria-checked={regole.creditiRiconsegna} aria-label="Rimborso dei crediti alla riconsegna" onclick={() => (regole!.creditiRiconsegna = !regole!.creditiRiconsegna)}><span></span></button></span></div>
        <div class="riga-imp"><span class="t"><b>Se il braccialetto si smarrisce, i crediti</b><span>Il braccialetto nuovo si paga come una consegna.</span></span>
          <span class="ctl"><span class="seg seg-s due" role="group" aria-label="Se si smarrisce">
            {#each [['RESTANO', 'Restano alla persona'], ['PERSI', 'Si perdono']] as [v, t] (v)}<button type="button" aria-pressed={regole.creditiSmarrimento === v} onclick={() => (regole!.creditiSmarrimento = v as RegoleBorsellino['creditiSmarrimento'])}>{t}</button>{/each}
          </span></span></div>
      </section>
    </fieldset>

    <aside class="lato-p largo-p">
      <section class="card sez" id="imp-macchine" aria-label="Macchine">
        <div class="sez-t"><h2 class="grow">Macchine</h2>{#if puo && !nuova}<button type="button" class="mini-btn" onclick={() => (nuova = { nome: '', tipo: 'SERVIZIO', servizioId: servizi.find((s) => s.attivo && !s.nuovo)?.id ?? '' })}><Icona nome="piu" />Nuova</button>{/if}</div>
        {#if chiave}
          <div class="chiave" role="status">
            <strong>Chiave di «{chiave.nome}»</strong>
            <code class="m">{chiave.chiave}</code>
            <span class="small">Id: <code class="m">{chiave.id}</code>. Copiala adesso nella macchina: non si potrà più rivedere (se si perde, se ne crea una nuova).</span>
            <button type="button" class="mini-btn sx" onclick={() => (chiave = null)}><Icona nome="check" />L'ho copiata</button>
          </div>
        {/if}
        {#if nuova}
          <form class="nuova" onsubmit={(e) => { e.preventDefault(); creaDispositivo(); }}>
            <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={nuova.nome} maxlength="80" placeholder="Es. Phon spogliatoio maschile" /></label>
            <div class="seg" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Tipo">
              <button type="button" aria-pressed={nuova.tipo === 'SERVIZIO'} onclick={() => (nuova!.tipo = 'SERVIZIO')}>Macchina</button>
              <button type="button" aria-pressed={nuova.tipo === 'SALDO'} onclick={() => (nuova!.tipo = 'SALDO')}>Lettore del saldo</button>
            </div>
            {#if nuova.tipo === 'SERVIZIO'}
              <label class="field"><span class="lbl">Servizio</span><select class="inp" bind:value={nuova.servizioId}>{#each servizi.filter((s) => s.attivo && !s.nuovo) as s (s.id)}<option value={s.id}>{s.nome}</option>{/each}</select></label>
            {/if}
            <div class="row">
              <button type="button" class="btn btn-secondary grow" onclick={() => (nuova = null)}>Annulla</button>
              <button class="btn btn-primary grow" disabled={!nuova.nome.trim() || (nuova.tipo === 'SERVIZIO' && !nuova.servizioId)}>Crea e mostra la chiave</button>
            </div>
          </form>
        {/if}
        {#if maiCollegate}
          <div class="avvisa"><Icona nome="avviso" />{maiCollegate === dispositivi.filter((d) => d.attivo).length ? 'Nessuna si è ancora collegata' : `${maiCollegate} non si sono ancora collegate`}: ognuna va configurata con la sua chiave.</div>
        {/if}
        {#each gruppiMacchine as g (g.nome)}
          <div class="dove-m">{g.nome}</div>
          {#each g.voci as d (d.id)}
            <div class="macc" class:spento={!d.attivo}>
              <span class="ico-m"><Icona nome={d.tipo === 'SALDO' ? 'occhio' : 'macchine'} /></span>
              <span class="t"><b>{d.nome}</b><span>{d.tipo === 'SALDO' ? 'mostra il saldo' : d.servizio?.toLowerCase()} · {quando(d.ultimoContatto)}{d.utilizziOggi ? ` · oggi ${d.utilizziOggi}` : ''}{d.attivo ? '' : ' · spenta'}</span></span>
              {#if puo}
                <button type="button" class="mini-btn" onclick={() => rigenera(d)}>Chiave</button>
                <button type="button" class="x-btn" aria-label={d.attivo ? `Spegni «${d.nome}»` : `Riaccendi «${d.nome}»`} title={d.attivo ? 'Spegni' : 'Riaccendi'} onclick={() => accendi(d)}><Icona nome="spegni" /></button>
              {/if}
            </div>
          {/each}
        {:else}
          <p class="muted small vuoto">Nessuna macchina.</p>
        {/each}
        <span class="muted small piede">La chiave si mostra una volta sola: copiala nella macchina. Le istruzioni per il tecnico sono nel manuale.</span>
      </section>
    </aside>
  </div>

  {#if sporco}
    <div class="salva-bar" role="status">
      <span>Modifiche da salvare</span>
      <button type="button" class="vuoto" onclick={carica}>Annulla</button>
      <button type="button" class="chiaro" onclick={salva}>Salva</button>
    </div>
  {/if}
{:else if !errore}
  <p class="muted">Caricamento…</p>
{/if}

<style>
  fieldset { border: none; margin: 0; padding: 0; min-width: 0; }
  .largo-p { flex-basis: 380px !important; max-width: 440px !important; }
  .tabella { overflow-x: auto; border-top: 1px solid var(--line-soft); }
  .serv { width: 100%; border-collapse: collapse; font-size: var(--text-md); }
  .serv th { padding: var(--space-10) var(--space-16); text-align: left; font-size: 11px; font-weight: 700; letter-spacing: 0.07em; text-transform: uppercase; color: var(--muted);
    border-bottom: 1px solid var(--line); white-space: nowrap; }
  .serv td { padding: var(--space-10) var(--space-16); border-top: 1px solid var(--line-soft); }
  .serv td .inp { width: 70px; height: 36px; text-align: center; font-family: var(--font-mono); }
  .serv .nome { font-weight: 600; }
  .serv .nome-n { width: 160px; text-align: left; font-family: var(--font-body); }
  .spento { opacity: 0.6; }
  .conto-c { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; padding: var(--space-12) var(--space-16); background: var(--carta); border-top: 1px solid var(--line-soft);
    font-size: var(--text-md); }
  .conto-c b { font-family: var(--font-mono); }
  .seg-s { display: grid; grid-template-columns: repeat(3, auto); }
  .seg-s.due { grid-template-columns: repeat(2, auto); }
  .seg-s button { padding: 0 var(--space-12); white-space: nowrap; }
  .chiave { display: flex; flex-direction: column; gap: var(--space-6); margin: var(--space-12) var(--space-16) 0; padding: var(--space-12); border-radius: var(--radius-md);
    background: var(--cloro-soft); color: var(--cloro-ink); }
  .chiave code { font-size: var(--text-lg); word-break: break-all; user-select: all; }
  .sx { align-self: flex-start; }
  .nuova { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-12) var(--space-16); background: var(--carta); border-bottom: 1px solid var(--line-soft); }
  select.inp { appearance: auto; }
  .avvisa { display: flex; gap: var(--space-8); margin: var(--space-12) var(--space-16) var(--space-4); padding: var(--space-8) var(--space-10); border-radius: var(--radius-sm);
    background: var(--warn-bg); color: var(--warn); font-size: var(--text-sm); font-weight: 600; }
  .avvisa :global(.ic) { width: 16px; height: 16px; flex-shrink: 0; margin-top: 1px; }
  .dove-m { padding: var(--space-10) var(--space-16) var(--space-4); font-size: 11px; font-weight: 700; letter-spacing: 0.07em; text-transform: uppercase; color: var(--muted); }
  .macc { display: flex; align-items: center; gap: var(--space-10); padding: 11px var(--space-16); border-top: 1px solid var(--line-soft); }
  .ico-m { width: 34px; height: 34px; border-radius: 10px; display: grid; place-items: center; background: var(--surface-sunken); color: var(--seg-fg); flex-shrink: 0; }
  .ico-m :global(.ic) { width: 18px; height: 18px; }
  .macc .t { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
  .macc .t b { font-size: var(--text-md); }
  .macc .t span { font-size: var(--text-xs); color: var(--muted); }
  .vuoto { padding: var(--space-12) var(--space-16); margin: 0; }
  .piede { padding: var(--space-10) var(--space-16) var(--space-14); display: block; border-top: 1px solid var(--line-soft); }
</style>
