<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import { api, messaggioErrore, type ImpostazioniRicevute, type PaginaRicevute, type SerieRicevute, type TipoSerie } from '../lib/api';
  import { euro } from '../lib/listino';
  import { anteprimaNumero, FORMATI_PRONTI, RIPARTE } from '../lib/ricevute';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Impostazioni delle ricevute (ADR 0018, parte 10): le serie con la loro numerazione, cosa c'è scritto e come si
   * consegnano. Le modifiche si salvano tutte insieme dalla barra in basso, come nelle altre impostazioni.
   */
  type Bozza = { imp: ImpostazioniRicevute; serie: SerieRicevute[] };
  let pagina = $state<PaginaRicevute | null>(null);
  let bozza = $state<Bozza | null>(null);
  let originale = $state('');
  let scelta = $state(0);
  let errore = $state('');
  let messaggio = $state('');
  let nuova = $state<{ nome: string; tipo: TipoSerie } | null>(null);
  const puo = $derived(sessione.puo('IMPOSTAZIONI'));

  async function carica() {
    try {
      pagina = await api.impostazioniRicevute();
      bozza = { imp: { ...pagina.impostazioni }, serie: pagina.serie.map((s) => ({ ...s })) };
      originale = JSON.stringify(bozza);
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  carica();

  const sporco = $derived(!!bozza && JSON.stringify(bozza) !== originale);
  const serie = $derived(bozza?.serie[scelta]);
  const anno = new Date().getFullYear();
  /** La stagione per l'anteprima: quella del periodo della serie, se la serie riparte a ogni stagione. */
  const stagione = $derived(bozza?.serie.find((s) => s.riparte === 'STAGIONE')?.periodo ?? `${anno}/${String(anno + 1).slice(2)}`);
  const prossimoN = (s: SerieRicevute) => (s.emesse > 0 ? null : s.prossimoNumero) ?? (s.emesse + 1);
  const anteprima = (s: SerieRicevute) => (s.emesse > 0 && JSON.stringify(s) === JSON.stringify(pagina?.serie.find((x) => x.id === s.id)) ? s.prossima
    : anteprimaNumero(s.formato, s.cifre, stagione, anno, prossimoN(s)));

  async function salva() {
    if (!bozza || !pagina) return;
    errore = '';
    messaggio = '';
    const prima = JSON.parse(originale) as Bozza;
    try {
      for (const s of bozza.serie) {
        const p = prima.serie.find((x) => x.id === s.id);
        if (JSON.stringify(p) !== JSON.stringify(s))
          await api.modificaSerieRicevute(s.id, { nome: s.nome.trim(), attiva: s.attiva, formato: s.formato.trim(), cifre: Number(s.cifre), riparte: s.riparte,
            prossimoNumero: s.prossimoNumero ? Number(s.prossimoNumero) : null });
      }
      if (JSON.stringify(prima.imp) !== JSON.stringify(bozza.imp))
        await api.modificaImpostazioniRicevute({ ...bozza.imp, bolloSoglia: Number(bozza.imp.bolloSoglia), bolloImporto: Number(bozza.imp.bolloImporto) });
      await carica();
      messaggio = 'Salvato: vale dalla prossima ricevuta.';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  async function creaSerie() {
    errore = '';
    try {
      await api.creaSerieRicevute(nuova!.nome.trim(), nuova!.tipo);
      nuova = null;
      await carica();
      scelta = (bozza?.serie.length ?? 1) - 1;
      messaggio = 'Serie aggiunta, spenta: accendila quando è pronta.';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  type Scelte = { valore: string; opzioni: { id: string; nome: string }[]; scegli: (v: string) => void };
  const seg = <T extends string>(valore: T, opzioni: { id: T; nome: string }[], scegli: (v: T) => void): Scelte => ({ valore, opzioni, scegli: (v) => scegli(v as T) });
</script>

<div class="pagina">
  {#if messaggio}<p class="msg ok nomargin" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  {#if bozza && pagina}
    <div class="colonne">
      <div class="princ">
        <section class="card sez" id="imp-serie" aria-label="Serie e numerazione">
          <div class="sez-t"><h2 class="grow">Serie e numerazione</h2>
            {#if puo && !nuova}<button type="button" class="mini-btn" onclick={() => (nuova = { nome: '', tipo: 'RICEVUTA' })}><Icona nome="piu" />Nuova serie</button>{/if}</div>
          <p class="muted small nomargin spiega-s">Ogni serie ha la sua numerazione. Un numero usato non torna mai libero, nemmeno se la ricevuta si annulla.</p>
          {#if nuova}
            <form class="nuova" onsubmit={(e) => { e.preventDefault(); creaSerie(); }}>
              <input class="inp" bind:value={nuova.nome} placeholder="Nome (es. Ricevute del campo estivo)" maxlength="60" aria-label="Nome della serie" />
              <select class="inp" bind:value={nuova.tipo} aria-label="Tipo"><option value="RICEVUTA">Ricevute</option><option value="FATTURA">Fatture</option></select>
              <button class="btn btn-primary" disabled={!nuova.nome.trim()}>Aggiungi</button>
              <button type="button" class="btn btn-secondary" onclick={() => (nuova = null)}>Annulla</button>
            </form>
          {/if}
          <div class="serie" role="group" aria-label="Le serie">
            {#each bozza.serie as s, i (s.id)}
              <button type="button" class="serie-b" aria-pressed={i === scelta} onclick={() => (scelta = i)}>
                <span class="ico-s" aria-hidden="true"><Icona nome="ricevuta" /></span>
                <span class="t"><b>{s.nome || 'Senza nome'}</b><span>{s.tipo === 'FATTURA' ? 'Fatture' : 'Ricevute'} · {s.emesse === 1 ? '1 nel periodo' : `${s.emesse} nel periodo`} {s.periodo || ''}</span></span>
                <span class="pill {s.attiva ? 'passa' : ''}">{s.attiva ? 'In uso' : 'Spenta'}</span>
                <span class="m num">{anteprima(s)}</span>
              </button>
            {/each}
          </div>
          {#if serie}
            <div class="dettaglio">
              <div class="riga-imp"><span class="t"><b>Nome e uso</b><span>Una serie spenta non fa ricevute; quelle già fatte restano.</span></span>
                <span class="ctl"><input class="inp largo" bind:value={serie.nome} maxlength="60" disabled={!puo} aria-label="Nome della serie" />
                  <button type="button" class="switch" role="switch" aria-checked={serie.attiva} aria-label="Serie in uso" disabled={!puo} onclick={() => (serie.attiva = !serie.attiva)}><span></span></button></span></div>
              <div class="riga-imp"><span class="t"><b>Formato del numero</b><span>Con {'{stagione}'} o {'{anno}'} il numero dice anche quando è stata fatta; {'{numero}'} non può mancare.</span></span>
                <span class="ctl colonna">
                  <span class="chips" role="group" aria-label="Formati pronti">
                    {#each FORMATI_PRONTI as f (f)}<button type="button" class="chip-f m" aria-pressed={serie.formato === f} disabled={!puo} onclick={() => (serie.formato = f)}>{f}</button>{/each}
                  </span>
                  <input class="inp m largo" bind:value={serie.formato} maxlength="40" disabled={!puo} aria-label="Formato del numero" />
                </span></div>
              <div class="riga-imp"><span class="t"><b>Il numero riparte da 1</b><span>Di solito a ogni stagione, come il tesseramento.</span></span>
                <span class="ctl"><span class="seg tre" role="group" aria-label="Quando riparte">
                  {#each RIPARTE as r (r.id)}<button type="button" aria-pressed={serie.riparte === r.id} disabled={!puo} onclick={() => (serie.riparte = r.id)}>{r.nome}</button>{/each}
                </span></span></div>
              <div class="riga-imp"><span class="t"><b>Cifre del numero</b><span>Con 4 cifre: 0001, 0002…</span></span>
                <span class="ctl"><input class="inp m corto" type="number" min="1" max="8" bind:value={serie.cifre} disabled={!puo} aria-label="Cifre del numero" /><span class="unita">cifre</span></span></div>
              <div class="riga-imp"><span class="t"><b>Partire da un numero</b>
                <span>{serie.emesse > 0 ? 'In questo periodo ci sono già ricevute: la numerazione continua da sé.' : 'Per continuare la numerazione del gestionale di prima. Vuoto: da 1.'}</span></span>
                <span class="ctl"><input class="inp m corto" type="number" min="1" placeholder="1" value={serie.prossimoNumero ?? ''} disabled={!puo || serie.emesse > 0} aria-label="Primo numero"
                  oninput={(e) => (serie.prossimoNumero = e.currentTarget.value ? Number(e.currentTarget.value) : null)} /></span></div>
              <div class="riga-imp"><span class="t"><b>La prossima sarà</b></span><span class="ctl"><span class="esempio m">{anteprima(serie)}</span></span></div>
            </div>
          {/if}
        </section>

        <section class="card sez" id="imp-scritto" aria-label="Cosa c'è scritto">
          <div class="sez-t"><h2 class="grow">Cosa c'è scritto</h2><span class="spiega">l'intestazione viene dai dati della società</span></div>
          <div class="riga-imp"><span class="t"><b>Intestazione</b>
            <span>Ragione sociale, partita IVA o codice fiscale, indirizzo.{#if pagina.mancaIntestazione.length}{' '}<strong class="manca">Manca: {pagina.mancaIntestazione.join(', ')}.</strong>{/if}</span></span>
            <span class="ctl"><a class="mini-btn" href="#/societa">Dati della società →</a></span></div>
          {@render scelte('Intestata a', 'Per un minore paga quasi sempre un genitore: la ricevuta è sua, con il nome del figlio. Intestate a chi frequenta, sono una per persona.',
            seg(bozza.imp.intestata, [{ id: 'PAGANTE', nome: 'A chi paga' }, { id: 'BENEFICIARIO', nome: 'A chi frequenta' }], (v) => (bozza!.imp.intestata = v)))}
          <div class="riga-imp"><span class="t"><b>Codici fiscali</b><span>Di chi paga e di chi frequenta: servono per le detrazioni e per i rimborsi welfare.</span></span>
            <span class="ctl"><button type="button" class="switch" role="switch" aria-checked={bozza.imp.codiciFiscali} aria-label="Codici fiscali sulla ricevuta" disabled={!puo}
              onclick={() => (bozza!.imp.codiciFiscali = !bozza!.imp.codiciFiscali)}><span></span></button></span></div>
          <div class="riga-imp"><span class="t"><b>Marca da bollo</b><span>Sopra la soglia la ricevuta ha il riquadro per la marca.</span></span>
            <span class="ctl"><button type="button" class="switch" role="switch" aria-checked={bozza.imp.bollo} aria-label="Marca da bollo" disabled={!puo} onclick={() => (bozza!.imp.bollo = !bozza!.imp.bollo)}><span></span></button>
              <span class="unita">sopra</span><input class="inp m corto" type="number" min="0" step="0.01" bind:value={bozza.imp.bolloSoglia} disabled={!puo || !bozza.imp.bollo} aria-label="Soglia della marca da bollo in euro" />
              <span class="unita">€, da</span><input class="inp m corto" type="number" min="0" step="0.01" bind:value={bozza.imp.bolloImporto} disabled={!puo || !bozza.imp.bollo} aria-label="Importo della marca da bollo in euro" /><span class="unita">€</span></span></div>
          <div class="riga-imp colonna-imp"><span class="t"><b>Dicitura in fondo</b><span>Il testo per le detrazioni o per l'ente. Con {'{beneficiario}'} e {'{nascita}'} si inseriscono nome e data di nascita di chi frequenta.</span></span>
            <textarea class="inp dicitura" rows="3" maxlength="1000" value={bozza.imp.dicitura ?? ''} disabled={!puo} aria-label="Dicitura in fondo alla ricevuta"
              oninput={(e) => (bozza!.imp.dicitura = e.currentTarget.value || null)}></textarea></div>
          <div class="riga-imp"><span class="t"><b>Spazio per la firma</b><span>Una riga «Per la società» in fondo, per firmare la copia stampata.</span></span>
            <span class="ctl"><button type="button" class="switch" role="switch" aria-checked={bozza.imp.firma} aria-label="Spazio per la firma" disabled={!puo} onclick={() => (bozza!.imp.firma = !bozza!.imp.firma)}><span></span></button></span></div>
        </section>

        <section class="card sez" id="imp-consegna" aria-label="Come si consegna">
          <div class="sez-t"><h2 class="grow">Come si consegna</h2><span class="spiega">quando si registra un pagamento</span></div>
          {@render scelte('Dopo ogni pagamento', 'Chiedi: compare il pannello con stampa, PDF ed email. Stampa subito: la ricevuta si fa e si apre per la stampa. Niente: si fa quando serve, dal pagamento.',
            seg(bozza.imp.dopo, [{ id: 'CHIEDI', nome: 'Chiedi' }, { id: 'STAMPA', nome: 'Stampa subito' }, { id: 'NIENTE', nome: 'Niente' }], (v) => (bozza!.imp.dopo = v)))}
          <div class="riga-imp"><span class="t"><b>Manda per email a chi paga</b><span>Appena fatta, in allegato, all'indirizzo nella scheda. Senza email si stampa.</span></span>
            <span class="ctl"><button type="button" class="switch" role="switch" aria-checked={bozza.imp.email} aria-label="Manda per email" disabled={!puo} onclick={() => (bozza!.imp.email = !bozza!.imp.email)}><span></span></button></span></div>
          {@render scelte('Un genitore paga per più figli', 'Una ricevuta con una riga per figlio, o una per figlio (comodo per le detrazioni di ciascuno).',
            seg(bozza.imp.famiglia, [{ id: 'CHIEDI', nome: 'Chiedi ogni volta' }, { id: 'FAMIGLIA', nome: 'Una per famiglia' }, { id: 'PERSONA', nome: 'Una per figlio' }], (v) => (bozza!.imp.famiglia = v)))}
        </section>
      </div>

      <aside class="lato">
        <section class="card pann">
          <span class="lbl">Anteprima</span>
          <div class="mini-r" aria-label="Anteprima della ricevuta">
            <span class="rs">{pagina.mancaIntestazione.includes('ragione sociale') ? 'Nome della società' : 'Intestazione della società'}</span>
            {#if pagina.mancaIntestazione.length}<span class="manca">Manca: {pagina.mancaIntestazione.join(', ')}</span>{/if}
            <span class="n"><span>{serie?.tipo === 'FATTURA' ? 'Fattura' : 'Ricevuta'} n.</span><b class="m">{serie ? anteprima(serie) : ''}</b></span>
            <span class="riga"><span>Ricevuto da</span><b>{bozza.imp.intestata === 'PAGANTE' ? 'il genitore che paga' : 'chi frequenta'}</b></span>
            {#if bozza.imp.codiciFiscali}<span class="riga"><span>C.F.</span><span>sì</span></span>{/if}
            <span class="riga"><span>Corso di nuoto · 1ª rata</span><span>{euro(320)}</span></span>
            <span class="tot"><span>Totale</span><span>{euro(320)}</span></span>
            {#if bozza.imp.bollo && 320 > Number(bozza.imp.bolloSoglia)}<span class="bollo">marca da bollo {euro(Number(bozza.imp.bolloImporto))}</span>{/if}
            {#if bozza.imp.dicitura}<span class="dic">{bozza.imp.dicitura}</span>{/if}
            {#if bozza.imp.firma}<span class="firma">Per la società</span>{/if}
          </div>
        </section>
      </aside>
    </div>

    {#if sporco && puo}
      <div class="salva-bar" role="status">
        <span>Ci sono modifiche non salvate</span>
        <button type="button" class="vuoto" onclick={() => (bozza = JSON.parse(originale))}>Annulla</button>
        <button type="button" class="chiaro" onclick={salva}>Salva</button>
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
  .spiega-s { padding: var(--space-12) var(--space-16) 0; }
  .nuova { display: flex; gap: var(--space-8); flex-wrap: wrap; padding: var(--space-12) var(--space-16) 0; }
  .nuova .inp:first-child { flex: 1 1 240px; }
  select.inp { appearance: auto; width: auto; }
  .serie { display: flex; flex-direction: column; margin-top: var(--space-10); border-top: 1px solid var(--line-soft); }
  .serie-b { display: flex; align-items: center; gap: var(--space-12); flex-wrap: wrap; padding: var(--space-12) var(--space-16); border: none; border-top: 1px solid var(--line-soft);
    background: var(--surface); font: inherit; color: var(--ink); text-align: left; cursor: pointer; }
  .serie-b:first-child { border-top: none; }
  .serie-b[aria-pressed='true'] { background: var(--cloro-soft); box-shadow: inset 3px 0 0 var(--cloro); }
  .ico-s { width: 38px; height: 38px; border-radius: var(--radius-sm); display: grid; place-items: center; background: var(--carta); color: var(--seg-fg); flex-shrink: 0; }
  .serie-b .t { flex: 1 1 200px; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
  .serie-b .t span { font-size: var(--text-sm); color: var(--muted); }
  .num { font-size: var(--text-md); }
  .dettaglio { border-top: 1px solid var(--line); }
  .ctl { display: flex; align-items: center; gap: var(--space-8); flex-wrap: wrap; }
  .ctl.colonna { flex-direction: column; align-items: flex-end; }
  .ctl .inp { height: 40px; }
  .ctl .largo { width: 240px; max-width: 100%; }
  .ctl .corto { width: 96px; }
  .chips { display: flex; gap: var(--space-6); flex-wrap: wrap; justify-content: flex-end; }
  .chip-f { position: relative; min-height: 32px; padding: 0 var(--space-10); border-radius: var(--radius-pill); border: 1px solid var(--line); background: var(--surface); font-size: var(--text-xs);
    color: var(--seg-fg); cursor: pointer; }
  .chip-f::after { content: ''; position: absolute; left: 0; right: 0; top: 50%; height: 44px; transform: translateY(-50%); }
  .chip-f[aria-pressed='true'] { background: var(--fill); border-color: var(--fill); color: var(--on-fill); }
  .seg { min-width: 260px; }
  .seg.tre { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .unita { font-size: var(--text-md); color: var(--muted); }
  .esempio { font-size: var(--text-base); font-weight: 500; padding: var(--space-6) var(--space-10); border-radius: var(--radius-sm); background: var(--carta); }
  .manca { color: var(--warn); font-weight: 600; }
  .colonna-imp { flex-direction: column; align-items: stretch; }
  /* in colonna la spiegazione non deve allungarsi (fuori colonna ha una base di 240px) */
  .colonna-imp > .t { flex: none; }
  .dicitura { width: 100%; box-sizing: border-box; min-height: 90px; font: inherit; font-size: var(--text-md); line-height: 1.45; padding: var(--space-10) var(--space-12); resize: vertical; }
  .pann { padding: var(--space-16); display: flex; flex-direction: column; gap: var(--space-10); }
  /* la ricevuta in piccolo: carta bianca anche nel tema scuro, come sarà stampata */
  .mini-r { display: flex; flex-direction: column; gap: var(--space-6); padding: var(--space-16); border-radius: var(--radius-md); background: #fff; color: #1d2a30; border: 1px solid var(--line);
    box-shadow: 0 2px 10px rgba(16, 33, 43, 0.08); font-size: var(--text-xs); }
  .mini-r .rs { font-family: var(--font-display); font-size: var(--text-md); font-weight: 800; }
  .mini-r .manca { color: #8a4b00; }
  .mini-r .n { display: flex; justify-content: space-between; align-items: baseline; border-top: 1px solid #e3ddd1; padding-top: var(--space-8); margin-top: var(--space-4); }
  .mini-r .riga, .mini-r .tot { display: flex; justify-content: space-between; gap: var(--space-10); }
  .mini-r .tot { border-top: 1px dashed #cfc8bb; padding-top: var(--space-6); font-weight: 700; }
  .mini-r .bollo { align-self: flex-end; width: 64px; height: 64px; border: 1.5px dashed #b9b1a3; border-radius: 6px; display: grid; place-items: center; text-align: center; color: #5d655f; line-height: 1.2; }
  .mini-r .dic { color: #4a524c; line-height: 1.35; white-space: pre-line; }
  .mini-r .firma { align-self: flex-start; min-width: 140px; border-top: 1px solid #1d2a30; padding-top: 2px; margin-top: var(--space-12); color: #5d655f; }
</style>
