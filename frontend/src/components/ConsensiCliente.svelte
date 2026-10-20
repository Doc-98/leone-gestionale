<script lang="ts">
  import { api, type Consenso, type SchedaCliente, type TipoConsenso } from '../lib/api';
  import { dataBreve, dataDi, iso } from '../lib/date';

  /**
   * I consensi privacy firmati sul modulo cartaceo: trattamento dei dati per il servizio, comunicazioni promozionali
   * (con i canali) e foto e video. Per i minori firma un genitore. Un consenso revocato o scaduto resta nello storico.
   */
  let { cliente, esegui }: { cliente: SchedaCliente; esegui: (azione: () => Promise<unknown>, fatto: string) => Promise<boolean> } = $props();

  const TIPI: { id: TipoConsenso; nome: string; spiega: string }[] = [
    { id: 'TRATTAMENTO', nome: 'Trattamento dei dati', spiega: 'Informativa letta e dati trattati per il servizio (iscrizioni, certificati, tornello).' },
    { id: 'MARKETING', nome: 'Comunicazioni promozionali', spiega: 'Novità, offerte ed eventi, solo sui canali scelti.' },
    { id: 'IMMAGINI', nome: 'Foto e video', spiega: 'Foto e video durante corsi, gare e feste (es. sul sito o sui social).' },
  ];
  const oggi = iso(new Date());
  const id = $derived(cliente.persona.id);
  const minore = $derived(cliente.persona.eta != null && cliente.persona.eta < 18);

  /** Lo stato di ogni tipo: l'ultimo consenso valido, se c'è. */
  const valido = (t: TipoConsenso) => cliente.consensi.find((c) => c.tipo === t && c.valido);
  const storico = $derived(cliente.consensi.filter((c) => !c.valido));

  let conferma = $state('');
  let mostraStorico = $state(false);
  let nuovo = $state<{ tipo: TipoConsenso; firmatoDa: string; datoIl: string; validoFino: string; email: boolean; sms: boolean; cartaceo: boolean } | null>(null);

  function apri(tipo: TipoConsenso) {
    const t = cliente.famiglia.tutori[0];
    const firmatario = minore && t ? `${t.nome} ${t.cognome} (${t.relazione === 'PADRE' ? 'padre' : t.relazione === 'MADRE' ? 'madre' : 'tutore'})`
      : `${cliente.persona.nome} ${cliente.persona.cognome}`;
    nuovo = { tipo, firmatoDa: firmatario, datoIl: oggi, validoFino: tipo === 'IMMAGINI' ? cliente.stagioneProposta.al : '', email: true, sms: false, cartaceo: false };
  }

  async function salva() {
    const n = nuovo!;
    const ok = await esegui(() => api.nuovoConsenso(id, { tipo: n.tipo, firmatoDa: n.firmatoDa.trim() || null, datoIl: n.datoIl || null, validoFino: n.validoFino || null,
      email: n.tipo === 'MARKETING' && n.email, sms: n.tipo === 'MARKETING' && n.sms, cartaceo: n.tipo === 'MARKETING' && n.cartaceo }), 'Consenso registrato.');
    if (ok) nuovo = null;
  }

  function revoca(c: Consenso) {
    if (conferma !== c.id) return void (conferma = c.id);
    conferma = '';
    esegui(() => api.revocaConsenso(c.id), 'Consenso revocato da oggi.');
  }

  const canali = (c: Consenso) => [c.email && 'email', c.sms && 'SMS', c.cartaceo && 'posta'].filter(Boolean).join(', ');
  const nomeTipo = (t: TipoConsenso) => TIPI.find((x) => x.id === t)!.nome;
</script>

