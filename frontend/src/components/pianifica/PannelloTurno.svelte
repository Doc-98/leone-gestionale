<script lang="ts">
  import Icona from '../Icona.svelte';
  import { api, eAvviso, messaggioErrore, type PersonaStaff, type Postazione, type Richiesta, type Turno } from '../../lib/api';
  import { giornoEsteso, ora } from '../../lib/date';
  import { accavallato, candidati, formatH, gruppoDi, testoRiservata, type TurnoBreve } from '../../lib/gestione';
  import { areaDi } from '../../lib/turni';
  import { descriviRichiestaSulTurno } from '../../lib/richieste';

  /**
   * Il turno scelto nella pianificazione: orario, chi lo fa (un tocco su «Assegna»), annulla o lascia scoperto.
   * Con turno null è un turno nuovo su una postazione libera.
   */
  let { turno, nuovo, postazioni, personale, turni, richiesta, compatto = false, fatto, chiudi }: {
    turno: Turno | null;
    nuovo?: { postazioneId: string; data: string; inizio?: string; fine?: string };
    postazioni: Postazione[];
    personale: PersonaStaff[];
    /** i turni pianificati della settimana, per le ore e gli accavallamenti */
    turni: Turno[];
    richiesta?: Richiesta;
    /** sul telefono (dentro un foglio): senza intestazione propria */
    compatto?: boolean;
    fatto: (messaggio: string) => void;
    chiudi: () => void;
  } = $props();

  // svelte-ignore state_referenced_locally
  const iniziale = { inizio: ora(turno?.inizio ?? nuovo?.inizio ?? '16:00'), fine: ora(turno?.fine ?? nuovo?.fine ?? '19:00') };
  let inizio = $state(iniziale.inizio);
  let fine = $state(iniziale.fine);
  let mostraOccupati = $state(false);
  let lavoro = $state(false);
  let errore = $state('');
  let avviso = $state<{ testo: string; riprova: () => void } | null>(null);
  let esito = $state('');
  // svelte-ignore state_referenced_locally
  let postazioneScelta = $state(nuovo?.postazioneId ?? '');

  const postazioneId = $derived(turno?.postazioneId ?? postazioneScelta);
  const data = $derived(turno?.data ?? nuovo?.data ?? '');
  const postazione = $derived(postazioni.find((p) => p.id === postazioneId));
  const nomePost = $derived(postazione ? gruppoDi(postazione) : { nome: turno?.mansione ?? '', etichetta: turno?.postazione ?? '' });
  const area = $derived(areaDi(postazione?.nome ?? turno?.postazione ?? ''));
  const breve = (t: Turno): TurnoBreve => ({ id: t.id, giorno: t.data, inizio: ora(t.inizio), fine: ora(t.fine), postazioneId: t.postazioneId, persona: t.persona });
  const tutti = $derived(turni.map(breve));
  const questo = $derived<TurnoBreve>({ id: turno?.id ?? '', giorno: data, inizio, fine, postazioneId, persona: turno?.persona ?? null });
  const elenco = $derived(candidati(questo, postazione, personale, tutti));
  const occupati = $derived(elenco.filter((c) => c.occupato && !c.attuale).length);
  const visibili = $derived(mostraOccupati ? elenco : elenco.filter((c) => !c.occupato || c.attuale));
  const conflitto = $derived(turno?.persona ? accavallato(turno.persona.id, questo, tutti) : undefined);
  const orarioCambiato = $derived(!!turno && (inizio !== iniziale.inizio || fine !== iniziale.fine));
  const nomePostazioneDi = (id: string) => {
    const p = postazioni.find((x) => x.id === id);
    return p ? `${gruppoDi(p).nome} · ${gruppoDi(p).etichetta}` : '';
  };

  async function salva(personaId: string | null, forza: boolean, messaggio: string) {
    errore = '';
    avviso = null;
    lavoro = true;
    try {
      const input = { postazioneId, data, inizio, fine, personaId, note: turno?.note ?? null, forza };
      if (turno) await api.modificaTurno(turno.id, input);
      else await api.creaTurno(input);
      esito = messaggio;
      fatto(messaggio);
    } catch (e) {
      if (eAvviso(e)) avviso = { testo: e.message, riprova: () => salva(personaId, true, messaggio) };
      else errore = messaggioErrore(e);
    } finally {
      lavoro = false;
    }
  }

  const assegna = (p: PersonaStaff, forza: boolean) => salva(p.id, forza, `${turno ? 'Assegnato' : 'Turno creato e assegnato'} a ${p.nome}.`);

  async function annulla() {
    if (!turno) return;
    lavoro = true;
    try {
      await api.annullaTurno(turno.id);
      fatto('Turno annullato.');
      chiudi();
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      lavoro = false;
    }
  }
