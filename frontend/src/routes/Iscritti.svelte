<script lang="ts">
  import { untrack } from 'svelte';
  import { api, ErroreApi, messaggioErrore, type ElencoIscritti, type PersonaScheda } from '../lib/api';
  import { giornoEsteso, iso, ora } from '../lib/date';

  /**
   * Gli iscritti a un turno del corso in una data: per l'istruttore il registro delle presenze, per la segreteria
   * le iscrizioni (nuove, soprannumero, ritiri). Si mostrano solo nome ed età: niente contatti.
   */
  let { lezioneId, data, cambiato }: { lezioneId: string; data: string; cambiato?: () => void } = $props();

  let elenco = $state<ElencoIscritti | null>(null);
  let errore = $state('');
  let messaggio = $state('');
  let presenze = $state<Record<string, boolean>>({});
  let salvando = $state(false);
  let ritiro = $state('');

  // iscrizione
  let aperta = $state(false);
  let cerca = $state('');
  let trovate = $state<PersonaScheda[]>([]);
  let nuova = $state({ nome: '', cognome: '', dataNascita: '' });
  let completo = $state<null | (() => Promise<void>)>(null);

  const giorno = untrack(() => data);
  const oggi = iso(new Date());

  async function carica() {
    try {
      elenco = await api.iscritti(lezioneId, giorno);
      presenze = Object.fromEntries(elenco.iscritti.filter((i) => i.presente != null).map((i) => [i.personaId, i.presente!]));
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a caricare gli iscritti.');
    }
  }
  carica();

  const modificate = $derived(
    elenco ? elenco.iscritti.filter((i) => presenze[i.personaId] !== undefined && presenze[i.personaId] !== i.presente).length : 0,
  );
  const presenti = $derived(Object.values(presenze).filter(Boolean).length);

  async function salvaPresenze() {
    if (!elenco) return;
    salvando = true;
    errore = '';
    try {
      const lista = elenco.iscritti.filter((i) => presenze[i.personaId] !== undefined).map((i) => ({ personaId: i.personaId, presente: presenze[i.personaId] }));
      elenco = await api.segnaPresenze(lezioneId, giorno, lista);
      messaggio = `Presenze salvate: ${presenti} su ${elenco.iscritti.length}.`;
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      salvando = false;
    }
  }

  function tuttiPresenti() {
    if (elenco) presenze = Object.fromEntries(elenco.iscritti.map((i) => [i.personaId, true]));
  }

  let ultimaRicerca = 0;
  async function cercaPersone() {
    const q = cerca.trim();
    const n = ++ultimaRicerca;
    if (q.length < 2) return (trovate = []);
    try {
      const r = await api.cercaPersone(q);
      if (n === ultimaRicerca) trovate = r;
    } catch {
      trovate = [];
    }
  }

  async function iscrivi(chi: { personaId?: string; nuovaPersona?: { nome: string; cognome: string; dataNascita: string | null } }, forza = false) {
    errore = '';
    completo = null;
    try {
      const i = await api.iscrivi(lezioneId, { ...chi, dal: giorno < oggi ? oggi : giorno, forza });
      messaggio = `${i.nome} ${i.cognome} iscritto${forza ? ' in soprannumero' : ''}.`;
      aperta = false;
      cerca = '';
      trovate = [];
      nuova = { nome: '', cognome: '', dataNascita: '' };
      await carica();
      cambiato?.();
    } catch (e) {
      if (e instanceof ErroreApi && e.status === 409 && e.message.includes('al completo')) {
        errore = e.message;
        completo = () => iscrivi(chi, true);
      } else errore = messaggioErrore(e);
    }
  }

  async function ritira(iscrizioneId: string) {
    if (ritiro !== iscrizioneId) return (ritiro = iscrizioneId);
    ritiro = '';
    try {
      await api.terminaIscrizione(iscrizioneId, oggi);
      messaggio = 'Ritirato: da domani non risulta più iscritto.';
      await carica();
      cambiato?.();
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  const eta = (n: number | null) => (n == null ? '' : n === 1 ? '1 anno' : `${n} anni`);
</script>

{#if elenco}
  {@const pieno = elenco.posti != null && elenco.iscritti.length >= elenco.posti}
  <div class="testa">
    <div>
      <div class="cap">{giornoEsteso(elenco.data)} · <span class="m">{ora(elenco.inizio)}–{ora(elenco.fine)}</span></div>
      <div class="muted small">{elenco.iscritti.length}{elenco.posti != null ? ` su ${elenco.posti} posti` : ' iscritti'}{pieno ? ' · al completo' : ''}</div>
    </div>
    {#if elenco.puoSegnarePresenze}<span class="tag t-soft">{presenti} presenti</span>{/if}
  </div>

  {#if !elenco.siTiene}<p class="hint nomargin">In questa data il turno non si tiene: le presenze non si segnano.</p>
  {:else if !elenco.puoSegnarePresenze && elenco.data > oggi}<p class="muted small nomargin">Le presenze si segnano il giorno della lezione.</p>{/if}
  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#if errore}
    <p class="msg err" role="alert">{errore}</p>
    {#if completo}<button class="btn btn-secondary" onclick={completo}>Iscrivi comunque in soprannumero</button>{/if}
  {/if}

  <ul class="card elenco">
    {#each elenco.iscritti as i (i.iscrizioneId)}
      <li>
        <span class="chi">
          <span class="nome">{i.cognome} {i.nome}</span>
          <span class="muted small">{eta(i.eta)}{i.al ? ` · fino al ${i.al}` : ''}{i.dal > elenco.data ? ` · dal ${i.dal}` : ''}</span>
        </span>
        {#if elenco.puoSegnarePresenze}
          <span class="seg pa" role="group" aria-label="Presenza di {i.nome}">
            <button aria-pressed={presenze[i.personaId] === true} onclick={() => (presenze = { ...presenze, [i.personaId]: true })}>Presente</button>
            <button aria-pressed={presenze[i.personaId] === false} onclick={() => (presenze = { ...presenze, [i.personaId]: false })}>Assente</button>
          </span>
        {/if}
        {#if elenco.puoIscrivere && !i.al}
          <button class="link small ritira" onclick={() => ritira(i.iscrizioneId)}>{ritiro === i.iscrizioneId ? 'Conferma ritiro' : 'Ritira'}</button>
        {/if}
      </li>
    {:else}
      <li class="muted small">Nessun iscritto.</li>
    {/each}
  </ul>

  {#if elenco.puoSegnarePresenze && elenco.iscritti.length}
    <div class="row">
      <button class="btn btn-secondary grow" onclick={tuttiPresenti}>Tutti presenti</button>
      <button class="btn btn-primary grow" disabled={salvando || !modificate} onclick={salvaPresenze}>
        {salvando ? 'Salvo…' : modificate ? `Salva presenze (${modificate})` : elenco.iscritti.some((i) => i.presente != null) ? 'Presenze salvate' : 'Salva presenze'}
      </button>
    </div>
  {/if}

  {#if elenco.puoIscrivere}
    {#if !aperta}
      <button class="btn btn-secondary" onclick={() => ((aperta = true), (messaggio = ''))}>Iscrivi qualcuno…</button>
    {:else}
      <div class="card iscrivi">
        <label class="field"><span class="lbl">Cerca nell'anagrafica</span>
          <input class="inp" bind:value={cerca} oninput={cercaPersone} placeholder="Nome e cognome" autocomplete="off" />
        </label>
        {#each trovate as p (p.id)}
          <button class="trovata" onclick={() => iscrivi({ personaId: p.id })}>
            <span>{p.cognome} {p.nome}</span><span class="muted small">{p.dataNascita ?? ''}</span>
          </button>
        {/each}
        <div class="lbl sep">Oppure una persona nuova</div>
        <div class="due">
          <input class="inp" bind:value={nuova.nome} placeholder="Nome" aria-label="Nome" />
          <input class="inp" bind:value={nuova.cognome} placeholder="Cognome" aria-label="Cognome" />
        </div>
        <label class="field"><span class="lbl">Data di nascita</span><input class="inp" type="date" bind:value={nuova.dataNascita} max={oggi} /></label>
        <button class="btn btn-primary" disabled={!nuova.nome.trim() || !nuova.cognome.trim()}
                onclick={() => iscrivi({ nuovaPersona: { nome: nuova.nome, cognome: nuova.cognome, dataNascita: nuova.dataNascita || null } })}>Iscrivi</button>
        <p class="muted small nomargin">La scheda completa (contatti, tutori, certificato) arriverà con la parte clienti.</p>
      </div>
    {/if}
  {/if}
{:else if errore}
  <p class="msg err" role="alert">{errore}</p>
{:else}
  <p class="muted">Caricamento…</p>
{/if}

<style>
  .testa { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-12); }
  .cap { font-weight: 600; }
  .cap::first-letter { text-transform: uppercase; }
  .elenco { list-style: none; margin: 0; padding: 0; overflow: hidden; }
  .elenco li { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-8) var(--space-12); border-top: 1px solid var(--line-soft); min-height: 52px; }
  .elenco li:first-child { border-top: none; }
  .chi { flex: 1; min-width: 0; display: flex; flex-direction: column; }
  .nome { font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .pa { grid-template-columns: auto auto; padding: 2px; }
  .pa button { min-height: 32px; padding: 0 var(--space-8); font-size: var(--text-xs); }
  .pa button[aria-pressed='true']:first-child { background: var(--ok); color: #fff; }
  .pa button[aria-pressed='true']:last-child { background: var(--danger); color: #fff; }
  .ritira { color: var(--danger); min-height: 32px; }
  .iscrivi { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-12); }
  .trovata { display: flex; justify-content: space-between; gap: var(--space-8); padding: var(--space-8) var(--space-10); border: 1px solid var(--line); border-radius: var(--radius-sm); background: var(--surface); color: var(--ink); font: inherit; cursor: pointer; text-align: left; }
  .trovata:hover { border-color: var(--cloro); }
  .sep { margin-top: var(--space-6); }
</style>
