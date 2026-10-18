<script lang="ts">
  import { untrack } from 'svelte';
  import { api, messaggioErrore, type Evento, type PersonaBreve, type SpazioPiano, type TipoEvento } from '../lib/api';
  import { ora } from '../lib/date';
  import { TIPI_EVENTO } from '../lib/piano';
  import SceltaSpazi from '../components/SceltaSpazi.svelte';

  let { evento, impiantoId, spazi, personale, data = '', inizio = '17:00', spazio = '', fatto, annullato }: {
    evento: Evento | null;
    impiantoId: string;
    spazi: SpazioPiano[];
    personale: PersonaBreve[];
    /** valori proposti per un evento nuovo (dal clic nella griglia) */
    data?: string;
    inizio?: string;
    spazio?: string;
    fatto: (messaggio: string, avvisi: string[]) => void;
    annullato: () => void;
  } = $props();

  const iniz = untrack(() => $state.snapshot({ evento, data, inizio, spazio }));
  const fineDefault = (h: string) => {
    const [o, m] = h.split(':').map(Number);
    return `${String(Math.min(o + 1, 23)).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
  };
  let tipo = $state<TipoEvento>(iniz.evento?.tipo ?? 'LEZIONE_PRIVATA');
  let titolo = $state(iniz.evento?.titolo ?? '');
  let giorno = $state(iniz.evento?.data ?? iniz.data);
  let fineGiorno = $state(iniz.evento?.dataFine ?? '');
  let oraInizio = $state(iniz.evento ? ora(iniz.evento.inizio) : iniz.inizio);
  let oraFine = $state(iniz.evento ? ora(iniz.evento.fine) : fineDefault(iniz.inizio));
  let scelti = $state<string[]>(iniz.evento?.spazi ?? (iniz.spazio ? [iniz.spazio] : []));
  let istruttoreId = $state(iniz.evento?.istruttore?.id ?? '');
  let note = $state(iniz.evento?.note ?? '');
  let tuttoIlGiorno = $state(iniz.evento ? ora(iniz.evento.inizio) === '00:00' && ora(iniz.evento.fine) === '23:59' : false);
  let errore = $state('');
  let lavoro = $state(false);
  let confermaAnnulla = $state(false);

  const chiusura = $derived(tipo === 'CHIUSURA');
  // titolo proposto finché non lo si scrive
  const titoloProposto = $derived(TIPI_EVENTO[tipo]);

  $effect(() => {
    if (chiusura && !iniz.evento) tuttoIlGiorno = true;
  });

  async function salva(e: SubmitEvent) {
    e.preventDefault();
    errore = '';
    lavoro = true;
    const dati = {
      impiantoId, tipo, titolo: titolo.trim() || titoloProposto, data: giorno, dataFine: fineGiorno || null,
      inizio: tuttoIlGiorno ? '00:00' : oraInizio, fine: tuttoIlGiorno ? '23:59' : oraFine,
      spazi: scelti, istruttoreId: chiusura ? null : istruttoreId || null, note: note || null,
    };
    try {
      const esito = evento ? await api.modificaEvento(evento.id, dati) : await api.creaEvento(dati);
      fatto(evento ? 'Evento aggiornato.' : 'Evento aggiunto al piano.', esito.avvisi);
    } catch (err) {
      errore = messaggioErrore(err);
    } finally {
      lavoro = false;
    }
  }

  async function annulla() {
    if (!evento) return;
    if (!confermaAnnulla) return (confermaAnnulla = true);
    lavoro = true;
    try {
      await api.annullaEvento(evento.id);
      annullato();
    } catch (err) {
      errore = messaggioErrore(err);
    } finally {
      lavoro = false;
    }
  }
</script>

<form class="form" onsubmit={salva}>
  <label class="field"><span class="lbl">Tipo</span>
    <select class="inp" bind:value={tipo}>
      {#each Object.entries(TIPI_EVENTO) as [k, v] (k)}<option value={k}>{v}</option>{/each}
    </select>
  </label>
  <label class="field"><span class="lbl">Titolo</span><input class="inp" bind:value={titolo} maxlength="120" placeholder={titoloProposto} /></label>
  {#if tipo === 'LEZIONE_PRIVATA' || tipo === 'LEZIONE_PROVA'}
    <p class="hint nomargin">Per ora niente nomi di clienti nel titolo: la scheda cliente arriva con la fase clienti e accessi.</p>
  {/if}
  <div class="due">
    <label class="field"><span class="lbl">{fineGiorno || chiusura ? 'Dal' : 'Giorno'}</span><input class="inp" type="date" bind:value={giorno} required /></label>
    <label class="field"><span class="lbl">Fino al (facoltativo)</span><input class="inp" type="date" bind:value={fineGiorno} min={giorno} /></label>
  </div>
  <label class="riga-switch">
    <span>Tutto il giorno</span>
    <button type="button" class="switch" role="switch" aria-checked={tuttoIlGiorno} aria-label="Tutto il giorno" onclick={() => (tuttoIlGiorno = !tuttoIlGiorno)}><span></span></button>
  </label>
  {#if !tuttoIlGiorno}
    <div class="due">
      <label class="field"><span class="lbl">Inizio</span><input class="inp m" type="time" bind:value={oraInizio} required /></label>
      <label class="field"><span class="lbl">Fine</span><input class="inp m" type="time" bind:value={oraFine} required /></label>
    </div>
  {/if}
  <div class="field">
    <span class="lbl">{chiusura ? 'Cosa chiude' : 'Dove'}</span>
    <SceltaSpazi {spazi} bind:scelti vuotoSignifica={chiusura ? "Tutto l'impianto" : 'Nessuno spazio in particolare'} />
  </div>
  {#if !chiusura}
    <label class="field"><span class="lbl">Istruttore (facoltativo)</span>
      <select class="inp" bind:value={istruttoreId}>
        <option value="">—</option>
        {#each personale as p (p.id)}<option value={p.id}>{p.nome}</option>{/each}
      </select>
    </label>
  {/if}
  <label class="field"><span class="lbl">Note (facoltative)</span><input class="inp" bind:value={note} maxlength="500" /></label>
  {#if chiusura}<p class="muted small nomargin">Le lezioni che cadono nella chiusura risultano sospese, senza toccarle.</p>{/if}

  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
  <button class="btn btn-primary" disabled={lavoro}>{evento ? 'Salva' : 'Aggiungi al piano'}</button>
  {#if evento}
    <button type="button" class="btn btn-danger" disabled={lavoro} onclick={annulla}>{confermaAnnulla ? "Tocca di nuovo per annullare l'evento" : "Annulla l'evento"}</button>
  {/if}
</form>

<style>
  .form { display: flex; flex-direction: column; gap: var(--space-14); }
  select.inp { appearance: auto; }
</style>
