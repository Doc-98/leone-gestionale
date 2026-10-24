# 0019 · Sistema visivo: icone al posto dei link-testo, quattro forme di azione

**Contesto.** Il gestionale ha preso da ClockWork il sistema visivo «Corsia», ma crescendo si era riempito di
link-testo usati come pulsanti («Modifica», «Annulla», «Togli», «Spegni», «Conferma: revoca»…): fino a cinque per
riga negli elenchi, tutti dello stesso verde, difficili da distinguere e da colpire, con le frecce disegnate come
caratteri (‹ › ↑ ↓ ×). Ogni schermata reinventava anche la conferma in due clic con una propria variabile di stato.

**Decisione.**

- **Le azioni hanno quattro forme**, dalla più pesante: pulsante grande (`.btn`), pulsante piccolo con icona
  (`.btn-sm`), pulsante-icona (`Azione.svelte`), link solo dentro il testo. Le azioni di riga sono pulsanti-icona;
  i link-testo come pulsanti non si usano più.
- **Un set di icone nostro** (`src/lib/icone.ts`), disegnato a mano sulla griglia di ClockWork (24×24, tratto 1,8),
  con nomi italiani tipizzati (`NomeIcona`). Niente librerie di icone, niente emoji, niente frecce di testo.
- **`Azione.svelte`** fa una cosa sola e la fa sempre uguale: icona, etichetta obbligatoria (nome accessibile e
  suggerimento), tono (`cloro` aggiunge, `danger` toglie), conferma in due tocchi (`conferma="Elimina?"`, si
  disarma da sola), interruttore (`premuto` → `aria-pressed`), link (`href`).
- **I contrasti diventano un test** (`contrasto.test.ts`, come in ClockWork), comprese le icone dei pulsanti-icona.
- Il brand book è in `docs/design-system/README.md`, con una copia navigabile tra gli artifact.

**Conseguenze.** Le righe sono più corte e si leggono meglio; l'etichetta, non più limitata a una parola a vista, nomina
la cosa («Togli Giulia Rossi dalla lista d'attesa»). Il costo è che un'icona va imparata: per questo il suggerimento
al passaggio del mouse, i nomi che dicono il significato e pochi gesti ricorrenti (matita, cestino, annulla, spegni).
Le conferme che hanno bisogno di altre scelte prima del secondo clic (il recesso in «Termina oggi») restano un piccolo
riquadro con i loro pulsanti.
