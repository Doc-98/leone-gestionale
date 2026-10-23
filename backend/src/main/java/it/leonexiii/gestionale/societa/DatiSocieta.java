package it.leonexiii.gestionale.societa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** La società che usa il gestionale: una sola riga. */
@Entity
@Table(name = "societa")
class DatiSocieta {

    @Id
    private Short id;
    @Column(nullable = false)
    private String nome;
    @Column(name = "ragione_sociale")
    private String ragioneSociale;
    @Column(name = "partita_iva")
    private String partitaIva;
    @Column(name = "codice_fiscale")
    private String codiceFiscale;
    private String indirizzo;
    private String cap;
    private String citta;
    private String provincia;
    private String email;
    private String telefono;
    @Column(name = "giorni_anticipo", nullable = false)
    private short giorniAnticipo;

    protected DatiSocieta() {}

    void modifica(Societa.Dati d) {
        if (d.nome() == null || d.nome().isBlank()) throw new IllegalArgumentException("Il nome della società è obbligatorio.");
        String piva = pulisci(d.partitaIva());
        if (piva != null && !piva.matches("[0-9]{11}")) throw new IllegalArgumentException("La partita IVA ha 11 cifre.");
        String cf = pulisci(d.codiceFiscale());
        if (cf != null) cf = cf.toUpperCase();
        if (cf != null && !cf.matches("[A-Z0-9]{11,16}")) throw new IllegalArgumentException("Codice fiscale non valido (11 cifre o 16 caratteri).");
        if (d.giorniAnticipo() < 0 || d.giorniAnticipo() > 365) throw new IllegalArgumentException("I giorni di anticipo vanno da 0 a 365.");
        this.nome = d.nome().strip();
        this.ragioneSociale = testo(d.ragioneSociale());
        this.partitaIva = piva;
        this.codiceFiscale = cf;
        this.indirizzo = testo(d.indirizzo());
        this.cap = testo(d.cap());
        this.citta = testo(d.citta());
        this.provincia = d.provincia() == null || d.provincia().isBlank() ? null : d.provincia().strip().toUpperCase();
        this.email = testo(d.email());
        this.telefono = testo(d.telefono());
        this.giorniAnticipo = (short) d.giorniAnticipo();
    }

    Societa.Dati dati() {
        return new Societa.Dati(nome, ragioneSociale, partitaIva, codiceFiscale, indirizzo, cap, citta, provincia, email, telefono, giorniAnticipo);
    }

    private static String testo(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String pulisci(String s) {
        return s == null || s.isBlank() ? null : s.replaceAll("\\s", "");
    }
}
