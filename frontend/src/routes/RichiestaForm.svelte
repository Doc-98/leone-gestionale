<script lang="ts">
  import { untrack } from 'svelte';
  import Icona from '../components/Icona.svelte';
  import { api, messaggioErrore, type PersonaBreve, type TipoRichiesta, type Turno } from '../lib/api';
  import { aggiungiGiorni, iso, ora } from '../lib/date';
  import { quando } from '../lib/richieste';
  import { occupato } from '../lib/turni';

  /**
   * Una richiesta su un mio turno (mockup di ottobre 2026): prima si sceglie cosa (sostituzione, scambio, cambio orario),
   * poi i dettagli. La sostituzione parte aperta a tutti; si può chiedere a un collega preciso, e chi è già in turno
   * in quell'orario si vede subito.
   */
  let { turno, fatto, tipoIniziale = null }: { turno: Turno; fatto: (messaggio: string) => void; tipoIniziale?: TipoRichiesta | null } = $props();

  let tipo = $state<TipoRichiesta | null>(untrack(() => tipoIniziale));
  // il form parte dall'orario attuale del turno e poi vive di vita propria
  let nuovoInizio = $state(untrack(() => ora(turno.inizio)));
  let nuovoFine = $state(untrack(() => ora(turno.fine)));
  let collegaId = $state(''); // vuoto = sostituzione aperta a tutti
  let turnoCollegaId = $state('');
  let messaggio = $state('');
  let errore = $state('');
  let lavoro = $state(false);

  let colleghi = $state<PersonaBreve[]>([]);
  let turniColleghi = $state<Turno[]>([]);
  api.colleghi().then((c) => (colleghi = c)).catch(() => {});
  api.turniStaff(iso(new Date()), aggiungiGiorni(iso(new Date()), 45)).then((t) => (turniColleghi = t)).catch(() => {});
  // Solo i turni dei colleghi (l'elenco dello staff comprende anche i miei)
  const scambiabili = $derived(turniColleghi.filter((t) => colleghi.some((c) => c.id === t.persona?.id)));

  const TIPI: { id: TipoRichiesta; nome: string; desc: string; icona: 'persona' | 'scambio' | 'ore' }[] = [
    { id: 'SOSTITUZIONE', nome: 'Sostituzione', desc: 'Quel giorno non ci sei: lo fa un collega', icona: 'persona' },
    { id: 'SCAMBIO', nome: 'Scambio', desc: 'Fai un suo turno, lui fa il tuo', icona: 'scambio' },
    { id: 'CAMBIO_ORARIO', nome: 'Cambio orario', desc: 'Stesso turno, inizio o fine diversi', icona: 'ore' },
  ];

  async function invia(e: SubmitEvent) {
    e.preventDefault();
    if (!tipo) return;
    errore = '';
    lavoro = true;
    try {
      await api.creaRichiesta({
        tipo,
        turnoId: turno.id,
        nuovoInizio: tipo === 'CAMBIO_ORARIO' ? nuovoInizio : null,
        nuovoFine: tipo === 'CAMBIO_ORARIO' ? nuovoFine : null,
        collegaId: tipo === 'SOSTITUZIONE' && collegaId ? collegaId : null,
        turnoCollegaId: tipo === 'SCAMBIO' ? turnoCollegaId : null,
        messaggio: messaggio || null,
      });
      fatto(
        tipo === 'CAMBIO_ORARIO' ? 'Richiesta inviata: ora la deve approvare il coordinatore.'
        : tipo === 'SCAMBIO' ? 'Proposta di scambio inviata al collega.'
        : collegaId ? 'Richiesta inviata al collega.' : 'Richiesta pubblicata: la vede chi è libero in quell\'orario.',
      );
    } catch (err) {
      errore = messaggioErrore(err);
    } finally {
      lavoro = false;
    }
  }
  const nomeCollega = $derived(colleghi.find((c) => c.id === collegaId)?.nome.split(' ')[0]);
</script>

