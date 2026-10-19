# 0004 · Lettura dei bracciali con la Web NFC (Android)

**Contesto.** I bracciali dei tornelli contengono un chip NFC. Prova del 17/10/2026 su Nothing Phone 3 con
Chrome: seriale di 4 byte letto in modo stabile, nessun dato salvato nel chip.

**Decisione.** La PWA legge il seriale con la Web NFC (`NDEFReader`). Il seriale viene normalizzato in
esadecimale maiuscolo nell'ordine di lettura e associato a una persona tramite la tabella `credenziale`
(unico tra le credenziali attive).

**Conseguenze.** Funziona solo su Android con Chrome: la postazione atrio usa un dispositivo Android.
Su iPhone la lettura richiederebbe un'app nativa (fuori dallo scopo per ora). Va verificato in che formato
il gestionale attuale salva lo stesso codice, per importare i bracciali esistenti.

**Aggiornamento (19/10/2026).** Al tornello e in segreteria ci sono già lettori NFC fissi: restano lo strumento
principale. La lettura dal telefono serve per comodità, lontano dalle postazioni (ADR 0009).

**Formato confermato (19/10/2026).** Il gestionale attuale salva il seriale in **decimale a 10 cifre** nell'ordine di
lettura: `0c:7a:5c:ae` → `0209345710`. Il nuovo gestionale salva l'esadecimale (`0C7A5CAE`) e converte in automatico
un codice di dieci cifre, quindi funzionano sia il telefono sia i lettori che scrivono il decimale, e i braccialetti
esistenti si importano senza ricodificarli. La scheda mostra anche il decimale.
