package it.leonexiii.gestionale.servizi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Una macchina (phon, docce) o un lettore del saldo, con la sua chiave (se ne conserva solo l'impronta SHA-256). */
@Entity
@Table(name = "dispositivo")
class Dispositivo {

    enum Tipo { SERVIZIO, SALDO }

    @Id
    private UUID id;
    @Column(nullable = false)
    private String nome;
    @Column(nullable = false)
    private String tipo;
    @Column(name = "servizio_id")
    private UUID servizioId;
    @Column(name = "chiave_hash", nullable = false)
    private String chiaveHash;
    @Column(nullable = false)
    private boolean attivo;
    @Column(name = "creato_il", nullable = false)
    private Instant creatoIl;
    @Column(name = "ultimo_contatto")
    private Instant ultimoContatto;

    protected Dispositivo() {}

    Dispositivo(String nome, Tipo tipo, UUID servizioId, String chiaveHash, Instant ora) {
        this.id = UUID.randomUUID();
        this.chiaveHash = chiaveHash;
        this.attivo = true;
        this.creatoIl = ora;
        modifica(nome, tipo, servizioId, true);
    }

    void modifica(String nome, Tipo tipo, UUID servizioId, boolean attivo) {
        if (nome == null || nome.isBlank() || nome.strip().length() > 80) throw new IllegalArgumentException("Dai un nome al dispositivo (al massimo 80 caratteri).");
        if (tipo == Tipo.SERVIZIO && servizioId == null) throw new IllegalArgumentException("Scegli il servizio della macchina (es. phon).");
        this.nome = nome.strip();
        this.tipo = tipo.name();
        this.servizioId = tipo == Tipo.SERVIZIO ? servizioId : null;
        this.attivo = attivo;
    }

    void nuovaChiave(String hash) { this.chiaveHash = hash; }
    void contatto(Instant ora) { this.ultimoContatto = ora; }

    UUID getId() { return id; }
    String getNome() { return nome; }
    Tipo tipo() { return Tipo.valueOf(tipo); }
    UUID getServizioId() { return servizioId; }
    String getChiaveHash() { return chiaveHash; }
    boolean isAttivo() { return attivo; }
    Instant getCreatoIl() { return creatoIl; }
    Instant getUltimoContatto() { return ultimoContatto; }
}
