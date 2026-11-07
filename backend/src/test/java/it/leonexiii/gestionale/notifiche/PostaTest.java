package it.leonexiii.gestionale.notifiche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Cosa succede a un'email: senza indirizzo non parte, senza server di posta si registra soltanto, con il server parte
 * (anche a più indirizzi separati da virgole), e un errore del server resta scritto. Ogni email resta nel registro.
 */
class PostaTest {

    private final Clock orologio = Clock.fixed(Instant.parse("2028-03-01T08:00:00Z"), ZoneId.of("Europe/Rome"));
    private final List<EmailInviata> registrate = new ArrayList<>();
    private EmailInviataRepository registro;

    @BeforeEach
    void registro() {
        registro = mock(EmailInviataRepository.class);
        when(registro.save(any())).thenAnswer(i -> {
            registrate.add(i.getArgument(0));
            return i.getArgument(0);
        });
    }

    private Posta posta(JavaMailSender smtp) {
        var fabbrica = new StaticListableBeanFactory();
        if (smtp != null) fabbrica.addBean("smtp", smtp);
        return new Posta(fabbrica.getBeanProvider(JavaMailSender.class), registro, orologio, "segreteria@esempio.it");
    }

    @Test
    void senzaIndirizzoNonParte() {
        var esito = posta(new Casella()).invia(null, " ", "Oggetto", "Testo", null);
        assertThat(esito.stato()).isEqualTo(Posta.Stato.SENZA_EMAIL);
        assertThat(registrate).singleElement().satisfies(e -> assertThat(e.getStato()).isEqualTo("SENZA_EMAIL"));
    }

    @Test
    void senzaServerDiPostaSiRegistraSoltanto() {
        var posta = posta(null);
        assertThat(posta.configurata()).isFalse();
        var esito = posta.invia(null, "famiglia@esempio.it", "Oggetto", "Testo", null);
        assertThat(esito.stato()).isEqualTo(Posta.Stato.NON_INVIATA);
        assertThat(esito.errore()).contains("non configurato");
        assertThat(registrate).hasSize(1);
    }

    @Test
    void conIlServerPartePerTuttiGliIndirizzi() {
        var casella = new Casella();
        var posta = posta(casella);
        assertThat(posta.configurata()).isTrue();
        var esito = posta.invia(null, "mamma@esempio.it, papa@esempio.it,", "Promemoria", "Buongiorno", null);
        assertThat(esito.stato()).isEqualTo(Posta.Stato.INVIATA);
        assertThat(casella.arrivate).singleElement().satisfies(m -> {
            assertThat(m.getTo()).containsExactly("mamma@esempio.it", "papa@esempio.it");
            assertThat(m.getFrom()).isEqualTo("segreteria@esempio.it");
            assertThat(m.getSubject()).isEqualTo("Promemoria");
        });
        assertThat(registrate.getFirst().getCreataIl()).isEqualTo(orologio.instant());
    }

    @Test
    void unErroreDelServerResta() {
        var guasta = new Casella() {
            @Override public void send(SimpleMailMessage m) { throw new MailSendException("Server irraggiungibile"); }
        };
        var esito = posta(guasta).invia(null, "famiglia@esempio.it", "Oggetto", "Testo", null);
        assertThat(esito.stato()).isEqualTo(Posta.Stato.ERRORE);
        assertThat(esito.errore()).isEqualTo("Server irraggiungibile");
        assertThat(registrate.getFirst().getErrore()).isEqualTo("Server irraggiungibile");
    }

    private static class Casella extends JavaMailSenderImpl {
        final List<SimpleMailMessage> arrivate = new ArrayList<>();
        @Override public void send(SimpleMailMessage m) { arrivate.add(m); }
    }
}
