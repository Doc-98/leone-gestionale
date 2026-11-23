<script lang="ts">
  import Fumetto from '../Fumetto.svelte';
  import Icona from '../Icona.svelte';
  import { api, type TipoRegolaPrezzo } from '../../lib/api';
  import { iso } from '../../lib/date';
  import { euro } from '../../lib/numeri';
  import { sessione } from '../../lib/sessione.svelte';
  import { tema } from '../../lib/tema.svelte';
  import type { NomeIcona } from '../../lib/icone';

  /**
   * La panoramica delle impostazioni (ADR 0024): in cima le cose da sistemare, poi un riquadro per area con i valori
   * che contano. Ogni dato si legge solo se chi guarda ha il permesso per vederlo; gli altri restano fuori.
   */
  interface Voce { id: string; nome: string; icona: NomeIcona }
  let { aree, telefono = false }: { aree: { nome: string; voci: Voce[] }[]; telefono?: boolean } = $props();

  type Fatto = { k: string; v: string };
  type Sistemare = { titolo: string; spiega: string; href: string; icona: NomeIcona };
  let fatti = $state<Record<string, Fatto[]>>({});
  let avvisi = $state<Record<string, string>>({});
  let sistemare = $state<Sistemare[]>([]);
  /** Le regole di prezzo che esistono, per dire «N accese su …» */
  const TIPI_REGOLA_PREZZO: TipoRegolaPrezzo[] = ['RIDUZIONE_TARDIVA', 'RECESSO', 'RATE', 'SOSPENSIONE'];
  const oggi = iso(new Date());

  const ICONE_AREA: Record<string, NomeIcona> = { 'La società': 'societa', 'Prezzi e pagamenti': 'listino', 'Clienti e accessi': 'tornello', Staff: 'postazioni', Sistema: 'ruoli' };
  const visibile = (id: string) => aree.some((a) => a.voci.some((v) => v.id === id));
  const n = (x: number, uno: string, tanti: string) => `${x} ${x === 1 ? uno : tanti}`;

  function aggiungi(area: string, ...f: (Fatto | null)[]) {
    fatti[area] = [...(fatti[area] ?? []), ...f.filter((x): x is Fatto => !!x)];
  }
  const da = (s: Sistemare) => (sistemare = [...sistemare, s]);

  async function carica() {
    const prova = async <T,>(f: () => Promise<T>): Promise<T | null> => { try { return await f(); } catch { return null; } };

    if (visibile('societa')) {
      const [stagioni, gruppi, dati] = await Promise.all([prova(api.stagioni), prova(api.gruppi), prova(api.societa)]);
      const inCorso = stagioni?.find((s) => s.dal <= oggi && oggi <= s.al);
      aggiungi('La società',
        inCorso ? { k: 'Stagione', v: `${inCorso.nome}${inCorso.quota != null ? ` · quota ${euro(inCorso.quota)}` : ''}` } : { k: 'Stagione', v: 'nessuna in corso' },
        gruppi ? { k: 'Gruppi', v: `${gruppi.filter((g) => g.attivo).length} · ${n(gruppi.reduce((s, g) => s + g.persone, 0), 'persona', 'persone')}` } : null);
      if (dati && ((!dati.partitaIva && !dati.codiceFiscale) || !dati.indirizzo))
        da({ titolo: 'Mancano dei dati per le ricevute', spiega: 'Servono la partita IVA (o il codice fiscale) e l\'indirizzo della società.', href: '#/societa/dati', icona: 'ricevuta' });
      if (!inCorso) avvisi['La società'] = 'Nessuna stagione in corso';
      if (inCorso && visibile('listino')) {
        const listino = await prova(() => api.listino(inCorso.id));
        if (listino) {
          const conta = (t: string) => listino.offerte.filter((o) => o.tipo === t && o.attiva).length;
          const accese = listino.regole.filter((r) => r.attiva && TIPI_REGOLA_PREZZO.includes(r.tipo)).length;
          aggiungi('Prezzi e pagamenti', { k: 'Listino', v: `${n(conta('CORSO'), 'abbonamento', 'abbonamenti')} · ${conta('CARNET')} carnet` },
            { k: 'Regole di prezzo', v: accese ? `${accese} accese su ${TIPI_REGOLA_PREZZO.length}` : 'nessuna accesa' });
        }
        // la stagione dopo, se comincia entro un anno, deve avere il suo listino
        const dopo = stagioni?.filter((s) => s.dal > oggi).sort((a, b) => a.dal.localeCompare(b.dal))[0];
        if (dopo) {
          const l = await prova(() => api.listino(dopo.id));
          if (l && !l.offerte.length) {
            avvisi['Prezzi e pagamenti'] = `Manca il listino della stagione ${dopo.nome}`;
            da({ titolo: `La stagione ${dopo.nome} non ha ancora un listino`, spiega: 'Puoi copiare quello di quest\'anno e aumentare i prezzi in un colpo.', href: '#/listino', icona: 'listino' });
          }
        }
      }
    }
    if (visibile('calendario')) {
      const stagioni = await prova(api.stagioni);
      const s = stagioni?.find((x) => x.dal <= oggi && oggi <= x.al);
      const c = s ? await prova(() => api.chiusure(s.dal, s.al)) : null;
      if (c) aggiungi('La società', { k: 'Chiusure', v: String(c.chiusure.length) });
    }
    if (visibile('metodi')) {
      const metodi = await prova(api.metodiPagamento);
      if (metodi) {
        const attivi = metodi.filter((m) => m.attivo);
        const daInc = attivi.filter((m) => m.daIncassare).length;
        aggiungi('Prezzi e pagamenti', { k: 'Metodi', v: `${attivi.length}${daInc ? ` · ${daInc} da incassare` : ''}` });
      }
    }
    if (visibile('ricevute')) {
      const r = await prova(api.impostazioniRicevute);
      const uso = r?.serie.find((x) => x.attiva && x.tipo === 'RICEVUTA');
      if (r) aggiungi('Prezzi e pagamenti', { k: 'Ricevute', v: uso ? `prossima ${uso.prossima}` : 'nessuna serie accesa' });
      if (r?.mancaIntestazione.length)
        da({ titolo: 'Alle ricevute manca l\'intestazione', spiega: `Nei dati della società manca: ${r.mancaIntestazione.join(', ')}.`, href: '#/societa', icona: 'ricevuta' });
    }
    if (visibile('cassa')) {
      const c = await prova(api.cassa);
      if (c) {
        const i = c.impostazioni;
        const casse = i.casse === 'UNICA' ? 'una cassa' : `${c.casse.filter((x) => x.attiva).length} casse`;
        const fondo = i.fondo === 'FISSO' ? `fondo ${euro(i.importoFondo)}` : i.fondo === 'RIPORTATO' ? 'fondo riportato' : 'fondo contato';
        aggiungi('Prezzi e pagamenti', { k: 'Cassa', v: `${casse} · ${fondo}` });
      }
    }
    if (visibile('regole')) {
      const regole = await prova(api.regole);
      if (regole) aggiungi('Clienti e accessi', { k: 'Controlli', v: `${regole.filter((r) => r.attiva).length} attivi su ${regole.length}` });
    }
    if (visibile('promemoria')) {
      const p = await prova(api.promemoria);
      if (p) {
        aggiungi('Clienti e accessi', { k: 'Promemoria', v: `${n(p.regole.filter((r) => r.attivo).length, 'attivo', 'attivi')} · ${p.orario}` });
        if (!p.postaConfigurata) {
          avvisi['Clienti e accessi'] = 'Posta non configurata: i promemoria non partono';
          da({ titolo: 'I promemoria email non partono', spiega: 'Il server di posta non è configurato: le email si registrano ma non arrivano a nessuno.', href: '#/promemoria', icona: 'promemoria' });
        }
      }
    }
    if (visibile('servizi')) {
      const [regole, servizi, dispositivi] = await Promise.all([prova(api.regoleBorsellino), prova(api.servizi), prova(api.dispositivi)]);
      if (regole) aggiungi('Clienti e accessi', { k: 'Credito', v: `${euro(regole.valoreCredito)} · cauzione ${euro(regole.cauzione)}` });
      if (servizi) aggiungi('Clienti e accessi', { k: 'Servizi', v: servizi.filter((s) => s.attivo).map((s) => s.nome.toLowerCase()).join(', ') || 'nessuno' });
      // con i crediti sul braccialetto le macchine non si collegano (ADR 0025): non è una cosa da sistemare
      const mai = regole?.creditiSu === 'BRACCIALETTO' ? 0 : dispositivi?.filter((d) => d.attivo && !d.ultimoContatto).length ?? 0;
      if (regole?.creditiSu === 'BRACCIALETTO') aggiungi('Clienti e accessi', { k: 'Crediti', v: 'sul braccialetto' });
      if (mai) da({ titolo: `${n(mai, 'macchina non si è', 'macchine non si sono')} mai collegate`, spiega: 'Hanno la loro chiave ma non hanno ancora parlato con il gestionale.', href: '#/servizi/macchine', icona: 'macchine' });
    }
    if (visibile('postazioni')) {
      const [mansioni, tariffe] = await Promise.all([prova(api.mansioni), prova(api.tariffe)]);
      if (mansioni) {
        aggiungi('Staff', { k: 'Mansioni', v: `${mansioni.length} · ${n(mansioni.reduce((s, m) => s + m.postazioni.filter((p) => p.attiva).length, 0), 'postazione', 'postazioni')}` });
        const valida = (t: { dal: string; al: string | null }) => t.dal <= oggi && (!t.al || t.al >= oggi);
        for (const m of mansioni.slice(0, 2)) {
          const t = tariffe?.find((x) => x.mansioneId === m.id && !x.persona && valida(x));
          aggiungi('Staff', { k: `Tariffa ${m.nome.toLowerCase()}`, v: t ? `${euro(t.euroOra)}/h` : 'manca' });
        }
        const senza = mansioni.filter((m) => !tariffe?.some((x) => x.mansioneId === m.id && !x.persona && valida(x)));
        if (tariffe && senza.length) {
          avvisi.Staff = `Senza tariffa: ${senza.map((m) => m.nome).join(', ')}`;
          da({ titolo: 'Ci sono mansioni senza tariffa', spiega: `${senza.map((m) => m.nome).join(', ')}: nel riepilogo le loro ore non hanno un compenso.`, href: '#/postazioni', icona: 'tariffe' });
        }
      }
    }
    if (visibile('account')) {
      const [account, ruoli] = await Promise.all([sessione.puo('ACCOUNT') ? prova(api.account) : null, sessione.puo('RUOLI') ? prova(api.ruoli) : null]);
      if (account) {
        const attivi = account.filter((a) => a.attivo);
        aggiungi('Sistema', { k: 'Account', v: `${attivi.length} · ${attivi.filter((a) => !a.ultimoAccesso).length} mai entrati` });
      }
      if (ruoli) aggiungi('Sistema', { k: 'Ruoli', v: String(ruoli.ruoli.length) });
    }
    if (visibile('aspetto'))
      aggiungi('Sistema', { k: 'Tema', v: tema.scelta === 'sistema' ? `come il sistema (ora ${tema.effettivo})` : tema.scelta });
  }
  carica();
