package it.leonexiii.gestionale.servizi;

import java.util.List;
import java.util.Map;

/**
 * Come sono scritti i crediti nella memoria del braccialetto (ADR 0025). Ogni marca di macchine ha il suo formato e la
 * sua crittografia: il resto del gestionale non li conosce, chiede a questa interfaccia. Se ne usa uno solo, scelto
 * con la proprietà {@code gestionale.braccialetti.formato}; uno nuovo si aggiunge come un'altra implementazione.
 */
interface FormatoBraccialetto {

    /** Cosa il lettore deve leggere: il tipo di chip, le chiavi dei settori e i blocchi. */
    record Accesso(String chip, List<ChiaveSettore> chiavi, List<Integer> blocchi) {}

    /** La chiave di un settore (MIFARE Classic: chiave A o B, 6 byte in esadecimale). */
    record ChiaveSettore(int settore, String tipo, String chiave) {}

    /** Il codice della proprietà (es. PROVA). */
    String codice();

    /** Il nome da mostrare nelle impostazioni. */
    String nome();

    /** Se si può già leggere e scrivere: un formato ancora sconosciuto dice di no, e spiega cosa manca in {@link #nota()}. */
    boolean pronto();

    String nota();

    Accesso accesso();

    /** I crediti scritti in quella memoria; IllegalArgumentException se la memoria non è nel formato o è rovinata. */
    int crediti(MemoriaBraccialetto memoria);

    /** I blocchi da scrivere perché la memoria dica quei crediti (partendo da quella appena letta). */
    Map<Integer, String> scrivi(MemoriaBraccialetto attuale, int crediti);
}
