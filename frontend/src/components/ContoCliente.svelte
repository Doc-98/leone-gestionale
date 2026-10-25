<script lang="ts">
  import Azione from './Azione.svelte';
  import Icona from './Icona.svelte';
  import { untrack } from 'svelte';
  import { api, messaggioErrore, type Addebito, type Conto, type MetodoPagamento, type PagamentiFamiglia } from '../lib/api';
  import { dataBreve, iso } from '../lib/date';
  import { euro } from '../lib/listino';
  import { STATI_ADDEBITO, aperto, inOrdineDiScadenza, ripartisci, somma } from '../lib/pagamenti';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Il conto di un cliente (ADR 0018, parte 5): cosa deve e entro quando, cosa ha già pagato, i familiari con qualcosa
   * da pagare (si paga tutto insieme, anche da un genitore), pagamenti misti, bonifici da incassare, annullamenti.
   * «versione» cambia quando la scheda registra qualcosa (es. un abbonamento): il conto si ricarica.
   */
  let { personaId, versione = 0, cambiato }: { personaId: string; versione?: number; cambiato?: () => void } = $props();
  const id = untrack(() => personaId);
  const oggi = iso(new Date());

  let conto = $state<Conto | null>(null);
  let metodi = $state<MetodoPagamento[]>([]);
  let errore = $state('');
  let messaggio = $state('');
  let tuttoLoStorico = $state(false);

  const puoPagare = $derived(sessione.puo('PAGAMENTI'));
  const puoAnnullare = $derived(sessione.puo('PAGAMENTI_ANNULLA'));
  /** Senza «Acconti e pagamenti misti» si paga per intero, con un solo metodo. */
  const flessibile = $derived(sessione.puo('PAGAMENTI_FLESSIBILI'));
  let famiglia = $state<PagamentiFamiglia>('A_SCELTA');
  api.societa().then((d) => (famiglia = d.pagamentiFamiglia)).catch(() => {});

  async function carica() {
    try {
      conto = await api.conto(id);
      if (daAprire) {
        daAprire = false;
        apriPagamento();
      }
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a caricare i pagamenti.');
    }
  }
  $effect(() => {
    void versione;
    untrack(carica);
  });
  api.metodiPagamento().then((m) => (metodi = m)).catch(() => {});
  const attivi = $derived(metodi.filter((m) => m.attivo));

  async function esegui(azione: () => Promise<unknown>, fatto: string) {
    errore = '';
    messaggio = '';
    try {
      await azione();
      await carica();
      messaggio = fatto;
      cambiato?.();
      return true;
    } catch (e) {
      errore = messaggioErrore(e);
      return false;
    }
  }

  // ── Cosa si paga ──────────────────────────────────────────────────────
  const propri = $derived(conto?.addebiti.filter(aperto) ?? []);
  const deiFamiliari = $derived(conto?.famiglia.flatMap((f) => f.aperti) ?? []);
  const chiusi = $derived(conto?.addebiti.filter((a) => !aperto(a)) ?? []);
  let scelti = $state<string[]>([]);

  type Riga = { metodoId: string; importo: string; riferimento: string };
  let modulo = $state<{ paganteId: string; data: string; righe: Riga[]; note: string; separati: boolean } | null>(null);

  const daPagare = $derived(inOrdineDiScadenza([...propri, ...deiFamiliari].filter((a) => scelti.includes(a.id))));
  const residuoScelto = $derived(somma(daPagare.map((a) => a.residuo)));
  const totale = $derived(modulo ? somma(modulo.righe.map((r) => r.importo)) : 0);
  const ripartizione = $derived(ripartisci(totale, daPagare));
  const quotaDi = (a: Addebito) => ripartizione.find((r) => r.id === a.id)?.quota ?? 0;

  /** Dal riassunto della scheda: apre il pagamento appena il conto è caricato. */
  let daAprire = false;
  export function avviaPagamento() {
    if (conto) apriPagamento();
    else daAprire = true;
  }

  function apriPagamento(soloScaduti = false) {
    if (!scelti.length) scelti = propri.filter((a) => !soloScaduti || a.stato === 'SCADUTO').map((a) => a.id);
    const tutore = conto!.paganti.find((p) => p.personaId !== id);
    modulo = { paganteId: tutore?.personaId ?? id, data: oggi, righe: [{ metodoId: attivi.find((m) => !m.borsellino)?.id ?? '', importo: '', riferimento: '' }], note: '', separati: false };
  }

  // l'importo della prima riga segue quanto si sceglie di pagare, finché c'è un solo metodo
  $effect(() => {
    const r = residuoScelto;
    untrack(() => {
      if (modulo && modulo.righe.length === 1) modulo.righe[0].importo = r ? String(r) : '';
    });
  });

  function scegli(a: Addebito, si: boolean) {
    scelti = si ? [...scelti, a.id] : scelti.filter((x) => x !== a.id);
  }

  const metodo = (mid: string) => metodi.find((m) => m.id === mid);
  const persone = $derived(new Set(daPagare.map((a) => a.personaId)).size);
  const modValido = $derived(!!modulo && daPagare.length > 0 && totale > 0 && totale <= residuoScelto && (flessibile || totale === residuoScelto)
    && modulo.righe.every((r) => r.metodoId && Number(r.importo) > 0));

  async function registra() {
    const m = modulo!;
    const ok = await esegui(() => api.registraPagamento({
      paganteId: m.paganteId, data: m.data, importo: totale, addebiti: daPagare.map((a) => a.id),
      metodi: m.righe.map((r) => ({ metodoId: r.metodoId, importo: Number(r.importo), riferimento: r.riferimento.trim() || null })),
      note: m.note.trim() || null, separati: m.separati,
    }), persone > 1 && (famiglia === 'PER_PERSONA' || (famiglia === 'A_SCELTA' && m.separati))
      ? `Pagamento di ${euro(totale)} registrato: uno per ogni persona.` : `Pagamento di ${euro(totale)} registrato.`);
    if (ok) {
      modulo = null;
      scelti = [];
    }
  }

  // ── Addebito a mano ───────────────────────────────────────────────────
  let addebito = $state<{ descrizione: string; importo: string; scadenza: string; note: string } | null>(null);
  async function salvaAddebito() {
    const a = addebito!;
    if (await esegui(() => api.nuovoAddebito({ personaId: id, descrizione: a.descrizione.trim(), importo: Number(a.importo), scadenza: a.scadenza || null,
      note: a.note.trim() || null }), `Addebito «${a.descrizione.trim()}» registrato.`)) addebito = null;
  }

  // ── Annullare ─────────────────────────────────────────────────────────
  let annulla = $state<{ tipo: 'pagamento' | 'addebito'; id: string; motivo: string } | null>(null);
  async function confermaAnnulla() {
    const a = annulla!;
    const ok = await esegui(() => a.tipo === 'pagamento' ? api.annullaPagamento(a.id, a.motivo.trim()) : api.annullaAddebito(a.id, a.motivo.trim()),
      a.tipo === 'pagamento' ? 'Pagamento annullato.' : 'Addebito annullato.');
    if (ok) annulla = null;
  }

  const pagamentiVisti = $derived(tuttoLoStorico ? conto?.pagamenti ?? [] : (conto?.pagamenti ?? []).slice(0, 5));
