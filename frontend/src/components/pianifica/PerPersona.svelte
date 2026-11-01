<script lang="ts">
  import Icona from '../Icona.svelte';
  import type { PersonaStaff, Postazione, Turno } from '../../lib/api';
  import { daIso, GIORNI_BREVI, ora } from '../../lib/date';
  import { ammessa, formatH, gruppoDi, orePerPersona, siAccavallano, testoRiservata } from '../../lib/gestione';
  import { areaDi } from '../../lib/turni';

  /**
   * La settimana per persona: una riga a testa, dal più carico al meno carico, e in cima i turni scoperti. Si tocca
   * un turno scoperto e poi «Assegna qui» sulla persona; chi ha già un turno alla stessa ora ha un «+» tratteggiato
   * accanto a quel turno, per assegnare comunque.
   */
  let { giorni, turni, personale, postazioni, assegna }: {
    giorni: string[];
    turni: Turno[];
    personale: PersonaStaff[];
    postazioni: Postazione[];
    assegna: (t: Turno, p: PersonaStaff, forza: boolean) => Promise<boolean>;
  } = $props();

  let sceltoId = $state('');
  let chiuso = $state('');

  const scoperti = $derived(turni.filter((t) => !t.persona));
  const scelto = $derived(scoperti.find((t) => t.id === sceltoId));
  const postDi = (t: Turno) => postazioni.find((p) => p.id === t.postazioneId);
  const breve = (t: Turno) => ({ giorno: t.data, inizio: ora(t.inizio), fine: ora(t.fine) });
  const ore = $derived(orePerPersona(turni.map((t) => ({ id: t.id, giorno: t.data, inizio: t.inizio, fine: t.fine, postazioneId: t.postazioneId, persona: t.persona }))));
  const righe = $derived([...personale].sort((a, b) => (ore.get(b.id)?.ore ?? 0) - (ore.get(a.id)?.ore ?? 0) || a.nome.localeCompare(b.nome)));
  const dove = (t: Turno) => {
    const p = postDi(t);
    return p ? `${gruppoDi(p).nome} · ${gruppoDi(p).etichetta}` : t.postazione;
  };
  const iniziali = (n: string) => n.split(' ').map((x) => x[0]).slice(0, 2).join('');

  const avviso = $derived(scelto
    ? `${GIORNI_BREVI[((daIso(scelto.data).getDay() + 6) % 7) + 1]} ${daIso(scelto.data).getDate()}, ${ora(scelto.inizio)}–${ora(scelto.fine)} · ${dove(scelto)}: scegli chi lo fa${testoRiservata(postDi(scelto)) ? ` (${testoRiservata(postDi(scelto)).trim().replace('.', '').toLowerCase()})` : ''}.`
    : scoperti.length ? `${scoperti.length === 1 ? 'Un turno scoperto' : `${scoperti.length} turni scoperti`}: tocca un turno nella riga rossa, poi «Assegna qui» sulla persona.` : 'Tutti i turni della settimana hanno qualcuno.');
  const tono = $derived(scelto ? 'cloro' : scoperti.length ? 'ferma' : 'passa');

  function stato(p: PersonaStaff): { spenta: boolean; perche: string } {
    if (!scelto) return { spenta: false, perche: '' };
    if (!ammessa(postDi(scelto), p)) return { spenta: true, perche: `${testoRiservata(postDi(scelto)).trim()}` };
    const s = turni.find((t) => t.persona?.id === p.id && siAccavallano(breve(t), breve(scelto)));
    if (s) return { spenta: true, perche: `Ha già un turno alla stessa ora (${ora(s.inizio)}–${ora(s.fine)}): usa il + accanto per assegnare comunque` };
    return { spenta: false, perche: '' };
  }

  async function qui(p: PersonaStaff, forza: boolean) {
    if (scelto && (await assegna(scelto, p, forza))) sceltoId = '';
  }
</script>

