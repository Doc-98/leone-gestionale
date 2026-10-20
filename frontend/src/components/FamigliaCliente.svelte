<script lang="ts">
  import { api, type Familiare, type PersonaScheda, type Relazione, type SchedaCliente } from '../lib/api';
  import { dataBreve } from '../lib/date';

  /**
   * Tutori e nucleo famiglia nella scheda: chi è responsabile della persona (riceve i promemoria, può venire a
   * prenderla all'uscita), di chi è tutore lei, e i fratelli (chi ha un tutore in comune). Il tutore è una persona come
   * le altre: se esiste già (stesso codice fiscale o scelto dalla ricerca) si collega, altrimenti si crea.
   */
  let { cliente, esegui }: { cliente: SchedaCliente; esegui: (azione: () => Promise<unknown>, fatto: string) => Promise<boolean> } = $props();

  const RELAZIONI: { id: Relazione; nome: string }[] = [
    { id: 'MADRE', nome: 'Madre' }, { id: 'PADRE', nome: 'Padre' }, { id: 'TUTORE', nome: 'Tutore' }, { id: 'NONNO', nome: 'Nonno/a' }, { id: 'ALTRO', nome: 'Altro' },
  ];
  const nomeRelazione = (r: Relazione) => RELAZIONI.find((x) => x.id === r)?.nome ?? r;

  const id = $derived(cliente.persona.id);
  const f = $derived(cliente.famiglia);
  const minore = $derived(cliente.persona.eta != null && cliente.persona.eta < 18);

  let conferma = $state('');
  let nuovo = $state<{
    modo: 'esistente' | 'nuovo'; cerca: string; trovate: PersonaScheda[]; scelto: PersonaScheda | null;
    nome: string; cognome: string; codiceFiscale: string; email: string; telefono: string;
    relazione: Relazione; comunicazioni: boolean; delegatoRitiro: boolean;
  } | null>(null);

  function apri() {
    nuovo = { modo: 'nuovo', cerca: '', trovate: [], scelto: null, nome: '', cognome: cliente.persona.cognome, codiceFiscale: '', email: '', telefono: '',
      relazione: 'MADRE', comunicazioni: true, delegatoRitiro: true };
  }

  let ultima = 0;
  async function cerca() {
    if (!nuovo) return;
    const q = nuovo.cerca.trim();
    const n = ++ultima;
    if (q.length < 2) return void (nuovo.trovate = []);
    try {
      const r = await api.cercaPersone(q);
      if (n === ultima && nuovo) nuovo.trovate = r.filter((p) => p.id !== id && !f.tutori.some((t) => t.personaId === p.id));
    } catch {
      /* la ricerca riprova al prossimo tasto */
    }
  }

  async function salva() {
    const t = nuovo!;
    const chi = t.modo === 'esistente' ? `${t.scelto!.nome} ${t.scelto!.cognome}` : `${t.nome} ${t.cognome}`;
    const ok = await esegui(() => api.aggiungiTutore(id, t.modo === 'esistente'
      ? { tutoreId: t.scelto!.id, relazione: t.relazione, comunicazioni: t.comunicazioni, delegatoRitiro: t.delegatoRitiro }
      : { nome: t.nome.trim(), cognome: t.cognome.trim(), codiceFiscale: t.codiceFiscale.trim() || null, email: t.email.trim() || null, telefono: t.telefono.trim() || null,
          relazione: t.relazione, comunicazioni: t.comunicazioni, delegatoRitiro: t.delegatoRitiro }), `${chi} aggiunto tra i tutori.`);
    if (ok) nuovo = null;
  }

  const pronto = $derived(nuovo && (nuovo.modo === 'esistente' ? !!nuovo.scelto : !!nuovo.nome.trim() && !!nuovo.cognome.trim()));

  const cambia = (t: Familiare, modifica: Partial<Pick<Familiare, 'relazione' | 'comunicazioni' | 'delegatoRitiro'>>) => {
    const l = { relazione: t.relazione, comunicazioni: t.comunicazioni, delegatoRitiro: t.delegatoRitiro, ...modifica };
    return esegui(() => api.modificaLegame(t.legameId, l), 'Tutore aggiornato.');
  };

  function chiudi(t: Familiare) {
    if (conferma !== t.legameId) return void (conferma = t.legameId);
    conferma = '';
    esegui(() => api.chiudiLegame(t.legameId), `${t.nome} ${t.cognome} non è più tra i tutori.`);
  }

  const eta = (d: string | null) => {
    if (!d) return '';
    const n = new Date(), b = new Date(d);
    const a = n.getFullYear() - b.getFullYear() - (n.getMonth() < b.getMonth() || (n.getMonth() === b.getMonth() && n.getDate() < b.getDate()) ? 1 : 0);
    return `${a} anni`;
  };
