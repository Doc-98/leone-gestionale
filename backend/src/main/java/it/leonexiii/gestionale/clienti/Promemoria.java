package it.leonexiii.gestionale.clienti;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Una regola di promemoria via email: per i tipi «in scadenza» si manda {@code giorni} giorni prima della scadenza; per
 * il tesseramento mancante si ripete ogni {@code giorni} giorni, al massimo {@code maxInvii} volte per stagione.
 */
@Entity
@Table(name = "promemoria")
class Promemoria {

    enum Tipo { CERTIFICATO, ABBONAMENTO, TESSERAMENTO, TESSERAMENTO_MANCANTE }

    @Id
    private UUID id;
    @Column(nullable = false)
    private String tipo;
    @Column(nullable = false)
    private short giorni;
    @Column(name = "max_invii")
    private Short maxInvii;
    @Column(nullable = false)
    private boolean attivo;
    @Column(nullable = false)
    private String oggetto;
    @Column(nullable = false)
    private String testo;

    protected Promemoria() {}

    Promemoria(Tipo tipo) {
        this.id = UUID.randomUUID();
        this.tipo = tipo.name();
    }

    void modifica(int giorni, Integer maxInvii, boolean attivo, String oggetto, String testo) {
        if (giorni < 0 || giorni > 365) throw new IllegalArgumentException("I giorni vanno da 0 a 365.");
        if (tipo() == Tipo.TESSERAMENTO_MANCANTE) {
            if (giorni < 1) throw new IllegalArgumentException("Ripeti almeno ogni giorno.");
            if (maxInvii == null || maxInvii < 1 || maxInvii > 20) throw new IllegalArgumentException("Indica quante volte al massimo (da 1 a 20).");
        }
        String o = oggetto == null ? "" : oggetto.strip();
        String t = testo == null ? "" : testo.strip();
        if (o.isEmpty() || o.length() > 200) throw new IllegalArgumentException("L'oggetto va da 1 a 200 caratteri.");
        if (t.isEmpty() || t.length() > 5000) throw new IllegalArgumentException("Il testo va da 1 a 5000 caratteri.");
        this.giorni = (short) giorni;
        this.maxInvii = tipo() == Tipo.TESSERAMENTO_MANCANTE ? maxInvii.shortValue() : null;
        this.attivo = attivo;
        this.oggetto = o;
        this.testo = t;
    }

    Tipo tipo() { return Tipo.valueOf(tipo); }
    UUID getId() { return id; }
    int getGiorni() { return giorni; }
    Integer getMaxInvii() { return maxInvii == null ? null : maxInvii.intValue(); }
    boolean isAttivo() { return attivo; }
    String getOggetto() { return oggetto; }
    String getTesto() { return testo; }
}
