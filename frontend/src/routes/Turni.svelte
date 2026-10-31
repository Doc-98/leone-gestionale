<script lang="ts">
  import Azione from '../components/Azione.svelte';
  import Foglio from '../components/Foglio.svelte';
  import ProssimoTurno from '../components/turni/ProssimoTurno.svelte';
  import VistaColonne from '../components/turni/VistaColonne.svelte';
  import VistaMese from '../components/turni/VistaMese.svelte';
  import VistaSettimana from '../components/turni/VistaSettimana.svelte';
  import { api, messaggioErrore, NonAutenticato, type Richiesta, type Riepilogo, type TipoRichiesta, type Turno } from '../lib/api';
  import { aggiungiGiorni, euro, formatOre, iso, lunedi, nomeMese, spostaMese } from '../lib/date';
  import { leggiPreferenza, salvaPreferenza } from '../lib/preferenze';
  import { sessione } from '../lib/sessione.svelte';
  import { oreTotali, prossimo, richiestaSul, titoloSettimana, VISTE_TURNI, type VistaTurni } from '../lib/turni';
  import RichiestaForm from './RichiestaForm.svelte';

  /**
   * I miei turni (ADR 0022) in tre viste a scelta: la settimana, il mese, la settimana a colonne (solo su schermi larghi).
   * In cima il prossimo turno; toccando un turno futuro si chiede una sostituzione, uno scambio o un cambio orario.
   */
  const oggi = iso(new Date());
  const io = $derived(sessione.utente?.personaId);
  let vista = $state<VistaTurni>(leggiPreferenza<VistaTurni>('turni.vista', 'settimana', ['settimana', 'mese', 'colonne']));
  let scegliVista = $state(false);
  let largo = $state(typeof matchMedia !== 'undefined' && matchMedia('(min-width: 1000px)').matches);
  $effect(() => {
    const mq = matchMedia('(min-width: 1000px)');
    const cambia = () => (largo = mq.matches);
    mq.addEventListener('change', cambia);
    return () => mq.removeEventListener('change', cambia);
  });
  // la vista a colonne non sta su un telefono: lì si vede la settimana
  const efficace = $derived<VistaTurni>(vista === 'colonne' && !largo ? 'settimana' : vista);

  let lun = $state(lunedi(oggi));
  let mese = $state(oggi.slice(0, 7));
  let turni = $state<Turno[]>([]);
  let staff = $state<Turno[]>([]);
  let futuri = $state<Turno[]>([]);
  let richieste = $state<Richiesta[]>([]);
  let aperte = $state<Richiesta[]>([]);
  let delMese = $state<Riepilogo | null>(null);
  let caricamento = $state(true);
  let errore = $state('');
  let messaggio = $state('');
  let chiedi = $state<{ turno: Turno; tipo: TipoRichiesta | null } | null>(null);
  let adesso = $state(minutiAdesso());

  function minutiAdesso() {
    const d = new Date();
    return d.getHours() * 60 + d.getMinutes();
  }
  $effect(() => {
    const id = setInterval(() => (adesso = minutiAdesso()), 60_000);
    return () => clearInterval(id);
  });

  const intervallo = $derived(efficace === 'mese' ? { da: `${mese}-01`, a: aggiungiGiorni(`${spostaMese(mese, 1)}-01`, -1) } : { da: lun, a: aggiungiGiorni(lun, 6) });

  function gestisci(e: unknown, testo: string) {
    if (e instanceof NonAutenticato) sessione.utente = null;
    else errore = messaggioErrore(e, testo);
  }

  $effect(() => {
    const { da, a } = intervallo;
    caricamento = true;
    errore = '';
    Promise.all([api.turniMiei(da, a), api.turniStaff(da, a).catch(() => [] as Turno[])])
      .then(([t, s]) => ((turni = t), (staff = s)))
      .catch((e) => gestisci(e, 'Non riesco a caricare i turni.'))
      .finally(() => (caricamento = false));
  });

  function caricaContorno() {
    const fino = aggiungiGiorni(oggi, 45);
    api.turniMiei(oggi, fino).then((t) => (futuri = t)).catch(() => {});
    api.turniStaff(oggi, aggiungiGiorni(oggi, 7)).then((s) => (staff = [...staff.filter((x) => !s.some((y) => y.id === x.id)), ...s])).catch(() => {});
    api.richiesteMie().then((r) => (richieste = r)).catch(() => {});
    api.richiesteAperte().then((r) => (aperte = r)).catch(() => {});
    api.riepilogoMio(oggi.slice(0, 7)).then((r) => (delMese = r)).catch(() => {});
  }
  caricaContorno();

  const succ = $derived(prossimo(futuri, oggi, adesso));
  const nota = (t: Turno) => richiestaSul(t, richieste, io);
  const perMe = $derived(richieste.filter((r) => r.stato === 'IN_ATTESA_COLLEGA' && r.collega?.id === io).length);
  const daCoprire = $derived(aperte.filter((r) => r.richiedente.id !== io).length);
  const mia = $derived(delMese?.righe[0]);

  const titolo = $derived(efficace === 'mese' ? nomeMese(mese).replace(/^./, (c) => c.toUpperCase()) : titoloSettimana(lun, !largo));
  const sotto = $derived(caricamento ? '…' : `${turni.filter((t) => t.stato === 'PIANIFICATO').length} turni · ${formatOre(oreTotali(turni))} h`);
  function sposta(d: number) {
    if (efficace === 'mese') mese = spostaMese(mese, d);
    else lun = aggiungiGiorni(lun, 7 * d);
  }
  function torna() {
    lun = lunedi(oggi);
    mese = oggi.slice(0, 7);
  }
  const aOggi = $derived(efficace === 'mese' ? mese === oggi.slice(0, 7) : lun === lunedi(oggi));

  function cambiaVista(v: VistaTurni) {
    vista = v;
    salvaPreferenza('turni.vista', v);
  }
  const apri = (turno: Turno, tipo: TipoRichiesta | null = null) => {
    messaggio = '';
    chiedi = { turno, tipo };
  };
  function fatto(m: string) {
    chiedi = null;
    messaggio = m;
    api.richiesteMie().then((r) => (richieste = r)).catch(() => {});
  }
