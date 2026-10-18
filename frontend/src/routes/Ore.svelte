<script lang="ts">
  import { api, messaggioErrore, type Riepilogo } from '../lib/api';
  import { sessione } from '../lib/sessione.svelte';
  import { euro, formatOre, meseIso } from '../lib/date';
  import Mese from '../components/Mese.svelte';

  let mese = $state(meseIso(new Date()));
  let mio = $state<Riepilogo | null>(null);
  let staff = $state<Riepilogo | null>(null);
  let errore = $state('');
  let aperto = $state<string | null>(null);
  const coordinatore = $derived(sessione.ha('ADMIN', 'COORDINATORE'));

  $effect(() => {
    const m = mese;
    errore = '';
    api.riepilogoMio(m).then((r) => (mio = r)).catch((e) => (errore = messaggioErrore(e)));
    if (coordinatore) api.riepilogo(m).then((r) => (staff = r)).catch((e) => (errore = messaggioErrore(e)));
  });
  const mia = $derived(mio?.righe[0]);
</script>

<section class="page ore">
  <div class="tutta"><Mese bind:mese /></div>
  {#if errore}<p class="msg err tutta" role="alert">{errore}</p>{/if}

  <div class="card box">
    <div class="lbl">Le tue ore</div>
    <div class="numeri">
      <div><span class="d grande">{formatOre(mia?.ore ?? 0)}</span> <span class="muted">h</span></div>
      <div class="m">{euro(mia?.compenso ?? 0)}</div>
    </div>
    <div class="muted small">{mia ? `${mia.turni} turni` : 'Nessun turno nel mese'}{mia?.tariffaMancante ? ' · per qualche turno manca la tariffa' : ''}</div>
    {#each mia?.voci ?? [] as v (v.mansione + v.euroOra)}
      <div class="voce small"><span>{v.mansione}</span><span class="m muted">{formatOre(v.ore)} h × {v.euroOra == null ? '—' : euro(v.euroOra)}</span></div>
    {/each}
    {#if mia}<a class="link small scarica" href={api.urlFoglioOreMio(mese)} download>Scarica il foglio ore (.xlsx)</a>{/if}
  </div>

  {#if coordinatore && staff}
  <div class="staff">
    <div class="intesta">
      <h2 class="lbl">Tutto lo staff</h2>
      <span class="row scarichi">
        {#if staff.righe.length}<a class="link small" href={api.urlFogliOreTutti(mese)} download>Tutti i fogli ore (.zip)</a>{/if}
        <a class="link small" href={api.urlCsv(mese)} download>Riepilogo CSV</a>
      </span>
    </div>
    {#if staff.turniScoperti}<p class="msg err nomargin">{staff.turniScoperti === 1 ? 'Un turno del mese è ancora da assegnare.' : `${staff.turniScoperti} turni del mese sono ancora da assegnare.`}</p>{/if}
    <div class="card elenco">
      {#each staff.righe as r (r.persona.id)}
        <button class="riga" aria-expanded={aperto === r.persona.id} onclick={() => (aperto = aperto === r.persona.id ? null : r.persona.id)}>
          <span class="nome">{r.persona.nome}{#if r.tariffaMancante}<span class="tag t-warn manca">tariffa mancante</span>{/if}</span>
          <span class="m">{formatOre(r.ore)} h</span>
          <span class="m soldi">{euro(r.compenso)}</span>
        </button>
        {#if aperto === r.persona.id}
          <div class="dettagli">
            {#each r.voci as v (v.mansione + v.euroOra)}
              <div class="voce small"><span>{v.mansione}</span><span class="m muted">{formatOre(v.ore)} h × {v.euroOra == null ? 'tariffa mancante' : euro(v.euroOra)} = {euro(v.compenso)}</span></div>
            {/each}
            <a class="link small scarica" href={api.urlFoglioOrePersona(r.persona.id, mese)} download>Foglio ore di {r.persona.nome} (.xlsx)</a>
          </div>
        {/if}
      {:else}
        <p class="muted small vuoto">Nessun turno nel mese.</p>
      {/each}
      <div class="riga totale"><span class="nome">Totale</span><span class="m">{formatOre(staff.totaleOre)} h</span><span class="m soldi">{euro(staff.totaleCompenso)}</span></div>
    </div>
  </div>
  {/if}
</section>

<style>
  .staff { display: flex; flex-direction: column; gap: var(--space-12); }
  /* Sul PC: le mie ore a sinistra, lo staff a destra */
  @media (min-width: 1024px) {
    .ore { display: grid; grid-template-columns: 340px minmax(0, 860px); align-items: start; column-gap: var(--space-20); }
    .tutta { grid-column: 1 / -1; max-width: 420px; }
    .msg.tutta { max-width: none; }
    .staff .intesta { margin-top: 0; }
  }
  .box { padding: var(--space-16); display: flex; flex-direction: column; gap: var(--space-6); }
  .numeri { display: flex; justify-content: space-between; align-items: baseline; }
  .grande { font-size: var(--text-4xl); font-weight: 800; }
  .numeri .m { font-size: var(--text-2xl); }
  .voce { display: flex; justify-content: space-between; gap: var(--space-8); }
  .intesta { display: flex; justify-content: space-between; align-items: center; margin-top: var(--space-8); }
  .intesta h2 { margin: 0; }
  .elenco { overflow: hidden; }
  .riga { display: grid; grid-template-columns: 1fr auto 96px; gap: var(--space-12); align-items: center; width: 100%; min-height: 48px; padding: var(--space-8) var(--space-14); border: none; border-top: 1px solid var(--line-soft); background: none; color: var(--ink); font: inherit; text-align: left; cursor: pointer; }
  .riga:first-child { border-top: none; }
  .soldi { text-align: right; }
  .totale { font-weight: 700; cursor: default; background: var(--surface-sunken); }
  .nome { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .dettagli { padding: 0 var(--space-14) var(--space-10); display: flex; flex-direction: column; gap: var(--space-4); }
  .manca { margin-left: var(--space-6); height: 20px; }
  .scarica { min-height: 32px; align-self: flex-start; }
  .scarichi { gap: var(--space-16); }
  .vuoto { padding: var(--space-12) var(--space-14); margin: 0; }
</style>
