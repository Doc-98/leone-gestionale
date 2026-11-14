<script lang="ts">
  import { onDestroy } from 'svelte';
  import Icona from './Icona.svelte';
  import type { NomeIcona } from '../lib/icone';
  import { creaConferma, etichettaArmata } from '../lib/conferma';

  /**
   * Pulsante-icona: l'azione di una riga o di un'intestazione, senza testo a vista.
   * - `etichetta` dice cosa succede («Modifica la lezione»): diventa il nome per lo screen reader e il suggerimento al
   *   passaggio del mouse. L'icona è sempre aria-hidden.
   * - `conferma`: per le azioni che non si annullano. Il primo tocco arma il pulsante, che mostra la domanda a parole
   *   («Elimina?»); il secondo esegue. Si disarma da solo dopo 5 secondi o uscendo dal pulsante.
   * - `premuto`: per gli interruttori (QR mostrato, metodo da incassare): aggiunge aria-pressed e lo stato pieno.
   * - `href`: diventa un link (scaricare un file, aprire una scheda) con lo stesso aspetto.
   */
  let {
    icona,
    etichetta,
    onclick,
    href,
    download,
    disabled = false,
    tono,
    conferma,
    premuto,
    type = 'button',
    cornice = false,
  }: {
    icona: NomeIcona;
    etichetta: string;
    onclick?: () => unknown;
    href?: string;
    download?: boolean | string;
    disabled?: boolean;
    tono?: 'danger' | 'cloro';
    conferma?: string;
    premuto?: boolean;
    type?: 'button' | 'submit';
    cornice?: boolean;
  } = $props();

  let armata = $state(false);
  const due = creaConferma((v) => (armata = v));
  onDestroy(due.disarma);

  function clic(e: MouseEvent) {
    if (conferma && !due.tocca()) {
      e.preventDefault();
      return;
    }
    onclick?.();
  }
</script>

{#if href}
  <a class="azione" class:cornice class:danger={tono === 'danger'} class:cloro={tono === 'cloro'} {href} {download} aria-label={etichetta} title={etichetta}>
    <Icona nome={icona} />
  </a>
{:else}
  <button
    {type}
    class="azione"
    class:cornice
    class:danger={tono === 'danger'}
    class:cloro={tono === 'cloro'}
    class:armata
    aria-label={armata && conferma ? etichettaArmata(conferma) : etichetta}
    title={armata ? undefined : etichetta}
    aria-pressed={premuto}
    {disabled}
    onclick={clic}
    onblur={due.disarma}
  >
    {#if armata}<span>{conferma}</span>{:else}<Icona nome={icona} />{/if}
  </button>
{/if}
