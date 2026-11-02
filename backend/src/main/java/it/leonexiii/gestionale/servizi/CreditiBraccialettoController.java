package it.leonexiii.gestionale.servizi;

import it.leonexiii.gestionale.identita.Autorizzazioni;
import it.leonexiii.gestionale.identita.Permesso;
import it.leonexiii.gestionale.identita.Utente;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** I crediti scritti sul braccialetto (ADR 0025): lettura, ricarica in due tempi, azzeramento e la macchina simulata. */
@RestController
@RequestMapping("/api/crediti-braccialetto")
class CreditiBraccialettoController {

    private final CreditiBraccialetto crediti;
    private final Autorizzazioni autorizzazioni;

    CreditiBraccialettoController(CreditiBraccialetto crediti, Autorizzazioni autorizzazioni) {
        this.crediti = crediti;
        this.autorizzazioni = autorizzazioni;
    }

    /** Dove sta il credito, il formato e cosa deve leggere il lettore (le chiavi solo a chi ricarica). */
    @GetMapping("/stato")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI', 'BORSELLINO', 'CLIENTI')")
    CreditiBraccialetto.Stato stato(@AuthenticationPrincipal Utente u) {
        var s = crediti.stato();
        if (!autorizzazioni.puo(u, Permesso.BORSELLINO) && !autorizzazioni.puo(u, Permesso.IMPOSTAZIONI))
            return new CreditiBraccialetto.Stato(s.sulBraccialetto(), s.formato(), s.nomeFormato(), s.pronto(), s.nota(), null);
        return s;
    }

    @GetMapping("/persone/{id}")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI', 'PAGAMENTI', 'BORSELLINO')")
    List<CreditiBraccialetto.SaldoDto> saldi(@PathVariable UUID id) {
        return crediti.saldi(id);
    }

    /** {"memoria": {...}} dal lettore, oppure {"crediti": n} scritti a mano dal display della macchina. */
    @PostMapping("/{braccialettoId}/letture")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'BORSELLINO', 'CLIENTI')")
    CreditiBraccialetto.EsitoLettura leggi(@AuthenticationPrincipal Utente u, @PathVariable UUID braccialettoId, @RequestBody CreditiBraccialetto.Lettura in) {
        return crediti.leggi(u.personaId(), braccialettoId, in);
    }

    @PostMapping("/{braccialettoId}/ricariche")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'BORSELLINO')")
    CreditiBraccialetto.RicaricaPreparata preparaRicarica(@AuthenticationPrincipal Utente u, @PathVariable UUID braccialettoId,
                                                         @RequestBody CreditiBraccialetto.NuovaRicarica in) {
        return crediti.preparaRicarica(u.personaId(), braccialettoId, in);
    }

    @PostMapping("/ricariche/{operazione}/conferma")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'BORSELLINO')")
    Map<String, UUID> confermaRicarica(@AuthenticationPrincipal Utente u, @PathVariable UUID operazione, @RequestBody CreditiBraccialetto.Conferma in) {
        return Map.of("pagamentoId", crediti.confermaRicarica(u.personaId(), operazione, in, autorizzazioni.puo(u, Permesso.PAGAMENTI_FLESSIBILI)));
    }

    /** {"memoria": {...}} se si è riusciti a rileggere il braccialetto (null se no). */
    @PostMapping("/ricariche/{operazione}/annulla")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'BORSELLINO')")
    void annullaRicarica(@PathVariable UUID operazione, @RequestBody(required = false) CreditiBraccialetto.Lettura in) {
        crediti.annullaRicarica(operazione, in == null ? null : in.memoria());
    }

    @PostMapping("/{braccialettoId}/azzeramento")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'BORSELLINO', 'CLIENTI')")
    CreditiBraccialetto.Azzeramento azzera(@AuthenticationPrincipal Utente u, @PathVariable UUID braccialettoId, @RequestBody CreditiBraccialetto.Lettura in) {
        return crediti.azzera(u.personaId(), braccialettoId, in.memoria());
    }

    @PostMapping("/macchina-simulata")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI', 'BORSELLINO')")
    CreditiBraccialetto.EsitoMacchina simulaMacchina(@RequestBody CreditiBraccialetto.UsoMacchina in) {
        return crediti.simulaMacchina(in);
    }
}