</script>

<div class="pannello ar-{area}">
  {#if !compatto}
    <span class="intesta"><span class="lbl grow">{turno ? (turno.persona ? 'Turno' : 'Turno scoperto') : 'Nuovo turno'}</span>
      <button class="x-btn" type="button" aria-label="Chiudi" onclick={chiudi}><Icona nome="chiudi" /></button></span>
  {/if}
  <h2>{giornoEsteso(data).replace(/^./, (c) => c.toUpperCase())}</h2>
  {#if !turno && compatto}
    <label class="field"><span class="lbl">Postazione</span>
      <select class="inp" bind:value={postazioneScelta}>{#each postazioni as p (p.id)}<option value={p.id}>{p.nome}</option>{/each}</select>
    </label>
  {:else}
    <span class="dove"><i></i><b>{nomePost.nome} · {nomePost.etichetta}</b></span>
  {/if}
  <div class="due">
    <label class="field"><span class="lbl">Inizio</span><input class="inp" type="time" bind:value={inizio} /></label>
    <label class="field"><span class="lbl">Fine</span><input class="inp" type="time" bind:value={fine} /></label>
  </div>
  {#if orarioCambiato}
    <button class="btn btn-secondary" type="button" disabled={lavoro} onclick={() => salva(turno?.persona?.id ?? null, false, 'Orario aggiornato.')}>Salva il nuovo orario</button>
  {/if}

  {#if richiesta}
    <a class="richiesta" href={`#/approva/${richiesta.id}`}>{descriviRichiestaSulTurno(richiesta)} →</a>
  {/if}

  <span class="lbl">Chi lo fa</span>
  <div class="cand" role="list" aria-label="Chi può fare il turno">
    {#each visibili as c (c.persona.id)}
      <div class="riga-c" role="listitem">
        <span class="n" title={c.occupato ? `Ha già un turno ${c.occupato.inizio}–${c.occupato.fine} · ${nomePostazioneDi(c.occupato.postazioneId)}` : undefined}>{c.persona.nome}</span>
        <span class="ore-b" title="ore nella settimana"><i style="width: {Math.min(100, (c.ore / 20) * 100)}%"></i></span>
        <span class="m s muted ore">{formatH(c.ore).replace(' h', '')}</span>
        {#if c.attuale}<span class="azione-c ora">assegnato ora</span>
        {:else if c.occupato}<button type="button" class="azione-c comunque" disabled={lavoro} title={`Si accavalla con ${c.occupato.inizio}–${c.occupato.fine}`} aria-label={`Assegna comunque a ${c.persona.nome}`} onclick={() => assegna(c.persona, true)}>Assegna comunque</button>
        {:else}<button type="button" class="azione-c" disabled={lavoro} aria-label={`Assegna a ${c.persona.nome}`} onclick={() => assegna(c.persona, false)}>Assegna</button>{/if}
      </div>
    {:else}
      <p class="muted small nessuno">Nessuno dello staff può lavorare qui.</p>
    {/each}
  </div>
  {#if occupati}
    <button type="button" class="mini-btn tratteggio" aria-expanded={mostraOccupati} onclick={() => (mostraOccupati = !mostraOccupati)}>
      {mostraOccupati ? 'Nascondi chi ha un turno alla stessa ora' : `Mostra anche chi ha un turno alla stessa ora (${occupati})`}</button>
  {/if}
  {#if conflitto && turno?.persona}
    <span class="avviso">{turno.persona.nome} ha anche un turno che si accavalla: {nomePostazioneDi(conflitto.postazioneId)}, {conflitto.inizio}–{conflitto.fine}.</span>
  {/if}
  {#if avviso}
    <div class="avviso con-azione" role="alert"><span>{avviso.testo}</span>
      <span class="riga-az"><button type="button" class="mini-btn" onclick={() => (avviso = null)}>Lascia stare</button><button type="button" class="mini-btn pieno-warn" onclick={avviso.riprova}>Assegna comunque</button></span></div>
  {/if}
  <span class="muted small">{testoRiservata(postazione)}Prima chi è libero, dal meno carico. La barretta sono le ore della settimana.</span>
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
  {#if esito && !avviso && !errore}<span class="fatto" role="status">{esito}</span>{/if}

  {#if turno}
    <div class="fondo">
      <button type="button" class="btn rosso-pieno" disabled={lavoro} onclick={annulla}>Annulla il turno</button>
      {#if turno.persona}<button type="button" class="btn rosso-bordo" disabled={lavoro} onclick={() => salva(null, false, 'Il turno ora è scoperto.')}>Lascia scoperto</button>{/if}
    </div>
  {:else}
    <button type="button" class="btn btn-secondary" disabled={lavoro} onclick={() => salva(null, false, 'Turno scoperto creato.')}>Crea scoperto</button>
  {/if}
</div>

<style>
  .pannello { display: flex; flex-direction: column; gap: var(--space-12); }
  .intesta { display: flex; align-items: center; gap: var(--space-8); }
  h2 { margin: 0; font-family: var(--font-display); font-size: var(--text-xl); font-weight: 800; }
  .dove { display: flex; align-items: center; gap: var(--space-8); font-size: var(--text-md); }
  .dove i { width: 10px; height: 10px; border-radius: 3px; background: var(--a); flex-shrink: 0; }
  .richiesta { display: flex; gap: var(--space-8); padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); background: var(--warn-bg); color: var(--warn);
    font-size: var(--text-sm); font-weight: 600; text-decoration: none; }
  .cand { display: flex; flex-direction: column; border: 1px solid var(--line); border-radius: var(--radius-md); overflow: hidden; }
  .riga-c { display: flex; align-items: center; gap: var(--space-10); min-height: 44px; padding: var(--space-6) var(--space-8) var(--space-6) var(--space-12); border-top: 1px solid var(--line-soft);
    background: var(--surface); font-size: var(--text-md); }
  .riga-c:first-child { border-top: none; }
  .riga-c .n { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .ore-b { width: 32px; height: 6px; border-radius: 3px; background: var(--surface-sunken); overflow: hidden; flex-shrink: 0; }
  .ore-b i { display: block; height: 100%; background: var(--cloro); }
  .ore { width: 34px; text-align: right; font-size: var(--text-xs); }
  .azione-c { position: relative; flex-shrink: 0; min-width: 84px; height: 32px; padding: 0 var(--space-12); display: inline-flex; align-items: center; justify-content: center;
    white-space: nowrap; border-radius: var(--radius-sm); font: inherit; font-size: var(--text-sm); font-weight: 700; }
  button.azione-c { border: 1px solid color-mix(in srgb, var(--cloro) 45%, transparent); background: var(--cloro-soft); color: var(--cloro-ink); cursor: pointer; }
  button.azione-c::after { content: ''; position: absolute; left: 0; right: 0; top: 50%; height: 44px; transform: translateY(-50%); }
  button.azione-c:hover { background: color-mix(in srgb, var(--cloro) 22%, var(--surface)); }
  button.azione-c.comunque { border-color: var(--warn-line); color: var(--warn); background: var(--warn-bg); }
  .azione-c.ora { color: var(--cloro-ink); font-size: var(--text-xs); }
  .nessuno { padding: var(--space-12); margin: 0; }
  .mini-btn { position: relative; height: 34px; padding: 0 var(--space-12); border-radius: var(--radius-sm); border: 1px solid var(--line); background: var(--surface); font: inherit;
    font-size: var(--text-sm); font-weight: 700; color: var(--ink); cursor: pointer; white-space: nowrap; }
  .mini-btn.tratteggio { align-self: flex-start; border-style: dashed; white-space: normal; text-align: left; height: auto; min-height: 34px; padding: var(--space-6) var(--space-12); }
  .mini-btn.pieno-warn { background: var(--warn); border-color: var(--warn); color: var(--surface); }
  .avviso { padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); background: var(--warn-bg); color: var(--warn); font-size: var(--text-sm); font-weight: 600; }
  .con-azione { display: flex; flex-direction: column; gap: var(--space-8); }
  .riga-az { display: flex; gap: var(--space-8); justify-content: flex-end; }
  .fatto { padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); background: var(--passa-bg); color: var(--passa-fg); font-size: var(--text-sm); font-weight: 600; }
  .fondo { display: flex; gap: var(--space-8); justify-content: space-between; flex-wrap: wrap; }
  .rosso-pieno { height: 40px; padding: 0 var(--space-16); background: var(--danger); border: 1.5px solid var(--danger); color: var(--on-danger); }
  .rosso-bordo { height: 40px; padding: 0 var(--space-16); background: var(--surface); border: 1.5px solid var(--danger); color: var(--danger); }
</style>
