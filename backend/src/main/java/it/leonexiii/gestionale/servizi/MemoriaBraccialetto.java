package it.leonexiii.gestionale.servizi;

import java.util.Map;
import java.util.TreeMap;

/**
 * La memoria di un braccialetto come la legge o la scrive il lettore del PC (ADR 0025): il seriale (lo stesso codice
 * del tornello) e i blocchi letti, per numero, ciascuno di 16 byte in esadecimale maiuscolo. Il gestionale non
 * interpreta i byte da sé: lo fa il {@link FormatoBraccialetto} della società.
 */
public record MemoriaBraccialetto(String uid, Map<Integer, String> blocchi) {

    public MemoriaBraccialetto {
        uid = uid == null ? "" : uid.replaceAll("[^0-9A-Za-z]", "").toUpperCase();
        TreeMap<Integer, String> b = new TreeMap<>();
        if (blocchi != null) blocchi.forEach((k, v) -> {
            if (k == null || k < 0 || k > 255) throw new IllegalArgumentException("Numero di blocco non valido: " + k + ".");
            String h = v == null ? "" : v.replaceAll("\\s", "").toUpperCase();
            if (!h.matches("[0-9A-F]{32}")) throw new IllegalArgumentException("Il blocco " + k + " non è di 16 byte in esadecimale.");
            b.put(k, h);
        });
        blocchi = Map.copyOf(b);
    }

    /** Il blocco, o null se il lettore non l'ha letto. */
    public String blocco(int n) {
        return blocchi.get(n);
    }
}
