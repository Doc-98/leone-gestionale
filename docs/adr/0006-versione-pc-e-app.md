# 0006 · Versione PC (Tauri) e versione app (PWA) con un'unica interfaccia

**Contesto.** Il gestionale va usato soprattutto sui PC della società (segreteria, coordinatori, admin):
è la versione principale. Lo staff lo usa dal telefono (turni, richieste, ore) e, in futuro, la postazione
atrio legge i bracciali. Indicazioni raccolte il 18/10/2026:

- i dati stanno probabilmente su **server della società**, non in cloud (da confermare con il loro tecnico);
- ai PC andranno collegati almeno un **lettore NFC da scrivania** e una **stampante**;
- i PC della segreteria sono **Windows**, moderni ma non potenti.

Alternative valutate:

| | PWA installata da Edge/Chrome | Programma Tauri | Programma nativo (JavaFX / .NET) |
|---|---|---|---|
| Installazione | dal browser | installer .msi/.exe | installer |
| Lettore NFC PC/SC, stampa silenziosa | no | sì | sì |
| Interfaccia condivisa con l'app del telefono | sì | sì | no (due interfacce da mantenere) |
| Peso | nessuno | pochi MB (usa WebView2 di Windows) | medio |

**Decisione.**

- **Una sola interfaccia** (Svelte + TypeScript) con due disposizioni: **desktop** (menu laterale, tabelle
  ampie, pianificazione a tutta settimana) sopra i 1024 px, **mobile** (schede in basso) sotto.
- **PC**: l'interfaccia viene impacchettata con **Tauri 2** in un programma Windows (WebView2). La parte
  nativa (Rust) espone solo i servizi hardware: lettura del lettore NFC (PC/SC), stampa su una stampante
  scelta. Installer prodotto in CI su un runner Windows.
- **Telefono**: la stessa interfaccia come PWA.
- Il server (Spring Boot + PostgreSQL) può stare su un server della società in rete interna; i PC lo
  raggiungono via HTTPS. L'accesso da fuori (staff da casa) va concordato con il tecnico (VPN o proxy).

**Regole per lasciare aperta la strada a un client nativo.**

1. Nessuna regola di business nell'interfaccia: i controlli lato client servono solo a dare un messaggio
   prima; decide sempre il server.
2. Ogni funzione passa da un endpoint pubblico documentato nella specifica OpenAPI. Niente scorciatoie
   utilizzabili solo dal web.
3. L'hardware sta dietro un'interfaccia piccola nel frontend (`dispositivi`: leggi bracciale, stampa):
   Tauri, Web NFC o un futuro client nativo la implementano senza toccare il resto.
4. L'autenticazione resta utilizzabile anche da un programma non web (sessione con cookie oggi; per i
   dispositivi fissi un accesso con credenziale del dispositivo, previsto in fase 3).

**Conseguenze.**

- Backend, database e test non cambiano. Un eventuale passaggio a un client nativo costerebbe solo le
  schermate (il client delle API si genera dalla specifica OpenAPI).
- I PC richiedono un'installazione (concordata con il tecnico). Per non mostrare l'avviso di Windows
  «editore sconosciuto» servirà un certificato di firma del codice.
- Il programma carica l'interfaccia inclusa nell'installer; gli aggiornamenti arrivano con l'updater di
  Tauri (da attivare quando ci sarà un server di produzione).
- I lettori in «emulazione tastiera» funzionano anche senza Tauri, come ripiego.
