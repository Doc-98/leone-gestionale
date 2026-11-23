package it.leonexiii.gestionale.listino;

import it.leonexiii.gestionale.identita.Autorizzazioni;
import it.leonexiii.gestionale.identita.Permesso;
import it.leonexiii.gestionale.identita.Utente;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
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

/** Il listino di una stagione: lo legge chi vende (Clienti), lo cambia chi ha «Impostazioni della società». */
@RestController
@RequestMapping("/api/listino")
class ListinoController {

    record ModificaRegola(boolean attiva, Map<String, Object> parametri) {}

    record Copia(UUID da, UUID a, BigDecimal aumentoPercentuale) {}

    private final Listino listino;
    private final Autorizzazioni autorizzazioni;

    ListinoController(Listino listino, Autorizzazioni autorizzazioni) {
        this.listino = listino;
        this.autorizzazioni = autorizzazioni;
    }

    @GetMapping
    @PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI', 'IMPOSTAZIONI')")
    Listino.PaginaListino pagina(@AuthenticationPrincipal Utente u, @RequestParam UUID stagione) {
        // se un'offerta si può eliminare (e perché) lo vede solo chi gestisce le impostazioni
        return listino.pagina(stagione, autorizzazioni.puo(u, Permesso.IMPOSTAZIONI));
    }

    @PostMapping("/offerte")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    Listino.OffertaDto crea(@RequestBody Listino.NuovaOfferta in) {
        return listino.crea(in);
    }

    @PutMapping("/offerte/{id}")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    Listino.OffertaDto modifica(@PathVariable UUID id, @RequestBody Listino.NuovaOfferta in) {
        return listino.modifica(id, in);
    }

    /** Elimina un'offerta mai venduta; 409 se è stata venduta (si spegne). */
    @DeleteMapping("/offerte/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    void elimina(@PathVariable UUID id) {
        listino.elimina(id);
    }

    @PutMapping("/regole/{stagione}/{tipo}")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    Listino.Regola regola(@PathVariable UUID stagione, @PathVariable String tipo, @RequestBody ModificaRegola in) {
        return listino.modificaRegola(stagione, tipo, in.attiva(), in.parametri());
    }

    @PostMapping("/copia")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    Map<String, Object> copia(@RequestBody Copia in) {
        // «saltate»: le offerte rimaste senza prezzi (es. dopo l'eliminazione di un gruppo) non si copiano
        Listino.EsitoCopia e = listino.copia(in.da(), in.a(), in.aumentoPercentuale());
        return Map.of("offerte", e.offerte(), "saltate", e.saltate());
    }
}
