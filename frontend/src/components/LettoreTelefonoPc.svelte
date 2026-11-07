<script lang="ts">
  import Icona from './Icona.svelte';
  import { api } from '../lib/api';
  import { oraDi } from '../lib/date';
  import { indirizzoPerTelefono, lettoreTelefono as l } from '../lib/lettore.svelte';

  /**
   * I lettori di questo PC (ADR 0017, 0025): un'etichetta che dice com'è il lettore e, aperta, l'elenco — il lettore da
   * scrivania, il telefono Android come lettore (con il QR e il codice di 6 cifre) e, se i crediti di phon e docce
   * stanno sul braccialetto, il lettore della memoria. I braccialetti letti dal telefono arrivano qui come se fossero
   * passati sul lettore da scrivania.
   */
  let { compatto = false }: { compatto?: boolean } = $props();

  let aperto = $state(false);
  let qr = $state('');
  let adesso = $state(Date.now());
  let sulBraccialetto = $state(false);
  api.statoCreditiBraccialetto().then((s) => (sulBraccialetto = s.sulBraccialetto)).catch(() => {});

  const indirizzo = $derived(indirizzoPerTelefono());
  const link = $derived(l.abbinamento ? `${indirizzo.url}/#/lettore/${l.abbinamento.token}` : '');
  const collegato = $derived(adesso > 0 && l.collegato);
  const inAttesa = $derived(!!l.abbinamento && !collegato);
  let mostraQr = $state(true);

  $effect(() => {
    if (!link) return void (qr = '');
    // la libreria del QR si scarica solo quando serve
    import('qrcode').then((m) => m.default.toString(link, { type: 'svg', margin: 1, errorCorrectionLevel: 'M' })).then((s) => (qr = s)).catch(() => (qr = ''));
  });
  // lo stato «collegato» dipende dal tempo che passa
  $effect(() => {
    const t = setInterval(() => (adesso = Date.now()), 5000);
    return () => clearInterval(t);
  });
  // appena il telefono si collega il QR si chiude (si riapre a mano)
  $effect(() => {
    if (collegato) mostraQr = false;
  });

  async function collega() {
    aperto = true;
    mostraQr = true;
    if (!l.abbinamento) await l.abbina();
  }

  const codice = $derived(l.abbinamento ? `${l.abbinamento.codice.slice(0, 3)} ${l.abbinamento.codice.slice(3)}` : '');
  const riassunto = $derived(collegato ? 'Telefono collegato' : inAttesa ? 'In attesa del telefono…' : 'Lettori');
</script>

