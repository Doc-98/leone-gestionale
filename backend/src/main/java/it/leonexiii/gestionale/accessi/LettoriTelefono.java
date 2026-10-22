package it.leonexiii.gestionale.accessi;

import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.Utente;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Il telefono come lettore NFC del PC (ADR 0017). Il PC chiede un abbinamento e mostra un QR (o un codice di 6 cifre);
 * il telefono lo apre, legge i braccialetti con la Web NFC e manda solo i codici; il PC li riceve chiedendoli ogni poco.
 * Il telefono non accede e non riceve dati dei clienti: va bene anche un telefono personale.
 *
 * Gli abbinamenti stanno in memoria (sono di passaggio): se il server riparte, si rifà l'abbinamento. Finiscono se il
 * PC non si fa vivo per {@link #PC_ASSENTE}, comunque dopo {@link #DURATA}.
 */
@Service
class LettoriTelefono {

    static final Duration DURATA = Duration.ofHours(12);
    static final Duration PC_ASSENTE = Duration.ofMinutes(10);
    static final int MAX_PER_ACCOUNT = 3;
    static final int MAX_LETTURE = 200;
    static final int MAX_ERRORI = 20;

    record Abbinamento(UUID id, String token, String codice, Instant scadeIl) {}

    record Lettura(long numero, String codice, Instant letta) {}

    record Letture(List<Lettura> letture, Instant telefonoVistoIl) {}

    private static final class Stato {
        final UUID id = UUID.randomUUID();
        final UUID accountId;
        final String token;
        final String codice;
        final Instant creato;
        volatile Instant pcVisto;
        volatile Instant telefonoVisto;
        final List<Lettura> letture = new ArrayList<>();
        long ultimo;

        Stato(UUID accountId, String token, String codice, Instant adesso) {
            this.accountId = accountId;
            this.token = token;
            this.codice = codice;
            this.creato = adesso;
            this.pcVisto = adesso;
        }
    }

    private final Map<UUID, Stato> perId = new ConcurrentHashMap<>();
    private final SecureRandom caso = new SecureRandom();
    private final List<Instant> errori = new ArrayList<>();
    private final Clock orologio;

    LettoriTelefono(Clock orologio) {
        this.orologio = orologio;
    }

    // ── Dal PC (collegato) ─────────────────────────────────────────────────

    synchronized Abbinamento abbina(Utente u) {
        pulisci();
        List<Stato> suoi = perId.values().stream().filter(s -> s.accountId.equals(u.accountId()))
            .sorted(Comparator.comparing((Stato s) -> s.creato)).toList();
        for (int i = 0; i <= suoi.size() - MAX_PER_ACCOUNT; i++) perId.remove(suoi.get(i).id);
        byte[] b = new byte[24];
        caso.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        String codice;
        do codice = String.format("%06d", caso.nextInt(1_000_000));
        while (perCodice(codice).isPresent());
        Stato s = new Stato(u.accountId(), token, codice, orologio.instant());
        perId.put(s.id, s);
        return new Abbinamento(s.id, s.token, s.codice, s.creato.plus(DURATA));
    }

    /** Le letture arrivate dopo il numero indicato; tiene vivo l'abbinamento. */
    Letture letture(Utente u, UUID id, long dopo) {
        Stato s = suo(u, id);
        s.pcVisto = orologio.instant();
        synchronized (s) {
            return new Letture(s.letture.stream().filter(l -> l.numero() > dopo).toList(), s.telefonoVisto);
        }
    }

    void scollega(Utente u, UUID id) {
        perId.remove(suo(u, id).id);
    }

    // ── Dal telefono (senza accesso: basta il token) ──────────────────────

    /** Il telefono si presenta (e lo rifà ogni tanto): il PC vede che è collegato. */
    void telefonoPresente(String token) {
        perToken(token).telefonoVisto = orologio.instant();
    }

    void leggi(String token, String grezzo) {
        Stato s = perToken(token);
        String codice = Codici.normalizza(grezzo);
        Instant adesso = orologio.instant();
        s.telefonoVisto = adesso;
        synchronized (s) {
            s.letture.add(new Lettura(++s.ultimo, codice, adesso));
            if (s.letture.size() > MAX_LETTURE) s.letture.removeFirst();
        }
    }

    /**
     * Il token dal codice di 6 cifre scritto sul telefono (al posto del QR). Contro chi prova i codici a caso: dopo
     * {@link #MAX_ERRORI} codici sbagliati in un minuto (da chiunque) si aspetta.
     */
    String tokenDaCodice(String codice) {
        pulisci();
        Instant adesso = orologio.instant();
        synchronized (errori) {
            errori.removeIf(t -> t.isBefore(adesso.minus(Duration.ofMinutes(1))));
            if (errori.size() >= MAX_ERRORI) throw new TroppiTentativi("Troppi codici sbagliati: riprova tra un minuto o inquadra il QR.");
        }
        Optional<String> token = perCodice(codice == null ? "" : codice.replaceAll("\\s", "")).map(s -> s.token);
        if (token.isEmpty()) {
            synchronized (errori) {
                errori.add(adesso);
            }
            throw new NonTrovato("Codice non valido o scaduto: rifai l'abbinamento dal PC.");
        }
        return token.get();
    }

    static class TroppiTentativi extends RuntimeException {
        TroppiTentativi(String messaggio) {
            super(messaggio);
        }
    }

    // ──────────────────────────────────────────────────────────────────────

    private Optional<Stato> perCodice(String codice) {
        return perId.values().stream().filter(s -> s.codice.equals(codice)).findFirst();
    }

    private Stato perToken(String token) {
        pulisci();
        return perId.values().stream().filter(s -> s.token.equals(token)).findFirst()
            .orElseThrow(() -> new NonTrovato("Abbinamento scaduto o chiuso dal PC: rifallo dal PC."));
    }

    private Stato suo(Utente u, UUID id) {
        pulisci();
        Stato s = perId.get(id);
        if (s == null || !s.accountId.equals(u.accountId())) throw new NonTrovato("Abbinamento scaduto o chiuso.");
        return s;
    }

    private void pulisci() {
        Instant adesso = orologio.instant();
        perId.values().removeIf(s -> adesso.isAfter(s.creato.plus(DURATA)) || adesso.isAfter(s.pcVisto.plus(PC_ASSENTE)));
    }
}
