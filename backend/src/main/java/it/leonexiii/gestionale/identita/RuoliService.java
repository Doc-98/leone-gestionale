package it.leonexiii.gestionale.identita;

import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.struttura.Impianti;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ruoli, permessi e assegnazioni (pagina «Ruoli e permessi», permesso RUOLI). */
@Service
class RuoliService {

    record PermessoDto(String codice, String gruppo, String nome, String descrizione, boolean potere) {}

    record AssegnatoDto(UUID assegnazioneId, UUID accountId, UUID personaId, String nome, String cognome, String email, UUID impiantoId, LocalDate dal) {}

    record RuoloDto(String codice, String nome, String descrizione, boolean diSistema, boolean modificabile, Set<Permesso> permessi, List<AssegnatoDto> persone) {}

    record AccountDto(UUID accountId, UUID personaId, String nome, String cognome, String email, boolean attivo) {}

    record Pagina(List<PermessoDto> permessi, List<RuoloDto> ruoli, List<AccountDto> account, List<Impianti.ImpiantoBreve> impianti) {}

    record NuovoRuolo(String nome, String descrizione, String copiaDa) {}

    record ModificaRuolo(String nome, String descrizione, Set<Permesso> permessi) {}

    record NuovaAssegnazione(UUID accountId, UUID impiantoId) {}

    private final RuoloDefinitoRepository ruoli;
    private final RuoloPermessoRepository permessi;
    private final AssegnazioneRuoloRepository assegnazioni;
    private final AccountRepository account;
    private final Autorizzazioni autorizzazioni;
    private final Impianti impianti;

    RuoliService(RuoloDefinitoRepository ruoli, RuoloPermessoRepository permessi, AssegnazioneRuoloRepository assegnazioni, AccountRepository account,
                 Autorizzazioni autorizzazioni, Impianti impianti) {
        this.ruoli = ruoli;
        this.permessi = permessi;
        this.assegnazioni = assegnazioni;
        this.account = account;
        this.autorizzazioni = autorizzazioni;
        this.impianti = impianti;
    }

    @Transactional(readOnly = true)
    Pagina pagina() {
        LocalDate oggi = LocalDate.now(UtenteService.FUSO);
        List<Account> tutti = account.tuttiConPersona();
        Map<UUID, Account> perId = tutti.stream().collect(Collectors.toMap(Account::getId, Function.identity()));
        Map<String, List<AssegnazioneRuolo>> perRuolo = assegnazioni.tuttiValidi(oggi).stream().collect(Collectors.groupingBy(AssegnazioneRuolo::getRuolo));
        List<RuoloDto> elenco = ruoli.findAll().stream()
            .sorted(Comparator.comparing((RuoloDefinito r) -> !r.isDiSistema()).thenComparing(r -> ordineSistema(r.getCodice())).thenComparing(RuoloDefinito::getNome))
            .map(r -> new RuoloDto(r.getCodice(), r.getNome(), r.getDescrizione(), r.isDiSistema(), !RuoloDefinito.ADMIN.equals(r.getCodice()),
                autorizzazioni.permessiDelRuolo(r.getCodice()),
                perRuolo.getOrDefault(r.getCodice(), List.of()).stream().filter(a -> perId.containsKey(a.getAccountId())).map(a -> {
                    Account acc = perId.get(a.getAccountId());
                    return new AssegnatoDto(a.getId(), acc.getId(), acc.getPersona().getId(), acc.getPersona().getNome(), acc.getPersona().getCognome(),
                        acc.getEmail(), a.getImpiantoId(), a.getDal());
                }).sorted(Comparator.comparing(AssegnatoDto::cognome).thenComparing(AssegnatoDto::nome)).toList()))
            .toList();
        return new Pagina(Arrays.stream(Permesso.values()).map(p -> new PermessoDto(p.name(), p.gruppo(), p.nome(), p.descrizione(), p.potere())).toList(),
            elenco,
            tutti.stream().map(a -> new AccountDto(a.getId(), a.getPersona().getId(), a.getPersona().getNome(), a.getPersona().getCognome(), a.getEmail(), a.isAttivo())).toList(),
            impianti.tutti());
    }

    private static int ordineSistema(String codice) {
        return switch (codice) {
            case "ADMIN" -> 0;
            case "COORDINATORE" -> 1;
            case "SEGRETERIA" -> 2;
            case "STAFF" -> 3;
            default -> 4;
        };
    }

    @Transactional
    String crea(NuovoRuolo in) {
        String base = codiceDa(in.nome());
        String codice = base;
        for (int i = 2; ruoli.existsById(codice); i++) codice = base + "_" + i;
        ruoli.saveAndFlush(new RuoloDefinito(codice, in.nome(), in.descrizione()));
        if (in.copiaDa() != null && !in.copiaDa().isBlank()) {
            for (Permesso p : autorizzazioni.permessiDelRuolo(in.copiaDa())) permessi.save(new RuoloPermesso(codice, p));
            permessi.flush();
        }
        autorizzazioni.invalida();
        return codice;
    }