<div class="lettori-pc" class:compatto>
  <button type="button" class="chip-l" class:ok={collegato} class:attesa={inAttesa} aria-expanded={aperto} onclick={() => (aperto = !aperto)}>
    <i></i>{riassunto}<Icona nome={aperto ? 'su' : 'giu'} />
  </button>

  {#if aperto}
    <section class="card pannello-l" aria-label="I lettori di questo PC">
      <div class="riga-l">
        <span class="ico-l"><Icona nome="nfc" /></span>
        <span class="t"><b>Lettore da scrivania</b><span>Il lettore USB accanto alla cassa: si passa il braccialetto e il codice compare nel campo, come scritto dalla tastiera.</span></span>
      </div>

      <div class="riga-l">
        <span class="ico-l"><Icona nome="telefono" /></span>
        <span class="t"><b>Telefono come lettore</b>
          <span>{#if collegato}Collegato{#if l.ultimo} · ultimo letto <span class="m">{l.ultimo.codice}</span> alle {oraDi(new Date(l.ultimo.quando).toISOString())}{/if}.
            {:else if inAttesa}Inquadra il QR qui sotto con il telefono.
            {:else}Un telefono Android con NFC legge i braccialetti e li manda qui, se il lettore da scrivania non c'è.{/if}</span></span>
        {#if !l.abbinamento}
          <button type="button" class="mini-btn pieno" onclick={collega}>Collega un telefono</button>
        {:else}
          <span class="azioni-l">
            {#if collegato}<button type="button" class="mini-btn" aria-pressed={mostraQr} onclick={() => (mostraQr = !mostraQr)}>{mostraQr ? 'Nascondi il QR' : 'Mostra il QR'}</button>{/if}
            <button type="button" class="mini-btn rosso" onclick={() => l.scollega()}>Scollega</button>
          </span>
        {/if}
      </div>

      {#if l.abbinamento && mostraQr}
        <div class="abbina">
          {#if qr}<div class="qr" role="img" aria-label="QR da inquadrare con il telefono">{@html qr}</div>{/if}
          <div class="passi-c">
            <ol class="passi small">
              <li>Sul telefono Android apri la fotocamera e <strong>inquadra il QR</strong>.</li>
              <li>Oppure in Chrome apri <span class="m">{indirizzo.url}/#/lettore</span> e scrivi il codice.</li>
              <li>Sul telefono tocca <strong>Inizia a leggere</strong> e appoggia i braccialetti.</li>
            </ol>
            <span class="codice-c"><span class="lbl">Codice</span><span class="codice-g m">{codice}</span></span>
            {#if !collegato}<span class="attesa-t" role="status"><i></i>In attesa del telefono…</span>{/if}
            {#if indirizzo.localhost}
              <p class="hint nomargin"><span>Questo PC è aperto su «localhost», che il telefono non raggiunge: apri il gestionale dall'indirizzo di rete del PC.</span></p>
            {:else if indirizzo.sviluppo}
              <p class="muted small nomargin">Sviluppo: perché il telefono raggiunga il PC, il frontend va avviato con «npm run dev:telefono» e in Chrome sul telefono l'indirizzo va segnato come sicuro (vedi README).</p>
            {/if}
          </div>
        </div>
      {/if}
      {#if l.errore}<p class="msg err nomargin riga-e" role="alert">{l.errore}</p>{/if}

      {#if sulBraccialetto}
        <div class="riga-l">
          <span class="ico-l spento"><Icona nome="braccialetto" /></span>
          <span class="t"><b>Lettore della memoria del braccialetto</b><span>Per scrivere i crediti di phon e docce sul braccialetto. Si collega quando sapremo che chip sono e come sono scritti i crediti.</span></span>
          <span class="pill">Non ancora collegato</span>
        </div>
      {/if}
    </section>
  {/if}
</div>

<style>
  /* l'etichetta sta nella riga di chi la usa; il pannello va a capo, largo quanto la riga */
  .lettori-pc { display: contents; }
  .chip-l { display: inline-flex; align-items: center; gap: var(--space-8); min-height: 44px; padding: 0 var(--space-14); border-radius: var(--radius-pill); border: 1px solid var(--line);
    background: var(--surface); font: inherit; font-size: var(--text-md); font-weight: 600; color: var(--ink); cursor: pointer; white-space: nowrap; align-self: flex-start; }
  .chip-l :global(.ic) { width: 16px; height: 16px; color: var(--muted); }
  .chip-l i { width: 9px; height: 9px; border-radius: 50%; background: var(--line-strong); }
  .chip-l.ok i { background: var(--passa); box-shadow: 0 0 0 3px var(--passa-bg); }
  .chip-l.attesa i { background: var(--warn); }
  .chip-l[aria-expanded='true'] { border-color: var(--cloro); box-shadow: 0 0 0 3px var(--cloro-soft); }
  .pannello-l { flex-basis: 100%; width: 100%; padding: 0; overflow: hidden; display: flex; flex-direction: column; }
  .riga-l { display: flex; align-items: center; gap: var(--space-14); padding: var(--space-14) 18px; border-top: 1px solid var(--line-soft); flex-wrap: wrap; }
  .riga-l:first-child { border-top: none; }
  .ico-l { width: 40px; height: 40px; border-radius: var(--radius-sm); display: grid; place-items: center; background: var(--cloro-soft); color: var(--cloro-ink); flex-shrink: 0; }
  .ico-l.spento { background: var(--surface-sunken); color: var(--muted); }
  .t { flex: 1 1 260px; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
  .t b { font-size: var(--text-base); }
  .t > span { font-size: var(--text-sm); color: var(--muted); }
  .azioni-l { display: flex; gap: var(--space-6); }
  .abbina { display: flex; gap: var(--space-20); align-items: center; flex-wrap: wrap; padding: 18px 20px; border-top: 1px solid var(--line-soft); background: var(--carta); }
  .qr { width: 176px; height: 176px; background: #fff; padding: 8px; border-radius: var(--radius-md); border: 1px solid var(--line); flex-shrink: 0; box-sizing: border-box; }
  .qr :global(svg) { width: 100%; height: 100%; display: block; }
  .passi-c { flex: 1 1 260px; display: flex; flex-direction: column; gap: var(--space-12); }
  .passi { margin: 0; padding-left: 1.2em; display: flex; flex-direction: column; gap: var(--space-6); }
  .codice-c { display: flex; flex-direction: column; gap: var(--space-4); }
  .codice-g { font-size: 34px; font-weight: 500; letter-spacing: 0.08em; line-height: 1; }
  .attesa-t { display: inline-flex; align-items: center; gap: var(--space-8); color: var(--muted); font-size: var(--text-md); font-weight: 600; }
  .attesa-t i { width: 10px; height: 10px; border-radius: 50%; background: var(--warn); animation: lampeggia 1.4s ease-in-out infinite; }
  @keyframes lampeggia { 50% { opacity: 0.3; } }
  @media (prefers-reduced-motion: reduce) { .attesa-t i { animation: none; } }
  .riga-e { margin: 0 18px var(--space-14); }
</style>
