<script lang="ts" generics="T extends { id: string }">
  import type { Snippet } from 'svelte';
  import { tick } from 'svelte';
  import Azione from './Azione.svelte';
  import Icona from './Icona.svelte';
  import StatoVuoto from './StatoVuoto.svelte';
  import { righeBlocco } from '../lib/blocco';
  import { annuncioSpostamento, postoDalPunto, sposta, spostaDiUno, stessoOrdine } from '../lib/riordina';

  /**
   * L'elenco modificabile (design system, «Elenco modificabile»; ADR 0028). Dentro la riga, da sinistra: la maniglia
   * (si trascina; a fuoco, frecce su e giù spostano di un posto), il contenuto, l'interruttore acceso/spento, il cestino
   * (due tocchi). In fondo la riga tratteggiata «Aggiungi un …». Elenco vuoto: «Nessun …» e sotto la stessa riga.
   * - Riga spenta: i valori restano leggibili (la pagina può attenuare solo il nome) e tutti i controlli restano attivi.
   * - `riordinabile` false: niente maniglia (default true: come nei metodi di pagamento).
   * - Il cestino si chiama «Elimina «X»» o «Rimuovi «X»», secondo `conferma` («Elimina?» o «Rimuovi?»).
   * - `modificabile` false: niente maniglia, interruttore, cestino né «Aggiungi»; una riga spenta porta il tag «Spento».
   * - Il riordino è ottimistico: l'ordine nuovo si vede subito; se `onriordina` fallisce (promessa rifiutata) torna quello
   *   di prima. L'ordine locale resta finché il genitore non passa `elementi` nuovi.
   * - `onelimina` può restituire (anche in una promessa) un testo: è il motivo per cui non si può eliminare, e compare sotto la riga.
   *   Va su più righe (`\n`) e ogni riga comincia con un attacco in grassetto fino ai due punti (`righeBlocco`).
   * - Lo spostamento da tastiera o con il dito è annunciato in una regione `role="status"`.
   */
  let {
    elementi,
    modificabile = true,
    riordinabile = true,
    nome,
    nomeElemento,
    articolo = 'un',
    apreFoglio = true,
    riga,
    onriordina,
    onaggiungi,
    onapri,
    onacceso,
    onelimina,
    haCestino = () => true,
    haInterruttore = () => true,
    acceso = () => true,
    testoAggiungi,
    testoVuoto,
    conferma = 'Elimina?',
  }: {
    elementi: T[];
    modificabile?: boolean;
    /** false: niente maniglia (l'ordine non si cambia) */
    riordinabile?: boolean;
    /** Il nome di una riga: entra nelle etichette («Sposta «Satispay»») */
    nome: (el: T) => string;
    /** «metodo» → «Aggiungi un metodo…» e «Nessun metodo.» */
    nomeElemento: string;
    /** «un» o «una» (e «Nessun»/«Nessuna») */
    articolo?: 'un' | 'una';
    /** «Aggiungi un metodo…» con i puntini quando apre un foglio */
    apreFoglio?: boolean;
    riga: Snippet<[T]>;
    onriordina?: (ids: string[]) => unknown;
    onaggiungi?: () => void;
    /** il tocco sul contenuto della riga (apre il foglio): senza, il contenuto non è un pulsante */
    onapri?: (el: T) => void;
    onacceso?: (el: T, acceso: boolean) => unknown;
    onelimina?: (el: T) => unknown;
    haCestino?: (el: T) => boolean;
    haInterruttore?: (el: T) => boolean;
    acceso?: (el: T) => boolean;
    testoAggiungi?: string;
    testoVuoto?: string;
    /** la domanda del secondo tocco: «Elimina?» (la cosa sparisce) o «Rimuovi?» (si scioglie un legame) */
    conferma?: string;
  } = $props();

  const uid = $props.id();
  /** L'ordine mostrato mentre aspetta il salvataggio, o durante il trascinamento; null = quello di `elementi`. */
  let locale = $state<string[] | null>(null);
  let trascinato = $state<string | null>(null);
  let annuncio = $state('');
  let blocchi = $state<Record<string, string>>({});
  let lista = $state<HTMLUListElement>();
  let bottoneAggiungi = $state<HTMLButtonElement>();
  const maniglie = new Map<string, HTMLButtonElement>();

  // Salvataggi del riordino: in coda, uno alla volta (le risposte non si scavalcano). L'ordine locale vale finché ce n'è
  // uno in corso; quando l'ultimo è finito torna quello di `elementi` (se il genitore l'ha aggiornato o se l'ultimo è fallito).
  let inCorso = 0;
  let elementiCambiati = false;
  let ultimoFallito = false;
  let ultimoMosso = '';
  let coda: Promise<unknown> = Promise.resolve();

  // quando il genitore passa elementi nuovi, vale il suo ordine (ma non a metà di un salvataggio)
  $effect(() => {
    elementi;
    if (inCorso === 0) locale = null;
    else elementiCambiati = true;
  });

  const mostrati = $derived.by(() => {
    if (!locale) return elementi;
    const per = new Map(elementi.map((e) => [e.id, e]));
    const nelOrdine = locale.map((id) => per.get(id)).filter((e): e is T => !!e);
    // un elemento arrivato dopo non si perde
    const resto = elementi.filter((e) => !locale!.includes(e.id));
    return [...nelOrdine, ...resto];
  });
  const idMostrati = $derived(mostrati.map((e) => e.id));

  /** «Elimina?» → «Elimina «X»»; «Rimuovi?» → «Rimuovi «X»» */
  const verbo = $derived(conferma.replace(/\?\s*$/, '').trim() || 'Elimina');
  const articoloNessuno = $derived(articolo === 'una' ? 'Nessuna' : 'Nessun');
  const scrittaAggiungi = $derived(testoAggiungi ?? `Aggiungi ${articolo} ${nomeElemento}${apreFoglio ? '…' : ''}`);
  const scrittaVuoto = $derived(testoVuoto ?? `${articoloNessuno} ${nomeElemento}.`);

  /** Lo screen reader legge anche due annunci uguali di fila: prima si svuota la regione. */
  async function annuncia(testo: string) {
    annuncio = '';
    await tick();
    annuncio = testo;
  }
  const pulisci = () => {
    if (Object.keys(blocchi).length) blocchi = {};
  };

  function salva(nuovo: string[], mosso: string) {
    locale = nuovo;
    ultimoMosso = mosso;
    inCorso++;
    const p = coda.then(async () => {
      try {
        await onriordina?.(nuovo);
        ultimoFallito = false;
      } catch {
        ultimoFallito = true;
      }
    });
    coda = p;
    p.then(async () => {
      inCorso--;
      if (inCorso > 0) return;
      const fallito = ultimoFallito;
      ultimoFallito = false;
      if (fallito || elementiCambiati) locale = null; // torna a quello di `elementi`
      elementiCambiati = false;
      if (fallito) {
        await tick();
        const el = elementi.find((x) => x.id === ultimoMosso);
        if (el) {
          annuncia(`Ordine non salvato: ${nome(el)} è tornato al posto ${elementi.indexOf(el) + 1}`);
          maniglie.get(el.id)?.focus();
        }
      }
    });
  }

  async function dalTasto(e: KeyboardEvent, el: T, i: number) {
    if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return;
    e.preventDefault();
    pulisci();
    const verso = e.key === 'ArrowUp' ? -1 : 1;
    const prima = idMostrati;
    const nuovo = spostaDiUno(prima, i, verso);
    if (stessoOrdine(nuovo, prima)) return;
    annuncia(annuncioSpostamento(nome(el), i + verso + 1, prima.length));
    salva(nuovo, el.id);
    await tick();
    maniglie.get(el.id)?.focus(); // spostare il nodo può togliere il fuoco: lo si rimette
  }

  /** Il trascinamento col puntatore o col dito: l'ordine si vede mentre si muove, si salva al rilascio. */
  let gesto: { id: string; da: number; righe: { top: number; bottom: number }[]; prima: string[]; pointerId: number } | null = null;

  function giu(e: PointerEvent, el: T, i: number) {
    if (e.button !== undefined && e.button > 0) return;
    if (!lista) return;
    const righe = [...lista.querySelectorAll<HTMLElement>(':scope > li[data-riga]')].map((li) => {
      const r = li.getBoundingClientRect();
      return { top: r.top, bottom: r.bottom };
    });
    pulisci();
    gesto = { id: el.id, da: i, righe, prima: idMostrati, pointerId: e.pointerId };
    trascinato = el.id;
    try { lista.setPointerCapture(e.pointerId); } catch { /* ambienti senza cattura */ }
  }
  function muovi(e: PointerEvent) {
    if (!gesto) return;
    const a = postoDalPunto(e.clientY, gesto.righe);
    locale = sposta(gesto.prima, gesto.da, a);
  }
  function su(e: PointerEvent) {
    if (!gesto) return;
    const g = gesto;
    gesto = null;
    trascinato = null;
    try { lista?.releasePointerCapture(g.pointerId); } catch { /* già rilasciata */ }
    const a = postoDalPunto(e.clientY, g.righe);
    const nuovo = sposta(g.prima, g.da, a);
    if (stessoOrdine(nuovo, g.prima)) {
      locale = null;
      return;
    }
    const el = elementi.find((x) => x.id === g.id);
    if (el) annuncia(annuncioSpostamento(nome(el), a + 1, g.prima.length));
    salva(nuovo, g.id);
  }
  function annulla() {
    if (!gesto) return;
    gesto = null;
    trascinato = null;
    locale = null;
  }

  async function elimina(el: T) {
    const i = idMostrati.indexOf(el.id);
    const vicino = idMostrati[i + 1] ?? idMostrati[i - 1];
    blocchi = {};
    let esito: unknown;
    try {
      esito = await onelimina?.(el);
    } catch (e) {
      esito = e instanceof Error ? e.message : 'Operazione non riuscita.';
    }
    if (typeof esito === 'string' && esito) {
      blocchi = { [el.id]: esito };
      // il cestino torna a riposo: il fuoco del tocco non deve lasciarlo «acceso» (la tastiera rimette l'anello da sé)
      const a = document.activeElement;
      if (a instanceof HTMLElement && !a.matches(':focus-visible')) a.blur();
      return;
    }
    // eliminata: il fuoco passa alla riga dopo (o prima, o a «Aggiungi») invece di perdersi
    await tick();
    if (elementi.some((x) => x.id === el.id)) return;
    (vicino ? fuocoDellaRiga(vicino) : bottoneAggiungi)?.focus();
  }

  /** Dove mettere il fuoco su una riga: la maniglia se c'è, altrimenti il contenuto che si apre, il cestino o il primo pulsante. */
  function fuocoDellaRiga(id: string): HTMLElement | null {
    const m = maniglie.get(id);
    if (m) return m;
    const li = [...(lista?.querySelectorAll<HTMLElement>(':scope > li[data-riga]') ?? [])].find((x) => x.dataset.id === id);
    return li?.querySelector<HTMLElement>('button.apri, .azione, button') ?? null;
  }

  function maniglia(nodo: HTMLButtonElement, id: string) {
    maniglie.set(id, nodo);
    return { destroy: () => maniglie.delete(id) };
  }
