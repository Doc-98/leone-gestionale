package it.leonexiii.gestionale.pianovasca;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Una lezione che si ripete ogni settimana nel giorno indicato, nel periodo di validità, su uno o più spazi. */
@Entity
@Table(name = "lezione")
class Lezione {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corso_id", nullable = false)
    private Corso corso;

    @Column(name = "giorno_settimana", nullable = false)
    private short giornoSettimana;

    @Column(nullable = false)
    private LocalTime inizio;

    @Column(nullable = false)
    private LocalTime fine;

    @Column(name = "valida_dal", nullable = false)
    private LocalDate validaDal;

    @Column(name = "valida_al")
    private LocalDate validaAl;

    @Column(name = "istruttore_id")
    private UUID istruttoreId;

    @ElementCollection
    @CollectionTable(name = "lezione_spazio", joinColumns = @JoinColumn(name = "lezione_id"))
    @Column(name = "spazio_id")
    private Set<UUID> spazi = new HashSet<>();

    @OneToMany(mappedBy = "lezione", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Eccezione> eccezioni = new HashSet<>();

    protected Lezione() {}

    Lezione(Corso corso) {
        this.id = UUID.randomUUID();
        this.corso = corso;
    }

    void modifica(int giornoSettimana, LocalTime inizio, LocalTime fine, LocalDate validaDal, LocalDate validaAl, Set<UUID> spazi, UUID istruttoreId) {
        if (giornoSettimana < 1 || giornoSettimana > 7) throw new IllegalArgumentException("Giorno della settimana tra 1 (lunedì) e 7 (domenica).");
        if (inizio == null || fine == null || !fine.isAfter(inizio)) throw new IllegalArgumentException("La fine della lezione deve essere dopo l'inizio.");
        if (validaDal == null) throw new IllegalArgumentException("Indica da quando vale la lezione.");
        if (validaAl != null && validaAl.isBefore(validaDal)) throw new IllegalArgumentException("La fine della validità è prima dell'inizio.");
        if (spazi == null || spazi.isEmpty()) throw new IllegalArgumentException("Scegli almeno una vasca o una corsia.");
        this.giornoSettimana = (short) giornoSettimana;
        this.inizio = inizio;
        this.fine = fine;
        this.validaDal = validaDal;
        this.validaAl = validaAl;
        this.spazi.clear();
        this.spazi.addAll(spazi);
        this.istruttoreId = istruttoreId;
    }

    /** La lezione cade in quella data (giorno della settimana e validità)? */
    boolean cadeIl(LocalDate data) {
        return data.getDayOfWeek().getValue() == giornoSettimana && !data.isBefore(validaDal) && (validaAl == null || !data.isAfter(validaAl));
    }

    /** Non si tiene più dal giorno indicato (le date passate restano nello storico). */
    void terminaIl(LocalDate ultimoGiorno) {
        if (validaAl == null || validaAl.isAfter(ultimoGiorno)) validaAl = ultimoGiorno;
    }

    Optional<Eccezione> eccezioneIl(LocalDate data) {
        return eccezioni.stream().filter(e -> e.getData().equals(data)).findFirst();
    }

    Eccezione aggiungiEccezione(LocalDate data, TipoEccezione tipo, LocalDate nuovaData, LocalTime nuovoInizio, LocalTime nuovoFine, String motivo) {
        if (!cadeIl(data)) throw new IllegalArgumentException("La lezione non si tiene il " + data + ".");
        if (eccezioneIl(data).isPresent()) throw new it.leonexiii.gestionale.condiviso.Conflitto("Per quel giorno c'è già una modifica: annullala prima.");
        Eccezione e = new Eccezione(this, data, tipo, nuovaData, nuovoInizio, nuovoFine, motivo);
        eccezioni.add(e);
        return e;
    }

    UUID getId() { return id; }
    Corso getCorso() { return corso; }
    int getGiornoSettimana() { return giornoSettimana; }
    LocalTime getInizio() { return inizio; }
    LocalTime getFine() { return fine; }
    LocalDate getValidaDal() { return validaDal; }
    LocalDate getValidaAl() { return validaAl; }
    UUID getIstruttoreId() { return istruttoreId; }
    Set<UUID> getSpazi() { return spazi; }
    Set<Eccezione> getEccezioni() { return eccezioni; }
}
