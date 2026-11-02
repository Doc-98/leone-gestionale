<script lang="ts">
  import Icona from '../Icona.svelte';
  import { api } from '../../lib/api';
  import { iso } from '../../lib/date';
  import { euro } from '../../lib/listino';
  import { euro as euroCent } from '../../lib/date';
  import { sessione } from '../../lib/sessione.svelte';
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
        inCorso ? { k: 'Stagione in corso', v: `${inCorso.nome}${inCorso.quota != null ? ` · quota ${euro(inCorso.quota)}` : ''}` } : { k: 'Stagione in corso', v: 'nessuna' },
        gruppi ? { k: 'Gruppi di clienti', v: `${gruppi.filter((g) => g.attivo).length} · ${n(gruppi.reduce((s, g) => s + g.persone, 0), 'persona', 'persone')}` } : null);
      if (dati && ((!dati.partitaIva && !dati.codiceFiscale) || !dati.indirizzo))
        da({ titolo: 'Mancano dei dati per le ricevute', spiega: 'Servono la partita IVA (o il codice fiscale) e l\'indirizzo della società.', href: '#/societa/dati', icona: 'ricevuta' });
      if (!inCorso) avvisi['La società'] = 'Nessuna stagione in corso';
      if (inCorso && visibile('listino')) {
        const listino = await prova(() => api.listino(inCorso.id));
        if (listino) {
          const conta = (t: string) => listino.offerte.filter((o) => o.tipo === t && o.attiva).length;
          const attive = listino.regole.filter((r) => r.attiva).map((r) => ({ RIDUZIONE_TARDIVA: 'riduzione', RATE: 'rate', RECESSO: 'recesso', SOSPENSIONE: 'sospensione' })[r.tipo]);
          aggiungi('Prezzi e pagamenti', { k: `Listino ${inCorso.nome}`, v: `${n(conta('CORSO'), 'abbonamento', 'abbonamenti')} · ${conta('CARNET')} carnet` },
            { k: 'Regole attive', v: attive.length ? attive.join(', ') : 'nessuna' });
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
      if (c) aggiungi('La società', { k: 'Chiusure in calendario', v: String(c.chiusure.length) });
    }
    if (visibile('metodi')) {
      const metodi = await prova(api.metodiPagamento);
      if (metodi) {
        const attivi = metodi.filter((m) => m.attivo);
        const daInc = attivi.filter((m) => m.daIncassare).map((m) => m.nome.toLowerCase());
        aggiungi('Prezzi e pagamenti', { k: 'Metodi di pagamento', v: `${attivi.length}${daInc.length ? ` · ${daInc.join(', ')} da incassare` : ''}` });
      }
    }
    if (visibile('regole')) {
      const regole = await prova(api.regole);
      if (regole) aggiungi('Clienti e accessi', { k: 'Controlli del tornello', v: `${regole.filter((r) => r.attiva).length} attivi su ${regole.length}` });
    }
    if (visibile('promemoria')) {
      const p = await prova(api.promemoria);
      if (p) {
        aggiungi('Clienti e accessi', { k: 'Promemoria email', v: `${n(p.regole.filter((r) => r.attivo).length, 'attivo', 'attivi')} · ${p.orario}` });
        if (!p.postaConfigurata) {
          avvisi['Clienti e accessi'] = 'Posta non configurata: i promemoria non partono';
          da({ titolo: 'I promemoria email non partono', spiega: 'Il server di posta non è configurato: le email si registrano ma non arrivano a nessuno.', href: '#/promemoria', icona: 'promemoria' });
        }
      }
    }
    if (visibile('servizi')) {
      const [regole, servizi, dispositivi] = await Promise.all([prova(api.regoleBorsellino), prova(api.servizi), prova(api.dispositivi)]);
      if (regole) aggiungi('Clienti e accessi', { k: 'Un credito vale', v: `${euro(regole.valoreCredito)} · cauzione ${euro(regole.cauzione)}` });
      if (servizi) aggiungi('Clienti e accessi', { k: 'Servizi a crediti', v: servizi.filter((s) => s.attivo).map((s) => s.nome.toLowerCase()).join(', ') || 'nessuno' });
      const mai = dispositivi?.filter((d) => d.attivo && !d.ultimoContatto).length ?? 0;
      if (mai) da({ titolo: `${n(mai, 'macchina non si è', 'macchine non si sono')} mai collegate`, spiega: 'Hanno la loro chiave ma non hanno ancora parlato con il gestionale.', href: '#/servizi/macchine', icona: 'macchine' });
    }
    if (visibile('postazioni')) {
      const [mansioni, tariffe] = await Promise.all([prova(api.mansioni), prova(api.tariffe)]);
      if (mansioni) {
        aggiungi('Staff', { k: 'Mansioni', v: `${mansioni.length} · ${n(mansioni.reduce((s, m) => s + m.postazioni.filter((p) => p.attiva).length, 0), 'postazione', 'postazioni')}` });
        const valida = (t: { dal: string; al: string | null }) => t.dal <= oggi && (!t.al || t.al >= oggi);
        for (const m of mansioni.slice(0, 2)) {
          const t = tariffe?.find((x) => x.mansioneId === m.id && !x.persona && valida(x));
          aggiungi('Staff', { k: `Tariffa ${m.nome.toLowerCase()}`, v: t ? `${euroCent(t.euroOra)}/h` : 'manca' });
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
        aggiungi('Sistema', { k: 'Account attivi', v: `${attivi.length} · ${attivi.filter((a) => !a.ultimoAccesso).length} mai entrati` });
      }
      if (ruoli) aggiungi('Sistema', { k: 'Ruoli', v: ruoli.ruoli.map((r) => r.nome.toLowerCase()).join(', ') });
    }
  }
  carica();
</script>

{#if telefono}
  {#if sistemare.length}
    <div class="card da-tel" role="status">
      <b>{n(sistemare.length, 'cosa da sistemare', 'cose da sistemare')}</b>
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
          <span class="t"><b>{s.titolo}</b><span>{s.spiega}</span></span>
          <a class="mini-btn" href={s.href}>Apri</a>
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
            {#each fatti[a.nome] as f (f.k)}<li><span class="k">{f.k}</span><span class="v">{f.v}</span></li>{/each}
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
  .sistemare .t span { font-size: var(--text-sm); color: var(--muted); }
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
  .fatti li { display: flex; align-items: baseline; gap: var(--space-8); font-size: var(--text-md); }
  .fatti .k { color: var(--muted); flex: 1; min-width: 0; white-space: nowrap; }
  .fatti .v { font-weight: 600; text-align: right; }
  .avvisa { display: flex; gap: var(--space-8); align-items: flex-start; padding: var(--space-8) var(--space-10); border-radius: var(--radius-sm); background: var(--warn-bg);
    color: var(--warn); font-size: var(--text-sm); font-weight: 600; }
  .avvisa :global(.ic) { width: 16px; height: 16px; margin-top: 1px; flex-shrink: 0; }
  .da-tel { display: flex; flex-direction: column; gap: var(--space-4); padding: var(--space-12) var(--space-14); background: var(--warn-bg); border-color: var(--warn-line); color: var(--warn); }
  .da-tel a { display: flex; align-items: center; justify-content: space-between; min-height: 36px; color: var(--warn); text-decoration: none; font-size: var(--text-sm); font-weight: 600; }
  .da-tel :global(.chev) { width: 16px; height: 16px; }
</style>