</script>

<section class="card blocco">
  <h3>Famiglia</h3>

  <div class="gruppo">
    <span class="lbl">Tutori</span>
    <ul class="righe">
      {#each f.tutori as t (t.legameId)}
        <li class="tutore">
          <div class="riga">
            <a class="nome" href={`#/clienti/${t.personaId}`}>{t.nome} {t.cognome}</a>
            <select class="inp rel" value={t.relazione} aria-label={`Relazione con ${t.nome}`} onchange={(e) => cambia(t, { relazione: e.currentTarget.value as Relazione })}>
              {#each RELAZIONI as r (r.id)}<option value={r.id}>{r.nome}</option>{/each}
            </select>
            <span class="grow"></span>
            <button class="link small rosso" onclick={() => chiudi(t)}>{conferma === t.legameId ? 'Conferma: togli' : 'Togli'}</button>
          </div>
          <div class="muted small">{[t.email, t.telefono].filter(Boolean).join(' · ') || 'Nessun contatto'}</div>
          <div class="interruttori">
            <label class="int"><button class="switch" role="switch" aria-checked={t.comunicazioni} aria-label={`${t.nome} riceve le comunicazioni`}
              onclick={() => cambia(t, { comunicazioni: !t.comunicazioni })}><span></span></button><span class="small">Riceve promemoria e comunicazioni</span></label>
            <label class="int"><button class="switch" role="switch" aria-checked={t.delegatoRitiro} aria-label={`${t.nome} può venire a prenderlo`}
              onclick={() => cambia(t, { delegatoRitiro: !t.delegatoRitiro })}><span></span></button><span class="small">Può venire a prenderlo all'uscita</span></label>
          </div>
        </li>
      {:else}
        <li class="muted small">{minore ? 'Nessun tutore: per un minore aggiungi almeno un genitore.' : 'Nessun tutore.'}</li>
      {/each}
    </ul>
  </div>

  {#if nuovo}
    <form class="modulo" onsubmit={(e) => { e.preventDefault(); salva(); }}>
      <div class="seg" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Tutore">
        <button type="button" aria-pressed={nuovo.modo === 'nuovo'} onclick={() => (nuovo!.modo = 'nuovo')}>Persona nuova</button>
        <button type="button" aria-pressed={nuovo.modo === 'esistente'} onclick={() => (nuovo!.modo = 'esistente')}>Già in anagrafica</button>
      </div>
      {#if nuovo.modo === 'esistente'}
        {#if nuovo.scelto}
          <div class="scelto"><strong>{nuovo.scelto.nome} {nuovo.scelto.cognome}</strong>{nuovo.scelto.dataNascita ? ` · ${dataBreve(nuovo.scelto.dataNascita)}` : ''}
            <button type="button" class="link small" onclick={() => (nuovo!.scelto = null)}>Cambia</button></div>
        {:else}
          <input class="inp" type="search" bind:value={nuovo.cerca} oninput={cerca} placeholder="Nome, cognome o codice fiscale" aria-label="Cerca il tutore" autocomplete="off" />
          {#each nuovo.trovate as p (p.id)}
            <button type="button" class="trovata" onclick={() => (nuovo!.scelto = p)}>{p.cognome} {p.nome}<span class="muted small">{p.dataNascita ? dataBreve(p.dataNascita) : ''}</span></button>
          {/each}
          <p class="muted small nomargin">Per collegare un fratello allo stesso genitore, cerca il genitore.</p>
        {/if}
      {:else}
        <div class="due">
          <input class="inp" bind:value={nuovo.nome} placeholder="Nome" aria-label="Nome del tutore" />
          <input class="inp" bind:value={nuovo.cognome} placeholder="Cognome" aria-label="Cognome del tutore" />
        </div>
        <input class="inp m" bind:value={nuovo.codiceFiscale} placeholder="Codice fiscale (facoltativo)" aria-label="Codice fiscale del tutore" maxlength="16" />
        <div class="due">
          <input class="inp" type="email" bind:value={nuovo.email} placeholder="Email" aria-label="Email del tutore" />
          <input class="inp" type="tel" bind:value={nuovo.telefono} placeholder="Telefono" aria-label="Telefono del tutore" />
        </div>
        <p class="muted small nomargin">Se il codice fiscale è già in anagrafica, si collega quella persona.</p>
      {/if}
      <div class="chips" role="group" aria-label="Relazione">
        {#each RELAZIONI as r (r.id)}<button type="button" class="chip" aria-pressed={nuovo.relazione === r.id} onclick={() => (nuovo!.relazione = r.id)}>{r.nome}</button>{/each}
      </div>
      <label class="int"><button type="button" class="switch" role="switch" aria-checked={nuovo.comunicazioni} aria-label="Riceve le comunicazioni"
        onclick={() => (nuovo!.comunicazioni = !nuovo!.comunicazioni)}><span></span></button><span class="small">Riceve promemoria e comunicazioni</span></label>
      <label class="int"><button type="button" class="switch" role="switch" aria-checked={nuovo.delegatoRitiro} aria-label="Può venire a prenderlo"
        onclick={() => (nuovo!.delegatoRitiro = !nuovo!.delegatoRitiro)}><span></span></button><span class="small">Può venire a prenderlo all'uscita</span></label>
      <div class="row">
        <button type="button" class="btn btn-secondary grow" onclick={() => (nuovo = null)}>Annulla</button>
        <button class="btn btn-primary grow" disabled={!pronto}>Aggiungi</button>
      </div>
    </form>
  {:else}
    <button class="btn btn-secondary" onclick={apri}>Aggiungi un tutore…</button>
  {/if}

  {#if f.fratelli.length}
    <div class="gruppo sotto">
      <span class="lbl">Fratelli e sorelle</span>
      {#each f.fratelli as x (x.personaId)}
        <div class="small"><a href={`#/clienti/${x.personaId}`}>{x.nome} {x.cognome}</a><span class="muted">{x.dataNascita ? ` · ${eta(x.dataNascita)}` : ''}</span></div>
      {/each}
    </div>
  {/if}

  {#if f.assistiti.length}
    <div class="gruppo sotto">
      <span class="lbl">È tutore di</span>
      {#each f.assistiti as x (x.legameId)}
        <div class="small"><a href={`#/clienti/${x.personaId}`}>{x.nome} {x.cognome}</a><span class="muted"> · {nomeRelazione(x.relazione).toLowerCase()}{x.dataNascita ? ` · ${eta(x.dataNascita)}` : ''}</span></div>
      {/each}
    </div>
  {/if}

  <p class="muted small nomargin sotto">
    {#if f.destinatari.length}I promemoria vanno a: <strong>{f.destinatari.join(', ')}</strong>
    {:else}<span class="manca">Nessuno riceve i promemoria:</span> aggiungi un'email alla persona o a un tutore che riceve le comunicazioni.{/if}
  </p>
</section>

<style>
  .blocco { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16); }
  h3 { margin: 0; font-size: var(--text-xl); }
  .gruppo { display: flex; flex-direction: column; gap: var(--space-4); }
  .righe { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
  .righe > li { padding: var(--space-8) 0; border-top: 1px solid var(--line-soft); }
  .righe > li:first-child { border-top: none; }
  .tutore { display: flex; flex-direction: column; gap: var(--space-4); }
  .riga { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .nome { font-weight: 600; color: var(--ink); }
  .rel { width: auto; height: 32px; min-height: 32px; padding: 0 var(--space-8); appearance: auto; font-size: var(--text-sm); }
  .interruttori { display: flex; flex-wrap: wrap; gap: var(--space-6) var(--space-16); margin-top: var(--space-4); }
  .int { display: flex; align-items: center; gap: var(--space-8); }
  .modulo { display: flex; flex-direction: column; gap: var(--space-10); }
  .trovata { display: flex; justify-content: space-between; gap: var(--space-10); padding: var(--space-8) var(--space-10); border: 1px solid var(--line-soft);
    border-radius: var(--radius-md); background: var(--surface); color: var(--ink); font: inherit; cursor: pointer; text-align: left; }
  .trovata:hover { border-color: var(--cloro); }
  .scelto { display: flex; align-items: center; gap: var(--space-10); }
  .sotto { padding-top: var(--space-10); border-top: 1px solid var(--line); }
  .link { min-height: 32px; }
  .rosso { color: var(--danger); }
  .manca { color: var(--warn); font-weight: 600; }
</style>
