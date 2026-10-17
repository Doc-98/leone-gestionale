<script lang="ts">
  import { sessione } from '../lib/sessione.svelte';
  import { api, ErroreApi, NonAutenticato, type Turno } from '../lib/api';
  import { aggiungiGiorni, giornoEsteso, iso, lunedi, ora, formatOre, daIso, MESI } from '../lib/date';

  const puoVedereTutti = $derived(sessione.ha('ADMIN', 'COORDINATORE'));
  let vista = $state<'miei' | 'tutti'>('miei');
  let settimana = $state(lunedi(iso(new Date())));
  let turni = $state<Turno[]>([]);
  let caricamento = $state(true);
  let errore = $state('');

  const fineSettimana = $derived(aggiungiGiorni(settimana, 6));
  const oggi = iso(new Date());

  $effect(() => {
    const da = settimana;
    const a = fineSettimana;
    const v = vista;
    caricamento = true;
    errore = '';
    (v === 'tutti' ? api.turniTutti(da, a) : api.turniMiei(da, a))
      .then((t) => (turni = t))
      .catch((e) => {
        if (e instanceof NonAutenticato) sessione.utente = null;
        else errore = e instanceof ErroreApi ? e.message : 'Non riesco a caricare i turni.';
      })
      .finally(() => (caricamento = false));
  });

  const perGiorno = $derived.by(() => {
    const g = new Map<string, Turno[]>();
    for (let i = 0; i < 7; i++) g.set(aggiungiGiorni(settimana, i), []);
    for (const t of turni) g.get(t.data)?.push(t);
    return [...g.entries()];
  });
  const oreSettimana = $derived(turni.filter((t) => t.stato !== 'ANNULLATO').reduce((s, t) => s + Number(t.ore), 0));

  const titoloSettimana = $derived.by(() => {
    const a = daIso(settimana);
    const b = daIso(fineSettimana);
    return a.getMonth() === b.getMonth()
      ? `${a.getDate()}–${b.getDate()} ${MESI[a.getMonth()]}`
      : `${a.getDate()} ${MESI[a.getMonth()]} – ${b.getDate()} ${MESI[b.getMonth()]}`;
  });
</script>

<section class="page">
  <header class="testa">
    <div>
      <div class="lbl">Leone XIII Sport · Piscina</div>
      <h1 class="page-title">Ciao, {sessione.utente?.nome}</h1>
    </div>
    <button class="link" onclick={() => sessione.esci()}>Esci</button>
  </header>

  {#if puoVedereTutti}
    <div class="seg due-voci" role="group" aria-label="Quali turni">
      <button aria-pressed={vista === 'miei'} onclick={() => (vista = 'miei')}>I miei turni</button>
      <button aria-pressed={vista === 'tutti'} onclick={() => (vista = 'tutti')}>Tutto lo staff</button>
    </div>
  {/if}

  <div class="settimana">
    <button class="icon-btn" aria-label="Settimana precedente" onclick={() => (settimana = aggiungiGiorni(settimana, -7))}>‹</button>
    <div class="centro">
      <div class="d titolo">{titoloSettimana}</div>
      <div class="muted small m">{caricamento ? '…' : `${turni.length} turni · ${formatOre(oreSettimana)} h`}</div>
    </div>
    <button class="icon-btn" aria-label="Settimana successiva" onclick={() => (settimana = aggiungiGiorni(settimana, 7))}>›</button>
  </div>
  {#if settimana !== lunedi(oggi)}
    <button class="link small torna" onclick={() => (settimana = lunedi(oggi))}>Torna a questa settimana</button>
  {/if}

  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

  {#each perGiorno as [giorno, delGiorno] (giorno)}
    <div class="giorno" class:oggi={giorno === oggi}>
      <h2 class="lbl">{giornoEsteso(giorno)}{giorno === oggi ? ' · oggi' : ''}</h2>
      {#each delGiorno as t (t.id)}
        <div class="card turno" class:annullato={t.stato === 'ANNULLATO'}>
          <span class="m orario">{ora(t.inizio)}–{ora(t.fine)}</span>
          <span class="dove">
            <span class="postazione">{t.postazione}</span>
            <span class="muted small">{t.persona ? t.persona.nome : t.mansione}</span>
          </span>
          <span class="m muted ore">{formatOre(Number(t.ore))} h</span>
        </div>
      {:else}
        <p class="muted small vuoto">{caricamento ? '' : 'Nessun turno'}</p>
      {/each}
    </div>
  {/each}
</section>

<style>
  .testa { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-12); }
  .due-voci { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .settimana { display: flex; align-items: center; justify-content: space-between; gap: var(--space-8); }
  .settimana .icon-btn { font-size: 22px; }
  .centro { text-align: center; }
  .titolo { font-size: var(--text-2xl); font-weight: 800; }
  .torna { align-self: center; margin-top: calc(-1 * var(--space-8)); }
  .giorno { display: flex; flex-direction: column; gap: var(--space-6); }
  .giorno h2 { margin: 0; }
  .giorno.oggi h2 { color: var(--cloro); }
  .turno { display: flex; align-items: center; gap: var(--space-12); padding: var(--space-10) var(--space-14); min-height: 56px; }
  .orario { font-size: var(--text-base); flex-shrink: 0; }
  .dove { flex: 1; min-width: 0; display: flex; flex-direction: column; }
  .postazione { font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .ore { font-size: var(--text-sm); }
  .annullato .orario, .annullato .postazione { text-decoration: line-through; color: var(--muted); }
  .vuoto { margin: 0; }
</style>
