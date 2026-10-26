package it.leonexiii.gestionale.pianovasca;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Un turno del corso: si ripete nel giorno della settimana indicato (ogni settimana, oppure una volta al mese nella
 * stessa posizione del mese della prima data), nel periodo di validità, su uno o più spazi, con i suoi istruttori.
 */
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

    /** Il primo istruttore del turno (compatibilità); l'elenco completo è in {@link #istruttori}. */
    @Column(name = "istruttore_id")
    private UUID istruttoreId;

    @ElementCollection
    @CollectionTable(name = "lezione_istruttore", joinColumns = @JoinColumn(name = "lezione_id"))
    @Column(name = "persona_id")
    private Set<UUID> istruttori = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Cadenza cadenza = Cadenza.SETTIMANALE;

    /** Quanti iscritti al massimo (facoltativo). */
    private Short posti;

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

    void modifica(int giornoSettimana, LocalTime inizio, LocalTime fine, LocalDate validaDal, LocalDate validaAl, Set<UUID> spazi,
                  List<UUID> istruttori, Cadenza cadenza) {
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
        List<UUID> suoi = istruttori == null ? List.of() : istruttori.stream().filter(java.util.Objects::nonNull).distinct().toList();
        this.istruttoreId = suoi.isEmpty() ? null : suoi.get(0);
        this.istruttori.clear();
        this.istruttori.addAll(suoi);
        this.cadenza = cadenza == null ? Cadenza.SETTIMANALE : cadenza;
        if (this.cadenza == Cadenza.MENSILE && validaDal.getDayOfWeek().getValue() != giornoSettimana)
            throw new IllegalArgumentException("Per un turno mensile la prima data deve cadere di " + Testi.giorno(giornoSettimana) + ".");
    }

    /** La lezione cade in quella data (giorno della settimana e validità)? */
    boolean cadeIl(LocalDate data) {
        if (data.getDayOfWeek().getValue() != giornoSettimana || data.isBefore(validaDal) || validaAl != null && data.isAfter(validaAl)) return false;
        return cadenza != Cadenza.MENSILE || settimanaDelMese(data) == settimanaDelMese(validaDal);
    }

    /** 1 per i primi sette giorni del mese, 2 per i sette dopo, e così via (il «quarto mercoledì» è la 4). */
    static int settimanaDelMese(LocalDate data) {
        return (data.getDayOfMonth() - 1) / 7 + 1;
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
    Integer getPosti() { return posti == null ? null : posti.intValue(); }

    void posti(Integer posti) {
        if (posti != null && (posti < 1 || posti > 500)) throw new IllegalArgumentException("Posti non validi.");
        this.posti = posti == null ? null : posti.shortValue();
    }

    /** Sostituisce gli spazi (es. quando la fascia passa da 6 a 5 corsie e le corsie si riportano per numero). */
    void spostaSu(Set<UUID> nuovi) {
        spazi.clear();
        spazi.addAll(nuovi);
    }

    /** Nuovo orario (quando la fascia in cui sta cambia orario). */
    void orario(LocalTime inizio, LocalTime fine) {
        if (!fine.isAfter(inizio)) throw new IllegalArgumentException("La fine deve essere dopo l'inizio.");
        this.inizio = inizio;
        this.fine = fine;
    }

    /** L'istruttore di questo turno: il primo dei suoi, oppure quello del corso. */
    UUID istruttoreEffettivo() {
        return istruttoreId != null ? istruttoreId : corso.getIstruttoreId();
    }

    /** La persona tiene questo turno (è tra i suoi istruttori, o è l'istruttore del corso se il turno non ne ha)? */
    boolean tenutoDa(UUID personaId) {
        if (personaId == null) return false;
        return istruttori.isEmpty() ? personaId.equals(corso.getIstruttoreId()) : istruttori.contains(personaId);
    }

    Set<UUID> getIstruttori() { return istruttori; }
    Cadenza getCadenza() { return cadenza; }
    Set<UUID> getSpazi() { return spazi; }
    Set<Eccezione> getEccezioni() { return eccezioni; }
}