    /** «Istruttore senior» → «ISTRUTTORE_SENIOR». */
    static String codiceDa(String nome) {
        String n = Normalizer.normalize(nome == null ? "" : nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (n.isEmpty() || !Character.isLetter(n.charAt(0))) n = "R_" + n;
        return n.length() > 34 ? n.substring(0, 34) : n;
    }

    @Transactional
    void modifica(Utente u, String codice, ModificaRuolo in) {
        RuoloDefinito r = ruoli.findById(codice).orElseThrow(() -> new NonTrovato("Ruolo non trovato."));
        r.modifica(in.nome(), in.descrizione());
        if (RuoloDefinito.ADMIN.equals(codice)) {
            if (in.permessi() != null && !in.permessi().equals(autorizzazioni.permessiDelRuolo(codice)))
                throw new IllegalArgumentException("ADMIN ha sempre tutti i permessi.");
        } else if (in.permessi() != null) {
            // chi non è admin non può darsi poteri che non ha
            if (!autorizzazioni.admin(u)) {
                Set<Permesso> suoi = autorizzazioni.permessi(u);
                Set<Permesso> nuovi = EnumSet.noneOf(Permesso.class);
                nuovi.addAll(in.permessi());
                nuovi.removeAll(autorizzazioni.permessiDelRuolo(codice));
                if (nuovi.stream().anyMatch(p -> p.potere() && !suoi.contains(p))) throw new AccessDeniedException("Non puoi dare permessi che non hai.");
            }
            permessi.eliminaDelRuolo(codice);
            permessi.flush();
            for (Permesso p : in.permessi()) permessi.save(new RuoloPermesso(codice, p));
            permessi.flush();
        }
        autorizzazioni.invalida();
    }

    @Transactional
    void elimina(String codice) {
        RuoloDefinito r = ruoli.findById(codice).orElseThrow(() -> new NonTrovato("Ruolo non trovato."));
        if (r.isDiSistema()) throw new Conflitto("I ruoli di sistema non si eliminano.");
        long n = assegnazioni.countByRuolo(codice);
        if (n > 0) throw new Conflitto("Il ruolo è (o è stato) assegnato: toglilo prima alle persone. Resta nello storico, quindi non si può eliminare; puoi togliergli tutti i permessi.");
        ruoli.delete(r);
        autorizzazioni.invalida();
    }

    @Transactional
    UUID assegna(Utente u, String codice, NuovaAssegnazione in) {
        ruoli.findById(codice).orElseThrow(() -> new NonTrovato("Ruolo non trovato."));
        if (RuoloDefinito.ADMIN.equals(codice) && !autorizzazioni.admin(u)) throw new AccessDeniedException("Solo un admin può nominare un admin.");
        if (in.accountId() == null || !account.existsById(in.accountId())) throw new NonTrovato("Account non trovato.");
        if (in.impiantoId() != null && impianti.tutti().stream().noneMatch(i -> i.id().equals(in.impiantoId()))) throw new NonTrovato("Impianto non trovato.");
        LocalDate oggi = LocalDate.now(UtenteService.FUSO);
        boolean doppione = assegnazioni.validi(in.accountId(), oggi).stream()
            .anyMatch(a -> a.getRuolo().equals(codice) && java.util.Objects.equals(a.getImpiantoId(), in.impiantoId()));
        if (doppione) throw new Conflitto("Ha già questo ruolo.");
        UUID id = assegnazioni.saveAndFlush(new AssegnazioneRuolo(in.accountId(), codice, in.impiantoId(), oggi)).getId();
        autorizzazioni.invalida();
        return id;
    }

    /** Da oggi non ha più il ruolo (resta nello storico); se era stato dato oggi, si toglie e basta. */
    @Transactional
    void togli(Utente u, UUID assegnazioneId) {
        AssegnazioneRuolo a = assegnazioni.findById(assegnazioneId).orElseThrow(() -> new NonTrovato("Assegnazione non trovata."));
        if (RuoloDefinito.ADMIN.equals(a.getRuolo())) {
            if (!autorizzazioni.admin(u)) throw new AccessDeniedException("Solo un admin può togliere il ruolo di admin.");
            if (a.getAccountId().equals(u.accountId())) throw new Conflitto("Non puoi togliere a te stesso il ruolo di admin.");
        }
        LocalDate oggi = LocalDate.now(UtenteService.FUSO);
        if (!a.getDal().isBefore(oggi)) assegnazioni.delete(a);
        else a.chiudi(oggi);
        assegnazioni.flush();
        autorizzazioni.invalida();
    }

}
