<script lang="ts">
  import Azione from './Azione.svelte';
  import Icona from './Icona.svelte';
  import { oraDi } from '../lib/date';
  import { indirizzoPerTelefono, lettoreTelefono as l } from '../lib/lettore.svelte';

  /**
   * Sul PC: usa un telefono Android come lettore NFC (ADR 0017). Si inquadra il QR (o si scrive il codice di 6 cifre)
   * e i braccialetti letti dal telefono arrivano qui come se fossero passati su un lettore da scrivania.
   */
  let { compatto = false }: { compatto?: boolean } = $props();

  let qr = $state('');
  let mostraQr = $state(true);
  let adesso = $state(Date.now());

  const indirizzo = $derived(indirizzoPerTelefono());
  const link = $derived(l.abbinamento ? `${indirizzo.url}/#/lettore/${l.abbinamento.token}` : '');
  const collegato = $derived(adesso > 0 && l.collegato);

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

  const codice = $derived(l.abbinamento ? `${l.abbinamento.codice.slice(0, 3)} ${l.abbinamento.codice.slice(3)}` : '');
</script>

<section class="card lettore-tel" class:compatto>
  {#if !l.abbinamento}
    <div class="riga">
      <Icona nome="nfc" />
      <div class="grow">
        <strong>Usa il telefono come lettore</strong>
        {#if !compatto}<div class="muted small">Senza lettore da scrivania: un telefono Android con NFC legge i braccialetti e li manda qui.</div>{/if}
      </div>
      <button type="button" class="btn btn-secondary" onclick={() => l.abbina()}>Collega un telefono</button>
    </div>
    {#if l.errore}<p class="msg err nomargin" role="alert">{l.errore}</p>{/if}
  {:else}
    <div class="riga">
      <span class="stato" class:ok={collegato}><span class="punto"></span>{collegato ? 'Telefono collegato' : 'In attesa del telefono…'}</span>
      <span class="grow"></span>
      <span class="gruppo-azioni">
        {#if collegato}<Azione icona="qr" etichetta="Mostra il QR per collegare il telefono" premuto={mostraQr} onclick={() => (mostraQr = !mostraQr)} />{/if}
        <Azione icona="scollega" tono="danger" etichetta="Scollega il telefono" onclick={() => l.scollega()} />
      </span>
    </div>
    {#if l.ultimo}
      <div class="small">Ultimo letto: <span class="m">{l.ultimo.codice}</span> <span class="muted">alle {oraDi(new Date(l.ultimo.quando).toISOString())}</span></div>
    {/if}
    {#if mostraQr}
      <div class="abbina">
        {#if qr}<div class="qr" aria-label="QR da inquadrare con il telefono">{@html qr}</div>{/if}
        <ol class="passi small">
          <li>Sul telefono Android apri la fotocamera e <strong>inquadra il QR</strong>.</li>
          <li>Oppure apri in Chrome <span class="m">{indirizzo.url}/#/lettore</span> e scrivi il codice <strong class="m">{codice}</strong>.</li>
          <li>Sul telefono tocca «Inizia a leggere» e appoggia i braccialetti.</li>
        </ol>
      </div>
      {#if indirizzo.localhost}
        <p class="hint nomargin"><span>Questo PC è aperto su «localhost», che il telefono non raggiunge: apri il gestionale dall'indirizzo di rete del PC.</span></p>
      {:else if indirizzo.sviluppo}
        <p class="muted small nomargin">Sviluppo: perché il telefono raggiunga il PC, il frontend va avviato con «npm run dev:telefono» e in Chrome sul telefono l'indirizzo va segnato come sicuro (vedi README).</p>
      {/if}
    {/if}
    {#if l.errore}<p class="msg err nomargin" role="alert">{l.errore}</p>{/if}
  {/if}
</section>

<style>
  .lettore-tel { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-12) var(--space-14); }
  .compatto { padding: var(--space-10) var(--space-12); box-shadow: none; }
  .riga { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .riga > :global(.ic) { width: 26px; height: 26px; fill: none; stroke: var(--cloro); stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; flex-shrink: 0; }
  .stato { display: inline-flex; align-items: center; gap: var(--space-8); font-weight: 600; color: var(--muted); }
  .stato.ok { color: var(--cloro-ink); }
  .punto { width: 10px; height: 10px; border-radius: 50%; background: var(--line-strong, #8f8677); }
  .stato.ok .punto { background: var(--passa, var(--cloro)); box-shadow: 0 0 0 4px var(--cloro-soft); }
  .abbina { display: flex; gap: var(--space-14); align-items: center; flex-wrap: wrap; }
  .qr { width: 168px; height: 168px; background: #fff; padding: 6px; border-radius: var(--radius-md); flex-shrink: 0; }
  .qr :global(svg) { width: 100%; height: 100%; display: block; }
  .passi { margin: 0; padding-left: 1.2em; display: flex; flex-direction: column; gap: var(--space-6); flex: 1; min-width: 220px; }
</style>
