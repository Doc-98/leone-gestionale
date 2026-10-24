/**
 * Modulo servizi (ADR 0018, parte 9): i servizi a crediti (phon, docce) con il prezzo in crediti e gli usi gratuiti al
 * giorno, i dispositivi (le macchine e i lettori del saldo, ognuno con la sua chiave) e gli utilizzi. Le macchine
 * chiamano l'API con il codice del braccialetto: il server risponde sì o no e scala i crediti dal borsellino della
 * persona. Un simulatore permette di provare tutto senza le macchine vere.
 */
package it.leonexiii.gestionale.servizi;
