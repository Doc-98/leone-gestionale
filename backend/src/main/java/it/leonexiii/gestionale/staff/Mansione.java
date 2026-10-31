package it.leonexiii.gestionale.staff;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Es. «Assistente spogliatoio», «Assistente atrio», «Istruttore». */
@Entity
@Table(name = "mansione")
class Mansione {

    @Id
    private UUID id;

    @Column(name = "impianto_id", nullable = false)
    private UUID impiantoId;

    @Column(nullable = false)
    private String codice;

    @Column(nullable = false)
    private String nome;

    /** Colonna del foglio ore della società; null = colonna propria. */
    @Column(name = "voce_foglio_ore")
    private String voceFoglioOre;

    /** Chi coordina questo gruppo di staff: vede le ore del gruppo (ADR 0022). */
    @ElementCollection
    @CollectionTable(name = "mansione_coordinatore", joinColumns = @JoinColumn(name = "mansione_id"))
    @Column(name = "persona_id")
    private Set<UUID> coordinatori = new HashSet<>();

    protected Mansione() {}

    Mansione(UUID impiantoId, String codice, String nome) {
        this.id = UUID.randomUUID();
        this.impiantoId = impiantoId;
        this.codice = codice;
        rinomina(nome);
    }

    void rinomina(String nome) {
        this.nome = Testi.obbligatorio(nome, "il nome della mansione", 120);
    }

    void voceFoglioOre(String voce) {
        this.voceFoglioOre = voce == null || voce.isBlank() ? null : voce;
    }

    void coordinatori(Collection<UUID> persone) {
        coordinatori.clear();
        coordinatori.addAll(persone);
    }

    UUID getId() { return id; }
    Set<UUID> getCoordinatori() { return Set.copyOf(coordinatori); }
    UUID getImpiantoId() { return impiantoId; }
    String getCodice() { return codice; }
    String getNome() { return nome; }
    String getVoceFoglioOre() { return voceFoglioOre; }
}
