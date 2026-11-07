package it.leonexiii.gestionale.notifiche;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API pubblica: invia un'email (testo semplice; più destinatari separati da virgola) e la registra, sempre, con il suo esito. */
@Service
public class Posta {

    public enum Stato { INVIATA, NON_INVIATA, SENZA_EMAIL, ERRORE }

    public record Esito(UUID emailId, Stato stato, String errore) {}

    public record EmailBreve(UUID id, UUID personaId, String destinatario, String oggetto, String testo, Stato stato, String errore, Instant creataIl) {}

    private final ObjectProvider<JavaMailSender> mittenteSmtp;
    private final EmailInviataRepository registro;
    private final Clock orologio;
    private final String mittente;

    Posta(ObjectProvider<JavaMailSender> mittenteSmtp, EmailInviataRepository registro, Clock orologio,
          @Value("${gestionale.posta.mittente:segreteria@leonexiiisport.it}") String mittente) {
        this.mittenteSmtp = mittenteSmtp;
        this.registro = registro;
        this.orologio = orologio;
        this.mittente = mittente;
    }

    /** C'è un server di posta (spring.mail.host)? Senza, le email si registrano ma non partono. */
    public boolean configurata() {
        return mittenteSmtp.getIfAvailable() != null;
    }

    @Transactional
    public Esito invia(UUID personaId, String destinatario, String oggetto, String testo, UUID inviataDa) {
        return invia(personaId, destinatario, oggetto, testo, inviataDa, null);
    }

    /** Un file da allegare (es. il PDF di una ricevuta). */
    public record Allegato(String nome, String tipo, byte[] dati) {}

    /** Come {@link #invia(UUID, String, String, String, UUID)}, con un allegato (se non è null). */
    public Esito invia(UUID personaId, String destinatario, String oggetto, String testo, UUID inviataDa, Allegato allegato) {
        Stato stato;
        String errore = null;
        JavaMailSender smtp = mittenteSmtp.getIfAvailable();
        if (destinatario == null || destinatario.isBlank()) {
            stato = Stato.SENZA_EMAIL;
            errore = "Nessun indirizzo email nella scheda.";
        } else if (smtp == null) {
            stato = Stato.NON_INVIATA;
            errore = "Server di posta non configurato.";
        } else {
            try {
                String[] a = java.util.Arrays.stream(destinatario.split(",")).map(String::strip).filter(x -> !x.isEmpty()).toArray(String[]::new);
                if (allegato == null) {
                    SimpleMailMessage m = new SimpleMailMessage();
                    m.setFrom(mittente);
                    m.setTo(a);
                    m.setSubject(oggetto);
                    m.setText(testo);
                    smtp.send(m);
                } else {
                    var m = smtp.createMimeMessage();
                    var h = new org.springframework.mail.javamail.MimeMessageHelper(m, true, "UTF-8");
                    h.setFrom(mittente);
                    h.setTo(a);
                    h.setSubject(oggetto);
                    h.setText(testo);
                    h.addAttachment(allegato.nome(), new org.springframework.core.io.ByteArrayResource(allegato.dati()), allegato.tipo());
                    smtp.send(m);
                }
                stato = Stato.INVIATA;
            } catch (jakarta.mail.MessagingException e) {
                stato = Stato.ERRORE;
                errore = e.getMessage();
            } catch (RuntimeException e) {
                stato = Stato.ERRORE;
                errore = e.getMessage();
            }
        }
        EmailInviata e = registro.save(new EmailInviata(personaId, destinatario, oggetto, testo, stato.name(), errore, inviataDa, orologio.instant()));
        return new Esito(e.getId(), stato, errore);
    }

    @Transactional(readOnly = true)
    public List<EmailBreve> ultime(int quante) {
        return registro.ultime(PageRequest.of(0, Math.clamp(quante, 1, 500))).stream().map(Posta::breve).toList();
    }

    @Transactional(readOnly = true)
    public List<EmailBreve> diPersona(UUID personaId, int quante) {
        return registro.findByPersonaIdOrderByCreataIlDesc(personaId, PageRequest.of(0, Math.clamp(quante, 1, 100))).stream().map(Posta::breve).toList();
    }

    private static EmailBreve breve(EmailInviata e) {
        return new EmailBreve(e.getId(), e.getPersonaId(), e.getDestinatario(), e.getOggetto(), e.getTesto(), Stato.valueOf(e.getStato()), e.getErrore(), e.getCreataIl());
    }
}
