<script lang="ts">
  import Icona from '../Icona.svelte';
  import { api, type GruppoRiepilogo, type Riepilogo } from '../../lib/api';
  import { euro, formatOre } from '../../lib/date';

  /**
   * Le ore dello staff (variante C del mockup, ADR 0022): i gruppi che l'utente vede, uno o tutti insieme. Con più gruppi,
   * «Tutti i gruppi» mostra una riga per gruppo e tutto il personale; dentro un gruppo, le sue persone. Ogni persona si
   * apre con il calcolo e il suo foglio ore.
   */
  let { mese, tutti, delGruppo, prima, gruppo = $bindable(), puoTariffe }: {
    mese: string;
    tutti: Riepilogo;
    delGruppo: Riepilogo | null;
    prima: Riepilogo | null;
    gruppo: string | null;
    puoTariffe: boolean;
  } = $props();

  let aperto = $state<string | null>(null);
  let cerca = $state('');
  let quanti = $state(30);

  const r = $derived(gruppo && delGruppo ? delGruppo : tutti);
  const scelto = $derived(tutti.gruppi.find((g) => g.id === gruppo) ?? null);
  const piuGruppi = $derived(tutti.gruppi.length > 1);
  const persone = $derived(r.righe.filter((x) => !cerca.trim() || x.persona.nome.toLowerCase().includes(cerca.trim().toLowerCase()))
    .sort((a, b) => b.ore - a.ore || a.persona.nome.localeCompare(b.persona.nome, 'it')));
  const senzaTariffa = $derived(r.righe.filter((x) => x.tariffaMancante).length);
  const variazione = $derived(!gruppo && prima && prima.totaleCompenso > 0 ? Math.round(((tutti.totaleCompenso - prima.totaleCompenso) / prima.totaleCompenso) * 100) : null);
  const quota = (g: GruppoRiepilogo) => (tutti.totaleOre ? (g.ore / tutti.totaleOre) * 100 : 0);
  const notaGruppo = (g: GruppoRiepilogo) =>
    g.turniScoperti ? `${g.turniScoperti} ${g.turniScoperti === 1 ? 'turno scoperto' : 'turni scoperti'}`
    : g.tariffeMancanti ? `${g.tariffeMancanti === 1 ? '1 persona' : `${g.tariffeMancanti} persone`} senza tariffa` : 'tutto in ordine';
  const iniziali = (n: string) => n.split(' ').map((x) => x[0]).join('').slice(0, 2);
  const scegli = (id: string | null) => {
    gruppo = id;
    aperto = null;
  };
</script>

