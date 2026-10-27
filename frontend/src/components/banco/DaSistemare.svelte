<script lang="ts">
  import Icona from '../Icona.svelte';
  import { daQuanto, motivoBreve, PROBLEMI, problema, rimedio, type DaSistemare, type Sistemato, type VistaSistemare } from '../../lib/banco';
  import { oraDi } from '../../lib/date';

  /**
   * «Da sistemare»: chi oggi è rimasto fuori e non è più passato, in quattro viste a scelta (schede con il rimedio,
   * righe con l'azione, pillole, colonne per motivo), e sotto lo storico di chi è stato sistemato. Un clic sul nome
   * riapre l'esito; l'azione porta dove si risolve (la scheda del cliente).
   */
  let {
    fuori,
    sistemati,
    vista,
    scelto,
    adesso,
    onapri,
    schede,
  }: {
    fuori: DaSistemare[];
    sistemati: Sistemato[];
    vista: VistaSistemare;
    scelto: string | null;
    adesso: Date;
    onapri: (passaggioId: string) => void;
    /** si possono aprire le schede dei clienti */
    schede: boolean;
  } = $props();

  const nome = (d: { persona: DaSistemare['persona']; codice: string }) => (d.persona ? `${d.persona.nome} ${d.persona.cognome}` : `Braccialetto ${d.codice}`);
  const iniziali = (d: { persona: DaSistemare['persona'] }) => (d.persona ? `${d.persona.nome[0] ?? ''}${d.persona.cognome[0] ?? ''}`.toUpperCase() : '?');
  const tipoDi = (d: DaSistemare) => PROBLEMI.find((x) => x.id === problema(d.motivi))!;
  const cosa = (s: Sistemato) => `Fermato alle ${oraDi(s.fermatoIl)} per «${motivoBreve(s.motivi)}» · passato alle ${oraDi(s.passatoIl)}`;

  // per motivo: ancora fuori o sistemati
  let vediSistemati = $state(false);
  const colonne = $derived(PROBLEMI.map((p) => ({ ...p, voci: fuori.filter((d) => problema(d.motivi) === p.id) })).filter((c) => c.voci.length || c.id !== 'altro'));
</script>

