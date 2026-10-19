package it.leonexiii.gestionale.pianovasca;

import it.leonexiii.gestionale.identita.Utente;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Piano vasca: lettura per tutti, modifiche per coordinatori e admin (con il controllo per impianto nei servizi). */
@RestController
@RequestMapping("/api")
class PianoController {

    private final PianoService piano;
    private final GestionePianoService gestione;
    private final FasceService fasce;
    private final IscrizioniService iscrizioni;

    PianoController(PianoService piano, GestionePianoService gestione, FasceService fasce, IscrizioniService iscrizioni) {
        this.piano = piano;
        this.gestione = gestione;
        this.fasce = fasce;
        this.iscrizioni = iscrizioni;
    }

    // ── Fasce ───────────────────────────────────────────────────────────────

    @GetMapping("/fasce")
    List<FasceService.FasciaDto> fasce(@AuthenticationPrincipal Utente u) {
        return fasce.fasce(u, null, null);
    }

    /** {"vascaId", "giorni": [1,2,4], "inizio", "fine", "assetto": 6, "validaDal", "validaAl"} */
    @PostMapping("/fasce")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    List<FasceService.FasciaDto> creaFasce(@AuthenticationPrincipal Utente u, @RequestBody FasceService.NuoveFasce in) {
        return fasce.crea(u, in);
    }

    @PutMapping("/fasce/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    FasceService.EsitoFascia modificaFascia(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody FasceService.ModificaFascia in) {
        return fasce.modifica(u, id, in);
    }

    /** {"assetto": 5}: i corsi della fascia passano sulle corsie con lo stesso numero. */
    @PostMapping("/fasce/{id}/assetto")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    FasceService.EsitoFascia cambiaAssetto(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody FasceService.NuovoAssetto in) {
        return fasce.cambiaAssetto(u, id, in);
    }

    @DeleteMapping("/fasce/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    void eliminaFascia(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        fasce.elimina(u, id);
    }

    // ── Iscritti e presenze ─────────────────────────────────────────────────

    @GetMapping("/lezioni/{id}/iscritti")
    IscrizioniService.ElencoIscritti iscritti(@AuthenticationPrincipal Utente u, @PathVariable UUID id,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return iscrizioni.elenco(u, id, data);
    }

    /** {"personaId"} oppure {"nuovaPersona": {"nome", "cognome", "dataNascita"}}, con "dal" e "forza" (soprannumero). */
    @PostMapping("/lezioni/{id}/iscritti")
    @ResponseStatus(HttpStatus.CREATED)
    IscrizioniService.IscrittoDto iscrivi(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody IscrizioniService.NuovaIscrizione in) {
        return iscrizioni.iscrivi(u, id, in);
    }

    @PostMapping("/iscrizioni/{id}/termina")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void terminaIscrizione(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody IscrizioniService.Termine in) {
        iscrizioni.termina(u, id, in);
    }

    /** {"data", "presenze": [{"personaId", "presente"}]} */
    @PutMapping("/lezioni/{id}/presenze")
    IscrizioniService.ElencoIscritti presenze(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody IscrizioniService.Presenze in) {
        return iscrizioni.segnaPresenze(u, id, in);
    }

    /** Spazi e occorrenze (lezioni ed eventi) del periodo, al massimo due mesi. */
    @GetMapping("/piano")
    PianoService.Piano piano(@AuthenticationPrincipal Utente u,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate da,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate a) {
        return piano.piano(u, da, a);
    }

    @GetMapping("/corsi")
    List<GestionePianoService.CorsoDto> corsi(@AuthenticationPrincipal Utente u) {
        return gestione.corsi(u);
    }

    @PostMapping("/corsi")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    GestionePianoService.CorsoDto creaCorso(@AuthenticationPrincipal Utente u, @RequestBody GestionePianoService.CorsoInput in) {
        return gestione.creaCorso(u, in);
    }

    @PutMapping("/corsi/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    GestionePianoService.CorsoDto modificaCorso(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody GestionePianoService.CorsoInput in) {
        return gestione.modificaCorso(u, id, in);
    }

    @PostMapping("/corsi/{id}/lezioni")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    GestionePianoService.Esito<GestionePianoService.LezioneDto> aggiungiLezione(@AuthenticationPrincipal Utente u, @PathVariable UUID id,
                                                                               @RequestBody GestionePianoService.LezioneInput in) {
        return gestione.aggiungiLezione(u, id, in);
    }

    @PutMapping("/lezioni/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    GestionePianoService.Esito<GestionePianoService.LezioneDto> modificaLezione(@AuthenticationPrincipal Utente u, @PathVariable UUID id,
                                                                               @RequestBody GestionePianoService.LezioneInput in) {
        return gestione.modificaLezione(u, id, in);
    }

    @DeleteMapping("/lezioni/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    void togliLezione(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        gestione.togliLezione(u, id);
    }

    /** {"data", "tipo": "SALTATA" | "SPOSTATA", "nuovaData", "nuovoInizio", "nuovoFine", "motivo"} */
    @PostMapping("/lezioni/{id}/eccezioni")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    GestionePianoService.Esito<UUID> aggiungiEccezione(@AuthenticationPrincipal Utente u, @PathVariable UUID id,
                                                      @RequestBody GestionePianoService.EccezioneInput in) {
        return gestione.aggiungiEccezione(u, id, in);
    }

    @DeleteMapping("/eccezioni/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    void togliEccezione(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        gestione.togliEccezione(u, id);
    }

    @GetMapping("/eventi/{id}")
    GestionePianoService.EventoDto evento(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        return gestione.evento(u, id, true);
    }

    @PostMapping("/eventi")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    GestionePianoService.Esito<GestionePianoService.EventoDto> creaEvento(@AuthenticationPrincipal Utente u, @RequestBody GestionePianoService.EventoInput in) {
        return gestione.creaEvento(u, in);
    }

    @PutMapping("/eventi/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    GestionePianoService.Esito<GestionePianoService.EventoDto> modificaEvento(@AuthenticationPrincipal Utente u, @PathVariable UUID id,
                                                                             @RequestBody GestionePianoService.EventoInput in) {
        return gestione.modificaEvento(u, id, in);
    }

    @PostMapping("/eventi/{id}/annulla")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    GestionePianoService.EventoDto annullaEvento(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        return gestione.annullaEvento(u, id);
    }
}