{#if piuGruppi}
  <div class="seg gruppi" role="group" aria-label="Gruppo di staff">
    <button type="button" aria-pressed={!gruppo} onclick={() => scegli(null)}>Tutti i gruppi</button>
    {#each tutti.gruppi as g (g.id)}<button type="button" aria-pressed={gruppo === g.id} onclick={() => scegli(g.id)}>{g.nome}</button>{/each}
  </div>
{:else if tutti.gruppi.length === 1}
  <span class="muted small">Gruppo: <b>{tutti.gruppi[0].nome}</b></span>
{/if}

<div class="tiles">
  <div class="tile hero-t"><span class="lbl">Ore</span><span class="v">{formatOre(r.totaleOre)} h</span>
    <span class="n">{gruppo ? `${tutti.totaleOre ? Math.round((r.totaleOre / tutti.totaleOre) * 100) : 0}% di tutte le ore` : piuGruppi ? `${tutti.gruppi.length} gruppi` : `${r.righe.reduce((s, x) => s + x.turni, 0)} turni`}</span></div>
  <div class="tile"><span class="lbl">Compensi</span><span class="v">{euro(r.totaleCompenso)}</span>
    <span class="n">{variazione != null ? `${variazione >= 0 ? '+' : ''}${variazione}% rispetto al mese prima` : 'nel mese'}</span></div>
  <div class="tile" class:arancio={senzaTariffa > 0}><span class="lbl">Persone</span><span class="v">{r.righe.length}</span>
    <span class="n">{senzaTariffa ? `${senzaTariffa === 1 ? '1 senza tariffa' : `${senzaTariffa} senza tariffa`}` : 'tutte con la tariffa'}</span></div>
  <div class="tile" class:arancio={r.turniScoperti > 0}><span class="lbl">Turni scoperti</span><span class="v">{r.turniScoperti}</span>
    <span class="n">{r.turniScoperti ? 'nel mese, da assegnare' : 'nessuno'}</span></div>
</div>

<div class="barra">
  <span class="grow"></span>
  {#if r.righe.length}<a class="btn btn-secondary btn-sm" href={api.urlFogliOreTutti(mese, gruppo)} download><Icona nome="scarica" />{scelto ? `Fogli ore · ${scelto.nome} (.zip)` : 'Tutti i fogli ore (.zip)'}</a>{/if}
  <a class="btn btn-secondary btn-sm" href={api.urlCsv(mese, gruppo)} download><Icona nome="scarica" />Riepilogo per l'amministrazione (CSV)</a>
</div>

{#if piuGruppi && !gruppo}
  <section class="sezione">
    <h2 class="lbl">Per gruppo</h2>
    <div class="card tabella">
      <div class="gg intest lbl"><span>Gruppo</span><span>Coordina</span><span>Persone</span><span class="dx">Ore</span><span class="dx">Compensi</span><span>Quota</span><span></span></div>
      {#each tutti.gruppi as g, i (g.id)}
        <button type="button" class="gg riga" class:pari={i % 2 === 1} onclick={() => scegli(g.id)}>
          <span class="chi"><b>{g.nome}</b><span class="small" class:warn={g.turniScoperti || g.tariffeMancanti}>{notaGruppo(g)}</span></span>
          <span class="muted small nome">{g.coordinatori.length ? g.coordinatori.join(', ') : '—'}</span>
          <span class="m">{g.persone}</span>
          <span class="m dx">{formatOre(g.ore)} h</span>
          <span class="m dx">{euro(g.compenso)}</span>
          <span class="quota"><i style:width="{quota(g)}%"></i></span>
          <Icona nome="dx" />
        </button>
      {/each}
    </div>
  </section>
{/if}

<section class="sezione">
  <div class="barra">
    <h2 class="lbl">{scelto ? scelto.nome : piuGruppi ? 'Tutto il personale' : 'Lo staff'}</h2>
    <span class="grow"></span>
    <input class="inp cerca" type="search" bind:value={cerca} placeholder="Cerca per nome" aria-label="Cerca per nome" />
  </div>
  <div class="card tabella">
    <div class="gp intest lbl"><span>Persona</span><span>{piuGruppi && !gruppo ? 'Gruppo' : 'Voci'}</span><span>Turni</span><span class="dx">Ore</span><span class="dx">Compenso</span><span></span></div>
    {#each persone.slice(0, quanti) as p, i (p.persona.id)}
      <div class="persona" class:pari={i % 2 === 1} class:aperta={aperto === p.persona.id}>
        <button type="button" class="gp riga" aria-expanded={aperto === p.persona.id} onclick={() => (aperto = aperto === p.persona.id ? null : p.persona.id)}>
          <span class="chi riga-av"><span class="av">{iniziali(p.persona.nome)}</span><b class="nome">{p.persona.nome}</b></span>
          <span class="small muted nome">{piuGruppi && !gruppo ? p.gruppo ?? '—' : p.voci.map((v) => v.mansione).join(', ')}</span>
          <span class="m">{p.turni}</span>
          <span class="m dx">{formatOre(p.ore)} h</span>
          <span class="m dx" class:warn={p.tariffaMancante}>{p.tariffaMancante ? 'manca tariffa' : euro(p.compenso)}</span>
          <Icona nome="dx" />
        </button>
        {#if aperto === p.persona.id}
          <div class="dett">
            <span class="voci">
              {#each p.voci as v (v.mansione + v.euroOra)}
                <span class="m small">{v.mansione}: {formatOre(v.ore)} h × {v.euroOra == null ? 'tariffa mancante' : `${euro(v.euroOra)} = ${euro(v.compenso)}`}</span>
              {/each}
            </span>
            <span class="azioni">
              {#if p.tariffaMancante && puoTariffe}<a class="btn btn-primary btn-sm" href="#/tariffe">Imposta la tariffa</a>{/if}
              <a class="btn btn-secondary btn-sm" href={api.urlFoglioOrePersona(p.persona.id, mese)} download><Icona nome="scarica" />Foglio ore</a>
            </span>
          </div>
        {/if}
      </div>
    {:else}
      <p class="muted small vuoto">{cerca ? 'Nessuno con questo nome.' : 'Nessun turno nel mese.'}</p>
    {/each}
    {#if persone.length > quanti}<button type="button" class="altri" onclick={() => (quanti = persone.length)}>Mostra tutte le {persone.length} persone</button>{/if}
    <div class="gp totale"><span>Totale</span><span></span><span class="m">{r.righe.reduce((s, x) => s + x.turni, 0)}</span><span class="m dx">{formatOre(r.totaleOre)} h</span><span class="m dx">{euro(r.totaleCompenso)}</span><span></span></div>
  </div>
</section>

<style>
  .gruppi { grid-template-columns: repeat(auto-fit, minmax(0, max-content)); align-self: flex-start; max-width: 100%; overflow-x: auto; }
  .gruppi button { white-space: nowrap; padding: 0 var(--space-14); }
  .tiles { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-10); }
  .tile { display: flex; flex-direction: column; gap: 2px; padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); background: var(--surface); border: 1px solid var(--line); min-width: 0; }
  .tile .v { font-family: var(--font-display); font-size: 26px; font-weight: 800; line-height: 1.1; white-space: nowrap; }
  .tile .n { font-size: var(--text-xs); color: var(--muted); }
  .tile.arancio { background: var(--warn-bg); border-color: transparent; } .tile.arancio .v { color: var(--warn); }
  .tile.hero-t { background: var(--hero); color: var(--on-fill); border-color: transparent; }
  .tile.hero-t .lbl, .tile.hero-t .n { color: color-mix(in srgb, var(--on-fill) 72%, transparent); }
  .barra { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-10); }
  .barra h2 { margin: 0; }
  .barra a { text-decoration: none; }
  .sezione { display: flex; flex-direction: column; gap: var(--space-8); }
  .sezione h2 { margin: 0; }
  .cerca { width: 240px; }
  .tabella { padding: 0; overflow: hidden; }
  .gg { display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr) 80px 90px 120px 90px 20px; gap: var(--space-16); align-items: center; }
  .gp { display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr) 60px 90px 120px 20px; gap: var(--space-16); align-items: center; }
  .intest { padding: var(--space-12) var(--space-16); border-bottom: 1px solid var(--line); }
  .riga { width: 100%; padding: var(--space-12) var(--space-16); border: none; background: none; font: inherit; color: var(--ink); text-align: left; cursor: pointer; font-size: var(--text-md); }
  .gg.riga + .gg.riga, .persona + .persona { border-top: 1px solid var(--line); }
  .pari, .persona.pari { background: color-mix(in srgb, var(--cloro) 7%, var(--surface)); }
  .persona.aperta { background: var(--cloro-soft); }
  .riga :global(.ic) { width: 18px; height: 18px; color: var(--muted); transition: transform 0.15s; }
  .persona .riga[aria-expanded='true'] :global(.ic) { transform: rotate(90deg); }
  .chi { display: flex; flex-direction: column; min-width: 0; line-height: 1.3; }
  .riga-av { flex-direction: row; align-items: center; gap: var(--space-10); }
  .av { width: 30px; height: 30px; border-radius: 50%; display: grid; place-items: center; background: var(--cloro-soft); color: var(--cloro-ink); font-size: 11px; font-weight: 700; flex-shrink: 0; }
  .nome { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0; }
  .dx { text-align: right; }
  .m { white-space: nowrap; }
  .warn { color: var(--warn); font-weight: 600; }
  .quota { height: 8px; border-radius: 4px; background: var(--surface-sunken); overflow: hidden; }
  .quota i { display: block; height: 100%; background: var(--cloro); }
  .dett { padding: 0 var(--space-16) var(--space-14) 56px; display: flex; flex-wrap: wrap; gap: var(--space-10) var(--space-20); align-items: flex-end; }
  .voci { flex: 1 1 280px; display: flex; flex-direction: column; gap: 4px; color: var(--muted); }
  .azioni { display: flex; gap: var(--space-8); }
  .azioni a { text-decoration: none; }
  .totale { padding: var(--space-12) var(--space-16); border-top: 1px solid var(--line); background: var(--surface-sunken); font-weight: 700; font-size: var(--text-md); }
  .altri { width: 100%; padding: var(--space-10); border: none; border-top: 1px solid var(--line-soft); background: var(--surface); color: var(--cloro); font: inherit; font-size: var(--text-sm); font-weight: 700; cursor: pointer; }
  .vuoto { padding: var(--space-14) var(--space-16); margin: 0; }
  @media (max-width: 900px) {
    .tiles { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    .gg { grid-template-columns: minmax(0, 1fr) 90px 20px; }
    .gg > :nth-child(2), .gg > :nth-child(3), .gg > :nth-child(5), .gg > :nth-child(6) { display: none; }
    .gp { grid-template-columns: minmax(0, 1fr) 70px 100px 20px; }
    .gp > :nth-child(2), .gp > :nth-child(3) { display: none; }
    .cerca { width: 100%; }
    .dett { padding-left: var(--space-16); }
  }
</style>
