# 0012 · Account dello staff

**Contesto.** Finora gli account esistevano solo nei dati demo. Per usare il gestionale servono: creare l'account a
chi arriva, chiuderlo a chi se ne va, reimpostare una password dimenticata, cambiare la propria.

**Decisione.**

- Permesso **Account** (di base solo gli admin): elenco, nuovo account (persona nuova o già in anagrafica, con un
  ruolo iniziale facoltativo se si ha anche «Ruoli e permessi»), disattiva, riattiva, reimposta password, email.
- **Password temporanea** generata dal server (12 caratteri facili da dettare), mostrata **una volta sola** a chi crea
  o reimposta l'account, da comunicare di persona. Con la password temporanea si può solo cambiarla: il server rifiuta
  ogni altra richiesta finché non è cambiata (non basta l'interfaccia).
- **Disattivazione immediata**: lo stato dell'account si legge a ogni richiesta (con la cache di ADR 0010); un
  account disattivato perde subito la sessione su tutti i dispositivi. Niente si cancella.
- Password propria: serve quella attuale; almeno 10 caratteri, diversa dalla precedente, senza email o cognome.
- Si registra l'ultimo accesso. Solo un admin interviene sugli account degli admin; nessuno disattiva se stesso.
- L'invio della password temporanea via email arriverà quando il server di posta sarà configurato (ADR 0013).