</script>

<section class="card blocco largo conto">
  <div class="testa">
    <h3>Pagamenti</h3>
    {#if conto}
      {@const t = conto.totali}
      <div class="totali">
        <span class="tot" class:zero={!t.daPagare}><span class="lbl">Da pagare</span><strong class="m">{euro(t.daPagare)}</strong></span>
        {#if t.scaduto}<span class="tot avviso"><span class="lbl">Scaduto</span><strong class="m">{euro(t.scaduto)}</strong></span>{/if}
        {#if t.credito}<span class="tot"><span class="lbl">Credito</span><strong class="m">{euro(t.credito)}</strong></span>{/if}
        {#if t.daIncassare}<span class="tot"><span class="lbl">Bonifici da incassare</span><strong class="m">{euro(t.daIncassare)}</strong></span>{/if}
      </div>
    {/if}
  </div>
  {#if messaggio}<p class="msg ok nomargin" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  {#if conto}
    <!-- Cosa c'è da pagare -->
    <ul class="righe">
      {#each propri as a (a.id)}
        {@render voce(a, false)}
      {:else}
        <li class="muted small">Niente da pagare.</li>
      {/each}
    </ul>

    {#if conto.famiglia.some((f) => f.aperti.length)}
      <div class="fam">
        <div class="lbl">In famiglia</div>
        <ul class="righe">
          {#each conto.famiglia.filter((f) => f.aperti.length) as f (f.personaId)}
            {#each f.aperti as a (a.id)}{@render voce(a, true)}{/each}
          {/each}
        </ul>
      </div>
    {/if}

    {#if puoPagare && !modulo && !addebito}
      <div class="azioni">
        <button class="btn btn-primary" disabled={!propri.length && !scelti.length} onclick={() => apriPagamento()}>
          {scelti.length ? `Registra pagamento (${scelti.length})…` : 'Registra pagamento…'}
        </button>
        <button class="btn btn-secondary" onclick={() => (addebito = { descrizione: '', importo: '', scadenza: oggi, note: '' })}>Aggiungi un addebito…</button>
      </div>
    {/if}

    <!-- Nuovo pagamento -->
    {#if modulo}
      <form class="modulo" onsubmit={(e) => { e.preventDefault(); registra(); }}>
        <div class="due">
          <label class="field"><span class="lbl">Chi paga</span>
            <select class="inp" bind:value={modulo.paganteId}>
              {#each conto.paganti as p (p.personaId)}<option value={p.personaId}>{p.nome}{p.relazione ? ` (${p.relazione.toLowerCase()})` : ''}</option>{/each}
            </select>
          </label>
          <label class="field"><span class="lbl">Data</span><input class="inp" type="date" bind:value={modulo.data} max={oggi} /></label>
        </div>
        {#if !daPagare.length}<p class="hint nomargin">Spunta qui sopra cosa si paga.</p>{/if}
        <div class="field">
          <span class="lbl">Come si paga {modulo.righe.length > 1 ? `· totale ${euro(totale)}` : ''}</span>
          {#each modulo.righe as r, i (i)}
            <div class="riga-metodo">
              <select class="inp" bind:value={r.metodoId} aria-label="Metodo">
                {#each attivi as m (m.id)}
                  {@const disp = conto.borsellini.find((b) => b.personaId === modulo!.paganteId)?.euro ?? 0}
                  <option value={m.id} disabled={m.borsellino && disp <= 0}>{m.borsellino ? `Borsellino (credito: ${euro(disp)})` : m.nome}</option>
                {/each}
              </select>
              <input class="inp m importo" type="number" min="0.01" step="0.01" bind:value={r.importo} aria-label="Importo in euro" readonly={!flessibile}
                     title={flessibile ? '' : 'Per un acconto serve il permesso «Acconti e pagamenti misti»'} />
              {#if metodo(r.metodoId)?.daIncassare}<input class="inp" bind:value={r.riferimento} placeholder="Riferimento (es. CRO)" maxlength="120" aria-label="Riferimento" />{/if}
              {#if modulo.righe.length > 1}<Azione icona="chiudi" etichetta="Togli questo metodo" onclick={() => modulo!.righe.splice(i, 1)} />{/if}
            </div>
          {/each}
          {#if flessibile}<button type="button" class="btn btn-secondary btn-sm sx" title="Es. parte in contanti, parte con la carta" onclick={() => modulo!.righe.push({ metodoId: attivi.find((m) => !modulo!.righe.some((x) => x.metodoId === m.id))?.id ?? attivi[0]?.id ?? '', importo: String(Math.max(0, somma([residuoScelto, -totale]))), riferimento: '' })}>
            <Icona nome="piu" />Aggiungi un altro metodo
          </button>{/if}
        </div>
        {#if daPagare.length && totale > 0}
          <div class="riepilogo small" aria-live="polite">
            {#each daPagare as a (a.id)}
              {@const q = quotaDi(a)}
              <div class="voce-r"><span>{a.personaId !== id ? `${a.persona} · ` : ''}{a.descrizione}</span>
                <span class="m">{q === 0 ? 'resta da pagare' : q < a.residuo ? `${euro(q)} su ${euro(a.residuo)}` : euro(q)}</span></div>
            {/each}
            {#if totale > residuoScelto}<p class="msg err nomargin">Il pagamento supera quanto c'è da pagare ({euro(residuoScelto)}).</p>{/if}
          </div>
        {/if}
        {#if persone > 1 && famiglia === 'A_SCELTA'}
          <label class="small spunta"><input type="checkbox" bind:checked={modulo.separati} /> Un pagamento per ogni persona (ricevute separate)</label>
        {:else if persone > 1}
          <p class="muted small nomargin">{famiglia === 'PER_PERSONA' ? 'Si registra un pagamento per ogni persona (impostazione della società).' : 'Si registra un pagamento unico per la famiglia (impostazione della società).'}</p>
        {/if}
        <input class="inp" bind:value={modulo.note} placeholder="Note (facoltative)" maxlength="300" aria-label="Note" />
        <div class="row">
          <button type="button" class="btn btn-secondary grow" onclick={() => { modulo = null; scelti = []; }}>Annulla</button>
          <button class="btn btn-primary grow" disabled={!modValido}>Registra {totale > 0 ? euro(totale) : ''}</button>
        </div>
      </form>
    {/if}

    <!-- Addebito a mano -->
    {#if addebito}
      <form class="modulo" onsubmit={(e) => { e.preventDefault(); salvaAddebito(); }}>
        <label class="field"><span class="lbl">Cosa</span><input class="inp" bind:value={addebito.descrizione} placeholder="Es. cuffia, ingresso extra" maxlength="200" /></label>
        <div class="due">
          <label class="field"><span class="lbl">Importo (€)</span><input class="inp m" type="number" min="0.01" step="0.01" bind:value={addebito.importo} /></label>
          <label class="field"><span class="lbl">Da pagare entro</span><input class="inp" type="date" bind:value={addebito.scadenza} /></label>
        </div>
        <input class="inp" bind:value={addebito.note} placeholder="Note (facoltative)" maxlength="300" aria-label="Note" />
        <p class="muted small nomargin">Le offerte del listino (es. quota agonistica) si addebitano da «Nuovo abbonamento o carnet…».</p>
        <div class="row">
          <button type="button" class="btn btn-secondary grow" onclick={() => (addebito = null)}>Annulla</button>
          <button class="btn btn-primary grow" disabled={!addebito.descrizione.trim() || !(Number(addebito.importo) > 0)}>Aggiungi</button>
        </div>
      </form>
    {/if}

    <!-- Storico -->
    {#if conto.pagamenti.length || chiusi.length}
      <div class="storico">
        <div class="lbl">Pagamenti registrati</div>
        <ul class="righe">
          {#each pagamentiVisti as p (p.id)}
            <li class="pag" class:spento={p.annullatoIl}>
              <div class="pag-testa">
                <strong class="m">{euro(p.importo)}</strong>
                <span class="small">{dataBreve(p.data)} · {p.pagante}{p.paganteId === id ? '' : ' per la famiglia'}</span>
                {#each p.metodi as m (m.id)}
                  <span class="tag {m.daIncassare && !m.incassatoIl ? 't-warn' : 't-altro'}">{m.metodo} {p.metodi.length > 1 ? euro(m.importo) : ''}{m.daIncassare ? (m.incassatoIl ? ' · incassato' : ' · da incassare') : ''}</span>
                  {#if m.daIncassare && !m.incassatoIl && !p.annullatoIl && puoPagare}
                    <Azione icona="incassato" tono="cloro" etichetta={`Segna incassato: ${m.metodo} di ${euro(m.importo)}`} onclick={() => esegui(() => api.incassa(m.id), `${m.metodo} di ${euro(m.importo)} segnato incassato.`)} />
                  {/if}
                {/each}
                {#if p.annullatoIl}<span class="tag t-altro">Annullato</span>
                {:else if puoAnnullare && annulla?.id !== p.id}<Azione icona="annulla" etichetta="Annulla il pagamento" onclick={() => (annulla = { tipo: 'pagamento', id: p.id, motivo: '' })} />{/if}
              </div>
              <div class="small muted">{p.voci.map((v) => `${v.personaId !== id && v.persona ? v.persona + ' · ' : ''}${v.descrizione}${p.voci.length > 1 ? ` (${euro(v.importo)})` : ''}`).join(' · ')}{p.note ? ` · ${p.note}` : ''}{p.annullatoIl && p.motivoAnnullamento ? ` · annullato: ${p.motivoAnnullamento}` : ''}</div>
              {#if annulla?.id === p.id}{@render chiediMotivo()}{/if}
            </li>
          {:else}
            <li class="muted small">Nessun pagamento.</li>
          {/each}
        </ul>
        {#if conto.pagamenti.length > 5}
          <button class="btn btn-quieto btn-sm sx" aria-expanded={tuttoLoStorico} onclick={() => (tuttoLoStorico = !tuttoLoStorico)}><Icona nome={tuttoLoStorico ? 'su' : 'giu'} />{tuttoLoStorico ? 'Solo gli ultimi' : `Tutti i ${conto.pagamenti.length} pagamenti`}</button>
        {/if}
        {#if chiusi.length}
          <details class="chiusi">
            <summary class="small">Pagati, crediti e annullati ({chiusi.length})</summary>
            <ul class="righe">{#each chiusi as a (a.id)}{@render voce(a, false)}{/each}</ul>
          </details>
        {/if}
      </div>
    {/if}
  {:else if !errore}
    <p class="muted small">Caricamento…</p>
  {/if}
</section>

{#snippet voce(a: Addebito, familiare: boolean)}
  {@const st = STATI_ADDEBITO[a.stato]}
  <li class="voce" class:spento={a.stato === 'ANNULLATO'}>
    {#if aperto(a) && puoPagare}
      <input type="checkbox" checked={scelti.includes(a.id)} onchange={(e) => scegli(a, e.currentTarget.checked)} aria-label="Paga {a.descrizione}" />
    {/if}
    <span class="grow">
      {#if familiare}<strong>{a.persona}</strong> · {/if}{a.descrizione}
      <span class="small muted"> · {a.stato === 'CREDITO' ? 'dal' : 'entro il'} {dataBreve(a.scadenza)}{a.pagato > 0 && a.residuo > 0 ? ` · pagati ${euro(a.pagato)} su ${euro(a.importo)}` : ''}{a.note ? ` · ${a.note}` : ''}{a.motivoAnnullamento ? ` · ${a.motivoAnnullamento}` : ''}</span>
    </span>
    <span class="m">{euro(a.stato === 'CREDITO' ? -a.importo : aperto(a) ? a.residuo : a.importo)}</span>
    <span class="tag {st.classe}">{st.nome}</span>
    {#if aperto(a) && a.pagato === 0 && puoAnnullare && annulla?.id !== a.id}
      <Azione icona="annulla" etichetta={`Annulla l'addebito «${a.descrizione}»`} onclick={() => (annulla = { tipo: 'addebito', id: a.id, motivo: '' })} />
    {/if}
    {#if annulla?.id === a.id}{@render chiediMotivo()}{/if}
  </li>
{/snippet}

{#snippet chiediMotivo()}
  <form class="motivo" onsubmit={(e) => { e.preventDefault(); confermaAnnulla(); }}>
    <input class="inp" bind:value={annulla!.motivo} placeholder="Perché si annulla (es. registrato due volte)" maxlength="300" aria-label="Motivo" />
    <button class="btn btn-danger" disabled={!annulla!.motivo.trim()}>Annulla {annulla!.tipo === 'pagamento' ? 'il pagamento' : 'l\'addebito'}</button>
    <Azione icona="chiudi" etichetta="Lascia com'è" onclick={() => (annulla = null)} />
  </form>
{/snippet}

<style>
  .blocco { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16); }
  h3 { margin: 0; font-size: var(--text-xl); }
  .testa { display: flex; align-items: center; justify-content: space-between; gap: var(--space-12); flex-wrap: wrap; }
  .totali { display: flex; gap: var(--space-8); flex-wrap: wrap; }
  .tot { display: flex; flex-direction: column; gap: 0; padding: var(--space-4) var(--space-10); border-radius: var(--radius-md); background: var(--surface-sunken); }
  .tot strong { font-size: var(--text-lg); }
  .tot.zero strong { color: var(--muted); }
  .tot.avviso { background: var(--ferma-bg); color: var(--ferma-fg); }
  .righe { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
  .righe > li { padding: var(--space-8) 0; border-top: 1px solid var(--line-soft); }
  .righe > li:first-child { border-top: none; }
  .voce { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .voce input[type='checkbox'] { width: 18px; height: 18px; accent-color: var(--cloro-ink, currentColor); }
  .spento { opacity: 0.6; }
  .fam, .storico { display: flex; flex-direction: column; gap: var(--space-4); padding-top: var(--space-8); border-top: 1px solid var(--line); }
  .azioni { display: flex; gap: var(--space-10); flex-wrap: wrap; }
  .modulo { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-12); border-radius: var(--radius-md); background: var(--surface-sunken); }
  select.inp { appearance: auto; }
  .riga-metodo { display: flex; gap: var(--space-6); align-items: center; flex-wrap: wrap; }
  .riga-metodo select { width: auto; min-width: 160px; }
  .riga-metodo .importo { width: 120px; }
  .riga-metodo .inp:not(select):not(.importo) { flex: 1; min-width: 160px; }
  .riepilogo { display: flex; flex-direction: column; gap: 2px; }
  .voce-r { display: flex; justify-content: space-between; gap: var(--space-10); }
  .pag { display: flex; flex-direction: column; gap: 2px; }
  .pag-testa { display: flex; align-items: center; gap: var(--space-8); flex-wrap: wrap; }
  .motivo { display: flex; gap: var(--space-6); align-items: center; flex-wrap: wrap; width: 100%; margin-top: var(--space-4); }
  .motivo .inp { flex: 1; min-width: 220px; }
  .sx { align-self: flex-start; min-height: 32px; }
  .chiusi summary { cursor: pointer; color: var(--muted); padding: var(--space-6) 0; }
  .link { min-height: 32px; }
  .spunta { display: flex; align-items: center; gap: var(--space-6); }
</style>
