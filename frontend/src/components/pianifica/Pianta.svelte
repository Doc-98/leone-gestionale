<script lang="ts">
  import Icona from '../Icona.svelte';
  import type { PersonaStaff, Postazione, Turno } from '../../lib/api';
  import { daIso, GIORNI_BREVI, iso, ora } from '../../lib/date';
  import { ammessa, formatH, gruppi, orePerPersona, scoperto, siAccavallano, testoRiservata } from '../../lib/gestione';

  /**
   * Un giorno alla volta, come sulla pianta dello spogliatoio: un riquadro per area con le sue postazioni. Si trascina
   * un nome dall'elenco su una postazione (o si tocca il nome e poi la postazione); per spostare qualcuno lo si
   * trascina da una postazione all'altra. Chi non può lavorare su una postazione (spogliatoio dell'altro sesso) non ci
   * si può lasciare; due turni accavallati della stessa persona chiedono conferma.
   */
  let { giorni, turni, personale, postazioni, assegna }: {
    giorni: string[];
    turni: Turno[];
    personale: PersonaStaff[];
    postazioni: Postazione[];
    /** da = il turno da cui la persona viene spostata (resta scoperto) */
    assegna: (t: Turno, p: PersonaStaff | null, forza: boolean, da?: Turno) => Promise<boolean>;
  } = $props();

  const oggi = iso(new Date());
  // svelte-ignore state_referenced_locally
  let giorno = $state(giorni.includes(oggi) ? oggi : giorni[0]);
  $effect(() => {
    if (!giorni.includes(giorno)) giorno = giorni.includes(oggi) ? oggi : giorni[0];
  });
  let filtro = $state<'' | 'M' | 'F'>('');
  let cerca = $state('');
  let inMano = $state<{ persona: PersonaStaff; da?: Turno } | null>(null);
  let sopra = $state('');
  let mostraOccupati = $state(false);
  let avviso = $state<{ testo: string; conferma?: () => void; tono: 'warn' | 'ok' | 'cloro' } | null>(null);

  const delGiorno = $derived(turni.filter((t) => t.data === giorno).sort((a, b) => a.inizio.localeCompare(b.inizio)));
  const aree = $derived(gruppi(postazioni));
  const ore = $derived(orePerPersona(turni.map((t) => ({ id: t.id, giorno: t.data, inizio: t.inizio, fine: t.fine, postazioneId: t.postazioneId, persona: t.persona }))));
  const breve = (t: Turno) => ({ giorno: t.data, inizio: ora(t.inizio), fine: ora(t.fine) });
  const postDi = (t: Turno) => postazioni.find((p) => p.id === t.postazioneId);
  const inTurno = (p: PersonaStaff) => delGiorno.filter((t) => t.persona?.id === p.id);
  const filtrati = $derived(personale.filter((p) => (!filtro || p.sesso === filtro) && (!cerca.trim() || p.nome.toLowerCase().includes(cerca.trim().toLowerCase()))));
  const liberi = $derived(filtrati.filter((p) => !inTurno(p).length).sort((a, b) => (ore.get(a.id)?.ore ?? 0) - (ore.get(b.id)?.ore ?? 0) || a.nome.localeCompare(b.nome)));
  const occupati = $derived(filtrati.filter((p) => inTurno(p).length));
  const scopertiDel = (g: string) => turni.filter((t) => t.data === g && !t.persona).length;
  const iniziali = (n: string) => n.split(' ').map((x) => x[0]).slice(0, 2).join('');
  const nome1 = (n: string) => n.split(' ')[0];

  async function lascia(t: Turno, mano: { persona: PersonaStaff; da?: Turno }) {
    sopra = '';
    const p = mano.persona;
    if (mano.da?.id === t.id) return void (inMano = null);
    if (!ammessa(postDi(t), p)) {
      avviso = { testo: `${p.nome} non può lavorare qui: ${testoRiservata(postDi(t)).trim().toLowerCase().replace('.', '')}.`, tono: 'warn' };
      return;
    }
    const acc = turni.find((x) => x.persona?.id === p.id && x.id !== mano.da?.id && x.id !== t.id && siAccavallano(breve(x), breve(t)));
    const esegui = async (forza: boolean) => {
      avviso = null;
      if (await assegna(t, p, forza, mano.da)) {
        avviso = { testo: `${nome1(p.nome)} ${mano.da ? 'spostato' : 'messo'} su ${t.postazione}, ${ora(t.inizio)}–${ora(t.fine)}.`, tono: 'ok' };
        inMano = null;
      }
    };
    if (acc) avviso = { testo: `${p.nome} ha già un turno ${ora(acc.inizio)}–${ora(acc.fine)} (${acc.postazione}): i due turni si accavallano.`, conferma: () => esegui(true), tono: 'warn' };
    else await esegui(false);
  }

  function prendi(e: DragEvent, persona: PersonaStaff, da?: Turno) {
    inMano = { persona, da };
    e.dataTransfer?.setData('text/plain', persona.id);
    if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move';
  }

  async function togli(t: Turno) {
    if (await assegna(t, null, false)) avviso = { testo: `${t.postazione}, ${ora(t.inizio)}–${ora(t.fine)}: ora è scoperto.`, tono: 'ok' };
  }

  const riass = $derived.by(() => {
    const s = delGiorno.filter((t) => !t.persona).length;
    return `${delGiorno.length} turni · ${new Set(delGiorno.filter((t) => t.persona).map((t) => t.persona!.id)).size} persone${s ? ` · ${scoperto(s)}` : ''}`;
  });
