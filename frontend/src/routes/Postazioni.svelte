<script lang="ts">
  import Azione from '../components/Azione.svelte';
  import Icona from '../components/Icona.svelte';
  import { api, messaggioErrore, type Impianto, type Mansione, type PersonaBreve, type PostazioneGestione, type Spazio } from '../lib/api';
  import Foglio from '../components/Foglio.svelte';

  let mansioni = $state<Mansione[]>([]);
  let spazi = $state<Spazio[]>([]);
  let impianti = $state<Impianto[]>([]);
  let voci = $state<string[]>([]);
  let personale = $state<PersonaBreve[]>([]);
  let caricamento = $state(true);
  let errore = $state('');
  let messaggio = $state('');

  // Pannelli
  let mansioneAperta = $state<{ mansione: Mansione | null } | null>(null);
  let postazioneAperta = $state<{ mansione: Mansione; postazione: PostazioneGestione | null } | null>(null);

  // Campi dei form (ricaricati a ogni apertura)
  let fNome = $state('');
  let fVoce = $state('');
  let fImpianto = $state('');
  let fSpazio = $state('');
  let fAttiva = $state(true);
  let fCoordinatori = $state<string[]>([]);
  let fErrore = $state('');
  let lavoro = $state(false);

  Promise.all([api.mansioni(), api.spazi(), api.impianti(), api.vociFoglioOre()])
    .then(([m, s, i, v]) => {
      mansioni = m;
      spazi = s;
      impianti = i;
      voci = v;
    })
    .catch((e) => (errore = messaggioErrore(e)))
    .finally(() => (caricamento = false));
  api.personale().then((p) => (personale = p)).catch(() => {});

  const nomeSpazio = (id: string | null) => spazi.find((s) => s.id === id)?.nome ?? '—';
  const nomeImpianto = (id: string) => impianti.find((i) => i.id === id)?.nome ?? '';

  function apriMansione(m: Mansione | null) {
    messaggio = '';
    fErrore = '';
    fNome = m?.nome ?? '';
    fVoce = m?.voceFoglioOre ?? '';
    fImpianto = m?.impiantoId ?? impianti[0]?.id ?? '';
    fCoordinatori = m?.coordinatori.map((c) => c.id) ?? [];
    mansioneAperta = { mansione: m };
  }

  function apriPostazione(m: Mansione, p: PostazioneGestione | null) {
    messaggio = '';
    fErrore = '';
    fNome = p?.nome ?? '';
    fSpazio = p?.spazioId ?? '';
    fAttiva = p?.attiva ?? true;
    postazioneAperta = { mansione: m, postazione: p };
  }

  async function salvaMansione(e: SubmitEvent) {
    e.preventDefault();
    if (!mansioneAperta) return;
    lavoro = true;
    fErrore = '';
    try {
      const m = mansioneAperta.mansione;
      let nuova = m ? await api.modificaMansione(m.id, fNome, fVoce || null, fCoordinatori) : await api.creaMansione(fImpianto, fNome, fVoce || null);
      if (!m && fCoordinatori.length) nuova = await api.modificaMansione(nuova.id, fNome, fVoce || null, fCoordinatori);
      mansioni = m ? mansioni.map((x) => (x.id === nuova.id ? nuova : x)) : [...mansioni, nuova];
      messaggio = m ? 'Mansione aggiornata.' : 'Mansione creata: ora aggiungi le sue postazioni.';
      mansioneAperta = null;
    } catch (err) {
      fErrore = messaggioErrore(err);
    } finally {
      lavoro = false;
    }
  }

  async function salvaPostazione(e: SubmitEvent) {
    e.preventDefault();
    if (!postazioneAperta) return;
    lavoro = true;
    fErrore = '';
    const { mansione, postazione } = postazioneAperta;
    try {
      if (postazione) {
        const esito = await api.modificaPostazione(postazione.id, fNome, fSpazio || null, fAttiva);
        sostituisci(esito.postazione);
        messaggio = esito.turniFuturi
          ? `Postazione disattivata. Ha ancora ${esito.turniFuturi === 1 ? 'un turno futuro' : `${esito.turniFuturi} turni futuri`}: restano validi, ma non se ne creano di nuovi.`
          : 'Postazione aggiornata.';
      } else {
        const p = await api.creaPostazione(mansione.id, fNome, fSpazio || null);
        mansioni = mansioni.map((m) => (m.id === mansione.id ? { ...m, postazioni: [...m.postazioni, p] } : m));
        messaggio = 'Postazione aggiunta.';
      }
      postazioneAperta = null;
    } catch (err) {
      fErrore = messaggioErrore(err);
    } finally {
      lavoro = false;
    }
  }

  function sostituisci(p: PostazioneGestione) {
    mansioni = mansioni.map((m) => (m.id === p.mansioneId ? { ...m, postazioni: m.postazioni.map((x) => (x.id === p.id ? p : x)) } : m));
  }

  /** Sposta una postazione di un posto dentro la sua mansione (l'ordine è quello delle righe in pianificazione). */
  async function sposta(m: Mansione, p: PostazioneGestione, verso: -1 | 1) {
    const i = m.postazioni.findIndex((x) => x.id === p.id);
    const altra = m.postazioni[i + verso];
    if (!altra) return;
    const tutte = mansioni.flatMap((x) => x.postazioni).sort((a, b) => a.ordine - b.ordine);
    const a = tutte.findIndex((x) => x.id === p.id);
    const b = tutte.findIndex((x) => x.id === altra.id);
    [tutte[a], tutte[b]] = [tutte[b], tutte[a]];
    try {
      mansioni = await api.ordinaPostazioni(tutte.map((x) => x.id));
    } catch (err) {
      errore = messaggioErrore(err);
    }
  }
