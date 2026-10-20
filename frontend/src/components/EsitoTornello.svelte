<script lang="ts">
  import type { EsitoTornello } from '../lib/api';

  /**
   * L'esito di un passaggio: verde o rosso, il nome di chi porta il braccialetto (per accorgersi degli scambi) e
   * tutti i motivi del rosso, ognuno con cosa fare.
   */
  let { esito, schedaHref = null, simulato = false }: { esito: EsitoTornello; schedaHref?: string | null; simulato?: boolean } = $props();

  const p = $derived(esito.persona);
  const uscita = $derived(esito.verso === 'USCITA');
  const avvisi = $derived(esito.verde && esito.motivi.some((m) => !m.blocca));
  const parola = $derived(
    simulato ? (esito.verde ? 'Passerebbe' : 'Non passerebbe')
      : uscita ? (esito.verde ? 'Può uscire' : 'Non può uscire')
      : esito.verde ? 'Può entrare' : 'Non può entrare',
  );
  const eta = $derived(p?.eta == null ? '' : p.eta === 1 ? '1 anno' : `${p.eta} anni`);
</script>

<section class="esito" class:verde={esito.verde} class:rosso={!esito.verde} class:avvisi class:simulato aria-live="polite">
  <div class="stato">
    <span class="segno" aria-hidden="true">
      {#if esito.verde}<svg viewBox="0 0 24 24"><path d="m5 12.5 4.5 4.5L19 7.5" /></svg>
      {:else}<svg viewBox="0 0 24 24"><path d="M7 7l10 10M17 7 7 17" /></svg>{/if}
    </span>
    <span class="parola">{parola}</span>
    {#if uscita}<span class="tag lato">Uscita</span>{/if}
    {#if esito.goldenPass}<span class="tag oro">Golden pass</span>{/if}
    {#if esito.motivi.some((m) => m.codice.endsWith('SOTTO_CONTROLLO'))}<span class="tag oro">Sotto controllo</span>{/if}
  </div>

  {#if p}
    <div class="chi">
      {#if schedaHref}<a class="nome" href={schedaHref}>{p.nome} {p.cognome}</a>{:else}<span class="nome">{p.nome} {p.cognome}</span>{/if}
      <span class="dett">{eta}{esito.codice ? `${eta ? ' · ' : ''}braccialetto ${esito.codice}` : ''}</span>
    </div>
  {:else if esito.codice}
    <div class="chi"><span class="nome m">{esito.codice}</span><span class="dett">Nessuna persona associata</span></div>
  {/if}

  {#if esito.motivi.length}
    <ul class="motivi">
      {#each esito.motivi as m (m.codice + m.messaggio)}
        <li class:avviso={!m.blocca}>
          <span class="msg-m">{m.messaggio}</span>
          <span class="rimedio">{m.rimedio}</span>
        </li>
      {/each}
    </ul>
  {/if}

  {#if esito.carnet}
    {@const c = esito.carnet}
    <p class="carnet">
      Carnet: {c.scalato ? 'scalato un ingresso' : c.rientro ? 'rientro, nessun ingresso scalato' : 'utilizzabile'}
      · {c.usati} su {c.ingressi} usati
    </p>
  {/if}
</section>

<style>
  .esito {
    display: flex; flex-direction: column; gap: var(--space-12); padding: var(--space-16) var(--space-18, 18px);
    border-radius: var(--radius-xl); border: 2px solid var(--b); background: var(--bg); color: var(--fg);
  }
  .verde { --b: var(--passa); --bg: var(--passa-bg); --fg: var(--passa-fg); }
  .rosso { --b: var(--ferma); --bg: var(--ferma-bg); --fg: var(--ferma-fg); }
  .verde.avvisi { border-color: var(--oro); }
  .lato { background: var(--surface); color: var(--ink); border: 1px solid var(--line-strong); }
  .simulato { border-width: 1px; border-radius: var(--radius); }
  .stato { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .segno { width: 40px; height: 40px; border-radius: 50%; background: var(--b); display: grid; place-items: center; flex-shrink: 0; }
  .segno svg { width: 24px; height: 24px; fill: none; stroke: #fff; stroke-width: 3; stroke-linecap: round; stroke-linejoin: round; }
  .simulato .segno { width: 28px; height: 28px; }
  .simulato .segno svg { width: 17px; height: 17px; }
  .parola { font-family: var(--font-display); font-size: 26px; font-weight: 800; letter-spacing: -0.01em; }
  .simulato .parola { font-size: var(--text-xl); }
  .oro { background: var(--oro-bg); color: var(--oro-fg); border: 1px solid var(--oro); }
  .chi { display: flex; flex-direction: column; gap: 2px; }
  .nome { font-family: var(--font-display); font-size: 30px; font-weight: 800; line-height: 1.1; color: inherit; text-decoration: none; }
  a.nome:hover { text-decoration: underline; }
  .simulato .nome { display: none; }
  .simulato .chi:has(.m) .nome { display: inline; font-size: var(--text-lg); }
  .dett { font-size: var(--text-md); opacity: 0.85; }
  .motivi { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--space-8); }
  .motivi li { display: flex; flex-direction: column; gap: 2px; padding: var(--space-10) var(--space-12); background: var(--surface); color: var(--ink);
    border-radius: var(--radius-md); border-left: 4px solid var(--ferma); }
  .motivi li.avviso { border-left-color: var(--oro); }
  .msg-m { font-weight: 600; font-size: var(--text-base); }
  .rimedio { font-size: var(--text-sm); color: var(--muted); }
  .carnet { margin: 0; font-size: var(--text-md); font-weight: 500; }
</style>
