<script lang="ts">
  import Azione from './Azione.svelte';
  import { api, ErroreApi, messaggioErrore, type Corso, type SchedaCliente, type TurnoFrequentato, type TurnoPosti } from '../lib/api';
  import { dataBreve, GIORNI_BREVI, iso, ora } from '../lib/date';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * I turni che la persona frequenta, con cambio di turno (anche di corso) e ritiro, l'iscrizione a un nuovo turno e
   * le liste d'attesa in cui si trova. Scegliendo il turno si vedono posti e attesa: se è pieno, si mette in attesa.
   */
  let { cliente, corsi, esegui }: {
    cliente: SchedaCliente; corsi: Corso[]; esegui: (azione: () => Promise<unknown>, fatto: string) => Promise<boolean>;
  } = $props();

  const oggi = iso(new Date());
  const id = $derived(cliente.persona.id);
  const puo = $derived(sessione.puo('ISCRIZIONI'));

  let scelta = $state<{ cambio: TurnoFrequentato | null; corsoId: string; dal: string; turni: TurnoPosti[] | null } | null>(null);
  let errore = $state('');
  let pieno = $state<{ turno: TurnoPosti; riprova: () => Promise<void> } | null>(null);

  const quando = (t: { giornoSettimana: number; inizio: string; fine: string }) => `${GIORNI_BREVI[t.giornoSettimana]} ${ora(t.inizio)}–${ora(t.fine)}`;

  async function apri(cambio: TurnoFrequentato | null) {
    errore = '';
    pieno = null;
    scelta = { cambio, corsoId: cambio?.corsoId ?? cliente.turni[0]?.corsoId ?? '', dal: oggi, turni: null };
    if (scelta.corsoId) await caricaTurni();
  }

  async function caricaTurni() {
    if (!scelta?.corsoId) return;
    scelta.turni = null;
    try {
      scelta.turni = await api.turniDelCorso(scelta.corsoId);
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  /** L'abbonamento è per un altro corso: va aggiornato a parte (il tornello guarda corso e fascia). */
  const avvisoCorso = (t: TurnoPosti) =>
    scelta?.cambio && t.corsoId !== scelta.cambio.corsoId && cliente.abbonamenti.some((a) => a.tipo === 'CORSO' && a.corsoId === scelta!.cambio!.corsoId
      && (!a.al || a.al >= oggi))
      ? ` L'abbonamento è per ${scelta.cambio.corso}: ricordati di aggiornarlo.` : '';

  async function scegli(t: TurnoPosti, forza = false) {
    const s = scelta!;
    errore = '';
    pieno = null;
    const azione = s.cambio
      ? () => api.cambiaTurno(s.cambio!.iscrizioneId, { lezioneId: t.lezioneId, dal: s.dal, forza })
      : () => api.iscrivi(t.lezioneId, { personaId: id, dal: s.dal, forza });
    const fatto = s.cambio
      ? `Turno cambiato: dal ${dataBreve(s.dal)} ${t.corso} ${quando(t)}.${avvisoCorso(t)}`
      : `Iscritto a ${t.corso} ${quando(t)} dal ${dataBreve(s.dal)}.`;
    try {
      await azione();
    } catch (e) {
      if (e instanceof ErroreApi && e.status === 409 && e.message.includes('al completo')) {
        errore = e.message;
        pieno = { turno: t, riprova: () => scegli(t, true) };
      } else errore = messaggioErrore(e);
      return;
    }
    scelta = null;
    await esegui(async () => {}, fatto);
  }

  async function attendi(t: TurnoPosti) {
    const ok = await esegui(() => api.aggiungiAttesa(t.lezioneId, { personaId: id }), `In lista d'attesa per ${t.corso} ${quando(t)}.`);
    if (ok) scelta = null;
  }

  function ritira(t: TurnoFrequentato) {
    esegui(() => api.terminaIscrizione(t.iscrizioneId, oggi), `Ritirato da ${t.corso} ${quando(t)}: oggi è l'ultimo giorno.`);
  }

  function togli(attesaId: string) {
    esegui(() => api.rinunciaAttesa(attesaId), 'Tolto dalla lista d\'attesa.');
  }

  const posti = (t: TurnoPosti) => (t.posti == null ? `${t.occupati} iscritti` : `${t.occupati}/${t.posti}`);
  const libero = (t: TurnoPosti) => t.liberi == null || t.liberi > 0;
  const giaQui = (t: TurnoPosti) => cliente.turni.some((x) => x.lezioneId === t.lezioneId) || cliente.attesa.some((a) => a.lezioneId === t.lezioneId);
</script>

<section class="card blocco">
  <h3>Turni frequentati</h3>
  <ul class="righe">
    {#each cliente.turni as t (t.iscrizioneId)}
      <li class="voce-l">
        <span class="grow"><strong>{t.corso}</strong> · <span class="m">{quando(t)}</span>
          <span class="muted small">{t.dal > oggi ? ` · dal ${dataBreve(t.dal)}` : ''}{t.al ? ` · fino al ${dataBreve(t.al)}` : ''}</span></span>
        {#if puo && (!t.al || t.al >= oggi)}
          <span class="gruppo-azioni">
            <Azione icona="scambio" etichetta={`Cambia turno di ${t.corso}`} onclick={() => apri(t)} />
            {#if !t.al}<Azione icona="persona-meno" tono="danger" etichetta={`Ritira da ${t.corso}`} conferma="Ritira?" onclick={() => ritira(t)} />{/if}
          </span>
        {/if}
      </li>
    {:else}
      <li class="muted small">Nessun turno.</li>
    {/each}
  </ul>

  {#if cliente.attesa.length}
    <div class="sotto">
      <span class="lbl">In lista d'attesa</span>
      {#each cliente.attesa as a (a.attesaId)}
        <div class="voce-l small">
          <span class="grow">{a.corso} · <span class="m">{quando(a)}</span> · <strong>{a.posizione}° in lista</strong>
            {#if a.liberi != null && a.liberi > 0}<span class="tag t-soft">posto libero</span>{/if}</span>
          {#if puo}<Azione icona="cestino" tono="danger" etichetta={`Togli dalla lista d'attesa di ${a.corso}`} conferma="Togli?" onclick={() => togli(a.attesaId)} />{/if}
        </div>
      {/each}
      {#if puo}<a class="small" href="#/attesa">Liste d'attesa</a>{/if}
    </div>
  {/if}

  {#if puo}
    {#if scelta}
      <div class="modulo">
        <div class="lbl">{scelta.cambio ? `Cambia turno: ${scelta.cambio.corso} ${quando(scelta.cambio)}` : 'Iscrivi a un turno'}</div>
        <div class="due">
          <label class="field"><span class="lbl">Corso</span>
            <select class="inp" bind:value={scelta.corsoId} onchange={caricaTurni}>
              <option value="" disabled>Scegli il corso</option>
              {#each corsi as c (c.id)}<option value={c.id}>{c.nome}</option>{/each}
            </select>
          </label>
          <label class="field"><span class="lbl">{scelta.cambio ? 'Nel nuovo turno dal' : 'Dal'}</span><input class="inp" type="date" bind:value={scelta.dal} min={oggi} /></label>
        </div>
        {#if scelta.cambio}<p class="muted small nomargin">Nel turno attuale l'ultimo giorno sarà il giorno prima.</p>{/if}
        {#if errore}
          <p class="msg err" role="alert">{errore}</p>
          {#if pieno}
            {@const p = pieno}
            <div class="row">
              <button class="btn btn-primary grow" onclick={() => attendi(p.turno)}>Metti in lista d'attesa</button>
              <button class="btn btn-secondary grow" onclick={p.riprova}>{scelta.cambio ? 'Cambia' : 'Iscrivi'} in soprannumero</button>
            </div>
          {/if}
        {/if}
        {#if scelta.turni}
          <ul class="turni">
            {#each scelta.turni as t (t.lezioneId)}
              {@const qui = giaQui(t)}
              <li class:pieno={!libero(t)}>
                <span class="m quando">{quando(t)}</span>
                <span class="grow small">{posti(t)}{!libero(t) ? ' · al completo' : ''}{t.inAttesa ? ` · ${t.inAttesa} in attesa` : ''}
                  {#if !libero(t) && t.liberoDal}<span class="muted"> · si libera dal {dataBreve(t.liberoDal)}</span>{/if}</span>
                {#if qui}<span class="muted small">già qui</span>
                {:else if libero(t)}<button class="btn btn-secondary piccolo" onclick={() => scegli(t)}>{scelta.cambio ? 'Passa qui' : 'Iscrivi'}</button>
                {:else}<button class="btn btn-secondary piccolo" onclick={() => attendi(t)}>Lista d'attesa</button>{/if}
              </li>
            {:else}
              <li class="muted small">Questo corso non ha turni.</li>
            {/each}
          </ul>
        {:else if scelta.corsoId}
          <p class="muted small nomargin">Carico i turni…</p>
        {/if}
        <button type="button" class="btn btn-secondary" onclick={() => (scelta = null)}>Chiudi</button>
      </div>
    {:else}
      <button class="btn btn-secondary" onclick={() => apri(null)}>Iscrivi a un turno…</button>
    {/if}
  {:else if !cliente.turni.length}
    <p class="muted small nomargin">Le iscrizioni ai turni le fa chi ha il permesso «Iscrizioni».</p>
  {/if}
</section>

<style>
  .blocco { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16); }
  h3 { margin: 0; font-size: var(--text-xl); }
  .righe { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
  .righe > li { padding: var(--space-8) 0; border-top: 1px solid var(--line-soft); }
  .righe > li:first-child { border-top: none; }
  .voce-l { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .sotto { display: flex; flex-direction: column; gap: var(--space-4); padding-top: var(--space-10); border-top: 1px solid var(--line); }
  .modulo { display: flex; flex-direction: column; gap: var(--space-10); padding-top: var(--space-10); border-top: 1px solid var(--line); }
  select.inp { appearance: auto; }
  .turni { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; border: 1px solid var(--line-soft); border-radius: var(--radius-md); overflow: hidden; }
  .turni li { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-6) var(--space-10); border-top: 1px solid var(--line-soft); min-height: 48px; }
  .turni li:first-child { border-top: none; }
  .turni li.pieno { background: var(--surface-sunken); }
  .quando { min-width: 118px; font-weight: 600; }
  .piccolo { min-height: 34px; padding: 0 var(--space-12); font-size: var(--text-sm); }
</style>
