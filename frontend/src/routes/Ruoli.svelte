<script lang="ts">
  import Azione from '../components/Azione.svelte';
  import Icona from '../components/Icona.svelte';
  import { api, messaggioErrore, type PaginaRuoli, type Permesso } from '../lib/api';
  import { dataBreve } from '../lib/date';
  import { schermo } from '../lib/schermo.svelte';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Ruoli e permessi (ADR 0010): cosa può fare ogni ruolo, ruoli nuovi, chi ha quale ruolo e dove. Le modifiche
   * valgono subito per tutti, senza rifare il login.
   */
  type R = PaginaRuoli['ruoli'][number];
  let pagina = $state<PaginaRuoli | null>(null);
  let scelto = $state('');
  let bozza = $state<{ nome: string; descrizione: string; permessi: Permesso[] } | null>(null);
  let nuovo = $state<{ nome: string; descrizione: string; copiaDa: string } | null>(null);
  let assegna = $state<{ accountId: string; impiantoId: string }>({ accountId: '', impiantoId: '' });
  let errore = $state('');
  let messaggio = $state('');
  let conferma = $state('');

  async function carica(seleziona?: string) {
    try {
      pagina = await api.ruoli();
      const c = seleziona ?? (scelto || pagina.ruoli[0]?.codice || '');
      apri(pagina.ruoli.find((r) => r.codice === c) ?? pagina.ruoli[0]);
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  carica();

  function apri(r: R | undefined) {
    if (!r) return;
    scelto = r.codice;
    bozza = { nome: r.nome, descrizione: r.descrizione ?? '', permessi: [...r.permessi] };
    conferma = '';
  }

  const ruolo = $derived(pagina?.ruoli.find((r) => r.codice === scelto) ?? null);
  const gruppi = $derived(
    pagina ? [...new Set(pagina.permessi.map((p) => p.gruppo))].map((g) => ({ nome: g, permessi: pagina!.permessi.filter((p) => p.gruppo === g) })) : [],
  );
  const cambiato = $derived(
    !!ruolo && !!bozza && (bozza.nome !== ruolo.nome || bozza.descrizione !== (ruolo.descrizione ?? '')
      || bozza.permessi.length !== ruolo.permessi.length || bozza.permessi.some((p) => !ruolo.permessi.includes(p))),
  );
  const nomeImpianto = (id: string | null) => (id ? pagina?.impianti.find((i) => i.id === id)?.nome ?? 'Impianto' : 'Tutta la società');

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

  const salva = () => esegui(() => api.modificaRuolo(scelto, { nome: bozza!.nome, descrizione: bozza!.descrizione || null, permessi: bozza!.permessi }),
    `«${bozza!.nome}» salvato: vale da subito.`);

  async function crea() {
    if (!nuovo) return;
    let codice = '';
    const ok = await esegui(async () => {
      codice = (await api.creaRuolo({ nome: nuovo!.nome, descrizione: nuovo!.descrizione || null, copiaDa: nuovo!.copiaDa || null })).codice;
    }, `Ruolo «${nuovo.nome}» creato.`);
    if (ok) {
      nuovo = null;
      apri(pagina?.ruoli.find((r) => r.codice === codice));
    }
  }

  const disponibili = $derived(
    pagina && ruolo ? pagina.account.filter((a) => a.attivo && !ruolo.persone.some((p) => p.accountId === a.accountId && (p.impiantoId ?? '') === assegna.impiantoId)) : [],
  );
</script>

{#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

{#if pagina}
  <div class="ruoli" class:largo={schermo.larga}>
    <aside class="elenco">
      {#each pagina.ruoli as r (r.codice)}
        <button class="voce-r card" class:on={r.codice === scelto} onclick={() => apri(r)}>
          <span class="nome-r">{r.nome}</span>
          <span class="muted small">{r.persone.length === 1 ? '1 persona' : `${r.persone.length} persone`}{r.diSistema ? ' · di sistema' : ''}</span>
        </button>
      {/each}
      {#if nuovo}
        <form class="card modulo" onsubmit={(e) => { e.preventDefault(); crea(); }}>
          <h3>Nuovo ruolo</h3>
          <input class="inp" bind:value={nuovo.nome} placeholder="Nome (es. Istruttore senior)" aria-label="Nome del ruolo" />
          <input class="inp" bind:value={nuovo.descrizione} placeholder="Descrizione (facoltativa)" aria-label="Descrizione" />
          <label class="field"><span class="lbl">Parti dai permessi di</span>
            <select class="inp" bind:value={nuovo.copiaDa}>
              <option value="">Nessun permesso</option>
              {#each pagina.ruoli as r (r.codice)}<option value={r.codice}>{r.nome}</option>{/each}
            </select>
          </label>
          <div class="row">
            <button type="button" class="btn btn-secondary grow" onclick={() => (nuovo = null)}>Annulla</button>
            <button class="btn btn-primary grow" disabled={!nuovo.nome.trim()}>Crea</button>
          </div>
        </form>
      {:else}
        <button class="btn btn-secondary" onclick={() => (nuovo = { nome: '', descrizione: '', copiaDa: 'STAFF' })}><Icona nome="piu" />Nuovo ruolo…</button>
      {/if}
    </aside>

    {#if ruolo && bozza}
      <section class="dettaglio">
        <div class="card blocco">
          <div class="due">
            <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={bozza.nome} /></label>
            <label class="field"><span class="lbl">Descrizione</span><input class="inp" bind:value={bozza.descrizione} /></label>
          </div>
          {#if !ruolo.modificabile}
            <p class="hint nomargin">Admin ha sempre tutti i permessi, compresi ruoli e regole del tornello.</p>
          {:else}
            <p class="muted small nomargin">Chiunque abbia un ruolo vede già i propri turni, il piano vasca, il tornello e lista e presenze dei propri turni.</p>
          {/if}
          <div class="gruppi">
            {#each gruppi as g (g.nome)}
              <div class="gruppo-p">
                <div class="lbl">{g.nome}</div>
                {#each g.permessi as p (p.codice)}
                  {@const on = !ruolo.modificabile ? p.potere : bozza.permessi.includes(p.codice)}
                  <div class="permesso">
                    <div class="grow">
                      <div class="nome-p">{p.nome}</div>
                      <div class="muted small">{p.descrizione}</div>
                    </div>
                    <button class="switch" role="switch" aria-checked={on} aria-label={p.nome} disabled={!ruolo.modificabile}
                            onclick={() => alterna(p.codice)}><span></span></button>
                  </div>
                {/each}
              </div>
            {/each}
          </div>
          <div class="row azioni">
            {#if cambiato}
              <button class="btn btn-primary" onclick={salva}>Salva</button>
              <button class="btn btn-secondary" onclick={() => apri(ruolo)}>Annulla</button>
            {/if}
            <span class="grow"></span>
            {#if !ruolo.diSistema}
              <button class="btn btn-danger btn-sm" onclick={() => conferma === 'del' ? esegui(() => api.eliminaRuolo(ruolo.codice), `Ruolo «${ruolo.nome}» eliminato.`, '') : (conferma = 'del')}>
                <Icona nome="cestino" />{conferma === 'del' ? 'Tocca di nuovo per eliminare' : 'Elimina il ruolo'}
              </button>
            {/if}
          </div>
        </div>

        <div class="card blocco">
          <h3>Chi ha questo ruolo</h3>
          <ul class="righe">
            {#each ruolo.persone as p (p.assegnazioneId)}
              <li>
                <span class="grow"><strong>{p.cognome} {p.nome}</strong> <span class="muted small">· {p.email}</span></span>
                <span class="tag t-altro">{nomeImpianto(p.impiantoId)}</span>
                <span class="muted small">dal {dataBreve(p.dal)}</span>
                <Azione icona="persona-meno" tono="danger" etichetta={`Togli il ruolo a ${p.nome} ${p.cognome}`} conferma="Togli?"
                  onclick={() => esegui(() => api.togliRuolo(p.assegnazioneId), `Ruolo tolto a ${p.nome} ${p.cognome}.`)} />
              </li>
            {:else}
              <li class="muted small">Nessuno.</li>
            {/each}
          </ul>
          <form class="assegna" onsubmit={(e) => { e.preventDefault(); esegui(() => api.assegnaRuolo(ruolo.codice, assegna.accountId, assegna.impiantoId || null), 'Ruolo assegnato: vale da subito.').then((ok) => ok && (assegna.accountId = '')); }}>
            <select class="inp" bind:value={assegna.accountId} aria-label="Persona">
              <option value="" disabled>Dai il ruolo a…</option>
              {#each disponibili as a (a.accountId)}<option value={a.accountId}>{a.cognome} {a.nome} · {a.email}</option>{/each}
            </select>
            <select class="inp" bind:value={assegna.impiantoId} aria-label="Dove">
              <option value="">Tutta la società</option>
              {#each pagina.impianti as i (i.id)}<option value={i.id}>{i.nome}</option>{/each}
            </select>
            <button class="btn btn-primary" disabled={!assegna.accountId}>Assegna</button>
          </form>
          <p class="muted small nomargin">Un ruolo su un impianto vale solo lì. Tolto un ruolo, resta nello storico.</p>
        </div>
      </section>
    {/if}
  </div>
{:else if !errore}
  <p class="muted">Caricamento…</p>
{/if}

<style>
  .ruoli { display: flex; flex-direction: column; gap: var(--space-16); }
  .ruoli.largo { display: grid; grid-template-columns: 280px minmax(0, 1fr); gap: var(--space-24, 24px); align-items: start; }
  .elenco { display: flex; flex-direction: column; gap: var(--space-8); }
  .voce-r { display: flex; flex-direction: column; align-items: flex-start; gap: 2px; padding: var(--space-10) var(--space-14); text-align: left; cursor: pointer;
    font: inherit; color: var(--ink); }
  .voce-r.on { border-color: var(--cloro); background: var(--cloro-soft); color: var(--cloro-ink); }
  .nome-r { font-weight: 700; }
  .modulo { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14); }
  .modulo h3 { margin: 0; font-size: var(--text-lg); }
  .dettaglio { display: flex; flex-direction: column; gap: var(--space-14); min-width: 0; }
  .blocco { display: flex; flex-direction: column; gap: var(--space-12); padding: var(--space-16); }
  h3 { margin: 0; font-size: var(--text-xl); }
  .gruppi { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: var(--space-16); }
  .gruppo-p { display: flex; flex-direction: column; gap: var(--space-4); }
  .permesso { display: flex; align-items: center; gap: var(--space-12); padding: var(--space-8) 0; border-top: 1px solid var(--line-soft); }
  .nome-p { font-weight: 600; }
  .azioni { align-items: center; }
  .righe { list-style: none; margin: 0; padding: 0; }
  .righe li { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; padding: var(--space-8) 0; border-top: 1px solid var(--line-soft); }
  .righe li:first-child { border-top: none; }
  .assegna { display: grid; grid-template-columns: minmax(0, 1fr); gap: var(--space-8); }
  @media (min-width: 900px) { .assegna { grid-template-columns: minmax(0, 1fr) 220px auto; } }
  select.inp { appearance: auto; }
</style>