</script>

{#if telefono}
  {#if sistemare.length}
    <div class="card da-tel" role="status">
      <span class="tit-tel"><b>{n(sistemare.length, 'cosa da sistemare', 'cose da sistemare')}</b><Fumetto tema="le cose da sistemare">Cose che il gestionale ha notato e che conviene sistemare, per esempio dati mancanti o un listino da preparare. Tocca una riga per aprire la pagina giusta.</Fumetto></span>
      {#each sistemare as s (s.titolo)}<a href={s.href}>{s.titolo}<Icona nome="dx" class="chev" /></a>{/each}
    </div>
  {/if}
{:else}
  {#if sistemare.length}
    <section class="card sez" aria-label="Da sistemare">
      <div class="sez-t"><h2 class="grow">Da sistemare</h2><span class="spiega">{n(sistemare.length, 'cosa', 'cose')}</span></div>
      {#each sistemare as s (s.titolo)}
        <div class="sistemare">
          <span class="ic-w"><Icona nome={s.icona} /></span>
          <span class="t"><span class="tit"><b>{s.titolo}</b><Fumetto tema="perché va sistemato">{s.spiega}</Fumetto></span></span>
          <a class="btn btn-secondary btn-sm" href={s.href} aria-label={`Apri: ${s.titolo}`}>Apri</a>
        </div>
      {/each}
    </section>
  {/if}

  <div class="aree">
    {#each aree as a (a.nome)}
      <a class="card area" href={`#/${a.voci[0].id}`}>
        <span class="testa-a"><span class="ico-a"><Icona nome={ICONE_AREA[a.nome] ?? a.voci[0].icona} /></span><h2 class="grow">{a.nome}</h2><Icona nome="dx" class="chev" /></span>
        {#if fatti[a.nome]?.length}
          <ul class="fatti">
            {#each fatti[a.nome] as f (f.k)}<li><span class="k">{f.k}</span><span class="v" title={f.v}>{f.v}</span></li>{/each}
          </ul>
        {:else}
          <span class="muted small">{a.voci.map((v) => v.nome).join(' · ')}</span>
        {/if}
        {#if avvisi[a.nome]}<span class="avvisa"><Icona nome="avviso" />{avvisi[a.nome]}</span>{/if}
      </a>
    {/each}
  </div>
{/if}

<style>
  .sistemare { display: flex; align-items: center; gap: var(--space-12); padding: var(--space-12) var(--space-16); border-top: 1px solid var(--line-soft); }
  .sez-t + .sistemare { border-top: none; }
  .sistemare .t { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
  .sistemare .btn { text-decoration: none; }
  .sistemare .tit { display: flex; align-items: center; gap: var(--space-6); flex-wrap: wrap; }
  .ic-w { width: 32px; height: 32px; border-radius: 9px; display: grid; place-items: center; background: var(--warn-bg); color: var(--warn); flex-shrink: 0; }
  .ic-w :global(.ic) { width: 17px; height: 17px; }
  .aree { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: var(--space-14); }
  .area { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-16); text-decoration: none; color: var(--ink); }
  .area:hover { border-color: var(--line-strong); }
  .testa-a { display: flex; align-items: center; gap: var(--space-10); }
  .ico-a { width: 36px; height: 36px; border-radius: 10px; display: grid; place-items: center; background: var(--cloro-soft); color: var(--cloro-ink); flex-shrink: 0; }
  .ico-a :global(.ic) { width: 19px; height: 19px; }
  .area h2 { margin: 0; font-family: var(--font-display); font-size: 19px; font-weight: 800; }
  .area :global(.chev) { width: 18px; height: 18px; color: var(--chevron); }
  .fatti { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--space-6); }
  /* etichetta a sinistra, valore a destra: due colonne, così il valore lungo non passa mai sotto l'etichetta */
  .fatti li { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: center; column-gap: var(--space-12); font-size: var(--text-md); }
  .fatti .k { color: var(--muted); white-space: nowrap; }
  /* il valore lungo si tronca con i puntini; l'etichetta resta intera */
  .fatti .v { font-weight: 600; text-align: right; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .avvisa { display: flex; gap: var(--space-8); align-items: flex-start; padding: var(--space-8) var(--space-10); border-radius: var(--radius-sm); background: var(--warn-bg);
    color: var(--warn); font-size: var(--text-sm); font-weight: 600; }
  .avvisa :global(.ic) { width: 16px; height: 16px; margin-top: 1px; flex-shrink: 0; }
  .da-tel { display: flex; flex-direction: column; gap: var(--space-4); padding: var(--space-12) var(--space-14); background: var(--warn-bg); border-color: var(--warn-line); color: var(--warn); }
  .tit-tel { display: flex; align-items: center; gap: var(--space-6); }
  .da-tel a { display: flex; align-items: center; justify-content: space-between; min-height: 36px; color: var(--warn); text-decoration: none; font-size: var(--text-sm); font-weight: 600; }
  .da-tel :global(.chev) { width: 16px; height: 16px; }
</style>
