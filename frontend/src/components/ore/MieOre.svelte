<script lang="ts">
  import Icona from '../Icona.svelte';
  import { api, type Riepilogo, type Turno } from '../../lib/api';
  import { euro, formatOre, MESI } from '../../lib/date';
  import { areaDi, attivo, NOMI_AREA, orario, orePerArea, settimaneDelMese, type Area } from '../../lib/turni';

  /**
   * Le mie ore del mese (varianti A e B del mockup): ore e compenso in grande con il foglio ore, il confronto con il mese
   * prima, settimana per settimana colorato per area, il calcolo del compenso e i turni del mese.
   */
  let { mese, mio, prima, turni }: { mese: string; mio: Riepilogo | null; prima: Riepilogo | null; turni: Turno[] } = $props();

  const riga = $derived(mio?.righe[0]);
  const validi = $derived(turni.filter(attivo).sort((a, b) => a.data.localeCompare(b.data) || a.inizio.localeCompare(b.inizio)));
  const settimane = $derived(settimaneDelMese(mese).map((s) => {
    const suoi = validi.filter((t) => t.data >= s.da && t.data <= s.a);
    return { ...s, ore: suoi.reduce((x, t) => x + Number(t.ore), 0), aree: orePerArea(suoi) };
  }));
  const massimo = $derived(Math.max(12, ...settimane.map((s) => s.ore)));
  const aree = $derived(orePerArea(validi));
  const differenza = $derived(riga && prima ? riga.ore - (prima.righe[0]?.ore ?? 0) : null);
  let tuttiTurni = $state(false);
  const breve = (d: string) => {
    const x = new Date(`${d}T12:00:00`);
    return `${['dom', 'lun', 'mar', 'mer', 'gio', 'ven', 'sab'][x.getDay()]} ${x.getDate()} ${MESI[x.getMonth()].slice(0, 3)}`;
  };
  const nomeArea = (a: Area) => (a === 'a' ? 'Atrio' : a === 'x' ? 'Altro' : `Spogliatoio ${NOMI_AREA[a].toLowerCase()}`);
</script>