</script>

<ul class="elenco" bind:this={lista} onclickcapture={pulisci} onpointermove={muovi} onpointerup={su} onpointercancel={annulla}>
  {#each mostrati as el, i (el.id)}
    {@const spenta = !acceso(el)}
    {@const n = nome(el)}
    <li data-riga data-id={el.id} class:trascinata={trascinato === el.id}>
      <div class="r">
        {#if modificabile && riordinabile}
          <button
            type="button"
            class="azione maniglia"
            aria-label={`Sposta «${n}»`}
            aria-describedby={`${uid}-frecce`}
            use:maniglia={el.id}
            onkeydown={(e) => dalTasto(e, el, i)}
            onpointerdown={(e) => giu(e, el, i)}
          ><Icona nome="trascina" /></button>
        {/if}
        {#if modificabile && onapri}
          <button type="button" class="contenuto apri" aria-haspopup={apreFoglio ? 'dialog' : undefined} onclick={() => onapri(el)}>{@render riga(el)}</button>
        {:else}
          <div class="contenuto">{@render riga(el)}</div>
        {/if}
        {#if modificabile}
          <!-- larghezza fissa (interruttore + cestino): gli interruttori restano in colonna anche senza cestino, e la
               domanda «Elimina?» prende il posto dei due controlli senza cambiare la larghezza del contenuto -->
          <div class="ctl">
            {#if haInterruttore(el)}
              <button
                type="button"
                class="switch tap44"
                role="switch"
                aria-checked={!spenta}
                aria-label={`Acceso: «${n}»`}
                onclick={() => onacceso?.(el, spenta)}
              ><span></span></button>
            {/if}
            {#if haCestino(el)}
              <Azione icona="cestino" tono="danger" etichetta={`${verbo} «${n}»`} {conferma} onclick={() => elimina(el)} />
            {:else}
              <span class="posto-azione" aria-hidden="true"></span>
            {/if}
          </div>
        {:else if spenta}
          <span class="tag t-altro">Spento</span>
        {/if}
      </div>
      {#if blocchi[el.id]}
        <p class="blocco" role="alert">{#each righeBlocco(blocchi[el.id]) as r, k (k)}<span class="riga-b">{#if r.attacco}<b>{r.attacco}</b>{' '}{/if}{r.resto}{' '}</span>{/each}</p>
      {/if}
    </li>
  {:else}
    <StatoVuoto come="li" testo={scrittaVuoto} />
  {/each}
  {#if modificabile && onaggiungi}
    <li class="ultima">
      <button type="button" class="aggiungi" bind:this={bottoneAggiungi} onclick={onaggiungi}><Icona nome="piu" /><span>{scrittaAggiungi}</span></button>
    </li>
  {/if}
</ul>
{#if modificabile && riordinabile}<span class="sr" id={`${uid}-frecce`}>Frecce su e giù per spostare</span>{/if}
<span class="sr" role="status">{annuncio}</span>

<style>
  .elenco { list-style: none; margin: 0; padding: 0; }
  li[data-riga] { border-top: 1px solid var(--line-soft); }
  li[data-riga]:first-child { border-top: none; }
  .r { display: flex; align-items: center; gap: var(--space-8); min-height: 56px; padding: var(--space-6) 0; }
  .contenuto { flex: 1 1 auto; min-width: 0; display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-4) var(--space-8); }
  /* riga spenta: a farla tenue è la pagina, solo sul nome (i tag restano al contrasto pieno); i controlli restano pieni */
  .ctl { display: flex; align-items: center; justify-content: flex-end; gap: var(--space-8); width: calc(50px + var(--space-8) + 40px); flex-shrink: 0; }
  .ctl:has(:global(.azione.armata)) .switch { display: none; }
  .posto-azione { width: 40px; height: 40px; flex-shrink: 0; }
  .ctl :global(.azione.danger:hover:not(:disabled)) { background: var(--ferma-bg); color: var(--ferma-fg); }
  .apri { background: none; border: none; padding: var(--space-6) 0; margin: 0; font: inherit; color: inherit; text-align: left; cursor: pointer; border-radius: var(--radius-sm); }
  .apri:hover { color: var(--cloro-ink); }
  .maniglia { cursor: grab; touch-action: none; }
  /* la riga trascinata si stacca dalle altre (fondo, bordo, ombra) senza coprire i tag, che hanno i loro colori */
  .trascinata { background: var(--surface); border-radius: var(--radius-md); box-shadow: var(--shadow-seg); outline: 1px solid var(--line-strong); position: relative; z-index: 1; }
  .trascinata .maniglia { cursor: grabbing; color: var(--ink); }
  .blocco { margin: 0 0 var(--space-8); padding: var(--space-8) var(--space-12); border-radius: var(--radius-md); background: var(--ferma-bg); color: var(--ferma-fg); font-size: var(--text-sm); display: flex; flex-direction: column; gap: var(--space-4); }
  .blocco b { color: inherit; font-weight: 700; }
  .ultima { padding: var(--space-8) 0 var(--space-6); }
  .elenco > :global(.stato-vuoto) { border-radius: var(--radius-md); margin-bottom: var(--space-8); }
  .aggiungi {
    display: flex; align-items: center; justify-content: center; gap: var(--space-8); width: 100%; min-height: 48px;
    padding: 0 var(--space-12); background: transparent; border: 1px dashed var(--line-dashed); border-radius: var(--radius-md);
    color: var(--cloro-ink); font: inherit; font-weight: 600; font-size: var(--text-base); cursor: pointer;
  }
  .aggiungi span { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; min-width: 0; }
  .aggiungi :global(svg) { color: var(--cloro); }
  .aggiungi:hover { background: var(--cloro-soft); border-color: var(--cloro); }
</style>
