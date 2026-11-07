package it.leonexiii.gestionale;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Ciò che i test controllano al posto del mondo vero: l'ora e la posta in uscita. */
@TestConfiguration(proxyBeanMethods = false)
class AmbienteDiProva {

    static final ZoneId ROMA = ZoneId.of("Europe/Rome");

    @Bean
    @Primary
    OrologioDiProva orologioDiProva() {
        return new OrologioDiProva();
    }

    @Bean
    CasellaFinta casellaFinta() {
        return new CasellaFinta();
    }

    /** L'ora vera, finché un test non la ferma su un momento preciso (ora di Roma). */
    static final class OrologioDiProva extends Clock {
        private volatile Instant fermo;

        void alle(String quando) {
            fermo = LocalDateTime.parse(quando).atZone(ROMA).toInstant();
        }

        /** Torna all'ora vera: lo fa la base dei test dopo ogni test. */
        void vero() {
            fermo = null;
        }

        @Override public ZoneId getZone() { return ROMA; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return fermo != null ? fermo : Instant.now(); }
    }

    /** Le email non partono: restano qui, e i test le leggono. */
    static final class CasellaFinta extends JavaMailSenderImpl {
        final List<SimpleMailMessage> arrivate = new CopyOnWriteArrayList<>();

        @Override public void send(SimpleMailMessage m) { arrivate.add(m); }
        @Override public void send(SimpleMailMessage... m) { arrivate.addAll(List.of(m)); }

        List<SimpleMailMessage> per(String email) {
            return arrivate.stream().filter(m -> m.getTo() != null && List.of(m.getTo()).contains(email)).toList();
        }
    }
}
