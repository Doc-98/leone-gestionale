package it.leonexiii.gestionale.clienti;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Un consenso privacy firmato (per i minori da un genitore): trattamento per il servizio, marketing, immagini. */
@Entity
@Table(name = "consenso")
class Consenso {

    enum Tipo { TRATTAMENTO, MARKETING, IMMAGINI }

    @Id
    private UUID id;
    @Column(name = "persona_id", nullable = false)
    private UUID personaId;
    @Column(nullable = false)
    private String tipo;
    @Column(name = "firmato_da")
    private String firmatoDa;
    @Column(name = "dato_il", nullable = false)
    private LocalDate datoIl;
    @Column(name = "valido_fino")
    private LocalDate validoFino;
    @Column(nullable = false)
    private boolean email;
    @Column(nullable = false)
    private boolean sms;
    @Column(nullable = false)
    private boolean cartaceo;
    @Column(name = "registrato_da")
    private UUID registratoDa;
    @Column(name = "registrato_il", nullable = false)
    private Instant registratoIl;
    @Column(name = "revocato_il")
    private Instant revocatoIl;

    protected Consenso() {}

    Consenso(UUID personaId, Tipo tipo, String firmatoDa, LocalDate datoIl, LocalDate validoFino, boolean email, boolean sms, boolean cartaceo,
             UUID registratoDa, Instant ora) {
        if (tipo == null) throw new IllegalArgumentException("Scegli il tipo di consenso.");
        if (datoIl == null) throw new IllegalArgumentException("Indica quando è stato firmato.");
        if (validoFino != null && validoFino.isBefore(datoIl)) throw new IllegalArgumentException("La validità finisce prima della firma.");
        if (tipo == Tipo.MARKETING && !email && !sms && !cartaceo) throw new IllegalArgumentException("Per il marketing indica almeno un canale.");
        this.id = UUID.randomUUID();
        this.personaId = personaId;
        this.tipo = tipo.name();
        this.firmatoDa = Testo.breve(firmatoDa, 120);
        this.datoIl = datoIl;
        this.validoFino = validoFino;
        this.email = tipo == Tipo.MARKETING && email;
        this.sms = tipo == Tipo.MARKETING && sms;
        this.cartaceo = tipo == Tipo.MARKETING && cartaceo;
        this.registratoDa = registratoDa;
        this.registratoIl = ora;
    }

    void revoca(Instant ora) {
        if (revocatoIl == null) revocatoIl = ora;
    }

    boolean validoIl(LocalDate g) {
        return revocatoIl == null && !g.isBefore(datoIl) && (validoFino == null || !g.isAfter(validoFino));
    }

    Tipo tipo() { return Tipo.valueOf(tipo); }
    UUID getId() { return id; }
    UUID getPersonaId() { return personaId; }
    String getFirmatoDa() { return firmatoDa; }
    LocalDate getDatoIl() { return datoIl; }
    LocalDate getValidoFino() { return validoFino; }
    boolean isEmail() { return email; }
    boolean isSms() { return sms; }
    boolean isCartaceo() { return cartaceo; }
    Instant getRevocatoIl() { return revocatoIl; }
}
