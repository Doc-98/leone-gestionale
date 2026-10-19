<script lang="ts">
  import { onMount } from 'svelte';
  import { api, messaggioErrore, type PersonaScheda } from '../lib/api';
  import { dataBreve, iso } from '../lib/date';
  import { schermo } from '../lib/schermo.svelte';
  import SchedaCliente from './SchedaCliente.svelte';

  /** Ricerca dei clienti e scheda (#/clienti/<id>): segreteria, coordinatori, admin. */
  const leggiId = () => location.hash.replace('#/', '').split('/')[1] ?? '';
  let scelto = $state(leggiId());
  let cerca = $state('');
  let trovate = $state<PersonaScheda[]>([]);
  let cercato = $state(false);
  let nuovo = $state<{ nome: string; cognome: string; dataNascita: string } | null>(null);
  let errore = $state('');
  const oggi = iso(new Date());

  onMount(() => {
    const f = () => (scelto = leggiId());
    window.addEventListener('hashchange', f);
    return () => window.removeEventListener('hashchange', f);
  });

  let ultima = 0;
  async function cercaPersone() {
    const q = cerca.trim();
    const n = ++ultima;
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
</script>

<div class="clienti" class:largo={schermo.larga}>
  {#if schermo.larga || !scelto}
    <aside class="ricerca">
      <label class="field">
        <span class="lbl">Cerca un cliente</span>
        <input class="inp" type="search" bind:value={cerca} oninput={cercaPersone} placeholder="Nome e cognome" autocomplete="off" />
      </label>
      {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
      {#if trovate.length}
        <ul class="card risultati">
          {#each trovate as p (p.id)}
            <li><a href={`#/clienti/${p.id}`} class:on={p.id === scelto}>
              <span class="nome">{p.cognome} {p.nome}</span>
              <span class="muted small">{p.dataNascita ? dataBreve(p.dataNascita) : ''}</span>
            </a></li>
          {/each}
        </ul>
      {:else if cercato}
        <p class="muted small nomargin">Nessuno con questo nome.</p>
      {:else}
        <p class="muted small nomargin">Scrivi almeno due lettere del nome o del cognome.</p>
      {/if}

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
          <p class="muted small nomargin">Contatti e tutori arriveranno con la scheda completa.</p>
        </form>
      {:else}
        <button class="btn btn-secondary" onclick={() => (nuovo = { nome: '', cognome: '', dataNascita: '' })}>Nuovo cliente…</button>
      {/if}
    </aside>
  {/if}

  {#if scelto}
    <div class="scheda">
      {#if !schermo.larga}<a class="link small" href="#/clienti">‹ Clienti</a>{/if}
      {#key scelto}<SchedaCliente personaId={scelto} />{/key}
    </div>
  {:else if schermo.larga}
    <div class="vuota card">Cerca un cliente per aprire la scheda: braccialetti, certificato, tesseramento, abbonamenti, ban.</div>
  {/if}
</div>

<style>
  .clienti { display: flex; flex-direction: column; gap: var(--space-16); }
  .clienti.largo { display: grid; grid-template-columns: 300px minmax(0, 1fr); align-items: start; gap: var(--space-24, 24px); }
  .ricerca { display: flex; flex-direction: column; gap: var(--space-12); }
  .largo .ricerca { position: sticky; top: 28px; }
  .risultati { list-style: none; margin: 0; padding: 0; overflow: hidden; }
  .risultati a { display: flex; justify-content: space-between; gap: var(--space-8); padding: var(--space-10) var(--space-12); color: var(--ink);
    text-decoration: none; border-top: 1px solid var(--line-soft); }
  .risultati li:first-child a { border-top: none; }
  .risultati a:hover { background: var(--carta); }
  .risultati a.on { background: var(--cloro-soft); color: var(--cloro-ink); }
  .nome { font-weight: 600; }
  .nuovo { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14); }
  .nuovo h3 { margin: 0; font-size: var(--text-lg); }
  .scheda { display: flex; flex-direction: column; gap: var(--space-10); min-width: 0; }
  .vuota { padding: var(--space-24, 24px); color: var(--muted); text-align: center; border-style: dashed; background: transparent; }
</style>
