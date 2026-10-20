<script lang="ts">
  import { onMount } from 'svelte';
  import { api } from './lib/api';
  import { sessione } from './lib/sessione.svelte';
  import { schermo } from './lib/schermo.svelte';
  import { cambiaServer, infoProgramma, type InfoProgramma } from './lib/dispositivi';
  import Login from './routes/Login.svelte';
  import Turni from './routes/Turni.svelte';
  import Richieste from './routes/Richieste.svelte';
  import Ore from './routes/Ore.svelte';
  import Pianifica from './routes/Pianifica.svelte';
  import Approva from './routes/Approva.svelte';
  import Modelli from './routes/Modelli.svelte';
  import Postazioni from './routes/Postazioni.svelte';
  import Tariffe from './routes/Tariffe.svelte';
  import PianoVasca from './routes/PianoVasca.svelte';
  import Corsi from './routes/Corsi.svelte';
  import Tornello from './routes/Tornello.svelte';
  import Clienti from './routes/Clienti.svelte';
  import Regole from './routes/Regole.svelte';
  import Ruoli from './routes/Ruoli.svelte';
  import Segnalazioni from './components/Segnalazioni.svelte';
  import Foglio from './components/Foglio.svelte';
  import CambioPassword from './routes/CambioPassword.svelte';
  import ListaAttesa from './routes/ListaAttesa.svelte';
  import Scadenze from './routes/Scadenze.svelte';
  import Promemoria from './routes/Promemoria.svelte';
  import Account from './routes/Account.svelte';
  import type { Permesso } from './lib/api';

  type Scheda = 'turni' | 'richieste' | 'ore' | 'piano' | 'corsi' | 'tornello' | 'clienti' | 'pianifica' | 'approva' | 'modelli' | 'postazioni' | 'tariffe' | 'regole' | 'ruoli' | 'scadenze' | 'promemoria' | 'account' | 'attesa';
  interface Sezione {
    id: Scheda;
    nome: string;
    /** Nome nella barra in basso del telefono; senza, la sezione non ci sta (si raggiunge da Pianificazione). */
    breve?: string;
    titolo: string;
    gruppo: 'Il mio lavoro' | 'Piscina' | 'Accessi' | 'Gestione' | 'Impostazioni';
    /** Visibile a chi ha almeno uno di questi permessi; senza, a tutti. */
    permessi?: Permesso[];
    larga?: boolean;
    icona: string;
  }

  // Icone a tratto, 24×24
  const I = {
    turni: 'M4 6h16M4 12h16M4 18h10',
    richieste: 'M7 7h10l-3-3M17 17H7l3 3',
    ore: 'M12 7v5l3 2M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Z',
    pianifica: 'M4 5h16v15H4zM4 10h16M9 5v15M14 5v15',
    approva: 'm5 12 4 4 10-10',
    modelli: 'M5 4h14v16H5zM8 8h8M8 12h8M8 16h5',
    postazioni: 'M12 3 3 8l9 5 9-5-9-5ZM3 13l9 5 9-5',
    piano: 'M3 8c2-1.5 4-1.5 6 0s4 1.5 6 0 4-1.5 6 0M3 13c2-1.5 4-1.5 6 0s4 1.5 6 0 4-1.5 6 0M3 18c2-1.5 4-1.5 6 0s4 1.5 6 0 4-1.5 6 0',
    corsi: 'M4 19V6a2 2 0 0 1 2-2h12v15H6a2 2 0 0 0-2 2M8 8h6M8 12h6',
    tornello: 'M5 4h4v16H5zM15 4h4v16h-4zM9 12h6M12 9l3 3-3 3',
    clienti: 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM2 21a7 7 0 0 1 14 0M17 11a3 3 0 1 0 0-6M22 21a6 6 0 0 0-4-5.6',
    scadenze: 'M12 7v5l3 2M5 3 2 6M19 3l3 3M12 4a8 8 0 1 0 0 16 8 8 0 0 0 0-16Z',
    promemoria: 'M4 6h16v12H4zM4 7l8 6 8-6',
    attesa: 'M7 3h10M7 21h10M8 3c0 5 8 5 8 9s-8 4-8 9M16 3c0 5-8 5-8 9s8 4 8 9',
    account: 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM2 21a7 7 0 0 1 11-5.7M17 14v6M14 17h6',
    ruoli: 'M12 3 4 6v6c0 4.5 3.4 8 8 9 4.6-1 8-4.5 8-9V6l-8-3ZM9 12l2 2 4-4',
    regole: 'M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0M16 4v4M10 10v4M18 16v4',
    tariffe: 'M12 3v18M16.5 7.5c0-1.7-2-3-4.5-3s-4.5 1.3-4.5 3 2 2.6 4.5 3 4.5 1.3 4.5 3-2 3-4.5 3-4.5-1.3-4.5-3',
  };

  const SEZIONI: Sezione[] = [
    { id: 'turni', nome: 'I miei turni', breve: 'Turni', titolo: 'I miei turni', gruppo: 'Il mio lavoro', icona: I.turni },
    { id: 'richieste', nome: 'Richieste', breve: 'Richieste', titolo: 'Richieste', gruppo: 'Il mio lavoro', icona: I.richieste },
    { id: 'ore', nome: 'Ore e compensi', breve: 'Ore', titolo: 'Ore e compensi', gruppo: 'Il mio lavoro', larga: true, icona: I.ore },
    { id: 'piano', nome: 'Piano vasca', breve: 'Piano', titolo: 'Piano vasca', gruppo: 'Piscina', larga: true, icona: I.piano },
    { id: 'attesa', nome: 'Liste d\'attesa', breve: 'Attesa', titolo: 'Liste d\'attesa', gruppo: 'Piscina', permessi: ['ISCRIZIONI'], larga: true, icona: I.attesa },
    { id: 'corsi', nome: 'Corsi', titolo: 'Corsi', gruppo: 'Piscina', permessi: ['PIANO_MODIFICA'], larga: true, icona: I.corsi },
    { id: 'tornello', nome: 'Tornello', breve: 'Tornello', titolo: 'Tornello', gruppo: 'Accessi', larga: true, icona: I.tornello },
    { id: 'clienti', nome: 'Clienti', breve: 'Clienti', titolo: 'Clienti', gruppo: 'Accessi', permessi: ['CLIENTI', 'BRACCIALETTI'], larga: true, icona: I.clienti },
    { id: 'scadenze', nome: 'Scadenze', breve: 'Scadenze', titolo: 'Scadenze', gruppo: 'Accessi', permessi: ['CLIENTI'], larga: true, icona: I.scadenze },
    { id: 'pianifica', nome: 'Pianificazione', breve: 'Pianifica', titolo: 'Pianificazione', gruppo: 'Gestione', permessi: ['PIANIFICAZIONE'], larga: true, icona: I.pianifica },
    { id: 'approva', nome: 'Da approvare', breve: 'Approva', titolo: 'Da approvare', gruppo: 'Gestione', permessi: ['APPROVAZIONI'], icona: I.approva },
    { id: 'modelli', nome: 'Settimana tipo', titolo: 'Settimana tipo', gruppo: 'Gestione', permessi: ['PIANIFICAZIONE'], larga: true, icona: I.modelli },
    { id: 'postazioni', nome: 'Mansioni e postazioni', titolo: 'Mansioni e postazioni', gruppo: 'Impostazioni', permessi: ['ANAGRAFICHE_STAFF'], larga: true, icona: I.postazioni },
    { id: 'tariffe', nome: 'Tariffe', titolo: 'Tariffe orarie', gruppo: 'Impostazioni', permessi: ['ANAGRAFICHE_STAFF'], larga: true, icona: I.tariffe },
    { id: 'regole', nome: 'Regole del tornello', titolo: 'Regole del tornello', gruppo: 'Impostazioni', permessi: ['REGOLE_TORNELLO', 'BRACCIALETTI'], larga: true, icona: I.regole },
    { id: 'promemoria', nome: 'Promemoria email', titolo: 'Promemoria email', gruppo: 'Impostazioni', permessi: ['CLIENTI'], larga: true, icona: I.promemoria },
    { id: 'account', nome: 'Account', titolo: 'Account dello staff', gruppo: 'Impostazioni', permessi: ['ACCOUNT'], larga: true, icona: I.account },
    { id: 'ruoli', nome: 'Ruoli e permessi', titolo: 'Ruoli e permessi', gruppo: 'Impostazioni', permessi: ['RUOLI'], larga: true, icona: I.ruoli },
  ];
  const ID = SEZIONI.map((s) => s.id);

  const leggiScheda = (): Scheda => {
    const h = location.hash.replace('#/', '').split('/')[0] as Scheda;
    return ID.includes(h) ? h : 'turni';
  };
  let scheda = $state<Scheda>(leggiScheda());
  let daApprovare = $state(0);
  let programma = $state<InfoProgramma | null>(null);

  onMount(() => {
    sessione.carica();
    infoProgramma().then((i) => (programma = i));
    const f = () => (scheda = leggiScheda());
    window.addEventListener('hashchange', f);
    return () => window.removeEventListener('hashchange', f);
  });

  const visibili = $derived(SEZIONI.filter((s) => !s.permessi || sessione.puo(...s.permessi)));
  const corrente = $derived(visibili.find((s) => s.id === scheda) ?? SEZIONI[0]);
  const gruppi = $derived(
    (['Il mio lavoro', 'Piscina', 'Accessi', 'Gestione', 'Impostazioni'] as const)
      .map((g) => ({ nome: g, voci: visibili.filter((s) => s.gruppo === g) }))
      .filter((g) => g.voci.length),
  );
  // Telefono: al massimo cinque voci in basso, le prime per il ruolo; il resto in «Altro»
  // in ordine di importanza; lo staff senza gestione ha richieste e ore in basso
  const ORDINE_BASSO: Scheda[] = ['turni', 'piano', 'tornello', 'clienti', 'scadenze', 'approva', 'pianifica', 'richieste', 'ore'];
  let cambioPassword = $state(false);
  const prime = $derived(
    sessione.puo('CLIENTI', 'APPROVAZIONI', 'PIANIFICAZIONE')
      ? ORDINE_BASSO.filter((id) => visibili.some((s) => s.id === id)).slice(0, 5)
      : (['turni', 'richieste', 'ore', 'piano', 'tornello'] as Scheda[]),
  );
  const altre = $derived(visibili.filter((s) => !prime.includes(s.id)));
  const inBasso = $derived(
    prime.map((id) => visibili.find((s) => s.id === id)).filter((s): s is Sezione => !!s).slice(0, altre.length ? 4 : 5),
  );
  const nelMenu = $derived(visibili.filter((s) => !inBasso.includes(s)));
  let menuAltro = $state(false);
  $effect(() => {
    void scheda;
    menuAltro = false;
  });
  const NOMI_RUOLI: Record<string, string> = { ADMIN: 'Admin', COORDINATORE: 'Coordinatore', SEGRETERIA: 'Segreteria', STAFF: 'Staff' };
  const ruolo = $derived(
    [...new Set((sessione.utente?.ruoli ?? []).map((r) => NOMI_RUOLI[r.ruolo] ?? r.ruolo.charAt(0) + r.ruolo.slice(1).toLowerCase().replaceAll('_', ' ')))].join(' · ') || 'Nessun ruolo',
  );

  $effect(() => {
    if (sessione.puo('APPROVAZIONI')) api.richiesteDaApprovare().then((r) => (daApprovare = r.length)).catch(() => {});
  });

  // Sul PC: Alt + numero per passare da una sezione all'altra
  function tasto(e: KeyboardEvent) {
    if (!e.altKey || e.ctrlKey || e.metaKey) return;
    const n = Number(e.key);
    if (n >= 1 && n <= visibili.length) {
      e.preventDefault();
      location.hash = `#/${visibili[n - 1].id}`;
    }
  }
