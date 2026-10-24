<script lang="ts">
  import { api, messaggioErrore, type RegoleBorsellino } from '../lib/api';
  import { sessione } from '../lib/sessione.svelte';

  /** Le regole del borsellino e del braccialetto: ogni bivio è una scelta della società. */
  let r = $state<RegoleBorsellino | null>(null);
  let scade = $state(true);
  let errore = $state('');
  let messaggio = $state('');
  const puo = $derived(sessione.puo('IMPOSTAZIONI'));

  api.regoleBorsellino().then((x) => {
    r = x;
    scade = x.validitaEuroMesi != null;
  }).catch((e) => (errore = messaggioErrore(e)));

  async function salva() {
    errore = '';
    messaggio = '';
    try {
      r = await api.modificaRegoleBorsellino({ ...r!, valoreCredito: Number(r!.valoreCredito), ricaricaMinima: Number(r!.ricaricaMinima), cauzione: Number(r!.cauzione),
        validitaEuroMesi: scade ? Number(r!.validitaEuroMesi ?? 12) : null });
      messaggio = 'Regole salvate.';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
</script>

{#if r}
  <form class="pagina" onsubmit={(e) => { e.preventDefault(); salva(); }}>
    {#if messaggio}<p class="msg ok nomargin" role="status">{messaggio}</p>{/if}
    {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}
    <fieldset disabled={!puo}>
      <section class="card blocco">
        <h3>Crediti di phon e docce</h3>
        <div class="due">
          <label class="field"><span class="lbl">Un credito vale (€)</span><input class="inp m" type="number" min="0.01" step="0.01" bind:value={r.valoreCredito} /></label>
          <label class="field"><span class="lbl">Ricarica minima (€)</span><input class="inp m" type="number" min="0" step="0.01" bind:value={r.ricaricaMinima} /></label>
        </div>
        <p class="muted small nomargin">Quanti crediti costa ogni servizio (e gli usi gratuiti al giorno) si decide in «Phon, docce e lettori».</p>
      </section>

      <section class="card blocco">
        <h3>Credito in euro</h3>
        <p class="muted small nomargin">Ci finiscono i rimborsi (recesso, sospensione, cauzione o crediti restituiti nel borsellino); si usa per pagare con il metodo «Borsellino».</p>
        <label class="spunta"><input type="checkbox" bind:checked={scade} /> Scade dopo
          <input class="inp m num" type="number" min="1" max="120" bind:value={r.validitaEuroMesi} disabled={!scade} /> mesi</label>
        <label class="spunta"><input type="checkbox" bind:checked={r.trasferibile} /> Si può trasferire a un familiare (genitori, figli, fratelli)</label>
        <label class="spunta"><input type="checkbox" bind:checked={r.restituibile} /> Si può restituire in denaro (serve anche il permesso «Rimborsi e correzioni del borsellino»)</label>
      </section>

      <section class="card blocco">
        <h3>Braccialetto</h3>
        <label class="field"><span class="lbl">Cauzione alla consegna (€, 0 = nessuna)</span><input class="inp m num" type="number" min="0" step="0.01" bind:value={r.cauzione} /></label>
        <label class="field"><span class="lbl">Alla riconsegna la cauzione (e i crediti) si restituiscono</span>
          <select class="inp" bind:value={r.cauzioneRiconsegna}>
            <option value="A_SCELTA">A scelta: in denaro o nel borsellino</option>
            <option value="DENARO">Sempre in denaro</option>
            <option value="BORSELLINO">Sempre nel borsellino</option>
          </select>
        </label>
        <label class="spunta"><input type="checkbox" bind:checked={r.creditiRiconsegna} /> Riconsegnando l'ultimo braccialetto si propone di rimborsare i crediti di phon e docce</label>
        <label class="field"><span class="lbl">Se il braccialetto si smarrisce, i crediti di phon e docce</span>
          <select class="inp" bind:value={r.creditiSmarrimento}>
            <option value="RESTANO">Restano alla persona (li ritrova sul nuovo braccialetto)</option>
            <option value="PERSI">Si perdono</option>
          </select>
        </label>
      </section>
      {#if puo}<div class="row"><button class="btn btn-primary">Salva le regole</button></div>{/if}
    </fieldset>
  </form>
{:else if errore}
  <p class="msg err">{errore}</p>
{/if}

<style>
  .pagina { display: flex; flex-direction: column; gap: var(--space-14); max-width: 760px; }
  fieldset { border: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--space-14); min-width: 0; }
  .blocco { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16); }
  h3 { margin: 0; font-size: var(--text-xl); }
  .spunta { display: flex; align-items: center; gap: var(--space-8); flex-wrap: wrap; }
  .num { width: 90px; }
  select.inp { appearance: auto; }
</style>