{#snippet azione(d: DaSistemare, cls = 'link-azione')}
  {#if schede}{@const r = rimedio(d)}<a class={cls} href={r.href}>{r.azione}</a>{/if}
{/snippet}

{#snippet storico(classe: string)}
  {#if sistemati.length}
    <h3 class="lbl sotto-titolo">Sistemati oggi · {sistemati.length}</h3>
    <ul class="sistemati {classe}">
      {#each sistemati as s (s.passaggioId)}
        <li title={cosa(s)}>
          <span class="spunta" aria-hidden="true"><Icona nome="check" /></span>
          <span class="s-testo"><span class="s-nome">{nome(s)}</span><span class="s-cosa">{classe === 'pillole' ? motivoBreve(s.motivi) : cosa(s)}</span></span>
          <span class="m ora">{oraDi(s.passatoIl)}</span>
        </li>
      {/each}
    </ul>
  {/if}
{/snippet}

{#if vista === 'motivi'}
  <div class="seg scelta" role="group" aria-label="Quali mostrare">
    <button type="button" aria-pressed={!vediSistemati} onclick={() => (vediSistemati = false)}>Ancora fuori · {fuori.length}</button>
    <button type="button" aria-pressed={vediSistemati} onclick={() => (vediSistemati = true)}>Sistemati oggi · {sistemati.length}</button>
  </div>
{/if}

{#if !fuori.length && !(vista === 'motivi' && vediSistemati)}
  <p class="nessuno muted small">Nessuno è rimasto fuori. Chi prende un rosso compare qui finché non ripassa in verde.</p>
{:else if vista === 'schede'}
  <ul class="schede">
    {#each fuori as d (d.passaggioId)}
      {@const t = tipoDi(d)}
      <li class="scheda" class:scelto={d.passaggioId === scelto}>
        <span class="testa">
          <span class="ico" aria-hidden="true"><Icona nome={t.icona} /></span>
          <span class="chi">
            <button type="button" class="nome" onclick={() => onapri(d.passaggioId)}>{nome(d)}</button>
            <span class="m ora">{oraDi(d.istante)} · fuori da {daQuanto(d.primaVolta, adesso)}{d.volte > 1 ? ` · ${d.volte} volte` : ''}</span>
          </span>
        </span>
        {#each d.motivi as m (m.codice + m.messaggio)}
          <span class="motivo"><span class="m-testo">{m.messaggio}</span><span class="m-rimedio">{m.rimedio}</span></span>
        {/each}
        <span class="piede">{@render azione(d)}<span class="grow"></span><button type="button" class="link-muto" onclick={() => onapri(d.passaggioId)}>Riapri l'esito</button></span>
      </li>
    {/each}
  </ul>
  {@render storico('schede')}
{:else if vista === 'righe'}
  <div class="card righe">
    {#each fuori as d (d.passaggioId)}
      <div class="riga" class:scelto={d.passaggioId === scelto}>
        <span class="iniziali" aria-hidden="true">{iniziali(d)}</span>
        <span class="chi">
          <span class="r-testa"><button type="button" class="nome" onclick={() => onapri(d.passaggioId)}>{nome(d)}</button>
            <span class="m ora">{oraDi(d.istante)} · fuori da {daQuanto(d.primaVolta, adesso)}</span></span>
          <span class="motivi">{#each d.motivi as m (m.codice + m.messaggio)}<span class="pastiglia">{m.messaggio}</span>{/each}</span>
        </span>
        {@render azione(d, 'btn btn-sm bottone-ferma')}
      </div>
    {/each}
  </div>
  {@render storico('righe')}
{:else if vista === 'pillole'}
  <div class="pillole">
    {#each fuori as d (d.passaggioId)}
      <button type="button" class="pillola" class:scelto={d.passaggioId === scelto} title={d.motivi.map((m) => m.messaggio).join(' · ')} onclick={() => onapri(d.passaggioId)}>
        <span class="iniziali" aria-hidden="true">{iniziali(d)}</span>
        <span class="p-testo"><span class="nome">{nome(d)}</span><span class="p-motivo">{motivoBreve(d.motivi)} <span class="m ora">· {oraDi(d.istante)}</span></span></span>
      </button>
    {/each}
  </div>
  {@render storico('pillole')}
{:else if !vediSistemati}
  <div class="colonne">
    {#each colonne as c (c.id)}
      <div class="card colonna">
        <span class="c-testa"><span class="ico piccola" aria-hidden="true"><Icona nome={c.icona} /></span><span class="lbl">{c.nome}</span><span class="grow"></span><b class="c-n">{c.voci.length}</b></span>
        {#each c.voci as d (d.passaggioId)}
          <button type="button" class="voce" class:scelto={d.passaggioId === scelto} onclick={() => onapri(d.passaggioId)}>
            <span class="v-testa"><span class="nome">{nome(d)}</span><span class="m ora">{oraDi(d.istante)}</span></span>
            <span class="v-motivo">{motivoBreve(d.motivi)}</span>
          </button>
        {:else}
          <span class="muted small">Nessuno.</span>
        {/each}
      </div>
    {/each}
  </div>
{:else}
  {#if sistemati.length}
    <ul class="sistemati righe-s card">
      {#each sistemati as s (s.passaggioId)}
        <li><span class="spunta" aria-hidden="true"><Icona nome="check" /></span><span class="s-testo"><span class="s-nome">{nome(s)}</span><span class="s-cosa">{cosa(s)}</span></span><span class="m ora">{oraDi(s.passatoIl)}</span></li>
      {/each}
    </ul>
  {:else}
    <p class="nessuno muted small">Nessuno sistemato, per ora.</p>
  {/if}
{/if}

<style>
  .nome, .link-muto, .pillola, .voce { font: inherit; color: inherit; text-align: left; }
  .nome { font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; min-width: 0; }
  button.nome { border: none; background: none; padding: 0; cursor: pointer; color: var(--ink); }
  button.nome:hover { text-decoration: underline; }
  .ora { font-size: var(--text-xs); color: var(--muted); font-weight: 400; }
  .nessuno { margin: 0; padding: var(--space-12) var(--space-14); border: 1px dashed var(--line-dashed, var(--line)); border-radius: var(--radius-md); }
  .scelto { outline: 2px solid var(--cloro); outline-offset: -2px; }
  .iniziali { width: 30px; height: 30px; border-radius: 50%; display: grid; place-items: center; flex-shrink: 0; background: var(--ferma); color: var(--surface); font-size: 11px; font-weight: 700; }
  .ico { width: 34px; height: 34px; border-radius: 9px; display: grid; place-items: center; flex-shrink: 0; background: var(--ferma-bg); color: var(--ferma); }
  .ico.piccola { width: 28px; height: 28px; border-radius: 8px; }
  .scelta { grid-template-columns: 1fr 1fr; max-width: 320px; }
  .sotto-titolo { margin: var(--space-8) 0 0; }

  /* schede */
  .schede { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(270px, 1fr)); gap: var(--space-10); }
  .scheda { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-14) var(--space-12) var(--space-16); border-radius: var(--radius-md);
    background: var(--surface); box-shadow: inset 4px 0 0 var(--ferma), inset 0 0 0 1px var(--line); }
  .scheda.scelto { background: var(--cloro-soft); }
  .testa { display: flex; align-items: center; gap: var(--space-10); min-width: 0; }
  .chi { flex: 1; min-width: 0; display: flex; flex-direction: column; line-height: 1.3; }
  .motivo { display: flex; flex-direction: column; gap: 2px; padding-top: var(--space-8); border-top: 1px solid var(--line-soft); font-size: var(--text-sm); }
  .m-testo { font-weight: 700; color: var(--ferma); }
  .m-rimedio { color: var(--muted); }
  .piede { display: flex; align-items: center; gap: var(--space-12); margin-top: auto; }
  .link-azione { font-size: var(--text-sm); font-weight: 700; color: var(--ferma); text-decoration: none; }
  .link-azione::after { content: ' →'; }
  .link-azione:hover { text-decoration: underline; }
  .link-muto { border: none; background: none; padding: 0; cursor: pointer; font-size: var(--text-sm); font-weight: 600; color: var(--muted); }
  .link-muto:hover { color: var(--ink); }

  /* righe */
  .righe { padding: 0; overflow: hidden; }
  .riga { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: var(--space-12); align-items: center; padding: var(--space-12) var(--space-16);
    border-top: 1px solid var(--line-soft); box-shadow: inset 3px 0 0 var(--ferma); }
  .riga:first-child { border-top: none; }
  .riga.scelto { background: var(--cloro-soft); }
  .r-testa { display: flex; align-items: baseline; gap: var(--space-8); flex-wrap: wrap; }
  .motivi { display: flex; gap: var(--space-6); flex-wrap: wrap; margin-top: 4px; }
  .pastiglia { padding: 1px 8px; border-radius: var(--radius-pill); background: var(--ferma-bg); color: var(--ferma); font-size: var(--text-xs); font-weight: 600;
    box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--ferma) 30%, transparent); }
  :global(.bottone-ferma) { background: var(--surface) !important; color: var(--ferma) !important; border: 1px solid color-mix(in srgb, var(--ferma) 45%, transparent) !important;
    box-shadow: none !important; text-decoration: none; white-space: nowrap; }

  /* pillole */
  .pillole { display: flex; flex-wrap: wrap; gap: var(--space-8); padding: var(--space-12); border-radius: var(--radius-md); background: var(--ferma-bg);
    box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--ferma) 35%, transparent); }
  .pillola { display: inline-flex; align-items: center; gap: var(--space-10); padding: 5px 14px 5px 5px; border: none; border-radius: var(--radius-pill);
    background: color-mix(in srgb, var(--surface) 70%, transparent); box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--ferma) 35%, transparent); cursor: pointer; max-width: 100%; }
  .pillola.scelto { background: var(--surface); }
  .p-testo { display: flex; flex-direction: column; line-height: 1.25; min-width: 0; }
  .p-motivo { font-size: var(--text-xs); font-weight: 600; color: var(--ferma); }

  /* per motivo */
  .colonne { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: var(--space-10); }
  .colonna { padding: var(--space-12); display: flex; flex-direction: column; gap: var(--space-8); }
  .c-testa { display: flex; align-items: center; gap: var(--space-8); }
  .c-n { color: var(--ferma); }
  .voce { width: 100%; display: flex; flex-direction: column; gap: 3px; padding: var(--space-8) var(--space-10); border: none; border-radius: var(--radius-sm);
    background: var(--ferma-bg); box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--ferma) 30%, transparent); cursor: pointer; }
  .v-testa { display: flex; align-items: baseline; gap: var(--space-8); }
  .v-testa .nome { flex: 1; font-size: var(--text-sm); }
  .v-motivo { font-size: var(--text-xs); font-weight: 600; color: var(--ferma); }

  /* sistemati */
  .sistemati { list-style: none; margin: 0; padding: 0; }
  .sistemati li { display: flex; align-items: center; gap: var(--space-10); min-width: 0; }
  .spunta { width: 26px; height: 26px; border-radius: 8px; display: grid; place-items: center; flex-shrink: 0; background: var(--surface-sunken); color: var(--passa); }
  .spunta :global(.ic) { width: 15px; height: 15px; }
  .s-testo { flex: 1; min-width: 0; display: flex; flex-direction: column; line-height: 1.3; }
  .s-nome { font-weight: 600; font-size: var(--text-sm); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .s-cosa { font-size: var(--text-xs); color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .sistemati.schede { display: grid; grid-template-columns: repeat(auto-fill, minmax(270px, 1fr)); gap: var(--space-8); }
  .sistemati.schede li { padding: 9px 12px 9px 14px; border-radius: var(--radius-md); background: var(--surface); box-shadow: inset 4px 0 0 var(--passa), inset 0 0 0 1px var(--line); }
  .sistemati.righe, .sistemati.righe-s { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg, 16px); overflow: hidden; }
  .sistemati.righe li, .sistemati.righe-s li { padding: 9px var(--space-16); border-top: 1px solid var(--line-soft); }
  .sistemati.righe li:first-child, .sistemati.righe-s li:first-child { border-top: none; }
  .sistemati.pillole { display: flex; flex-wrap: wrap; gap: var(--space-6); padding: 0; background: none; box-shadow: none; }
  .sistemati.pillole li { padding: 3px 10px 3px 4px; border-radius: var(--radius-pill); background: var(--surface); box-shadow: inset 0 0 0 1px var(--line); }
  .sistemati.pillole .spunta { width: 20px; height: 20px; border-radius: 50%; background: none; }
  .sistemati.pillole .s-testo { flex-direction: row; gap: var(--space-6); align-items: baseline; }
  .sistemati.pillole .s-cosa { max-width: 140px; }
</style>