</script>

<section class="page" class:stretta={efficace !== 'colonne'}>
  {#if efficace !== 'colonne' && succ}
    <ProssimoTurno turno={succ} {staff} {oggi} {adesso} nota={nota(succ)} onchiedi={() => apri(succ)} />
  {/if}

  <div class="testa">
    <span class="titolo"><b class="d">{titolo}</b><span class="muted small">{sotto}</span></span>
    <span class="gruppo-azioni">
      <Azione cornice icona="oggi" etichetta={efficace === 'mese' ? 'Torna a questo mese' : 'Torna a questa settimana'} disabled={aOggi} onclick={torna} />
      <Azione cornice icona="sx" etichetta={efficace === 'mese' ? 'Mese precedente' : 'Settimana precedente'} onclick={() => sposta(-1)} />
      <Azione cornice icona="dx" etichetta={efficace === 'mese' ? 'Mese successivo' : 'Settimana successiva'} onclick={() => sposta(1)} />
      <Azione icona="impostazioni" etichetta="Scegli come mostrare i turni" premuto={scegliVista} onclick={() => (scegliVista = !scegliVista)} />
    </span>
  </div>

  {#if scegliVista}
    <section class="card viste" aria-label="Come mostrare i turni">
      <span class="lbl">Come mostrare i turni</span>
      <div class="chips">
        {#each VISTE_TURNI as v (v.id)}
          <button type="button" class="chip" aria-pressed={vista === v.id} disabled={v.id === 'colonne' && !largo} onclick={() => cambiaVista(v.id)}>{v.nome}</button>
        {/each}
      </div>
      <p class="muted small nomargin">{largo ? 'La scelta resta per il tuo account su questo dispositivo.' : 'La vista a colonne è per gli schermi larghi: sul telefono si vede la settimana.'}</p>
    </section>
  {/if}
  {#if messaggio}<p class="msg ok nomargin" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  {#if efficace === 'settimana'}
    <VistaSettimana {lun} {turni} {oggi} {nota} onapri={(t) => apri(t)} />
  {:else if efficace === 'mese'}
    <VistaMese {mese} {turni} {staff} {oggi} {nota} onchiedi={(t, tipo) => apri(t, tipo)} />
  {:else}
    <VistaColonne {lun} {turni} {staff} {oggi} {adesso} {nota} onchiedi={(t, tipo) => apri(t, tipo)}>
      {#snippet lato()}
        {#if perMe || daCoprire}
          <section class="card riquadro">
            <span class="lbl warn">Per te · {perMe + daCoprire}</span>
            {#if perMe}<span class="small">{perMe === 1 ? 'Un collega ti chiede' : `${perMe} colleghi ti chiedono`} una sostituzione o uno scambio.</span>{/if}
            {#if daCoprire}<span class="small">{daCoprire === 1 ? 'Un turno da coprire' : `${daCoprire} turni da coprire`} dai colleghi.</span>{/if}
            <a class="link-r" href="#/richieste">Richieste →</a>
          </section>
        {/if}
        {#if mia}
          <section class="card riquadro riga"><span class="grow small"><b>{formatOre(mia.ore)} h</b> a {nomeMese(oggi.slice(0, 7)).split(' ')[0].toLowerCase()} · <span class="muted">{euro(mia.compenso)}</span></span><a class="link-r" href="#/ore">Ore →</a></section>
        {/if}
      {/snippet}
    </VistaColonne>
  {/if}

  {#if efficace !== 'colonne' && mia}
    <a class="card riquadro riga link-ore" href="#/ore"><span class="grow small"><b>{formatOre(mia.ore)} h</b> a {nomeMese(oggi.slice(0, 7)).split(' ')[0].toLowerCase()} · <span class="muted">{euro(mia.compenso)}</span></span><span class="link-r">Ore →</span></a>
  {/if}
</section>

{#if chiedi}
  <Foglio titolo="Richiesta sul turno" chiudi={() => (chiedi = null)}>
    <RichiestaForm turno={chiedi.turno} tipoIniziale={chiedi.tipo} {fatto} />
  </Foglio>
{/if}

<style>
  .stretta { max-width: 720px; }
  .testa { display: flex; align-items: center; gap: var(--space-8); }
  .titolo { flex: 1; min-width: 0; display: flex; flex-direction: column; line-height: 1.25; }
  .titolo b { font-size: var(--text-xl); font-weight: 800; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .viste { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-12) var(--space-14); }
  .riquadro { padding: var(--space-12) var(--space-14); display: flex; flex-direction: column; gap: var(--space-6); }
  .riquadro.riga { flex-direction: row; align-items: center; gap: var(--space-10); }
  .link-ore { text-decoration: none; color: var(--ink); }
  .link-r { font-size: var(--text-sm); font-weight: 700; color: var(--cloro); text-decoration: none; white-space: nowrap; }
  .warn { color: var(--warn); }
  .nomargin { margin: 0; }
</style>
