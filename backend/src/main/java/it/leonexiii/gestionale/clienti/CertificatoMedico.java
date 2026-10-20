package it.leonexiii.gestionale.clienti;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Certificato medico: valido fino al giorno di scadenza compreso. Il «provvisorio» è una dichiarazione firmata di breve
 * durata (dall'interessato o da un genitore) in attesa del certificato vero: vale al massimo per i giorni fissati
 * nella regola del tornello.
 */
@Entity
@Table(name = "certificato_medico")
class CertificatoMedico {

    @Id
    private UUID id;
    @Column(name = "persona_id", nullable = false)
    private UUID personaId;
    @Column(nullable = false)
    private String tipo;
    @Column(name = "rilasciato_il")
    private LocalDate rilasciatoIl;
    @Column(nullable = false)
    private LocalDate scadenza;
    private String note;
    @Column(name = "firmato_da")
    private String firmatoDa;
    @Column(name = "registrato_da")
    private UUID registratoDa;
    @Column(name = "registrato_il", nullable = false)
    private Instant registratoIl;
    @Column(name = "annullato_il")
    private Instant annullatoIl;

    protected CertificatoMedico() {}

    CertificatoMedico(UUID personaId, String tipo, LocalDate rilasciatoIl, LocalDate scadenza, String note, String firmatoDa, UUID registratoDa, Instant ora) {
        if (scadenza == null) throw new IllegalArgumentException("Indica la scadenza del certificato.");
        if (!"NON_AGONISTICO".equals(tipo) && !"AGONISTICO".equals(tipo) && !"PROVVISORIO".equals(tipo))
            throw new IllegalArgumentException("Tipo di certificato non valido.");
        if ("PROVVISORIO".equals(tipo)) {
            if (rilasciatoIl == null) throw new IllegalArgumentException("Indica quando è stata firmata la dichiarazione.");
            if (firmatoDa == null || firmatoDa.isBlank()) throw new IllegalArgumentException("Indica chi ha firmato la dichiarazione.");
        }
        this.firmatoDa = Testo.breve(firmatoDa, 120);
        if (rilasciatoIl != null && rilasciatoIl.isAfter(scadenza)) throw new IllegalArgumentException("Il certificato scade prima di essere rilasciato.");
        this.id = UUID.randomUUID();
        this.personaId = personaId;
        this.tipo = tipo;
        this.rilasciatoIl = rilasciatoIl;
        this.scadenza = scadenza;
        this.note = Testo.breve(note, 300);
        this.registratoDa = registratoDa;
        this.registratoIl = ora;
    }

    void annulla(Instant ora) {
        if (annullatoIl == null) annullatoIl = ora;
    }

    boolean validoIl(LocalDate g) {
        return annullatoIl == null && !g.isAfter(scadenza);
    }

    UUID getId() { return id; }
    UUID getPersonaId() { return personaId; }
    String getTipo() { return tipo; }
    LocalDate getRilasciatoIl() { return rilasciatoIl; }
    LocalDate getScadenza() { return scadenza; }
    String getNote() { return note; }
    String getFirmatoDa() { return firmatoDa; }
    Instant getAnnullatoIl() { return annullatoIl; }
}
