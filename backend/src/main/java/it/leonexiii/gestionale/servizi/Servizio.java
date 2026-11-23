package it.leonexiii.gestionale.servizi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Un servizio a crediti (es. phon, doccia): quanti crediti costa e quante volte al giorno è gratis. */
@Entity
@Table(name = "servizio")
class Servizio {

    @Id
    private UUID id;
    @Column(nullable = false)
    private String nome;
    @Column(nullable = false)
    private int crediti;
    @Column(name = "gratuiti_al_giorno", nullable = false)
    private int gratuitiAlGiorno;
    private Integer minuti;
    @Column(nullable = false)
    private boolean attivo;
    @Column(nullable = false)
    private int ordine;

    protected Servizio() {}

    /** Un servizio nuovo va in fondo all'elenco («ordine» lo dà chi lo crea) e, se non si dice altro, è acceso. */
    Servizio(ServiziService.NuovoServizio in, int ordine) {
        this.id = UUID.randomUUID();
        this.attivo = true;
        this.ordine = ordine;
        modifica(in);
    }

    void modifica(ServiziService.NuovoServizio in) {
        if (in.nome() == null || in.nome().isBlank() || in.nome().strip().length() > 60) throw new IllegalArgumentException("Dai un nome al servizio (al massimo 60 caratteri).");
        if (in.crediti() == null || in.crediti() < 0) throw new IllegalArgumentException("I crediti per ogni uso sono zero o più.");
        if (in.gratuitiAlGiorno() != null && in.gratuitiAlGiorno() < 0) throw new IllegalArgumentException("Gli usi gratuiti al giorno sono zero o più.");
        if (in.minuti() != null && in.minuti() <= 0) throw new IllegalArgumentException("I minuti sono più di zero (o vuoti).");
        this.nome = in.nome().strip();
        this.crediti = in.crediti();
        this.gratuitiAlGiorno = in.gratuitiAlGiorno() == null ? 0 : in.gratuitiAlGiorno();
        this.minuti = in.minuti();
        // «attivo» assente = resta com'è; l'ordine non si cambia da qui (c'è «ordina»)
        if (in.attivo() != null) this.attivo = in.attivo();
    }

    void ordina(int ordine) { this.ordine = ordine; }

    UUID getId() { return id; }
    String getNome() { return nome; }
    int getCrediti() { return crediti; }
    int getGratuitiAlGiorno() { return gratuitiAlGiorno; }
    Integer getMinuti() { return minuti; }
    boolean isAttivo() { return attivo; }
    int getOrdine() { return ordine; }
}
