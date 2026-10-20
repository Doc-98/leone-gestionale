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

/** API pubblica: invia un'email (testo semplice) e la registra, sempre, con il suo esito. */
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
                SimpleMailMessage m = new SimpleMailMessage();
                m.setFrom(mittente);
                m.setTo(destinatario);
                m.setSubject(oggetto);
                m.setText(testo);
                smtp.send(m);
                stato = Stato.INVIATA;
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
