<script lang="ts">
  import { untrack } from 'svelte';
  import { api, messaggioErrore, type Corso, type Offerta, type PropostaPrezzo, type SchedaCliente, type Stagione } from '../lib/api';
  import { dataBreve, iso } from '../lib/date';
  import { etichettaVolte, euro } from '../lib/listino';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Vendita dal listino (ADR 0018): si sceglie l'offerta della stagione e la frequenza; il gestionale propone l'importo
   * (prezzo per frequenza e gruppo, riduzione per iscrizione tardiva, rate). Chi ha il permesso lo può cambiare,
   * scrivendo il motivo.
   */
  let { cliente, corsi, esegui, chiudi, aMano }: {
    cliente: SchedaCliente; corsi: Corso[]; esegui: (azione: () => Promise<unknown>, fatto: string) => Promise<boolean>;
    chiudi: () => void; aMano: () => void;
  } = $props();

  const id = untrack(() => cliente.persona.id);
  let dal = $state(iso(new Date()));
  let stagioni = $state<Stagione[]>([]);
  let offerte = $state<Offerta[]>([]);
  let caricato = $state(false);
  let offertaId = $state('');
  let corsoId = $state('');
  let volte = $state<number | null>(null);
  let al = $state('');
  let proposta = $state<PropostaPrezzo | null>(null);
  let importo = $state('');
  let motivo = $state('');
  let errore = $state('');
  const puoCambiare = $derived(sessione.puo('SCONTI'));

  api.stagioni().then((s) => (stagioni = s)).catch(() => {});
  const stagione = $derived(stagioni.find((s) => s.dal <= dal && dal <= s.al) ?? null);

  // il listino della stagione della data d'inizio
  $effect(() => {
    const s = stagione;
    if (!s) return void ((offerte = []), (caricato = stagioni.length > 0));
    api.listino(s.id).then((p) => {
      offerte = p.offerte.filter((o) => o.attiva && o.tipo !== 'ALTRO');
      caricato = true;
      if (!offerte.some((o) => o.id === offertaId)) scegliOfferta(predefinita());
    }).catch((e) => (errore = messaggioErrore(e)));
  });

  /** L'offerta che comprende il corso che la persona frequenta, altrimenti la prima. */
  function predefinita(): Offerta | undefined {
    const suoi = cliente.turni.map((t) => t.corsoId);
    return offerte.find((o) => o.corsi.some((c) => suoi.includes(c))) ?? offerte[0];
  }

  const offerta = $derived(offerte.find((o) => o.id === offertaId));
  const frequenze = $derived(offerta?.tipo === 'CORSO' ? [...new Set(offerta.prezzi.map((p) => p.volte))].sort((a, b) => chiave(a) - chiave(b)) : []);
  /** Prezzo unico prima, poi mono, bi, tri…, open in fondo. */
  function chiave(v: number | null) {
    return v == null ? -1 : v === 0 ? 99 : v;
  }

  function scegliOfferta(o: Offerta | undefined) {
    offertaId = o?.id ?? '';
    proposta = null;
    if (!o) return;
    const suoi = cliente.turni.filter((t) => o.corsi.includes(t.corsoId));
    corsoId = suoi[0]?.corsoId ?? (o.corsi.length === 1 ? o.corsi[0] : '');
    const f = [...new Set(o.prezzi.map((p) => p.volte))];
    const n = suoi.length;
    volte = o.tipo === 'CORSO' ? (f.includes(n) ? n : f.includes(1) ? 1 : f[0] ?? null) : null;
  }

  // la proposta si ricalcola cambiando offerta, frequenza o data
  $effect(() => {
    const o = offertaId, v = volte, d = dal;
    if (!o || !d) return;
    api.propostaPrezzo(id, o, v, d).then((p) => {
      proposta = p;
      importo = String(p.proposto);
      al = p.al ?? '';
      motivo = '';
      errore = '';
    }).catch((e) => {
      proposta = null;
      errore = messaggioErrore(e);
    });
  });

  const cambiato = $derived(proposta != null && importo !== '' && Number(importo) !== Number(proposta.proposto));
  const nomeCorso = (c: string) => corsi.find((x) => x.id === c)?.nome ?? 'corso';
  const nomeGruppo = (g: string | null) => cliente.gruppi.find((x) => x.gruppoId === g)?.nome ?? 'gruppo';

  async function vendi() {
    const o = offerta!;
    const ok = await esegui(() => api.nuovoAbbonamento(id, {
      offertaId: o.id, volteASettimana: o.tipo === 'CORSO' ? volte : null, corsoId: o.tipo === 'CORSO' ? corsoId || null : null,
      dal, al: al || null, importo: cambiato ? Number(importo) : null, motivoImporto: cambiato ? motivo : null,
    }), `${o.nome} registrato: ${euro(cambiato ? Number(importo) : proposta!.proposto)}.`);
    if (ok) chiudi();
  }
</script>

