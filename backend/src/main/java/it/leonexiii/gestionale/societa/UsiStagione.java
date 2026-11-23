package it.leonexiii.gestionale.societa;

import java.util.List;

/**
 * Ciò che usa una stagione e ne impedisce l'eliminazione (ADR 0029). Ogni modulo che si appoggia alle stagioni lo
 * implementa (listino, clienti, pianovasca): un modulo non legge le tabelle di un altro, e {@link Societa} chiede a tutti.
 * Per l'ordine nel messaggio si usa {@code @Order} sull'implementazione.
 */
public interface UsiStagione {

    /** Ciò che usa la stagione e ne impedisce l'eliminazione; vuoto se niente. */
    List<Uso> usi(Societa.Stagione stagione);

    /**
     * Ciò che conserva il nome della stagione (come i tesseramenti, che ne tengono una copia scritta): finché c'è, la
     * stagione non si rinomina. Di solito niente.
     */
    default List<Uso> usiDelNome(Societa.Stagione stagione) {
        return List.of();
    }

    /**
     * Un uso, con il testo al plurale giusto («12 offerte nel listino»). {@code storico}: non si può togliere
     * (vendite, tesseramenti, esiti), a differenza di ciò che si può svuotare prima (le offerte del listino).
     */
    record Uso(String testo, boolean storico) {}
}