</script>

<div class="barra">
  <div class="giorni" role="group" aria-label="Giorno">
    {#each giorni as g (g)}
      <button type="button" aria-pressed={giorno === g} onclick={() => ((giorno = g), (avviso = null))}
        aria-label={`${GIORNI_BREVI[((daIso(g).getDay() + 6) % 7) + 1]} ${daIso(g).getDate()}${scopertiDel(g) ? `, ${scoperto(scopertiDel(g))}` : ''}`}>
        <span>{GIORNI_BREVI[((daIso(g).getDay() + 6) % 7) + 1]}</span><b>{daIso(g).getDate()}</b>{#if scopertiDel(g)}<i class="punto"></i>{/if}
      </button>
    {/each}
  </div>
  <span class="grow"></span>
  <span class="muted small">{riass}</span>
</div>

{#if avviso || inMano}
  <div class="avviso t-{avviso?.tono ?? 'cloro'}" role="status">
    <span>{avviso?.testo ?? `Ora tocca la postazione per ${nome1(inMano!.persona.nome)}.`}</span>
    {#if avviso?.conferma}<button class="mini-btn" type="button" onclick={() => (avviso = null)}>Lascia stare</button><button class="mini-btn pieno" type="button" onclick={avviso.conferma}>Assegna comunque</button>
    {:else if inMano && !avviso}<button class="mini-btn" type="button" onclick={() => (inMano = null)}>Annulla</button>{/if}
    <button type="button" class="x-btn" aria-label="Chiudi il messaggio" onclick={() => ((avviso = null), (inMano = null))}><Icona nome="chiudi" /></button>
  </div>
{/if}

<div class="colonne-p">
  <div class="princ card pianta">
    {#each aree as a (a.chiave)}
      <section class="area ar-{a.area}" aria-label={a.nome}>
        <span class="etichetta"><i></i>{a.nome}</span>
        <div class="posti">
          {#each a.righe as r (r.postazione.id)}
            {@const suoi = delGiorno.filter((t) => t.postazioneId === r.postazione.id)}
            {#each suoi as t (t.id)}
              {@const ok = !inMano || ammessa(r.postazione, inMano.persona)}
              <div class="post" class:vuota={!t.persona} class:ok-drop={!!inMano && ok} class:no-drop={!!inMano && !ok} class:sopra={sopra === t.id} role="group"
                aria-label={`${r.postazione.nome}, ${ora(t.inizio)}–${ora(t.fine)}`}
                ondragover={(e) => { if (inMano && ok) { e.preventDefault(); sopra = t.id; } }} ondragleave={() => (sopra = '')}
                ondrop={(e) => { e.preventDefault(); if (inMano) lascia(t, inMano); }}>
                <button type="button" class="area-tap" aria-label={inMano ? `Metti ${inMano.persona.nome} su ${r.postazione.nome}, ${ora(t.inizio)}–${ora(t.fine)}` : `${r.postazione.nome}, ${ora(t.inizio)}–${ora(t.fine)}`}
                  onclick={() => inMano && lascia(t, inMano)}></button>
                <span class="testa-p"><b>{r.etichetta}</b><span class="m">{ora(t.inizio)}–{ora(t.fine)}</span></span>
                {#if t.persona}
                  {@const p = personale.find((x) => x.id === t.persona!.id)}
                  <span class="chip-p" draggable={!!p} ondragstart={(e) => p && prendi(e, p, t)} ondragend={() => (sopra = '')} title="Trascina su un'altra postazione per spostare" role="listitem">
                    <span class="av">{iniziali(t.persona.nome)}</span><span class="n">{t.persona.nome}</span>
                    <button type="button" class="x" aria-label={`Togli ${t.persona.nome} da ${r.postazione.nome}`} onclick={() => togli(t)}>×</button>
                  </span>
                {:else}
                  <span class="vuoto-t">{inMano ? (ok ? `Lascia qui ${nome1(inMano.persona.nome)}` : 'Non adatta') : 'Trascina qui un nome'}</span>
                {/if}
              </div>
            {:else}
              <div class="post chiusa"><span class="testa-p"><b>{r.etichetta}</b></span><span class="small">Nessun turno oggi</span></div>
            {/each}
          {/each}
        </div>
      </section>
    {/each}
  </div>

  <aside class="lato-p card staff-p">
    <span class="intesta"><h2 class="grow">Staff</h2><span class="muted small">{personale.length}</span></span>
    <div class="seg tre" role="group" aria-label="Filtra">
      <button type="button" aria-pressed={filtro === ''} onclick={() => (filtro = '')}>Tutti</button>
      <button type="button" aria-pressed={filtro === 'M'} onclick={() => (filtro = 'M')}>Uomini</button>
      <button type="button" aria-pressed={filtro === 'F'} onclick={() => (filtro = 'F')}>Donne</button>
    </div>
    <label class="cerca"><Icona nome="cerca" /><span class="sr">Cerca nello staff</span><input class="inp" placeholder="Cerca" bind:value={cerca} /></label>
    <span class="lbl">Liberi · dal meno carico</span>
    <div class="staff">
      {#each liberi as p (p.id)}
        <button type="button" class="riga-s" draggable="true" ondragstart={(e) => prendi(e, p)} ondragend={() => (sopra = '')} aria-pressed={inMano?.persona.id === p.id && !inMano.da}
          onclick={() => ((inMano = inMano?.persona.id === p.id ? null : { persona: p }), (avviso = null))}>
          <span class="av" class:u={p.sesso === 'M'} class:d={p.sesso === 'F'}>{iniziali(p.nome)}</span>
          <span class="t"><b>{p.nome}</b><span>{formatH(ore.get(p.id)?.ore ?? 0)} questa settimana</span></span><span class="maniglia" aria-hidden="true">⋮⋮</span>
        </button>
      {:else}
        <p class="muted small nomargin">Nessuno libero{cerca || filtro ? ' con questi filtri' : ''}.</p>
      {/each}
    </div>
    {#if occupati.length}
      <button type="button" class="mostra" aria-expanded={mostraOccupati} onclick={() => (mostraOccupati = !mostraOccupati)}>
        <span class="lbl grow">Già in turno oggi · {occupati.length}</span><Icona nome={mostraOccupati ? 'su' : 'giu'} /></button>
      {#if mostraOccupati}
        <div class="staff">
          {#each occupati as p (p.id)}
            <button type="button" class="riga-s in-turno" draggable="true" ondragstart={(e) => prendi(e, p)} ondragend={() => (sopra = '')} aria-pressed={inMano?.persona.id === p.id && !inMano.da}
              onclick={() => ((inMano = inMano?.persona.id === p.id ? null : { persona: p }), (avviso = null))}>
              <span class="av" class:u={p.sesso === 'M'} class:d={p.sesso === 'F'}>{iniziali(p.nome)}</span>
              <span class="t"><b>{p.nome}</b><span>{inTurno(p).map((t) => `${ora(t.inizio)}–${ora(t.fine)}`).join(', ')}</span></span>
            </button>
          {/each}
        </div>
      {/if}
    {/if}
    <span class="muted small">Trascina un nome su una postazione, oppure tocca il nome e poi la postazione. Per spostare qualcuno trascinalo da una postazione all'altra.</span>
  </aside>
</div>

<style>
  .barra { display: flex; align-items: center; gap: var(--space-12); flex-wrap: wrap; }
  .giorni { display: flex; gap: var(--space-6); flex-wrap: wrap; }
  .giorni button { position: relative; display: flex; flex-direction: column; align-items: center; min-width: 60px; padding: var(--space-6) var(--space-10); border-radius: 12px;
    border: 1px solid var(--line); background: var(--surface); font: inherit; color: var(--ink); cursor: pointer; }
  .giorni button b { font-family: var(--font-display); font-size: var(--text-lg); font-weight: 800; line-height: 1.1; }
  .giorni button span { font-size: 11px; font-weight: 700; text-transform: uppercase; color: var(--muted); }
  .giorni button[aria-pressed='true'] { background: var(--fill); border-color: var(--fill); color: var(--on-fill); }
  .giorni button[aria-pressed='true'] span { color: color-mix(in srgb, var(--on-fill) 70%, transparent); }
  .punto { position: absolute; top: -4px; right: -4px; width: 11px; height: 11px; border-radius: 50%; background: var(--ferma); box-shadow: 0 0 0 2px var(--carta); }
  .avviso { align-self: flex-start; max-width: 100%; display: inline-flex; align-items: center; gap: var(--space-10); min-height: 40px; padding: var(--space-4) var(--space-6) var(--space-4) var(--space-14);
    border-radius: var(--radius-md); font-size: var(--text-md); font-weight: 600; flex-wrap: wrap; }
  .avviso.t-cloro { background: var(--cloro-soft); color: var(--cloro-ink); }
  .avviso.t-warn { background: var(--warn-bg); color: var(--warn); }
  .avviso.t-ok { background: var(--passa-bg); color: var(--passa-fg); }
  .avviso .x-btn { color: inherit; }
  .mini-btn { position: relative; height: 32px; padding: 0 var(--space-12); border-radius: var(--radius-sm); border: 1px solid var(--line); background: var(--surface); font: inherit;
    font-size: var(--text-sm); font-weight: 700; color: var(--ink); cursor: pointer; white-space: nowrap; }
  .mini-btn.pieno { background: var(--warn); border-color: var(--warn); color: var(--surface); }
  /* la pianta: un riquadro per area, che si dispongono da soli per stare nello schermo */
  .pianta { padding: var(--space-14); display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: var(--space-14); align-content: start; }
  .area { position: relative; padding: var(--space-12); border-radius: var(--radius-md); background: var(--a-bg); border: 2px solid var(--a); display: flex; flex-direction: column; gap: var(--space-10); }
  .etichetta { display: flex; align-items: center; gap: var(--space-6); font-size: var(--text-xs); font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--a-fg); }
  .etichetta i { width: 10px; height: 10px; border-radius: 3px; background: var(--a); }
  .posti { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: var(--space-8); }
  .post { position: relative; min-height: 82px; padding: var(--space-8) var(--space-10); border-radius: 12px; background: var(--surface); border: 1.5px solid var(--a);
    display: flex; flex-direction: column; gap: var(--space-6); font-size: var(--text-sm); box-shadow: 0 1px 2px rgba(16, 33, 43, 0.08); transition: box-shadow 0.12s, opacity 0.12s; }
  .testa-p { position: relative; display: flex; flex-direction: column; pointer-events: none; min-width: 0; }
  .testa-p b { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .testa-p .m { font-size: var(--text-xs); color: var(--muted); white-space: nowrap; }
  .post.vuota { border: 2px dashed var(--ferma); background: repeating-linear-gradient(135deg, var(--surface) 0 7px, var(--ferma-bg) 7px 10px); }
  .post.chiusa { border-style: dashed; border-color: var(--line-strong); background: var(--surface-sunken); color: var(--muted); }
  .post.ok-drop { box-shadow: 0 0 0 3px var(--cloro); }
  .post.no-drop { opacity: 0.35; }
  .post.sopra { box-shadow: 0 0 0 4px var(--cloro); background: var(--cloro-soft); }
  .area-tap { position: absolute; inset: 0; border: none; background: none; cursor: pointer; border-radius: 12px; }
  .chip-p { position: relative; z-index: 1; display: flex; align-items: center; gap: var(--space-6); padding: 3px; border-radius: 999px; background: var(--a-bg); color: var(--ink);
    font-weight: 600; cursor: grab; min-width: 0; }
  .chip-p .av { width: 24px; height: 24px; font-size: 10px; background: var(--surface); color: var(--a-fg); }
  .chip-p .n { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .chip-p .x { position: relative; width: 24px; height: 24px; flex-shrink: 0; border-radius: 50%; border: none; background: none; color: var(--muted); cursor: pointer; font-size: 15px; line-height: 1; }
  .chip-p .x:hover { background: var(--surface); color: var(--danger); }
  .vuoto-t { position: relative; font-size: var(--text-xs); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-weight: 700; color: var(--ferma-fg); pointer-events: none; }
  .staff-p { padding: var(--space-14); gap: var(--space-10); }
  .intesta { display: flex; align-items: baseline; gap: var(--space-8); }
  .intesta h2 { margin: 0; font-family: var(--font-display); font-size: var(--text-xl); font-weight: 800; }
  .seg.tre { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .cerca { position: relative; display: block; }
  .cerca :global(.ic) { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); width: 18px; height: 18px; color: var(--muted); }
  .cerca .inp { padding-left: 38px; }
  .staff { display: flex; flex-direction: column; }
  .riga-s { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-8) var(--space-10); border-radius: 10px; border: none; background: none; font: inherit; color: var(--ink);
    text-align: left; cursor: grab; width: 100%; }
  .riga-s:hover { background: var(--carta); }
  .riga-s[aria-pressed='true'] { background: var(--cloro-soft); box-shadow: inset 0 0 0 2px var(--cloro); }
  .riga-s.in-turno { opacity: 0.65; }
  .av { width: 32px; height: 32px; flex-shrink: 0; border-radius: 50%; display: grid; place-items: center; font-size: 11px; font-weight: 700; background: var(--surface-sunken); color: var(--ink); }
  .av.u { background: var(--area-m-bg); color: var(--area-m-fg); }
  .av.d { background: var(--area-f-bg); color: var(--area-f-fg); }
  .riga-s .t { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
  .riga-s .t b { font-size: var(--text-md); font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .riga-s .t span { font-size: var(--text-xs); color: var(--muted); }
  .maniglia { color: var(--line-strong); font-size: 14px; letter-spacing: -2px; }
  .mostra { display: flex; align-items: center; gap: var(--space-8); min-height: 40px; padding: 0 var(--space-4); border: none; background: none; cursor: pointer; color: var(--muted); }
  .mostra :global(.ic) { width: 18px; height: 18px; }
</style>
