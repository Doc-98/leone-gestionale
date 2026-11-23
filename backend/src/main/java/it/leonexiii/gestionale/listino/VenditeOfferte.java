package it.leonexiii.gestionale.listino;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Quante volte è stata venduta un'offerta del listino (ADR 0029). Una vendita lascia una copia dell'importo: l'offerta
 * venduta non si elimina, si spegne. La implementano i moduli che registrano vendite (clienti, pagamenti).
 */
public interface VenditeOfferte {

    /**
     * Per ogni offerta richiesta, quante volte è stata venduta (le righe di questo modulo che la citano, anche
     * annullate); le offerte assenti nella mappa valgono 0. Ogni vendita si conta una volta sola: chi registra una
     * vendita in più righe (come gli addebiti di un abbonamento) conta solo quella principale.
     */
    Map<UUID, Long> vendite(Collection<UUID> offerte);
}
