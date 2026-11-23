package it.leonexiii.gestionale.pagamenti;

import java.util.Map;
import java.util.UUID;

/**
 * Chi sa di soldi di un metodo ancora «aperti» fuori dai pagamenti lo dice qui, e il metodo non si elimina finché ci sono.
 * Dice anche se c'è una cassa aperta, perché finché lo è un metodo non entra né esce dal cassetto.
 * Lo implementa la cassa (il modulo pagamenti non conosce le casse).
 */
public interface ControlloMetodi {

    /** Per ogni metodo con entrate o uscite in una cassa non ancora chiusa: il nome della prima di queste casse. */
    Map<UUID, String> metodiInCassaNonChiusa();

    /** C'è almeno una sessione di cassa aperta (non chiusa): un metodo non può cambiare il suo essere «nel cassetto». */
    boolean cassaAperta();

    /** La cassa esiste ed è accesa: un movimento che arriva da una cassa spenta o eliminata va nella principale. */
    boolean cassaAccesa(UUID cassaId);
}
