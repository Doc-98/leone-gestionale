package it.leonexiii.gestionale.listino;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

    ListinoController(Listino listino) {
        this.listino = listino;
    }

    @GetMapping
    @PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI', 'IMPOSTAZIONI')")
    Listino.PaginaListino pagina(@RequestParam UUID stagione) {
        return listino.pagina(stagione);
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
    Map<String, Integer> copia(@RequestBody Copia in) {
        return Map.of("offerte", listino.copia(in.da(), in.a(), in.aumentoPercentuale()));
    }
}
