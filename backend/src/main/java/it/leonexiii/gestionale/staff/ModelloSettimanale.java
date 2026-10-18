package it.leonexiii.gestionale.staff;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** La settimana tipo di un impianto: da qui si generano i turni del mese (ciò che oggi è l'Excel). */
@Entity
@Table(name = "modello_settimanale")
class ModelloSettimanale {

    @Id
    private UUID id;

    @Column(name = "impianto_id", nullable = false)
    private UUID impiantoId;

    @Column(nullable = false)
    private String nome;

    @Column(name = "valido_dal", nullable = false)
    private LocalDate validoDal;

    @Column(name = "valido_al")
    private LocalDate validoAl;

    @OneToMany(mappedBy = "modello", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("giornoSettimana, inizio")
    private List<ModelloRiga> righe = new ArrayList<>();

    protected ModelloSettimanale() {}

    UUID getId() { return id; }
    UUID getImpiantoId() { return impiantoId; }
    String getNome() { return nome; }
    LocalDate getValidoDal() { return validoDal; }
    LocalDate getValidoAl() { return validoAl; }
    List<ModelloRiga> getRighe() { return righe; }

    boolean validoIl(LocalDate giorno) {
        return !giorno.isBefore(validoDal) && (validoAl == null || !giorno.isAfter(validoAl));
    }

    ModelloRiga aggiungiRiga(Postazione postazione, int giornoSettimana, java.time.LocalTime inizio, java.time.LocalTime fine, UUID personaId) {
        ModelloRiga r = new ModelloRiga(this, postazione, giornoSettimana, inizio, fine, personaId);
        righe.add(r);
        return r;
    }
}
