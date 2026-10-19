<script lang="ts">
  import type { Occorrenza, SpazioPiano } from '../lib/api';
  import { giornoEsteso, ora } from '../lib/date';
  import { colore, etichettaSpazi, nomeCategoria, STATI } from '../lib/piano';

  let { occorrenza: o, spazi, gestore, vediIscritti = false, azioni }: {
    occorrenza: Occorrenza;
    spazi: SpazioPiano[];
    gestore: boolean;
    vediIscritti?: boolean;
    azioni: {
      iscritti: () => void;
      saltaOSposta: () => void;
      ripristina: () => void;
      modificaEvento: () => void;
      apriCorso: () => void;
    };
  } = $props();

  const lezione = $derived(o.tipo === 'LEZIONE');
  const modificata = $derived(!!o.eccezioneId);
</script>

<div class="dett {colore(o.categoria)}">
  <div class="testa">
    <span class="tag cat">{nomeCategoria(o)}</span>
    {#if STATI[o.stato]}<span class="tag t-warn">{STATI[o.stato]}</span>{/if}
  </div>
  <h3>{o.titolo}</h3>
  {#if o.dettaglio}<p class="muted nomargin">{o.dettaglio}</p>{/if}

  <dl>
    <dt>Quando</dt>
    <dd><span class="cap">{giornoEsteso(o.data)}</span> · <span class="m">{o.categoria === 'CHIUSURA' && ora(o.inizio) === '00:00' ? 'tutto il giorno' : `${ora(o.inizio)}–${ora(o.fine)}`}</span></dd>
    <dt>Dove</dt>
    <dd>{etichettaSpazi(o.spazi, spazi)}</dd>
    {#if o.istruttore}<dt>Istruttore</dt><dd>{o.istruttore.nome}</dd>{/if}
    {#if o.iscritti != null}<dt>Iscritti</dt><dd>{o.iscritti}{o.posti != null ? ` su ${o.posti} posti` : ''}</dd>{/if}
    {#if o.nota}<dt>Nota</dt><dd>{o.nota}</dd>{/if}
  </dl>

  {#if o.conflitti.length}
    <div class="msg err">
      Si sovrappone a:
      <ul>{#each o.conflitti as c (c)}<li>{c}</li>{/each}</ul>
    </div>
  {/if}

  {#if vediIscritti}
    <div class="azioni"><button class="btn btn-primary" onclick={azioni.iscritti}>Iscritti e presenze</button></div>
  {/if}
  {#if gestore}
    <div class="azioni">
      {#if lezione}
        {#if modificata}
          <button class="btn btn-secondary" onclick={azioni.ripristina}>
            {o.stato === 'SALTATA' ? 'Si tiene regolarmente' : 'Annulla lo spostamento'}
          </button>
        {:else if o.stato === 'REGOLARE'}
          <button class="btn btn-secondary" onclick={azioni.saltaOSposta}>Salta o sposta questa data…</button>
        {/if}
        <button class="btn btn-secondary" onclick={azioni.apriCorso}>Apri il corso</button>
      {:else if o.stato !== 'ANNULLATO'}
        <button class="btn btn-secondary" onclick={azioni.modificaEvento}>Modifica o annulla l'evento…</button>
      {/if}
    </div>
  {/if}
</div>

<style>
  .dett { display: flex; flex-direction: column; gap: var(--space-8); }
  .testa { display: flex; gap: var(--space-6); flex-wrap: wrap; }
  .cat { background: var(--c-bg); color: var(--c-fg); box-shadow: inset 3px 0 0 var(--c-line); }
  h3 { margin: 0; font-family: var(--font-display); font-size: var(--text-2xl); font-weight: 800; line-height: 1.15; }
  dl { display: grid; grid-template-columns: 96px 1fr; gap: var(--space-6) var(--space-12); margin: var(--space-4) 0 0; }
  dt { font-size: var(--text-xs); font-weight: 600; letter-spacing: .06em; text-transform: uppercase; color: var(--muted); padding-top: 2px; }
  dd { margin: 0; }
  .cap::first-letter { text-transform: uppercase; }
  ul { margin: var(--space-4) 0 0; padding-left: var(--space-16); }
  .azioni { display: flex; flex-direction: column; gap: var(--space-8); margin-top: var(--space-8); }
</style>
