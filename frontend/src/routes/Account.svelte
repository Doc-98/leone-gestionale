<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import Azione from '../components/Azione.svelte';
  import { api, messaggioErrore, type Account, type PaginaRuoli, type PasswordTemporanea } from '../lib/api';
  import { dataBreve, dataDi, oraDi } from '../lib/date';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Gli account dello staff: si creano con una password temporanea (mostrata una volta sola, da cambiare al primo
   * accesso), si disattivano (l'accesso si chiude subito), si reimposta la password. I ruoli si danno in «Ruoli e permessi».
   */
  let elenco = $state<Account[]>([]);
  let ruoli = $state<PaginaRuoli | null>(null);
  let errore = $state('');
  let messaggio = $state('');
  let temporanea = $state<(PasswordTemporanea & { chi: string }) | null>(null);
  let nuovo = $state<{ nome: string; cognome: string; email: string; ruolo: string; impiantoId: string } | null>(null);
  let cerca = $state('');
  let mostraDisattivati = $state(false);

  const daRuoli = $derived(sessione.puo('RUOLI'));

  async function carica() {
    try {
      elenco = await api.account();
      if (daRuoli && !ruoli) ruoli = await api.ruoli();
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  carica();

  const visibili = $derived(elenco.filter((a) => (mostraDisattivati || a.attivo)
    && (!cerca.trim() || `${a.nome} ${a.cognome} ${a.email}`.toLowerCase().includes(cerca.trim().toLowerCase()))));
  const nomeRuolo = (c: string) => ruoli?.ruoli.find((r) => r.codice === c)?.nome ?? c.charAt(0) + c.slice(1).toLowerCase().replaceAll('_', ' ');
  const nomeImpianto = (id: string | null) => (id ? ruoli?.impianti.find((i) => i.id === id)?.nome ?? 'un impianto' : null);

  async function esegui(azione: () => Promise<unknown>, fatto: string) {
    errore = '';
    messaggio = '';
    try {
      await azione();
      await carica();
      messaggio = fatto;
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  async function crea() {
    if (!nuovo) return;
    errore = '';
    try {
      const r = await api.creaAccount({ nome: nuovo.nome, cognome: nuovo.cognome, email: nuovo.email, ruolo: nuovo.ruolo || null, impiantoId: nuovo.impiantoId || null });
      temporanea = { ...r, chi: `${nuovo.nome} ${nuovo.cognome}` };
      nuovo = null;
      await carica();
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  async function reimposta(a: Account) {
    try {
      const r = await api.reimpostaPassword(a.id);
      temporanea = { ...r, chi: `${a.nome} ${a.cognome}` };
      await carica();
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  async function copia() {
    try {
      await navigator.clipboard.writeText(temporanea!.password);
      messaggio = 'Password copiata.';
    } catch {
      /* gli appunti non sono disponibili: si legge a schermo */
    }
  }
</script>

{#if temporanea}
  <div class="temporanea card" role="status">
    <div class="lbl">Password temporanea di {temporanea.chi}</div>
    <div class="pw m">{temporanea.password}</div>
    <p class="small nomargin">Comunicala di persona: accede con <strong>{temporanea.email}</strong> e al primo accesso sceglie la sua. Non verrà più mostrata.</p>
    <div class="row"><button class="btn btn-secondary" onclick={copia}><Icona nome="copia" />Copia</button><button class="btn btn-primary" onclick={() => (temporanea = null)}>Fatto</button></div>
  </div>
{/if}
{#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

<div class="barra">
  <input class="inp cerca" type="search" bind:value={cerca} placeholder="Cerca per nome o email" aria-label="Cerca" />
  <label class="small spunta"><input type="checkbox" bind:checked={mostraDisattivati} /> Mostra i disattivati</label>
  <span class="grow"></span>
  {#if !nuovo}<button class="btn btn-primary" onclick={() => (nuovo = { nome: '', cognome: '', email: '', ruolo: 'STAFF', impiantoId: '' })}><Icona nome="piu" />Nuovo account…</button>{/if}
</div>

{#if nuovo}
  <form class="card modulo" onsubmit={(e) => { e.preventDefault(); crea(); }}>
    <h3>Nuovo account</h3>
    <div class="due">
      <input class="inp" bind:value={nuovo.nome} placeholder="Nome" aria-label="Nome" />
      <input class="inp" bind:value={nuovo.cognome} placeholder="Cognome" aria-label="Cognome" />
    </div>
    <input class="inp" type="email" bind:value={nuovo.email} placeholder="Email (serve per accedere)" aria-label="Email" />
    {#if daRuoli && ruoli}
      <div class="due">
        <label class="field"><span class="lbl">Ruolo</span>
          <select class="inp" bind:value={nuovo.ruolo}>
            <option value="">Nessuno (lo darai dopo)</option>
            {#each ruoli.ruoli as r (r.codice)}<option value={r.codice}>{r.nome}</option>{/each}
          </select>
        </label>
        <label class="field"><span class="lbl">Dove</span>
          <select class="inp" bind:value={nuovo.impiantoId}>
            <option value="">Tutta la società</option>
            {#each ruoli.impianti as i (i.id)}<option value={i.id}>{i.nome}</option>{/each}
          </select>
        </label>
      </div>
    {/if}
    <p class="muted small nomargin">Si crea con una password temporanea, da comunicare di persona: al primo accesso va cambiata.</p>
    <div class="row">
      <button type="button" class="btn btn-secondary grow" onclick={() => (nuovo = null)}>Annulla</button>
      <button class="btn btn-primary grow" disabled={!nuovo.nome.trim() || !nuovo.cognome.trim() || !nuovo.email.includes('@')}>Crea l'account</button>
    </div>
  </form>
{/if}

<ul class="card elenco">
  {#each visibili as a (a.id)}
    <li class:spento={!a.attivo}>
      <span class="chi">
        <span class="nome">{a.cognome} {a.nome}{a.id === sessione.utente?.accountId ? ' (tu)' : ''}</span>
        <span class="muted small">{a.email}</span>
      </span>
      <span class="ruoli">
        {#each a.ruoli as r (r.ruolo + r.impiantoId)}<span class="tag t-altro">{nomeRuolo(r.ruolo)}{nomeImpianto(r.impiantoId) ? ` · ${nomeImpianto(r.impiantoId)}` : ''}</span>{:else}<span class="muted small">Nessun ruolo</span>{/each}
      </span>
      <span class="stato small">
        {#if !a.attivo}<span class="tag t-warn">Disattivato</span>
        {:else if a.deveCambiarePassword}<span class="tag t-soft">Primo accesso da fare</span>
        {:else}<span class="muted">{a.ultimoAccesso ? `Ultimo accesso ${dataBreve(dataDi(a.ultimoAccesso))} ${oraDi(a.ultimoAccesso)}` : 'Mai entrato'}</span>{/if}
      </span>
      <span class="azioni">
        {#if a.attivo}
          <Azione icona="chiave" etichetta={`Reimposta la password di ${a.nome} ${a.cognome}`} conferma="Nuova password?" onclick={() => reimposta(a)} />
          {#if a.id !== sessione.utente?.accountId}
            <Azione icona="spegni" tono="danger" etichetta={`Disattiva l'account di ${a.nome} ${a.cognome}`} conferma="Disattiva?"
              onclick={() => esegui(() => api.disattivaAccount(a.id), `Account di ${a.nome} ${a.cognome} disattivato: è uscito da tutti i dispositivi.`)} />
          {/if}
        {:else}
          <Azione icona="spegni" tono="cloro" etichetta={`Riattiva l'account di ${a.nome} ${a.cognome}`} onclick={() => esegui(() => api.riattivaAccount(a.id), `Account di ${a.nome} ${a.cognome} riattivato.`)} />
        {/if}
      </span>
    </li>
  {:else}
    <li class="muted small">Nessun account.</li>
  {/each}
</ul>
{#if daRuoli}<p class="muted small nomargin">I ruoli si cambiano da <a href="#/ruoli">Ruoli e permessi</a>.</p>{/if}

<style>
  .temporanea { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-14) var(--space-16); border: 2px solid var(--cloro); background: var(--cloro-soft); color: var(--cloro-ink); }
  .pw { font-size: 28px; letter-spacing: 0.06em; }
  .barra { display: flex; align-items: center; gap: var(--space-12); flex-wrap: wrap; }
  .cerca { width: 260px; }
  .spunta { display: flex; align-items: center; gap: var(--space-6); }
  .modulo { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16); }
  .modulo h3 { margin: 0; font-size: var(--text-xl); }
  select.inp { appearance: auto; }
  .elenco { list-style: none; margin: 0; padding: 0; overflow: hidden; }
  .elenco li { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(0, 1.2fr) minmax(0, 1fr) auto; gap: var(--space-10); align-items: center;
    padding: var(--space-10) var(--space-14); border-top: 1px solid var(--line-soft); }
  .elenco li:first-child { border-top: none; }
  .spento { opacity: 0.6; }
  .chi { display: flex; flex-direction: column; min-width: 0; }
  .nome { font-weight: 600; }
  .ruoli { display: flex; gap: var(--space-4); flex-wrap: wrap; }
  .azioni { display: flex; gap: var(--space-4); justify-content: flex-end; flex-wrap: wrap; }
  @media (max-width: 900px) { .elenco li { grid-template-columns: 1fr; } .azioni { justify-content: flex-start; } }
</style>