</script>

<svelte:window onkeydown={tasto} />

{#snippet icona(d: string)}
  <svg class="ic" viewBox="0 0 24 24" aria-hidden="true"><path d={d} /></svg>
{/snippet}

{#snippet badge(s: Sezione)}
  {#if s.id === 'approva' && daApprovare}<span class="badge" aria-label="{daApprovare} da approvare">{daApprovare}</span>{/if}
{/snippet}

{#if !sessione.pronto}
  <p class="muted centro">Caricamento…</p>
{:else if !sessione.utente}
  <main class="accesso"><Login /></main>
{:else if sessione.utente.deveCambiarePassword}
  <main class="accesso">
    <h1 class="page-title">Nuova password</h1>
    <CambioPassword obbligatorio />
  </main>
{:else}
  <div class="guscio" class:desktop={schermo.larga}>
    {#if schermo.larga}
      <aside class="lato">
        <div class="marchio">
          <span class="d logo">Leone</span>
          <span class="lbl">Gestionale · Piscina</span>
        </div>
        <nav aria-label="Sezioni">
          {#each gruppi as g (g.nome)}
            <div class="gruppo-nav">
              <div class="lbl">{g.nome}</div>
              {#each g.voci as s (s.id)}
                <a href={`#/${s.id}`} class="voce-nav" class:on={scheda === s.id} aria-current={scheda === s.id ? 'page' : undefined}
                   title="{s.nome} (Alt+{visibili.indexOf(s) + 1})">
                  {@render icona(s.icona)}<span class="grow">{s.nome}</span>{@render badge(s)}
                </a>
              {/each}
            </div>
          {/each}
        </nav>
        <div class="io">
          <div class="io-nome">{sessione.utente.nome} {sessione.utente.cognome}</div>
          <div class="muted small">{ruolo}</div>
          <button class="link small" onclick={() => (cambioPassword = true)}>Cambia password</button>
          <button class="link small" onclick={() => sessione.esci()}>Esci</button>
          {#if programma}
            <div class="programma muted">Programma {programma.versione} · {location.host}</div>
            <button class="link small" onclick={cambiaServer}>Cambia server</button>
          {/if}
        </div>
      </aside>
    {/if}

    <main class:larga={schermo.larga && corrente.larga} class:con-nav={!schermo.larga}>
      <header class="testa">
        <div>
          {#if !schermo.larga}<div class="lbl">Leone XIII Sport · Piscina</div>{/if}
          <h1 class="page-title">{corrente.titolo}</h1>
        </div>
        {#if !schermo.larga}
          <div class="chi">
            <span class="muted small">{sessione.utente.nome}</span>
            <button class="link small" onclick={() => (cambioPassword = true)}>Password</button>
            <button class="link small" onclick={() => sessione.esci()}>Esci</button>
          </div>
        {/if}
      </header>
      {#key corrente.id}
        {#if corrente.id === 'turni'}<Turni />
        {:else if corrente.id === 'richieste'}<Richieste />
        {:else if corrente.id === 'ore'}<Ore />
        {:else if corrente.id === 'pianifica'}<Pianifica />
        {:else if corrente.id === 'approva'}<Approva cambiate={(n) => (daApprovare = n)} />
        {:else if corrente.id === 'modelli'}<Modelli />
        {:else if corrente.id === 'postazioni'}<Postazioni />
        {:else if corrente.id === 'tariffe'}<Tariffe />
        {:else if corrente.id === 'piano'}<PianoVasca />
        {:else if corrente.id === 'corsi'}<Corsi />
        {:else if corrente.id === 'tornello'}<Tornello />
        {:else if corrente.id === 'clienti'}<Clienti />
        {:else if corrente.id === 'regole'}<Regole />
        {:else if corrente.id === 'ruoli'}<Ruoli />
        {:else if corrente.id === 'scadenze'}<Scadenze />
        {:else if corrente.id === 'attesa'}<ListaAttesa />
        {:else if corrente.id === 'promemoria'}<Promemoria />
        {:else if corrente.id === 'account'}<Account />{/if}
      {/key}
    </main>

    {#if sessione.puo('CLIENTI', 'BRACCIALETTI')}<Segnalazioni />{/if}
    {#if cambioPassword}
      <Foglio titolo="Cambia password" chiudi={() => (cambioPassword = false)}>
        <CambioPassword fatto={() => (cambioPassword = false)} />
      </Foglio>
    {/if}

    {#if !schermo.larga}
      {#if menuAltro}
        <div class="altro card" id="menu-altro">
          {#each nelMenu as s (s.id)}
            <a href={`#/${s.id}`} class:on={scheda === s.id}>{@render icona(s.icona)}<span class="grow">{s.nome}</span>{@render badge(s)}</a>
          {/each}
        </div>
      {/if}
      <nav class="basso" aria-label="Sezioni" style="--n: {inBasso.length + (nelMenu.length ? 1 : 0)}">
        {#each inBasso as s (s.id)}
          <a href={`#/${s.id}`} class:on={scheda === s.id} aria-current={scheda === s.id ? 'page' : undefined}>
            {s.breve ?? s.nome}{@render badge(s)}
          </a>
        {/each}
        {#if nelMenu.length}
          <button class:on={menuAltro || nelMenu.some((s) => s.id === scheda)} aria-expanded={menuAltro} aria-controls="menu-altro"
                  onclick={() => (menuAltro = !menuAltro)}>Altro{#if nelMenu.some((s) => s.id === 'approva') && daApprovare}<span class="badge">{daApprovare}</span>{/if}</button>
        {/if}
      </nav>
    {/if}
  </div>
{/if}

<style>
  main {
    width: 100%;
    max-width: 640px;
    margin: 0 auto;
    padding: calc(env(safe-area-inset-top, 0px) + var(--space-20)) var(--space-16) calc(env(safe-area-inset-bottom, 0px) + var(--space-20));
    display: flex; flex-direction: column; gap: var(--space-16);
  }
  main.con-nav { padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 96px); }
  .centro { text-align: center; margin-top: 40vh; }
  .testa { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-12); }
  .chi { display: flex; flex-direction: column; align-items: flex-end; }
  .chi .link { min-height: 32px; }

  /* ── Desktop ─────────────────────────────────────────────── */
  .guscio.desktop { display: grid; grid-template-columns: 248px minmax(0, 1fr); min-height: 100vh; }
  .desktop main { max-width: 760px; padding: 28px 40px 48px; margin: 0; }
  .desktop main.larga { max-width: 1440px; }
  .lato {
    position: sticky; top: 0; height: 100vh; display: flex; flex-direction: column; gap: var(--space-20);
    padding: var(--space-20) var(--space-12); background: var(--surface); border-right: 1px solid var(--line); overflow-y: auto;
  }
  .marchio { display: flex; flex-direction: column; gap: var(--space-2); padding: 0 var(--space-10); }
  .logo { font-size: 26px; font-weight: 800; color: var(--cloro); line-height: 1; }
  .lato nav { display: flex; flex-direction: column; gap: var(--space-16); flex: 1; }
  .gruppo-nav { display: flex; flex-direction: column; gap: var(--space-2); }
  .gruppo-nav > .lbl { padding: 0 var(--space-10) var(--space-4); font-size: var(--text-2xs); }
  .voce-nav {
    display: flex; align-items: center; gap: var(--space-10); min-height: 38px; padding: 0 var(--space-10);
    border-radius: var(--radius-sm); color: var(--ink); text-decoration: none; font-size: var(--text-md); font-weight: 500;
  }
  .voce-nav .ic { width: 18px; height: 18px; color: var(--muted); }
  .voce-nav:hover { background: var(--carta); }
  .voce-nav.on { background: var(--cloro-soft); color: var(--cloro-ink); font-weight: 600; }
  .voce-nav.on .ic { color: var(--cloro); }
  .io { border-top: 1px solid var(--line-soft); padding: var(--space-12) var(--space-10) 0; display: flex; flex-direction: column; align-items: flex-start; }
  .io-nome { font-weight: 600; font-size: var(--text-md); }
  .io .link { min-height: 32px; }
  .programma { font-size: var(--text-2xs); margin-top: var(--space-6); }

  /* ── Telefono ─────────────────────────────────────────────── */
  nav.basso {
    position: fixed; left: 0; right: 0; bottom: 0; z-index: 20;
    display: grid; grid-template-columns: repeat(var(--n), minmax(0, 1fr));
    padding-bottom: env(safe-area-inset-bottom, 0px);
    background: var(--surface); border-top: 1px solid var(--line);
  }
  nav.basso a { position: relative; display: flex; align-items: center; justify-content: center; gap: 4px; min-height: 60px; padding: 0 4px; font-size: var(--text-xs); font-weight: 600; color: var(--muted); text-decoration: none; text-align: center; }
  nav.basso a.on, nav.basso button.on { color: var(--cloro); box-shadow: inset 0 3px 0 var(--cloro); }
  nav.basso button { position: relative; display: flex; align-items: center; justify-content: center; gap: 4px; min-height: 60px; padding: 0 4px;
    border: none; background: none; font: inherit; font-size: var(--text-xs); font-weight: 600; color: var(--muted); cursor: pointer; }
  .altro { position: fixed; right: var(--space-12); bottom: calc(env(safe-area-inset-bottom, 0px) + 68px); z-index: 21; width: min(300px, calc(100vw - 24px));
    display: flex; flex-direction: column; padding: var(--space-6); box-shadow: 0 8px 28px rgba(16, 33, 43, 0.18); }
  .altro a { display: flex; align-items: center; gap: var(--space-10); min-height: 46px; padding: 0 var(--space-10); border-radius: var(--radius-sm);
    color: var(--ink); text-decoration: none; font-weight: 500; }
  .altro a .ic { width: 18px; height: 18px; color: var(--muted); }
  .altro a.on { background: var(--cloro-soft); color: var(--cloro-ink); }
  .badge { min-width: 18px; height: 18px; border-radius: 9px; background: var(--cloro); color: var(--on-cloro); font-size: 11px; display: inline-flex; align-items: center; justify-content: center; padding: 0 5px; }
</style>
