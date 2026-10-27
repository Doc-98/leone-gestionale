<script lang="ts">
  import { CHIUSURA_IN_SVILUPPO } from '../../lib/cassa';
  import { euro } from '../../lib/listino';

  /**
   * La chiusura di cassa di un giorno: fondo, contanti entrati e usciti, quanto dovrebbe esserci nel cassetto e la
   * differenza con i contati. È ancora in sviluppo (ADR 0018, parte 11): i conti funzionano come aiuto, ma non si
   * salva niente e i pulsanti spiegano perché.
   */
  let { entrati, usciti, giorno }: { entrati: number; usciti: number; giorno: string } = $props();

  let fondo = $state('');
  let contati = $state('');
  let avviso = $state(false);

  const numero = (s: string) => {
    const n = Number(s.replace(/\s|€/g, '').replace(/\./g, '').replace(',', '.'));
    return Number.isFinite(n) ? n : 0;
  };
  const attesi = $derived(Math.round((numero(fondo) + entrati - usciti) * 100) / 100);
  const diff = $derived(Math.round((numero(contati) - attesi) * 100) / 100);
</script>

<section class="card chiusura" aria-label="Chiusura di cassa">
  <div class="testa">
    <h2>Chiusura di cassa</h2>
    <span class="grow"></span>
    <span class="tag sviluppo">in sviluppo</span>
  </div>
  <div class="conti">
    <label class="riga"><span>Fondo cassa di {giorno}</span><input class="inp m piccolo" inputmode="decimal" bind:value={fondo} placeholder="0,00" aria-label="Fondo cassa" /></label>
    <span class="riga"><span>+ Contanti incassati</span><span class="m imp">{euro(entrati)}</span></span>
    <span class="riga"><span>− Contanti restituiti</span><span class="m imp">{euro(usciti)}</span></span>
    <span class="riga tot"><span>Attesi in cassa</span><span class="m imp">{euro(attesi)}</span></span>
  </div>
  <label class="field">
    <span class="lbl">Contati</span>
    <input class="inp m grande" inputmode="decimal" bind:value={contati} placeholder="Quanto c'è nel cassetto" />
  </label>
  {#if contati.trim()}
    <div class="diff" class:ok={diff === 0} class:piu={diff > 0} class:meno={diff < 0} role="status">
      <span>{diff === 0 ? 'La cassa torna' : diff > 0 ? 'Ci sono più contanti del previsto' : 'Mancano dei contanti'}</span>
      <span class="grow"></span><span class="m">{diff > 0 ? '+' : diff < 0 ? '−' : ''}{euro(Math.abs(diff))}</span>
    </div>
  {/if}
  <div class="bottoni">
    <button type="button" class="btn btn-primary" onclick={() => (avviso = true)}>Chiudi la cassa</button>
    <button type="button" class="btn btn-secondary" onclick={() => (avviso = true)}>Fondo per domani…</button>
  </div>
  {#if avviso}<p class="hint nomargin" role="status">{CHIUSURA_IN_SVILUPPO}</p>{/if}
  <span class="muted small">Carte e Satispay si controllano con l'estratto del POS.</span>
</section>

<style>
  .chiusura { padding: var(--space-16) var(--space-18, 18px); display: flex; flex-direction: column; gap: var(--space-12); }
  .testa { display: flex; align-items: center; gap: var(--space-10); }
  .testa h2 { margin: 0; font-size: var(--text-xl); }
  .sviluppo { background: var(--oro-bg); color: var(--oro-fg); }
  .conti { display: flex; flex-direction: column; gap: var(--space-6); font-size: var(--text-md); }
  .riga { display: flex; justify-content: space-between; align-items: center; gap: var(--space-10); }
  .riga.tot { padding-top: var(--space-8); border-top: 1px solid var(--line); font-weight: 700; }
  .imp { font-weight: 700; }
  .piccolo { width: 110px; height: 36px; text-align: right; }
  .grande { font-size: var(--text-xl); height: 48px; }
  .diff { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); font-weight: 700; font-size: var(--text-md); }
  .diff.ok { background: var(--passa-bg); color: var(--passa-fg); }
  .diff.piu { background: var(--warn-bg); color: var(--warn); }
  .diff.meno { background: var(--ferma-bg); color: var(--ferma-fg); }
  .bottoni { display: flex; gap: var(--space-8); flex-wrap: wrap; }
  .bottoni .btn-primary { flex: 1 1 auto; }
</style>