</script>

<section class="page">
  <div class="barra">
    <p class="muted small nomargin grow">Le mansioni sono i ruoli pagati (con la loro tariffa e la colonna nel foglio ore); le postazioni sono le righe della pianificazione.</p>
    <button class="btn btn-primary compatto" onclick={() => apriMansione(null)}><Icona nome="piu" />Nuova mansione…</button>
  </div>
  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

  <div class="mansioni">
    {#each mansioni as m (m.id)}
      <div class="card mansione">
        <header>
          <div class="grow">
            <h2>{m.nome}</h2>
            <div class="muted small">
              {#if impianti.length > 1}{nomeImpianto(m.impiantoId)} · {/if}Foglio ore: {#if m.voceFoglioOre}<span class="tag t-soft">{m.voceFoglioOre}</span>{:else}<span>colonna propria</span>{/if}
              · Coordina: {m.coordinatori.length ? m.coordinatori.map((c) => c.nome).join(', ') : 'chi ha il permesso «Ore dello staff»'}
            </div>
          </div>
          <Azione icona="matita" etichetta={`Modifica la mansione ${m.nome}`} onclick={() => apriMansione(m)} />
        </header>
        <table>
          <thead><tr><th class="ord"><span class="sr">Ordine</span></th><th>Postazione</th><th>Spazio</th><th>Stato</th><th></th></tr></thead>
          <tbody>
            {#each m.postazioni as p, i (p.id)}
              <tr class:spenta={!p.attiva}>
                <td class="ord">
                  <span class="gruppo-azioni">
                    <Azione icona="su" etichetta={`Sposta su ${p.nome}`} disabled={i === 0} onclick={() => sposta(m, p, -1)} />
                    <Azione icona="giu" etichetta={`Sposta giù ${p.nome}`} disabled={i === m.postazioni.length - 1} onclick={() => sposta(m, p, 1)} />
                  </span>
                </td>
                <td class="nome">{p.nome}</td>
                <td class="muted">{nomeSpazio(p.spazioId)}</td>
                <td>{#if p.attiva}<span class="tag t-soft">attiva</span>{:else}<span class="tag t-altro">disattivata</span>{/if}</td>
                <td class="azioni"><Azione icona="matita" etichetta={`Modifica la postazione ${p.nome}`} onclick={() => apriPostazione(m, p)} /></td>
              </tr>
            {:else}
              <tr><td colspan="5" class="muted small">Nessuna postazione.</td></tr>
            {/each}
          </tbody>
        </table>
        <button class="btn btn-secondary btn-sm aggiungi" onclick={() => apriPostazione(m, null)}><Icona nome="piu" />Aggiungi una postazione</button>
      </div>
    {:else}
      {#if !caricamento}<p class="muted">Nessuna mansione.</p>{/if}
    {/each}
  </div>
</section>

{#if mansioneAperta}
  <Foglio titolo={mansioneAperta.mansione ? 'Modifica mansione' : 'Nuova mansione'} chiudi={() => (mansioneAperta = null)}>
    <form class="form" onsubmit={salvaMansione}>
      <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={fNome} maxlength="120" required placeholder="Es. Assistente spogliatoio" /></label>
      {#if !mansioneAperta.mansione && impianti.length > 1}
        <label class="field"><span class="lbl">Impianto</span>
          <select class="inp" bind:value={fImpianto}>{#each impianti as i (i.id)}<option value={i.id}>{i.nome}</option>{/each}</select>
        </label>
      {/if}
      <label class="field"><span class="lbl">Colonna nel foglio ore</span>
        <select class="inp" bind:value={fVoce}>
          <option value="">Colonna propria (col nome della mansione)</option>
          {#each voci as v (v)}<option value={v}>{v}</option>{/each}
        </select>
      </label>
      <div class="field">
        <span class="lbl">Chi coordina il gruppo</span>
        <span class="muted small">Con il permesso «Ore dello staff», chi coordina dei gruppi vede le ore solo di quelli. Chi ha il permesso senza coordinare nessun gruppo (la direzione) li vede tutti.</span>
        <div class="coordinatori">
          {#each personale as p (p.id)}
            <label class="spunta"><input type="checkbox" checked={fCoordinatori.includes(p.id)}
              onchange={(e) => (fCoordinatori = e.currentTarget.checked ? [...fCoordinatori, p.id] : fCoordinatori.filter((x) => x !== p.id))} />{p.nome}</label>
          {/each}
        </div>
      </div>
      {#if fErrore}<p class="msg err" role="alert">{fErrore}</p>{/if}
      <button class="btn btn-primary" disabled={lavoro}>{mansioneAperta.mansione ? 'Salva' : 'Crea la mansione'}</button>
    </form>
  </Foglio>
{/if}

{#if postazioneAperta}
  <Foglio titolo={postazioneAperta.postazione ? 'Modifica postazione' : `Nuova postazione · ${postazioneAperta.mansione.nome}`} chiudi={() => (postazioneAperta = null)}>
    <form class="form" onsubmit={salvaPostazione}>
      <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={fNome} maxlength="120" required placeholder="Es. Spogliatoio M · postazione 3" /></label>
      <label class="field"><span class="lbl">Spazio (facoltativo)</span>
        <select class="inp" bind:value={fSpazio}>
          <option value="">—</option>
          {#each spazi.filter((s) => s.impiantoId === postazioneAperta?.mansione.impiantoId) as s (s.id)}<option value={s.id}>{s.nome}</option>{/each}
        </select>
      </label>
      {#if postazioneAperta.postazione}
        <label class="riga-switch">
          <span>Attiva<br /><span class="muted small">Disattivata non compare più tra le postazioni assegnabili né nei mesi generati.</span></span>
          <button type="button" class="switch" role="switch" aria-checked={fAttiva} aria-label="Attiva" onclick={() => (fAttiva = !fAttiva)}><span></span></button>
        </label>
      {/if}
      {#if fErrore}<p class="msg err" role="alert">{fErrore}</p>{/if}
      <button class="btn btn-primary" disabled={lavoro}>{postazioneAperta.postazione ? 'Salva' : 'Aggiungi la postazione'}</button>
    </form>
  </Foglio>
{/if}

<style>
  .barra { display: flex; align-items: center; gap: var(--space-16); }
  .compatto { height: 40px; font-size: var(--text-md); padding: 0 var(--space-14); }
  .mansioni { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 520px), 1fr)); gap: var(--space-16); align-items: start; }
  .coordinatori { display: flex; flex-direction: column; gap: var(--space-4); max-height: 220px; overflow-y: auto; padding: var(--space-6) var(--space-10); border: 1px solid var(--line); border-radius: var(--radius-md); }
  .spunta { display: flex; align-items: center; gap: var(--space-8); min-height: 36px; font-size: var(--text-md); }
  .mansione { padding: var(--space-14) var(--space-16); display: flex; flex-direction: column; gap: var(--space-8); }
  header { display: flex; align-items: flex-start; gap: var(--space-12); }
  h2 { margin: 0; font-size: var(--text-xl); }
  header .tag { height: 20px; }
  table { width: 100%; border-collapse: collapse; font-size: var(--text-md); }
  th { text-align: left; font-size: var(--text-2xs); font-weight: 600; letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted); padding: var(--space-6) var(--space-6); border-bottom: 1px solid var(--line); }
  td { padding: var(--space-4) var(--space-6); border-bottom: 1px solid var(--line-soft); vertical-align: middle; }
  td .tag { height: 20px; }
  .ord { width: 64px; white-space: nowrap; }
  .nome { font-weight: 600; }
  .spenta .nome { color: var(--muted); text-decoration: line-through; }
  .azioni { text-align: right; }
  .aggiungi { align-self: flex-start; }
  .form { display: flex; flex-direction: column; gap: var(--space-14); }
  select.inp { appearance: auto; }
  .sr { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
</style>