<form class="form" onsubmit={invia}>
  <div class="card riepilogo">
    <div class="postazione">{turno.postazione}</div>
    <div class="muted small">{quando(turno)}</div>
  </div>

  {#if !tipo}
    <div class="scelte">
      {#each TIPI as k (k.id)}
        <button type="button" class="scelta" onclick={() => (tipo = k.id)}>
          <span class="icona"><Icona nome={k.icona} /></span>
          <span class="grow"><b>{k.nome}</b><span class="muted small">{k.desc}</span></span>
          <Icona nome="dx" />
        </button>
      {/each}
    </div>
  {:else}
    <div class="seg tre" role="group" aria-label="Tipo di richiesta">
      {#each TIPI as k (k.id)}<button type="button" aria-pressed={tipo === k.id} onclick={() => (tipo = k.id)}>{k.id === 'CAMBIO_ORARIO' ? 'Orario' : k.nome}</button>{/each}
    </div>

    {#if tipo === 'SOSTITUZIONE'}
      <div class="field">
        <span class="lbl">A chi la chiedi</span>
        <div class="colleghi" role="radiogroup" aria-label="A chi chiedi la sostituzione">
          <button type="button" role="radio" aria-checked={collegaId === ''} class="collega" class:on={collegaId === ''} onclick={() => (collegaId = '')}>
            <span class="av">··</span><span class="grow nome">Chiunque sia libero</span><span class="muted small">aperta a tutti</span>
          </button>
          {#each colleghi as c (c.id)}
            {@const occ = occupato(c.id, turno, turniColleghi)}
            <button type="button" role="radio" aria-checked={collegaId === c.id} class="collega" class:on={collegaId === c.id} class:spento={!!occ}
                    disabled={!!occ} onclick={() => (collegaId = c.id)}>
              <span class="av">{c.nome.split(' ').map((x) => x[0]).join('').slice(0, 2)}</span><span class="grow nome">{c.nome}</span>
              <span class="small" class:libero={!occ}>{occ ? `in turno alle ${ora(occ.inizio)}` : 'libero'}</span>
            </button>
          {/each}
        </div>
      </div>
    {:else if tipo === 'SCAMBIO'}
      <label class="field"><span class="lbl">Con quale turno</span>
        <select class="inp" bind:value={turnoCollegaId} required>
          <option value="" disabled>Scegli il turno di un collega</option>
          {#each scambiabili as t (t.id)}<option value={t.id}>{t.persona?.nome} · {quando(t)} · {t.postazione}</option>{/each}
        </select>
      </label>
    {:else}
      <div class="due">
        <label class="field"><span class="lbl">Nuovo inizio</span><input class="inp m" type="time" bind:value={nuovoInizio} required /></label>
        <label class="field"><span class="lbl">Nuova fine</span><input class="inp m" type="time" bind:value={nuovoFine} required /></label>
      </div>
    {/if}

    <label class="field"><span class="lbl">Messaggio (facoltativo)</span>
      <input class="inp" bind:value={messaggio} maxlength="500" placeholder="es. ho un esame" /></label>

    {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
    <button class="btn btn-primary" disabled={lavoro || (tipo === 'SCAMBIO' && !turnoCollegaId)}>
      {lavoro ? 'Invio…' : tipo === 'SOSTITUZIONE' ? (collegaId ? `Chiedi a ${nomeCollega}` : 'Chiedi a tutti') : 'Invia richiesta'}</button>
    {#if tipo === 'SOSTITUZIONE'}<p class="muted small nomargin">Se un collega accetta, la richiesta passa al coordinatore, che conferma chi ti sostituisce.</p>{/if}
  {/if}
</form>

<style>
  .form { display: flex; flex-direction: column; gap: var(--space-14); }
  .riepilogo { padding: var(--space-12) var(--space-14); }
  .postazione { font-weight: 600; }
  .tre { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  select.inp { appearance: auto; }
  .scelte { display: flex; flex-direction: column; gap: var(--space-8); }
  .scelta { display: flex; align-items: center; gap: var(--space-12); padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); border: 1px solid var(--line);
    background: var(--surface); color: var(--ink); text-align: left; cursor: pointer; font: inherit; width: 100%; }
  .scelta b { display: block; font-size: var(--text-md); }
  .scelta .icona { width: 38px; height: 38px; border-radius: 10px; display: grid; place-items: center; background: var(--cloro-soft); color: var(--cloro-ink); flex-shrink: 0; }
  .scelta > :global(.ic:last-child) { color: var(--muted); }
  .colleghi { display: flex; flex-direction: column; gap: var(--space-6); max-height: 300px; overflow-y: auto; }
  .collega { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-8) var(--space-12); border-radius: var(--radius-md); border: 1px solid var(--line);
    background: var(--surface); color: var(--ink); text-align: left; cursor: pointer; font: inherit; min-height: 44px; }
  .collega.on { border-color: var(--cloro); background: var(--cloro-soft); }
  .collega.spento { opacity: 0.5; cursor: default; }
  .collega .av { width: 30px; height: 30px; border-radius: 50%; display: grid; place-items: center; background: var(--cloro-soft); color: var(--cloro-ink); font-size: 11px; font-weight: 700; flex-shrink: 0; }
  .collega .nome { font-weight: 600; font-size: var(--text-md); }
  .collega .small { color: var(--warn); white-space: nowrap; }
  .collega .small.libero { color: var(--passa); }
  .collega .muted.small { color: var(--muted); }
  .nomargin { margin: 0; }
</style>