<form class="vendita" onsubmit={(e) => { e.preventDefault(); vendi(); }}>
  <div class="due">
    <label class="field"><span class="lbl">Dal</span><input class="inp" type="date" bind:value={dal} /></label>
    <label class="field"><span class="lbl">Offerta {stagione ? `(listino ${stagione.nome})` : ''}</span>
      <select class="inp" value={offertaId} onchange={(e) => scegliOfferta(offerte.find((o) => o.id === e.currentTarget.value))}>
        {#if !offerte.length}<option value="">—</option>{/if}
        {#if offerte.some((o) => o.tipo === 'CORSO')}<optgroup label="Abbonamenti ai corsi">{#each offerte.filter((o) => o.tipo === 'CORSO') as o (o.id)}<option value={o.id}>{o.nome}</option>{/each}</optgroup>{/if}
        {#if offerte.some((o) => o.tipo === 'CARNET')}<optgroup label="Carnet">{#each offerte.filter((o) => o.tipo === 'CARNET') as o (o.id)}<option value={o.id}>{o.nome}</option>{/each}</optgroup>{/if}
      </select>
    </label>
  </div>

  {#if caricato && !offerte.length}
    <p class="hint nomargin">{stagione ? `Il listino ${stagione.nome} è vuoto.` : 'Nessuna stagione comprende questa data.'} Si può registrare a mano.</p>
  {/if}

  {#if offerta?.tipo === 'CORSO'}
    {#if offerta.corsi.length > 1}
      <label class="field"><span class="lbl">Corso</span>
        <select class="inp" bind:value={corsoId}><option value="" disabled>Scegli il corso</option>{#each offerta.corsi as c (c)}<option value={c}>{nomeCorso(c)}</option>{/each}</select>
      </label>
    {/if}
    {#if frequenze.length > 1 || frequenze[0] != null}
      <div class="field"><span class="lbl">Frequenza</span>
        <div class="chips">{#each frequenze as f (f ?? -1)}<button type="button" class="chip" aria-pressed={volte === f} onclick={() => (volte = f)}>{etichettaVolte(f)}</button>{/each}</div>
      </div>
    {/if}
  {/if}

  {#if proposta}
    <div class="conto" aria-live="polite">
      <div class="voce"><span>Listino{proposta.gruppoId ? ` · ${nomeGruppo(proposta.gruppoId)}` : ''}</span><span>{euro(proposta.listino)}</span></div>
      {#if proposta.prezzoBase != null}<div class="voce muted small"><span>Prezzo per tutti</span><span>{euro(proposta.prezzoBase)}</span></div>{/if}
      {#if proposta.riduzionePercentuale}
        <div class="voce"><span>Iscrizione a stagione iniziata (−{proposta.riduzionePercentuale}%)</span><span>−{euro(proposta.riduzione)}</span></div>
      {/if}
      <div class="voce totale"><span>Proposto</span><strong>{euro(proposta.proposto)}</strong></div>
      {#if proposta.rate.length}
        <div class="rate small">
          {#each proposta.rate as r (r.numero)}<span>{r.numero === 1 ? 'All\'iscrizione' : r.scadenza ? `Entro il ${dataBreve(r.scadenza)}` : `Rata ${r.numero}`}: {euro(r.importo)}</span>{/each}
        </div>
      {/if}
    </div>
    <div class="due">
      <label class="field"><span class="lbl">Importo (€)</span>
        <input class="inp m" type="number" min="0" step="0.01" bind:value={importo} disabled={!puoCambiare}
               title={puoCambiare ? '' : 'Per cambiarlo serve il permesso «Cambiare l\'importo proposto»'} /></label>
      <label class="field"><span class="lbl">Fino al</span><input class="inp" type="date" bind:value={al} min={dal} /></label>
    </div>
    {#if cambiato}
      <label class="field"><span class="lbl">Perché l'importo è diverso</span><input class="inp" bind:value={motivo} placeholder="Es. fratello già iscritto, accordo con la direzione" maxlength="300" /></label>
    {/if}
  {/if}
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  <div class="row">
    <button type="button" class="btn btn-secondary grow" onclick={chiudi}>Annulla</button>
    <button class="btn btn-primary grow" disabled={!proposta || (offerta?.tipo === 'CORSO' && !corsoId) || (cambiato && !motivo.trim())}>Registra</button>
  </div>
  <button type="button" class="link small sx" onclick={aMano}>Registra a mano, senza listino</button>
</form>

<style>
  .vendita { display: flex; flex-direction: column; gap: var(--space-10); }
  select.inp { appearance: auto; }
  .conto { display: flex; flex-direction: column; gap: var(--space-4); padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); background: var(--surface-sunken); }
  .voce { display: flex; justify-content: space-between; gap: var(--space-10); }
  .totale { padding-top: var(--space-6); margin-top: var(--space-2, 2px); border-top: 1px solid var(--line); font-size: var(--text-lg); }
  .rate { display: flex; flex-wrap: wrap; gap: var(--space-4) var(--space-14); color: var(--muted); }
  .sx { align-self: flex-start; min-height: 32px; }
</style>
