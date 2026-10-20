package it.leonexiii.gestionale.identita;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Anagrafica unica: staff, clienti e tutori sono tutti persone. */
@Entity
@Table(name = "persona")
public class Persona {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String cognome;

    @Column(name = "data_nascita")
    private LocalDate dataNascita;

    private String email;

    private String telefono;

    private String sesso;
    @Column(name = "luogo_nascita")
    private String luogoNascita;
    @Column(name = "codice_fiscale")
    private String codiceFiscale;
    private String indirizzo;
    private String cap;
    private String citta;
    private String provincia;
    private String nazione;
    private String note;

    @Column(name = "creato_il", nullable = false, insertable = false, updatable = false)
    private Instant creatoIl;

    protected Persona() {}

    /** Persona senza account (es. un cliente iscritto a un corso): bastano nome, cognome e data di nascita. */
    Persona(String nome, String cognome, LocalDate dataNascita) {
        this.id = UUID.randomUUID();
        this.nome = nome;
        this.cognome = cognome;
        this.dataNascita = dataNascita;
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public String getCognome() { return cognome; }
    public LocalDate getDataNascita() { return dataNascita; }
    public String getEmail() { return email; }
    public String getTelefono() { return telefono; }

    void anagrafica(String nome, String cognome, LocalDate dataNascita, String sesso, String luogoNascita, String codiceFiscale, String indirizzo,
                    String cap, String citta, String provincia, String nazione, String note) {
        this.nome = nome;
        this.cognome = cognome;
        this.dataNascita = dataNascita;
        this.sesso = sesso;
        this.luogoNascita = luogoNascita;
        this.codiceFiscale = codiceFiscale;
        this.indirizzo = indirizzo;
        this.cap = cap;
        this.citta = citta;
        this.provincia = provincia;
        this.nazione = nazione;
        this.note = note;
    }

    String getSesso() { return sesso; }
    String getLuogoNascita() { return luogoNascita; }
    String getCodiceFiscale() { return codiceFiscale; }
    String getIndirizzo() { return indirizzo; }
    String getCap() { return cap; }
    String getCitta() { return citta; }
    String getProvincia() { return provincia; }
    String getNazione() { return nazione; }
    String getNote() { return note; }

    void contatti(String email, String telefono) {
        this.email = email;
        this.telefono = telefono;
    }

    public String nomeCompleto() {
        return nome + " " + cognome;
    }
}
