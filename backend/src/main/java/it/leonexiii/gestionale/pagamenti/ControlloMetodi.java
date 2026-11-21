package it.leonexiii.gestionale.pagamenti;

import java.util.Map;
import java.util.UUID;

/**
 * Chi sa di soldi di un metodo ancora «aperti» fuori dai pagamenti lo dice qui, e il metodo non si elimina finché ci sono.
 * Lo implementa la cassa (il modulo pagamenti non conosce le casse).
 */
public interface ControlloMetodi {

    /** Per ogni metodo con entrate o uscite in una cassa non ancora chiusa: il nome della prima di queste casse. */
    Map<UUID, String> metodiInCassaNonChiusa();
}