<div class="mie">
  <div class="sx">
    <section class="hero">
      <span class="numeri">
        <span class="col"><span class="lbl">Ore lavorate</span><span><b class="d grande">{formatOre(riga?.ore ?? 0)}</b><span class="u"> h</span></span></span>
        <span class="col dx"><span class="lbl">Compenso</span><b class="m soldi">{euro(riga?.compenso ?? 0)}</b></span>
      </span>
      <span class="nota">
        {riga ? `${riga.turni} turni` : 'Nessun turno nel mese'}{differenza != null && prima?.righe.length ? ` · ${differenza >= 0 ? '+' : ''}${formatOre(differenza)} h rispetto al mese prima` : ''}{riga?.tariffaMancante ? ' · per qualche turno manca la tariffa' : ''}
      </span>
      {#if riga}
        <span class="bottoni">
          <a class="pieno" href={api.urlFoglioOreMio(mese)} download><Icona nome="scarica" />Foglio ore (.xlsx)</a>
        </span>
      {/if}
    </section>

    {#if riga}
      <section class="card calcolo">
        <span class="lbl">Come si arriva al compenso</span>
        {#each riga.voci as v (v.mansione + v.euroOra)}
          <span class="voce"><span class="grow">{v.mansione}</span><span class="m">{formatOre(v.ore)} h × {v.euroOra == null ? 'tariffa mancante' : euro(v.euroOra)}</span></span>
        {/each}
        <span class="voce tot"><span class="grow">Totale</span><span class="m">{euro(riga.compenso)}</span></span>
        <span class="muted small">La tariffa la decide il coordinatore. Se per un turno manca, qui compare un avviso.</span>
      </section>
    {/if}
  </div>

  <div class="dx-col">
    <section class="card settimane">
      <span class="testa"><span class="lbl grow">Settimana per settimana</span>
        <span class="legenda">{#each aree as a (a.area)}<span class="ar-{a.area}"><i class="segno-area"></i>{NOMI_AREA[a.area]}</span>{/each}</span></span>
      <div class="barre">
        {#each settimane as s (s.da)}
          <span class="sett">
            <span class="m h">{formatOre(s.ore)} h</span>
            <span class="bar">{#each s.aree as a (a.area)}<i class="ar-{a.area}" style:height="{(a.ore / massimo) * 100}%"></i>{/each}</span>
            <span class="muted small">{s.nome}</span>
          </span>
        {/each}
      </div>
      {#if aree.length > 1}
        <div class="perarea">
          {#each aree as a (a.area)}<span class="ar-{a.area}"><i class="segno-area"></i><span class="grow">{nomeArea(a.area)}</span><span class="m muted">{formatOre(a.ore)} h</span></span>{/each}
        </div>
      {/if}
    </section>

    {#if validi.length}
      <section class="card turni">
        <div class="riga-t intest lbl"><span>Giorno</span><span>Orario</span><span>Dove</span><span class="ore">Ore</span></div>
        {#each tuttiTurni ? validi : validi.slice(0, 10) as t, i (t.id)}
          <div class="riga-t ar-{areaDi(t.postazione)}" class:pari={i % 2 === 1}>
            <span>{breve(t.data)}</span><span class="m">{orario(t)}</span>
            <span class="dove"><span class="segno-area"></span><span class="nome">{t.postazione}</span></span>
            <span class="m ore">{formatOre(Number(t.ore))} h</span>
          </div>
        {/each}
        {#if validi.length > 10}
          <button type="button" class="altri" onclick={() => (tuttiTurni = !tuttiTurni)}>{tuttiTurni ? 'Mostra meno' : `Mostra tutti i ${validi.length} turni`}</button>
        {/if}
        <div class="riga-t totale"><span>Totale</span><span></span><span>{validi.length} turni</span><span class="m ore">{formatOre(validi.reduce((x, t) => x + Number(t.ore), 0))} h</span></div>
      </section>
    {/if}
  </div>
</div>

<style>
  .mie { display: flex; flex-wrap: wrap; gap: var(--space-20); align-items: flex-start; }
  .sx { flex: 1 1 320px; max-width: 420px; display: flex; flex-direction: column; gap: var(--space-14); }
  .dx-col { flex: 3 1 520px; min-width: 0; display: flex; flex-direction: column; gap: var(--space-14); }
  @media (max-width: 760px) { .sx { max-width: none; } }
  .hero { display: flex; flex-direction: column; gap: var(--space-12); padding: var(--space-20); border-radius: 16px; background: var(--hero); color: var(--on-fill); }
  .hero .lbl { color: color-mix(in srgb, var(--on-fill) 70%, transparent); }
  .numeri { display: flex; align-items: flex-end; gap: var(--space-10); }
  .col { display: flex; flex-direction: column; flex: 1; min-width: 0; }
  .col.dx { align-items: flex-end; flex: 0 0 auto; }
  .grande { font-size: 44px; font-weight: 800; line-height: 1; }
  .u { opacity: 0.7; }
  .soldi { font-size: 24px; font-weight: 600; white-space: nowrap; }
  .nota { font-size: var(--text-sm); opacity: 0.8; }
  .bottoni { display: flex; gap: var(--space-8); }
  .pieno { flex: 1; min-height: 44px; display: inline-flex; align-items: center; justify-content: center; gap: var(--space-6); border-radius: var(--radius-md); background: #fff;
    color: var(--ink); font-weight: 700; font-size: var(--text-md); text-decoration: none; }
  .pieno :global(.ic) { width: 18px; height: 18px; }
  .calcolo { padding: var(--space-14) var(--space-16); display: flex; flex-direction: column; gap: var(--space-8); }
  .voce { display: flex; gap: var(--space-8); font-size: var(--text-md); }
  .voce .m { white-space: nowrap; }
  .tot { font-weight: 700; padding-top: var(--space-8); border-top: 1px solid var(--line-soft); }
  .settimane { padding: var(--space-16); display: flex; flex-direction: column; gap: var(--space-14); }
  .testa { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .legenda { display: flex; gap: var(--space-14); font-size: var(--text-xs); color: var(--muted); }
  .legenda span, .perarea span { display: inline-flex; align-items: center; gap: 5px; }
  .barre { display: flex; gap: var(--space-14); align-items: flex-end; }
  .sett { flex: 1; display: flex; flex-direction: column; align-items: center; gap: var(--space-6); }
  .sett .h { font-size: var(--text-sm); font-weight: 600; white-space: nowrap; }
  .bar { width: 100%; max-width: 56px; height: 140px; display: flex; flex-direction: column-reverse; border-radius: 8px; overflow: hidden; background: var(--surface-sunken); }
  .bar i { display: block; width: 100%; background: var(--a); }
  .perarea { display: flex; flex-direction: column; gap: var(--space-6); padding-top: var(--space-10); border-top: 1px solid var(--line-soft); font-size: var(--text-md); }
  .perarea span { display: flex; gap: var(--space-8); }
  .turni { padding: 0; overflow: hidden; }
  .riga-t { display: grid; grid-template-columns: 130px 120px minmax(0, 1fr) 70px; gap: var(--space-14); align-items: center; padding: var(--space-10) var(--space-16);
    font-size: var(--text-md); border-top: 1px solid var(--line); background: var(--surface); }
  .riga-t.intest { border-top: none; border-bottom: 1px solid var(--line); padding-top: var(--space-12); padding-bottom: var(--space-12); }
  .riga-t.intest + .riga-t { border-top: none; }
  .riga-t.pari { background: color-mix(in srgb, var(--cloro) 7%, var(--surface)); }
  .riga-t .ore { text-align: right; white-space: nowrap; }
  .riga-t .m, .riga-t > :first-child { white-space: nowrap; }
  .dove { display: flex; align-items: center; gap: var(--space-8); min-width: 0; }
  .nome { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .totale { font-weight: 700; background: var(--surface-sunken); }
  .altri { width: 100%; padding: var(--space-10); border: none; border-top: 1px solid var(--line-soft); background: var(--surface); color: var(--cloro); font: inherit;
    font-size: var(--text-sm); font-weight: 700; cursor: pointer; }
  @media (max-width: 600px) {
    .riga-t { grid-template-columns: 90px minmax(0, 1fr) 60px; }
    .riga-t > :nth-child(2) { display: none; }
  }
</style>