<section class="card blocco">
  <h3>Consensi privacy</h3>
  <ul class="righe">
    {#each TIPI as t (t.id)}
      {@const c = valido(t.id)}
      <li class="cons">
        <div class="riga">
          <span class="tag {c ? 't-soft' : t.id === 'TRATTAMENTO' ? 't-warn' : 't-altro'}">{c ? 'Sì' : t.id === 'TRATTAMENTO' ? 'Manca' : 'No'}</span>
          <strong class="grow">{t.nome}</strong>
          {#if c}
            <button class="link small rosso" onclick={() => revoca(c)}>{conferma === c.id ? 'Conferma: revoca' : 'Revoca'}</button>
          {:else if !nuovo}
            <button class="link small" onclick={() => apri(t.id)}>Registra</button>
          {/if}
        </div>
        {#if c}
          <div class="muted small">Firmato{c.firmatoDa ? ` da ${c.firmatoDa}` : ''} il {dataBreve(c.datoIl)}{c.validoFino ? ` · vale fino al ${dataBreve(c.validoFino)}` : ''}{c.tipo === 'MARKETING' ? ` · via ${canali(c)}` : ''}</div>
        {:else}
          <div class="muted small">{t.spiega}</div>
        {/if}
      </li>
    {/each}
  </ul>

  {#if nuovo}
    <form class="modulo" onsubmit={(e) => { e.preventDefault(); salva(); }}>
      <div class="lbl">{nomeTipo(nuovo.tipo)}</div>
      <input class="inp" bind:value={nuovo.firmatoDa} placeholder={minore ? 'Firmato da (un genitore)' : 'Firmato da'} aria-label="Firmato da" />
      <div class="due">
        <label class="field"><span class="lbl">Firmato il</span><input class="inp" type="date" bind:value={nuovo.datoIl} max={oggi} /></label>
        <label class="field"><span class="lbl">Vale fino al (facoltativo)</span><input class="inp" type="date" bind:value={nuovo.validoFino} min={nuovo.datoIl} /></label>
      </div>
      {#if nuovo.tipo === 'MARKETING'}
        <div class="chips" role="group" aria-label="Canali">
          <button type="button" class="chip" aria-pressed={nuovo.email} onclick={() => (nuovo!.email = !nuovo!.email)}>Email</button>
          <button type="button" class="chip" aria-pressed={nuovo.sms} onclick={() => (nuovo!.sms = !nuovo!.sms)}>SMS</button>
          <button type="button" class="chip" aria-pressed={nuovo.cartaceo} onclick={() => (nuovo!.cartaceo = !nuovo!.cartaceo)}>Posta</button>
        </div>
      {/if}
      <div class="row">
        <button type="button" class="btn btn-secondary grow" onclick={() => (nuovo = null)}>Annulla</button>
        <button class="btn btn-primary grow" disabled={!nuovo.datoIl || (nuovo.tipo === 'MARKETING' && !nuovo.email && !nuovo.sms && !nuovo.cartaceo)}>Registra</button>
      </div>
    </form>
  {/if}

  {#if storico.length}
    <button class="link small sx" onclick={() => (mostraStorico = !mostraStorico)}>{mostraStorico ? 'Nascondi lo storico' : `Storico (${storico.length})`}</button>
    {#if mostraStorico}
      {#each storico as c (c.id)}
        <div class="small muted">{nomeTipo(c.tipo)} · firmato il {dataBreve(c.datoIl)} · {c.revocatoIl ? `revocato il ${dataBreve(dataDi(c.revocatoIl))}` : c.validoFino && c.validoFino < oggi ? `scaduto il ${dataBreve(c.validoFino)}` : `dal ${dataBreve(c.datoIl)}`}</div>
      {/each}
    {/if}
  {/if}
</section>

<style>
  .blocco { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16); }
  h3 { margin: 0; font-size: var(--text-xl); }
  .righe { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
  .righe > li { padding: var(--space-8) 0; border-top: 1px solid var(--line-soft); }
  .righe > li:first-child { border-top: none; }
  .cons { display: flex; flex-direction: column; gap: 2px; }
  .riga { display: flex; align-items: center; gap: var(--space-10); }
  .riga .tag { min-width: 56px; justify-content: center; }
  .modulo { display: flex; flex-direction: column; gap: var(--space-10); padding-top: var(--space-10); border-top: 1px solid var(--line); }
  .link { min-height: 32px; }
  .sx { align-self: flex-start; }
  .rosso { color: var(--danger); }
</style>
