<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import { api, messaggioErrore, type PaginaRuoli, type Permesso } from '../lib/api';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * I ruoli in lista (ADR 0010, 0024): a sinistra i ruoli (predefiniti e creati dalla società), a destra i permessi del
   * ruolo scelto e chi lo ha. Regge anche 20 o 40 ruoli: si scorre solo in verticale. Per confrontare 2 o 3 ruoli c'è
   * la vista affiancata, con le differenze evidenziate. Le modifiche valgono subito per tutti, senza rifare il login.
   */
  let { nuovo = 0 }: { nuovo?: number } = $props();

  type R = PaginaRuoli['ruoli'][number];
  let pagina = $state<PaginaRuoli | null>(null);
  let scelto = $state('');
  let bozza = $state<{ nome: string; descrizione: string; permessi: Permesso[] } | null>(null);
  let rinomina = $state(false);
  let q = $state('');
  let confronto = $state(false);
  let scelti = $state<string[]>([]);
  let soloDiff = $state(false);
  let elimina = $state<{ sostituto: string } | null>(null);
  let assegna = $state<{ accountId: string; impiantoId: string }>({ accountId: '', impiantoId: '' });
  let errore = $state('');
  let messaggio = $state('');

  async function carica(seleziona?: string) {
    try {
      pagina = await api.ruoli();
      const c = seleziona ?? scelto;
      apri(pagina.ruoli.find((r) => r.codice === c) ?? pagina.ruoli.find((r) => r.codice === 'COORDINATORE') ?? pagina.ruoli[0]);
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  carica();

  function apri(r: R | undefined) {
    if (!r) return;
    scelto = r.codice;
    bozza = { nome: r.nome, descrizione: r.descrizione ?? '', permessi: [...r.permessi] };
    rinomina = false;
    elimina = null;
  }
  const ruolo = $derived(pagina?.ruoli.find((r) => r.codice === scelto) ?? null);
  const gruppi = $derived(pagina ? [...new Set(pagina.permessi.map((p) => p.gruppo))].map((g) => ({ nome: g, permessi: pagina!.permessi.filter((p) => p.gruppo === g) })) : []);
  const cambiato = $derived(!!ruolo && !!bozza && (bozza.nome !== ruolo.nome || bozza.descrizione !== (ruolo.descrizione ?? '')
    || bozza.permessi.length !== ruolo.permessi.length || bozza.permessi.some((p) => !ruolo.permessi.includes(p))));
  const persone = (n: number) => (n ? `${n} ${n === 1 ? 'persona' : 'persone'}` : 'nessuno, ancora');
  const dove = (id: string | null) => (id ? `solo nell'impianto ${pagina?.impianti.find((i) => i.id === id)?.nome ?? ''}` : 'in tutti gli impianti');
  const haPermesso = (r: R, p: PaginaRuoli['permessi'][number]) => (!r.modificabile ? p.potere : r.permessi.includes(p.codice));

  async function esegui(azione: () => Promise<unknown>, fatto: string, seleziona?: string) {
    errore = '';
    messaggio = '';
    try {
      await azione();
      await carica(seleziona);
      await sessione.aggiorna();
      messaggio = fatto;
      return true;
    } catch (e) {
      errore = messaggioErrore(e);
      return false;
    }
  }
  function alterna(p: Permesso) {
    if (!bozza) return;
    bozza.permessi = bozza.permessi.includes(p) ? bozza.permessi.filter((x) => x !== p) : [...bozza.permessi, p];
  }
  const salva = () => esegui(() => api.modificaRuolo(scelto, { nome: bozza!.nome.trim(), descrizione: bozza!.descrizione.trim() || null, permessi: bozza!.permessi }),
    `«${bozza!.nome}» salvato: vale da subito.`);

  // ── Nuovo, duplica, elimina ───────────────────────────────────────────
  function nomeLibero(base: string) {
    const usati = new Set(pagina?.ruoli.map((r) => r.nome.toLowerCase()));
    if (!usati.has(base.toLowerCase())) return base;
    for (let i = 2; ; i++) if (!usati.has(`${base} ${i}`.toLowerCase())) return `${base} ${i}`;
  }
  async function crea(nome: string, copiaDa: string | null, descrizione: string | null, fatto: string) {
    let codice = '';
    const ok = await esegui(async () => { codice = (await api.creaRuolo({ nome, descrizione, copiaDa })).codice; }, fatto);
    if (ok) {
      confronto = false;
      q = '';
      apri(pagina?.ruoli.find((r) => r.codice === codice));
      if (!copiaDa) rinomina = true;
    }
  }
  let ultimoNuovo = 0;
  $effect(() => {
    if (nuovo > ultimoNuovo && pagina) {
      ultimoNuovo = nuovo;
      crea(nomeLibero('Nuovo ruolo'), null, 'Scegli i permessi qui sotto e dagli un nome.', 'Ruolo creato senza permessi: dagli un nome e accendi quelli che servono.');
    }
  });
  const duplica = () => crea(nomeLibero(`${ruolo!.nome} (copia)`), ruolo!.codice, ruolo!.descrizione, `Copia di «${ruolo!.nome}» creata.`);
  async function eliminaRuolo() {
    const r = ruolo!;
    const sost = elimina?.sostituto;
    await esegui(async () => {
      // chi aveva il ruolo riceve quello scelto al suo posto, nello stesso impianto
      if (sost) for (const p of r.persone) {
        const giaLoHa = pagina!.ruoli.find((x) => x.codice === sost)?.persone.some((y) => y.accountId === p.accountId && y.impiantoId === p.impiantoId);
        if (!giaLoHa) await api.assegnaRuolo(sost, p.accountId, p.impiantoId);
      }
      await api.eliminaRuolo(r.codice);
    }, `Ruolo «${r.nome}» eliminato.`, '');
  }

  // ── Elenco e confronto ────────────────────────────────────────────────
  const filtrati = $derived((pagina?.ruoli ?? []).filter((r) => !q.trim() || r.nome.toLowerCase().includes(q.trim().toLowerCase())));
  const sezioni = $derived([
    { nome: 'Predefiniti', ruoli: filtrati.filter((r) => r.diSistema) },
    { nome: 'Creati dalla società', ruoli: filtrati.filter((r) => !r.diSistema) },
  ].filter((s) => s.ruoli.length));
  function tocca(r: R) {
    if (!confronto) return apri(r);
    scelti = scelti.includes(r.codice) ? scelti.filter((c) => c !== r.codice) : scelti.length >= 3 ? [...scelti.slice(1), r.codice] : [...scelti, r.codice];
  }
  function apriConfronto() {
    const altro = pagina!.ruoli.find((r) => r.codice !== scelto && r.modificabile)?.codice;
    scelti = [scelto, ...(altro ? [altro] : [])];
    confronto = true;
  }
  const ruoliConfronto = $derived(scelti.map((c) => pagina?.ruoli.find((r) => r.codice === c)).filter((r): r is R => !!r));
  const righeConfronto = $derived(gruppi.map((g) => ({
    nome: g.nome,
    righe: g.permessi.map((p) => {
      const v = ruoliConfronto.map((r) => haPermesso(r, p));
      return { p, v, diversa: v.some((x) => x !== v[0]) };
    }).filter((x) => !soloDiff || x.diversa),
  })).filter((g) => g.righe.length));

  const disponibili = $derived(pagina && ruolo ? pagina.account.filter((a) => a.attivo && !ruolo.persone.some((p) => p.accountId === a.accountId && (p.impiantoId ?? '') === assegna.impiantoId)) : []);
</script>

{#if messaggio && !cambiato}<p class="msg ok" role="status">{messaggio}</p>{/if}
{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

{#if pagina && ruolo && bozza}
  <div class="due-c" id="imp-ruoli">
    <div class="card lista">
      <div class="testa-l">
        <label class="cerca"><Icona nome="cerca" /><span class="sr">Cerca un ruolo</span><input class="inp" bind:value={q} placeholder="Cerca un ruolo" /></label>
        {#if confronto}<span class="muted small">Scegli da 2 a 3 ruoli da mettere a confronto.</span>{/if}
      </div>
      {#each sezioni as s (s.nome)}
        <span class="sez-l">{s.nome}</span>
        {#each s.ruoli as r (r.codice)}
          <button type="button" class="voce-r" aria-pressed={confronto ? scelti.includes(r.codice) : r.codice === scelto} onclick={() => tocca(r)}>
            {#if confronto}<span class="scelta" class:on={scelti.includes(r.codice)}><Icona nome="check" /></span>{/if}
            <span class="t"><b>{r.nome}</b><span>{persone(r.persone.length)}</span></span>
          </button>
        {/each}
      {:else}
        <p class="muted small vuoto">Nessun ruolo con «{q.trim()}».</p>
      {/each}
      <div class="piede-l">
        {#if confronto}<button type="button" class="mini-btn grow" onclick={() => (confronto = false)}>Chiudi il confronto</button>
        {:else}<button type="button" class="mini-btn grow" onclick={apriConfronto}>Confronta ruoli</button>{/if}
      </div>
    </div>

    {#if !confronto}
      <div class="destra">
        <section class="card sez" aria-label="Permessi del ruolo">
          <div class="sez-t">
            {#if rinomina && ruolo.modificabile}
              <span class="nomina"><input class="inp" bind:value={bozza.nome} aria-label="Nome del ruolo" /><input class="inp" bind:value={bozza.descrizione} placeholder="Descrizione (facoltativa)" aria-label="Descrizione" /></span>
              <button type="button" class="mini-btn" onclick={() => (rinomina = false)}>Fatto</button>
            {:else}
              <span class="titolo"><h2>{bozza.nome}</h2>{#if bozza.descrizione}<span class="spiega">{bozza.descrizione}</span>{/if}</span>
              {#if ruolo.modificabile}<button type="button" class="mini-btn" onclick={() => (rinomina = true)}>Rinomina</button>{/if}
              <button type="button" class="mini-btn" onclick={duplica}>Duplica</button>
              {#if !ruolo.diSistema}<button type="button" class="mini-btn rosso" onclick={() => (elimina = { sostituto: '' })}>Elimina</button>{/if}
            {/if}
          </div>
          {#if elimina}
            <div class="conferma" role="alertdialog" aria-label="Eliminare il ruolo">
              <b>Eliminare il ruolo «{ruolo.nome}»?</b>
              <span>{ruolo.persone.length ? `${persone(ruolo.persone.length)} ${ruolo.persone.length === 1 ? 'lo ha' : 'lo hanno'}: lo perderanno insieme ai suoi permessi. Puoi dare loro un altro ruolo al suo posto.` : 'Nessuno ha questo ruolo: si elimina e basta.'}</span>
              {#if ruolo.persone.length}
                <label class="field"><span class="lbl">Ruolo da dare al posto di questo</span>
                  <select class="inp" bind:value={elimina.sostituto}><option value="">Nessuno: perdono questi permessi</option>
                    {#each pagina.ruoli.filter((r) => r.codice !== ruolo.codice && r.codice !== 'ADMIN') as r (r.codice)}<option value={r.codice}>{r.nome}</option>{/each}</select></label>
              {/if}
              <span class="row fine"><button type="button" class="btn btn-secondary" onclick={() => (elimina = null)}>Annulla</button><button type="button" class="btn btn-danger pieno" onclick={eliminaRuolo}>Elimina il ruolo</button></span>
            </div>
          {/if}
          {#if ruolo.diSistema}<div class="nota">{ruolo.modificabile ? 'È un ruolo predefinito: si può cambiare ma non eliminare.' : 'Admin ha sempre tutti i permessi: non si possono togliere.'}</div>{/if}
          {#each gruppi as g (g.nome)}
            <div class="gruppo-p">{g.nome}<span class="n">{g.permessi.filter((p) => (!ruolo.modificabile ? p.potere : bozza!.permessi.includes(p.codice))).length} su {g.permessi.length}</span></div>
            {#each g.permessi as p (p.codice)}
              {@const on = !ruolo.modificabile ? p.potere : bozza.permessi.includes(p.codice)}
              <div class="perm"><span class="t"><b>{p.nome}</b><span>{p.descrizione}</span></span>
                <button type="button" class="switch" role="switch" aria-checked={on} aria-label={p.nome} disabled={!ruolo.modificabile} onclick={() => alterna(p.codice)}><span></span></button></div>
            {/each}
          {/each}
        </section>
        <span class="muted small">Chiunque abbia un ruolo vede già i propri turni, il piano vasca, il tornello e lista e presenze dei propri turni.</span>

        <section class="card sez" aria-label="Chi ha questo ruolo">
          <div class="sez-t"><h2 class="grow">Chi lo ha</h2><span class="spiega">{persone(ruolo.persone.length)}</span></div>
          <div class="persone">
            {#each ruolo.persone as p (p.assegnazioneId)}
              <div class="persona"><span class="av">{(p.nome[0] ?? '') + (p.cognome[0] ?? '')}</span><span class="t"><b>{p.nome} {p.cognome}</b><span>{dove(p.impiantoId)}</span></span>
                <button type="button" class="x-btn" aria-label="Togli il ruolo a {p.nome} {p.cognome}" title="Togli il ruolo"
                  onclick={() => confirm(`Togliere il ruolo «${ruolo.nome}» a ${p.nome} ${p.cognome}?`) && esegui(() => api.togliRuolo(p.assegnazioneId), `Ruolo tolto a ${p.nome} ${p.cognome}.`)}><Icona nome="chiudi" /></button></div>
            {/each}
            <form class="assegna" onsubmit={(e) => { e.preventDefault(); esegui(() => api.assegnaRuolo(ruolo.codice, assegna.accountId, assegna.impiantoId || null), 'Ruolo dato: vale da subito.').then((ok) => ok && (assegna.accountId = '')); }}>
              <select class="inp" bind:value={assegna.accountId} aria-label="Persona"><option value="" disabled>Dai il ruolo a qualcuno…</option>
                {#each disponibili as a (a.accountId)}<option value={a.accountId}>{a.cognome} {a.nome}</option>{/each}</select>
              {#if pagina.impianti.length > 1 || assegna.impiantoId}
                <select class="inp" bind:value={assegna.impiantoId} aria-label="Dove"><option value="">In tutti gli impianti</option>{#each pagina.impianti as i (i.id)}<option value={i.id}>Solo {i.nome}</option>{/each}</select>
              {/if}
              <button class="mini-btn pieno" disabled={!assegna.accountId}>Dai il ruolo</button>
            </form>
          </div>
        </section>
      </div>
    {:else}
      <div class="destra">
        <section class="card sez confronto">
          <div class="sez-t"><h2 class="grow">Confronto</h2>
            <label class="riga-switch corto"><span class="spiega">Solo le differenze</span><button type="button" class="switch" role="switch" aria-checked={soloDiff} aria-label="Solo le differenze" onclick={() => (soloDiff = !soloDiff)}><span></span></button></label></div>
          <div class="tabella">
            <div class="conf" style="grid-template-columns: minmax(220px, 1fr) repeat({Math.max(1, ruoliConfronto.length)}, 130px)">
              <div class="h primo">Permesso</div>
              {#each ruoliConfronto as r (r.codice)}<div class="h">{r.nome}<span>{persone(r.persone.length)}</span></div>{/each}
              {#each righeConfronto as g (g.nome)}
                <div class="g">{g.nome}</div>
                {#each g.righe as x (x.p.codice)}
                  <div class="c perm-c" class:diversa={x.diversa}><b>{x.p.nome}</b><span>{x.p.descrizione}</span></div>
                  {#each x.v as v, i (i)}<div class="c" class:diversa={x.diversa}>{#if v}<span class="si" aria-label="sì"><Icona nome="check" /></span>{:else}<span class="no" aria-label="no"></span>{/if}</div>{/each}
                {/each}
              {:else}
                <div class="g">Nessuna differenza tra i ruoli scelti.</div>
              {/each}
            </div>
          </div>
        </section>
        <span class="muted small">In giallo i permessi in cui i ruoli scelti sono diversi.</span>
      </div>
    {/if}
  </div>

  {#if cambiato}
    <div class="salva-bar" role="status">
      <span>Ruolo «{ruolo.nome}» cambiato: vale da subito per tutti dopo il salvataggio</span>
      <button type="button" class="vuoto" onclick={() => apri(ruolo)}>Annulla</button>
      <button type="button" class="chiaro" disabled={!bozza.nome.trim()} onclick={salva}>Salva</button>
    </div>
  {/if}
{:else if !errore}
  <p class="muted">Caricamento…</p>
{/if}

<style>
  .due-c { display: grid; grid-template-columns: 300px minmax(0, 1fr); gap: var(--space-18, 18px); align-items: start; }
  .lista { padding: 0; overflow: hidden; display: flex; flex-direction: column; }
  .testa-l { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-12); border-bottom: 1px solid var(--line); }
  .cerca { position: relative; display: block; }
  .cerca :global(.ic) { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); width: 18px; height: 18px; color: var(--muted); }
  .cerca .inp { width: 100%; box-sizing: border-box; padding-left: 38px; height: 40px; }
  .sez-l { padding: var(--space-8) var(--space-14) var(--space-4); font-size: 11px; font-weight: 700; letter-spacing: 0.07em; text-transform: uppercase; color: var(--muted); }
  .voce-r { display: flex; align-items: center; gap: var(--space-10); width: 100%; padding: 9px var(--space-14); border: none; border-left: 3px solid transparent; background: none;
    font: inherit; color: var(--ink); text-align: left; cursor: pointer; }
  .voce-r:hover { background: var(--carta); }
  .voce-r[aria-pressed='true'] { background: var(--cloro-soft); border-left-color: var(--cloro); }
  .voce-r .t { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
  .voce-r .t b { font-size: var(--text-md); font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .voce-r .t span { font-size: var(--text-xs); color: var(--muted); }
  .scelta { width: 20px; height: 20px; border-radius: 6px; border: 1.5px solid var(--line-strong); background: var(--surface); display: grid; place-items: center; flex-shrink: 0; color: #fff; }
  .scelta :global(.ic) { width: 12px; height: 12px; visibility: hidden; }
  .scelta.on { background: var(--cloro); border-color: var(--cloro); }
  .scelta.on :global(.ic) { visibility: visible; }
  .vuoto { margin: 0; padding: var(--space-10) var(--space-14); }
  .piede-l { padding: var(--space-10) var(--space-12); border-top: 1px solid var(--line); display: flex; gap: var(--space-8); }
  .piede-l .mini-btn { justify-content: center; }
  .destra { display: flex; flex-direction: column; gap: var(--space-14); min-width: 0; }
  .titolo { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
  .nomina { display: flex; gap: var(--space-8); flex: 1; flex-wrap: wrap; }
  .nomina .inp { height: 38px; flex: 1 1 200px; }
  .conferma { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16); background: var(--ferma-bg); color: var(--ferma-fg);
    border-bottom: 1px solid color-mix(in srgb, var(--ferma) 30%, transparent); font-size: var(--text-md); }
  .conferma .lbl { color: var(--ferma-fg); }
  .conferma select { max-width: 320px; appearance: auto; }
  .pieno { background: var(--danger); border-color: var(--danger); color: #fff; }
  .fine { justify-content: flex-end; }
  .nota { padding: var(--space-8) var(--space-16); font-size: var(--text-xs); color: var(--muted); border-bottom: 1px solid var(--line-soft); }
  .gruppo-p { display: flex; gap: var(--space-8); padding: var(--space-8) var(--space-16); background: var(--carta); font-size: 11px; font-weight: 700; letter-spacing: 0.07em;
    text-transform: uppercase; color: var(--muted); border-top: 1px solid var(--line-soft); }
  .gruppo-p .n { margin-left: auto; font-family: var(--font-mono); letter-spacing: 0; }
  .perm { display: flex; align-items: center; gap: var(--space-14); padding: 11px var(--space-16); border-top: 1px solid var(--line-soft); }
  .perm .t { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
  .perm .t b { font-size: var(--text-md); font-weight: 600; }
  .perm .t span { font-size: var(--text-xs); color: var(--muted); }
  .switch:disabled { opacity: 0.55; cursor: default; }
  .persone { padding: var(--space-4) var(--space-16) var(--space-12); }
  .persona { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-8) 0; border-top: 1px solid var(--line-soft); font-size: var(--text-md); }
  .persona:first-child { border-top: none; }
  .persona .t { flex: 1; min-width: 0; display: flex; flex-direction: column; }
  .persona .t span { font-size: var(--text-xs); color: var(--muted); }
  .av { width: 34px; height: 34px; border-radius: 50%; display: grid; place-items: center; flex-shrink: 0; background: var(--cloro-soft); color: var(--cloro-ink); font-size: var(--text-xs); font-weight: 700; }
  .assegna { display: flex; gap: var(--space-8); flex-wrap: wrap; align-items: center; margin-top: var(--space-8); }
  .assegna select { flex: 1 1 200px; height: 38px; appearance: auto; }
  .corto { min-height: 0; gap: var(--space-10); }
  .tabella { overflow-x: auto; }
  .conf { display: grid; font-size: var(--text-md); min-width: 480px; }
  .conf .h { padding: var(--space-12) var(--space-10); border-bottom: 1px solid var(--line); font-weight: 700; display: flex; flex-direction: column; align-items: center; gap: 2px; text-align: center; }
  .conf .h span { font-size: var(--text-xs); font-weight: 500; color: var(--muted); }
  .conf .h.primo { align-items: flex-start; padding-left: var(--space-16); font-size: 11px; letter-spacing: 0.07em; text-transform: uppercase; color: var(--muted); }
  .conf .c { padding: 9px var(--space-10); border-top: 1px solid var(--line-soft); display: flex; align-items: center; justify-content: center; }
  .conf .perm-c { justify-content: flex-start; flex-direction: column; align-items: flex-start; padding-left: var(--space-16); }
  .conf .perm-c span { font-size: var(--text-xs); color: var(--muted); }
  .conf .c.diversa { background: var(--warn-bg); }
  .conf .g { grid-column: 1 / -1; padding: var(--space-8) var(--space-16); background: var(--carta); font-size: 11px; font-weight: 700; letter-spacing: 0.07em; text-transform: uppercase;
    color: var(--muted); border-top: 1px solid var(--line-soft); }
  .si { width: 22px; height: 22px; border-radius: 50%; background: var(--cloro); display: grid; place-items: center; color: #fff; }
  .si :global(.ic) { width: 12px; height: 12px; }
  .no { width: 22px; height: 2px; border-radius: 1px; background: var(--line-strong); }
  @media (max-width: 900px) { .due-c { grid-template-columns: 1fr; } }
</style>
