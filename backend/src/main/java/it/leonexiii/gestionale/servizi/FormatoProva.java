package it.leonexiii.gestionale.servizi;

import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Un formato inventato, per la demo, i test e il lettore simulato (ADR 0025): MIFARE Classic, settore 1 con la chiave
 * di fabbrica, blocco 4 = «LG01» · crediti (4 byte) · crediti invertiti (4 byte, per accorgersi di una memoria
 * rovinata) · 4 byte a zero. Un blocco tutto a zero è un braccialetto mai scritto: zero crediti. Non è sicuro e non
 * va usato con le macchine vere.
 */
class FormatoProva implements FormatoBraccialetto {

    static final int BLOCCO = 4;
    private static final String FIRMA = "4C473031"; // «LG01»
    private static final int MASSIMO = 100_000;

    public String codice() { return "PROVA"; }

    public String nome() { return "Di prova (solo per la demo)"; }

    public boolean pronto() { return true; }

    public String nota() {
        return "Un formato inventato per provare ricariche e letture con il lettore simulato: non è quello delle macchine vere.";
    }

    public Accesso accesso() {
        return new Accesso("MIFARE_CLASSIC_1K", List.of(new ChiaveSettore(1, "A", "FFFFFFFFFFFF")), List.of(BLOCCO));
    }

    public int crediti(MemoriaBraccialetto memoria) {
        String b = memoria.blocco(BLOCCO);
        if (b == null) throw new IllegalArgumentException("Il lettore non ha letto il blocco " + BLOCCO + " del braccialetto.");
        if (b.equals("0".repeat(32))) return 0;
        if (!b.startsWith(FIRMA)) throw new IllegalArgumentException("Il braccialetto non è scritto nel formato di prova.");
        long c = Long.parseLong(b.substring(8, 16), 16);
        long inverso = Long.parseLong(b.substring(16, 24), 16);
        if ((c ^ inverso) != 0xFFFFFFFFL || c > MASSIMO) throw new IllegalArgumentException("La memoria del braccialetto è rovinata: i crediti non tornano.");
        return (int) c;
    }

    public Map<Integer, String> scrivi(MemoriaBraccialetto attuale, int crediti) {
        if (crediti < 0 || crediti > MASSIMO) throw new IllegalArgumentException("Crediti fuori misura: " + crediti + ".");
        HexFormat h = HexFormat.of().withUpperCase();
        String blocco = FIRMA + h.toHexDigits(crediti) + h.toHexDigits(~crediti) + "00000000";
        return Map.of(BLOCCO, blocco);
    }
}
