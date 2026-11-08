package it.leonexiii.gestionale.pagamenti;

import java.util.UUID;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * La cassa da cui passa un pagamento (ADR 0018, parte 11): il computer la dice nell'intestazione «X-Cassa» di ogni
 * richiesta (si sceglie una volta per computer). Senza, o fuori da una richiesta (compiti notturni), è vuota: il
 * pagamento va nella cassa principale.
 */
final class CassaDellaRichiesta {

    static final String INTESTAZIONE = "X-Cassa";

    private CassaDellaRichiesta() {}

    static UUID id() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes r)) return null;
        String h = r.getRequest().getHeader(INTESTAZIONE);
        if (h == null || h.isBlank()) return null;
        try {
            return UUID.fromString(h.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