{#if chiuso !== avviso}
  <div class="avviso t-{tono}" role="status">
    <Icona nome={scelto ? 'info' : scoperti.length ? 'avviso' : 'check'} />
    <span>{avviso}</span>
    {#if scelto}<button class="mini-btn" type="button" onclick={() => (sceltoId = '')}>Lascia stare</button>{/if}
    <button type="button" class="x-btn" aria-label="Chiudi il messaggio" onclick={() => (chiuso = avviso)}><Icona nome="chiudi" /></button>
  </div>
{/if}

<div class="card scorre">
  <div class="gp" role="grid" aria-label="Turni della settimana per persona">
    <div class="riga" role="row">
      <span class="th primo" role="columnheader"><span class="lbl">Persona</span></span>
      {#each giorni as g (g)}<span class="th" role="columnheader"><span class="lbl">{GIORNI_BREVI[((daIso(g).getDay() + 6) % 7) + 1]}</span><b>{daIso(g).getDate()}</b></span>{/each}
      <span class="th ultimo" role="columnheader"><span class="lbl">Ore</span></span>
    </div>

    {#if scoperti.length}
      <div class="riga vuoti" role="row">
        <span class="chi" role="rowheader"><b>Scoperti</b></span>
        {#each giorni as g (g)}
          <div class="c" role="gridcell">
            {#each scoperti.filter((t) => t.data === g) as t (t.id)}
              <button type="button" class="blk vuoto ar-{areaDi(t.postazione)}" class:scelto={sceltoId === t.id} aria-pressed={sceltoId === t.id}
                onclick={() => (sceltoId = sceltoId === t.id ? '' : t.id)}><span class="o">{ora(t.inizio)}–{ora(t.fine)}</span><span class="p">{dove(t)}</span></button>
            {/each}
          </div>
        {/each}
        <span class="tot m">{scoperti.length}</span>
      </div>
    {/if}

    {#each righe as p (p.id)}
      {@const st = stato(p)}
      {@const o = ore.get(p.id)?.ore ?? 0}
      <div class="riga" role="row">
        <span class="chi" class:spenta={st.spenta} title={st.perche || undefined} role="rowheader"><span class="av">{iniziali(p.nome)}</span><span class="nome">{p.nome}</span></span>
        {#each giorni as g (g)}
          {@const suoi = turni.filter((t) => t.persona?.id === p.id && t.data === g)}
          {@const quelGiorno = !!scelto && scelto.data === g && ammessa(postDi(scelto), p)}
          {@const sovr = quelGiorno ? suoi.find((t) => siAccavallano(breve(t), breve(scelto!))) : undefined}
          <div class="c" class:spenta={st.spenta && !sovr} role="gridcell">
            {#each suoi as t (t.id)}
              <div class="riga-blk">
                <span class="blk ar-{areaDi(t.postazione)}" class:spento={!!sovr}><span class="o">{ora(t.inizio)}–{ora(t.fine)}</span><span class="p">{dove(t)}</span></span>
                {#if sovr === t}<button type="button" class="piu-acc" title={`Assegna comunque: si accavalla con ${ora(t.inizio)}–${ora(t.fine)} · ${dove(t)}`}
                  aria-label={`Assegna comunque a ${p.nome}: si accavalla con ${ora(t.inizio)}–${ora(t.fine)}`} onclick={() => qui(p, true)}>+</button>{/if}
              </div>
            {/each}
            {#if quelGiorno && !sovr}<button type="button" class="qui-btn" aria-label={`Assegna qui: ${p.nome}`} onclick={() => qui(p, false)}>Assegna qui</button>{/if}
          </div>
        {/each}
        <span class="tot" class:spenta={st.spenta}><b class="m">{formatH(o)}</b><span class="ore-b"><i style="width: {Math.min(100, (o / 22) * 100)}%" class:troppe={o > 18}></i></span></span>
      </div>
    {/each}
  </div>
</div>
<span class="muted small">Le righe vanno dal più carico al meno carico. La barretta diventa arancione oltre le 18 ore.</span>

<style>
  .avviso { align-self: flex-start; max-width: 100%; display: inline-flex; align-items: center; gap: var(--space-12); padding: var(--space-6) var(--space-6) var(--space-6) var(--space-14);
    border-radius: var(--radius-md); font-size: var(--text-md); }
  .avviso :global(.ic) { width: 18px; height: 18px; flex-shrink: 0; }
  .avviso.t-cloro { background: var(--cloro-soft); color: var(--cloro-ink); }
  .avviso.t-ferma { background: var(--ferma-bg); color: var(--ferma-fg); }
  .avviso.t-passa { background: var(--passa-bg); color: var(--passa-fg); }
  .avviso .x-btn { color: inherit; }
  .mini-btn { position: relative; height: 32px; padding: 0 var(--space-12); border-radius: var(--radius-sm); border: 1px solid var(--line); background: var(--surface); font: inherit;
    font-size: var(--text-sm); font-weight: 700; color: var(--ink); cursor: pointer; white-space: nowrap; }
  .scorre { padding: 0; overflow-x: auto; }
  .gp { min-width: 940px; }
  .riga { display: grid; grid-template-columns: 190px repeat(7, minmax(0, 1fr)) 92px; }
  .th { padding: var(--space-10) var(--space-8); display: flex; flex-direction: column; align-items: center; line-height: 1.15; border-bottom: 1px solid var(--line); }
  .th b { font-family: var(--font-display); font-size: 20px; font-weight: 800; }
  .th.primo { align-items: flex-start; justify-content: flex-end; padding-left: var(--space-12); }
  .th.ultimo { align-items: flex-end; justify-content: flex-end; padding-right: var(--space-12); }
  .chi { padding: var(--space-8) var(--space-12); display: flex; align-items: center; gap: var(--space-10); border-top: 1px solid var(--line-soft); min-width: 0; font-size: var(--text-md); }
  .chi .nome { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .av { width: 30px; height: 30px; flex-shrink: 0; border-radius: 50%; display: grid; place-items: center; font-size: 11px; font-weight: 700; background: var(--surface-sunken); color: var(--ink); }
  .c { padding: 4px; border-top: 1px solid var(--line-soft); border-left: 1px solid var(--line-soft); display: flex; flex-direction: column; gap: 4px; min-height: 50px; min-width: 0; }
  .tot { padding: var(--space-8) var(--space-12); border-top: 1px solid var(--line-soft); border-left: 1px solid var(--line-soft); display: flex; flex-direction: column; justify-content: center;
    align-items: flex-end; gap: 4px; font-size: var(--text-sm); }
  .ore-b { width: 64px; height: 6px; border-radius: 3px; background: var(--surface-sunken); overflow: hidden; }
  .ore-b i { display: block; height: 100%; background: var(--cloro); }
  .ore-b i.troppe { background: var(--warn); }
  /* la riga dei turni scoperti, in rosso tenue */
  .vuoti > * { background: var(--ferma-bg); }
  .vuoti .chi, .vuoti .tot { color: var(--ferma-fg); }
  /* chi non può coprire il turno scelto: grigio, senza cambiare l'altezza */
  .spenta { opacity: 0.3; filter: grayscale(1); }
  .blk { position: relative; display: flex; flex-direction: column; gap: 1px; padding: 5px 6px; border-radius: 8px; border: none; background: var(--a-bg); color: var(--a-fg); font: inherit;
    text-align: left; min-width: 0; }
  button.blk { cursor: pointer; }
  .blk .o { font-family: var(--font-mono); font-size: 10.5px; white-space: nowrap; overflow: hidden; }
  .blk .p { font-size: var(--text-xs); font-weight: 700; color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .blk.vuoto { background: repeating-linear-gradient(135deg, var(--surface) 0 7px, var(--ferma-bg) 7px 10px); color: var(--ferma-fg); box-shadow: inset 0 0 0 2px var(--ferma); }
  .blk.vuoto .p { color: var(--ferma-fg); }
  .blk.vuoto.scelto { box-shadow: inset 0 0 0 3px var(--ink); }
  .blk.spento { opacity: 0.3; filter: grayscale(1); }
  .riga-blk { display: flex; gap: 4px; align-items: stretch; min-width: 0; }
  .riga-blk .blk { flex: 1; }
  /* si accavalla: un piccolo + tratteggiato accanto al turno con cui si sovrappone, per assegnare comunque */
  .piu-acc { width: 24px; flex-shrink: 0; padding: 0; border: 1.5px dashed var(--warn); border-radius: 8px; background: var(--warn-bg); color: var(--warn); font: inherit; font-size: 16px;
    font-weight: 800; line-height: 1; cursor: pointer; }
  .piu-acc:hover { border-style: solid; }
  .qui-btn { flex: 1; min-height: 30px; border: 1.5px dashed var(--cloro); border-radius: 8px; background: var(--cloro-soft); color: var(--cloro-ink); font: inherit; font-size: var(--text-xs);
    font-weight: 700; cursor: pointer; }
</style>
