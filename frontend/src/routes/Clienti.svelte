<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import Azione from '../components/Azione.svelte';
  import { onMount, tick } from 'svelte';
  import { api, messaggioErrore, type GiornataBanco, type PersonaScheda, type VoceScadenza } from '../lib/api';
  import { motivoBreve } from '../lib/banco';
  import { aggiungiGiorni, dataBreve, GIORNI, iso, daIso, oraDi } from '../lib/date';
  import { euro } from '../lib/listino';
  import { codiceValido, normalizzaCodice } from '../lib/nfc';
  import { recenti, type Recente } from '../lib/recenti';
  import { schermo } from '../lib/schermo.svelte';
  import { sessione } from '../lib/sessione.svelte';
  import SchedaCliente from './SchedaCliente.svelte';

  /**
   * I clienti (#/clienti/<id> apre la scheda): segreteria, coordinatori, admin.
   *
   * Senza nessuno aperto, la ricerca sta al centro (si raggiunge da ovunque con «/», e il lettore dei braccialetti apre
   * la scheda di chi lo porta), con sotto tre elenchi: aperti di recente, al banco oggi, in scadenza questa settimana;
   * in fondo i numeri della giornata. Con un cliente aperto la ricerca passa a lato della scheda.
   */
  const leggiId = () => location.hash.replace('#/', '').split('/')[1] ?? '';
  let scelto = $state(leggiId());
  let cerca = $state('');
  let trovate = $state<PersonaScheda[]>([]);
  let cercato = $state(false);
  let attivo = $state(0);
  let nuovo = $state<{ nome: string; cognome: string; dataNascita: string } | null>(null);
  let errore = $state('');
  let campo = $state<HTMLInputElement | null>(null);
  const oggi = iso(new Date());

  onMount(() => {
    const f = () => {
      scelto = leggiId();
      if (!scelto) caricaHome();
    };
    window.addEventListener('hashchange', f);
    // «/» porta alla ricerca da qualsiasi punto della pagina, tranne quando si sta già scrivendo
    const tasto = (e: KeyboardEvent) => {
      if (e.key !== '/' || e.ctrlKey || e.metaKey || e.altKey) return;
      const t = e.target as HTMLElement | null;
      if (t && (t.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(t.tagName))) return;
      e.preventDefault();
      campo?.focus();
      campo?.select();
    };
    window.addEventListener('keydown', tasto);
    return () => {
      window.removeEventListener('hashchange', f);
      window.removeEventListener('keydown', tasto);
    };
  });

  let ultima = 0;
  async function cercaPersone() {
    const q = cerca.trim();
    const n = ++ultima;
    attivo = 0;
    if (q.length < 2) {
      trovate = [];
      cercato = false;
      return;
    }
    try {
      const r = await api.cercaPersone(q);
      if (n === ultima) {
        trovate = r;
        cercato = true;
      }
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  function apri(id: string) {
    cerca = '';
    trovate = [];
    cercato = false;
    location.hash = `#/clienti/${id}`;
  }

  /** Frecce per scegliere, Invio per aprire; un codice di braccialetto (dal lettore) apre la scheda di chi lo porta. */
  async function tasti(e: KeyboardEvent) {
    if (e.key === 'ArrowDown' && trovate.length) { e.preventDefault(); attivo = (attivo + 1) % trovate.length; }
    else if (e.key === 'ArrowUp' && trovate.length) { e.preventDefault(); attivo = (attivo - 1 + trovate.length) % trovate.length; }
    else if (e.key === 'Escape') { cerca = ''; trovate = []; cercato = false; }
    else if (e.key === 'Enter') {
      e.preventDefault();
      const c = normalizzaCodice(cerca);
      if (/\d/.test(c) && codiceValido(c)) {
        try {
          const r = await api.braccialettoDiChi(c);
          if (r.personaId) return apri(r.personaId);
          errore = `Il braccialetto ${c} non è di nessun cliente.`;
          return;
        } catch {
          /* non era un braccialetto: si cerca per nome */
        }
      }
      if (trovate[attivo]) apri(trovate[attivo].id);
    }
  }

  async function crea() {
    if (!nuovo) return;
    errore = '';
    try {
      const p = await api.nuovoCliente({ nome: nuovo.nome, cognome: nuovo.cognome, dataNascita: nuovo.dataNascita || null });
      nuovo = null;
      location.hash = `#/clienti/${p.id}`;
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  // ── la pagina senza nessuno aperto ──
  let aperti = $state<Recente[]>([]);
  let giornata = $state<GiornataBanco | null>(null);
  let scadenze = $state<VoceScadenza[] | null>(null);
  let incassato = $state<{ totale: number; pagamenti: number } | null>(null);
  function caricaHome() {
    aperti = recenti();
    api.giornataBanco(12).then((g) => (giornata = g)).catch(() => {});
    if (sessione.puo('CLIENTI'))
      api.scadenze(7).then((s) => {
        scadenze = [...s.certificati, ...s.abbonamenti, ...s.tesseramento]
          .filter((v) => v.giorni != null && v.giorni >= 0 && v.giorni <= 7)
          .sort((a, b) => (a.giorni ?? 0) - (b.giorni ?? 0));
      }).catch(() => {});
    if (sessione.puo('PAGAMENTI'))
      api.incassi(oggi, oggi).then((i) => (incassato = { totale: i.totale, pagamenti: i.pagamenti.length })).catch(() => {});
  }
  onMount(async () => {
    if (!scelto) caricaHome();
    await tick();
    if (!scelto && schermo.larga) campo?.focus();
  });

  /** Una persona per riga nel «al banco oggi»: il passaggio più recente di ognuno. */
  const alBanco = $derived.by(() => {
    if (!giornata) return [];
    const visti = new Set<string>();
    return giornata.passaggi.filter((p) => p.persona && !visti.has(p.persona.id) && visti.add(p.persona.id)).slice(0, 6);
  });
  const eta = (n: number | null) => (n == null ? '' : n === 1 ? '1 anno' : `${n} anni`);
  const iniziali = (nome: string, cognome: string) => `${nome[0] ?? ''}${cognome[0] ?? ''}`.toUpperCase();
  function quando(v: VoceScadenza): string {
    if (v.giorni === 0) return 'oggi';
    if (!v.scadenza) return '';
    const d = daIso(v.scadenza);
    return `${GIORNI[d.getDay()].slice(0, 3)} ${d.getDate()}`;
  }
  function apertoIl(r: Recente): string {
    const giorno = r.quando.slice(0, 10);
    const d = new Date(r.quando).toLocaleDateString('sv-SE', { timeZone: 'Europe/Rome' });
    if (d === oggi) return `aperto alle ${oraDi(r.quando)}`;
    if (d === aggiungiGiorni(oggi, -1)) return 'ieri';
    return dataBreve(d || giorno);
  }
</script>

{#snippet risultati()}
  {#if trovate.length}
    <ul class="card risultati" role="listbox" aria-label="Risultati">
      {#each trovate as p, i (p.id)}
        <li role="option" aria-selected={i === attivo}><a href={`#/clienti/${p.id}`} class:on={p.id === scelto || i === attivo} onmouseenter={() => (attivo = i)}>
          <span class="nome">{p.cognome} {p.nome}</span>
          <span class="muted small">{p.dataNascita ? dataBreve(p.dataNascita) : ''}</span>
        </a></li>
      {/each}
    </ul>
  {:else if cercato}
    <p class="muted small nomargin">Nessuno con questo nome.</p>
  {/if}
{/snippet}

{#snippet formNuovo()}
  {#if nuovo}
    <form class="card nuovo" onsubmit={(e) => { e.preventDefault(); crea(); }}>
      <h3>Nuovo cliente</h3>
      <div class="due">
        <input class="inp" bind:value={nuovo.nome} placeholder="Nome" aria-label="Nome" />
        <input class="inp" bind:value={nuovo.cognome} placeholder="Cognome" aria-label="Cognome" />
      </div>
      <label class="field"><span class="lbl">Data di nascita</span><input class="inp" type="date" bind:value={nuovo.dataNascita} max={oggi} /></label>
      <div class="row">
        <button type="button" class="btn btn-secondary grow" onclick={() => (nuovo = null)}>Annulla</button>
        <button class="btn btn-primary grow" disabled={!nuovo.nome.trim() || !nuovo.cognome.trim()}>Crea</button>
      </div>
      <p class="muted small nomargin">Contatti e tutori si aggiungono poi nella scheda.</p>
    </form>
  {/if}
{/snippet}

{#if scelto}
  <div class="clienti" class:largo={schermo.larga}>
    {#if schermo.larga}
      <aside class="ricerca">
        <label class="field">
          <span class="lbl">Cerca un cliente <kbd>/</kbd></span>
          <input class="inp" type="search" bind:this={campo} bind:value={cerca} oninput={cercaPersone} onkeydown={tasti}
                 placeholder="Nome, cognome o braccialetto" autocomplete="off" />
        </label>
        {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
        {@render risultati()}
        {#if !nuovo}<button class="btn btn-secondary" onclick={() => (nuovo = { nome: '', cognome: '', dataNascita: '' })}><Icona nome="piu" />Nuovo cliente…</button>{/if}
        {@render formNuovo()}
      </aside>
    {/if}
    <div class="scheda">
      {#if !schermo.larga}<span><Azione cornice icona="indietro" etichetta="Torna ai clienti" href="#/clienti" /></span>{/if}
      {#key scelto}<SchedaCliente personaId={scelto} />{/key}
    </div>
  </div>
{:else}
  <div class="home">
    <div class="testa">
      <span class="grow"></span>
      <button class="btn btn-primary" onclick={() => (nuovo = { nome: '', cognome: '', dataNascita: '' })}><Icona nome="piu" />Nuovo cliente…</button>
    </div>

    <div class="centro">
      <label class="cerca-g">
        <Icona nome="cerca" />
        <input class="inp" type="search" aria-label="Cerca un cliente" bind:this={campo} bind:value={cerca} oninput={cercaPersone} onkeydown={tasti}
               placeholder="Nome, cognome o braccialetto" autocomplete="off" />
        {#if schermo.larga}<kbd title="Premi / per iniziare a cercare">/</kbd>{/if}
      </label>
      {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
      <div class="sotto-ricerca">{@render risultati()}</div>
      {#if !trovate.length && !cercato}<span class="muted small">Funziona anche il lettore: passa il braccialetto e si apre la scheda di chi lo porta.</span>{/if}
      <div class="sotto-ricerca">{@render formNuovo()}</div>
    </div>

    <div class="tre">
      <section class="blocco">
        <div class="blocco-testa"><h2 class="lbl">Aperti di recente</h2></div>
        <ul class="card righe">
          {#each aperti as r (r.id)}
            <li><a href={`#/clienti/${r.id}`} class="persona"><span class="av" aria-hidden="true">{iniziali(r.nome, r.cognome)}</span>
              <span class="chi"><span class="n">{r.nome} {r.cognome}</span><span class="perche">{[eta(r.eta), apertoIl(r)].filter(Boolean).join(' · ')}</span></span></a></li>
          {:else}
            <li class="vuoto muted small">Le schede che apri compaiono qui.</li>
          {/each}
        </ul>
      </section>

      <section class="blocco">
        <div class="blocco-testa"><h2 class="lbl">Al banco oggi</h2><a href="#/tornello" class="link-p">Tutti i passaggi →</a></div>
        <ul class="card righe">
          {#each alBanco as p (p.id)}
            <li><a href={`#/clienti/${p.persona!.id}`} class="persona"><span class="av" class:r={!p.verde} aria-hidden="true">{iniziali(p.persona!.nome, p.persona!.cognome)}</span>
              <span class="chi"><span class="n">{p.persona!.nome} {p.persona!.cognome}</span>
                <span class="perche" class:rosso={!p.verde}>{!p.verde ? `Fermato alle ${oraDi(p.istante)} · ${motivoBreve(p.motivi)}` : `${p.verso === 'USCITA' ? 'Uscito' : 'Entrato'} alle ${oraDi(p.istante)}${p.goldenPass ? ' · golden pass' : p.carnetScalato ? ' · carnet' : ''}`}</span></span></a></li>
          {:else}
            <li class="vuoto muted small">{giornata ? 'Nessun passaggio oggi.' : 'Carico…'}</li>
          {/each}
        </ul>
      </section>

      {#if scadenze}
        <section class="blocco">
          <div class="blocco-testa"><h2 class="lbl">In scadenza questa settimana</h2><a href="#/scadenze" class="link-p">Scadenze →</a></div>
          <ul class="card righe">
            {#each scadenze.slice(0, 6) as v (v.riferimento + v.personaId)}
              <li><a href={`#/clienti/${v.personaId}`} class="persona"><span class="av w" aria-hidden="true">{iniziali(v.nome, v.cognome)}</span>
                <span class="chi"><span class="n">{v.nome} {v.cognome}</span><span class="perche">{v.descrizione}</span></span>
                <span class="scad" class:oggi={v.giorni === 0}>{quando(v)}</span></a></li>
            {:else}
              <li class="vuoto muted small">Niente in scadenza nei prossimi sette giorni.</li>
            {/each}
          </ul>
        </section>
      {/if}
    </div>

    {#if giornata}
      <div class="kpi-oggi">
        <a class="k" href="#/tornello"><span class="lbl">Entrati oggi</span><span class="v">{giornata.conteggi.entrati}</span>
          <span class="nota">{giornata.conteggi.dentro == null ? 'persone diverse' : `${giornata.conteggi.dentro} dentro ora`}</span></a>
        <a class="k" class:warn={giornata.daSistemare.length > 0} href="#/tornello"><span class="lbl">Da sistemare</span><span class="v">{giornata.daSistemare.length}</span><span class="nota">rimasti fuori</span></a>
        {#if incassato}<a class="k" href="#/incassi"><span class="lbl">Incassato oggi</span><span class="v">{euro(incassato.totale)}</span><span class="nota">{incassato.pagamenti === 1 ? '1 pagamento' : `${incassato.pagamenti} pagamenti`}</span></a>{/if}
        {#if scadenze}<a class="k" href="#/scadenze"><span class="lbl">In scadenza</span><span class="v">{scadenze.length}</span><span class="nota">nei prossimi 7 giorni</span></a>{/if}
      </div>
    {/if}
  </div>
{/if}

<style>
  .clienti { display: flex; flex-direction: column; gap: var(--space-16); }
  .clienti.largo { display: grid; grid-template-columns: 300px minmax(0, 1fr); align-items: start; gap: var(--space-24, 24px); }
  .ricerca { display: flex; flex-direction: column; gap: var(--space-12); }
  .largo .ricerca { position: sticky; top: 28px; }
  .ricerca kbd { margin-left: var(--space-6); }
  kbd { display: inline-grid; place-items: center; min-width: 22px; height: 22px; padding: 0 6px; border-radius: 6px; border: 1px solid var(--line-strong);
    border-bottom-width: 2px; font-family: var(--font-mono); font-size: 12px; color: var(--muted); background: var(--surface); }
  .risultati { list-style: none; margin: 0; padding: 0; overflow: hidden; }
  .risultati a { display: flex; justify-content: space-between; gap: var(--space-8); padding: var(--space-10) var(--space-12); color: var(--ink);
    text-decoration: none; border-top: 1px solid var(--line-soft); }
  .risultati li:first-child a { border-top: none; }
  .risultati a.on { background: var(--cloro-soft); color: var(--cloro-ink); }
  .nome { font-weight: 600; }
  .nuovo { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14); text-align: left; }
  .nuovo h3 { margin: 0; font-size: var(--text-lg); }
  .due { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-8); }
  .scheda { display: flex; flex-direction: column; gap: var(--space-10); min-width: 0; }

  .home { display: flex; flex-direction: column; gap: var(--space-24, 24px); }
  /* il pulsante sale sulla riga del titolo della pagina, senza coprirlo */
  .testa { display: flex; align-items: center; margin: -64px 0 -16px; min-height: 48px; pointer-events: none; }
  .testa .btn { pointer-events: auto; }
  .centro { display: flex; flex-direction: column; align-items: center; gap: var(--space-8); padding: var(--space-8) 0; }
  .cerca-g { position: relative; width: 100%; max-width: 640px; display: flex; align-items: center; }
  .cerca-g > :global(.ic) { position: absolute; left: 16px; color: var(--muted); pointer-events: none; }
  .cerca-g .inp { width: 100%; height: 56px; padding-left: 48px; padding-right: 48px; font-size: var(--text-lg); border-radius: var(--radius-lg, 16px); }
  .cerca-g kbd { position: absolute; right: 14px; }
  .sotto-ricerca { width: 100%; max-width: 640px; }
  .sotto-ricerca:empty { display: none; }

  .tre { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: var(--space-16); align-items: stretch; }
  .blocco { display: flex; flex-direction: column; gap: var(--space-8); }
  .blocco-testa { display: flex; align-items: center; justify-content: space-between; height: 24px; }
  .blocco-testa h2 { margin: 0; }
  .link-p { font-size: var(--text-sm); font-weight: 600; color: var(--cloro); text-decoration: none; }
  .righe { list-style: none; margin: 0; padding: 4px 14px; flex: 1; }
  .righe li { border-top: 1px solid var(--line-soft); }
  .righe li:first-child { border-top: none; }
  .righe .vuoto { padding: var(--space-12) 0; }
  .persona { display: flex; align-items: center; gap: var(--space-10); padding: 9px 0; color: var(--ink); text-decoration: none; min-width: 0; }
  .persona:hover .n { text-decoration: underline; }
  .av { width: 32px; height: 32px; border-radius: 50%; display: grid; place-items: center; flex-shrink: 0; background: var(--cloro-soft); color: var(--cloro-ink); font-size: 11px; font-weight: 700; }
  .av.r { background: var(--ferma-bg); color: var(--ferma); }
  .av.w { background: var(--warn-bg); color: var(--warn); }
  .chi { flex: 1; min-width: 0; display: flex; flex-direction: column; line-height: 1.3; }
  .n { font-weight: 600; font-size: var(--text-sm); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .perche { font-size: var(--text-xs); color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .perche.rosso { color: var(--ferma); }
  .scad { font-size: var(--text-xs); font-weight: 600; color: var(--muted); white-space: nowrap; }
  .scad.oggi { color: var(--warn); }

  .kpi-oggi { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: var(--space-8); width: 100%; max-width: 960px; align-self: center; }
  .k { display: flex; flex-direction: column; gap: 2px; padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); background: var(--surface);
    border: 1px solid var(--line); color: var(--ink); text-decoration: none; }
  .k:hover { border-color: var(--line-strong); }
  .k.warn { background: var(--warn-bg); border-color: transparent; }
  .k .v { font-family: var(--font-display); font-size: 26px; font-weight: 800; line-height: 1.1; }
  .k.warn .v { color: var(--warn); }
  .k .nota { font-size: var(--text-xs); color: var(--muted); }
</style>
