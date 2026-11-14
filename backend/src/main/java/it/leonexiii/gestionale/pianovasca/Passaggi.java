package it.leonexiii.gestionale.pianovasca;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Chiude un passaggio di livello quando il bambino entra in un turno del livello nuovo (da un'iscrizione, da un cambio
 * di turno o dalla lista d'attesa): esce dalle altre liste del passaggio e lascia il vecchio turno il giorno prima.
 */
@Component
class Passaggi {

    private final PassaggioLivelloRepository passaggi;
    private final AttesaRepository attese;
    private final AttesaEventoRepository eventi;
    private final IscrizioneRepository iscrizioni;
    private final Clock orologio;

    Passaggi(PassaggioLivelloRepository passaggi, AttesaRepository attese, AttesaEventoRepository eventi, IscrizioneRepository iscrizioni, Clock orologio) {
        this.passaggi = passaggi;
        this.attese = attese;
        this.eventi = eventi;
        this.iscrizioni = iscrizioni;
        this.orologio = orologio;
    }

    void entrato(UUID passaggioId, LocalDate dal, UUID chi) {
        PassaggioLivello p = passaggi.findById(passaggioId).orElse(null);
        if (p == null || !p.aperto()) return;
        var adesso = orologio.instant();
        p.chiudi(PassaggioLivello.Stato.FATTO, adesso);
        for (Attesa a : attese.delPassaggio(passaggioId)) {
            a.chiudi(Attesa.Stato.RINUNCIA, adesso);
            eventi.save(new AttesaEvento(a.getId(), AttesaEvento.Tipo.ENTRATA_ALTROVE, adesso, chi));
        }
        iscrizioni.findById(p.getIscrizioneId()).ifPresent(vecchia -> {
            LocalDate ultimo = dal.minusDays(1);
            if (ultimo.isBefore(vecchia.getDal())) ultimo = vecchia.getDal();
            if (vecchia.getAl() == null || vecchia.getAl().isAfter(ultimo)) vecchia.termina(ultimo);
        });
    }
}
