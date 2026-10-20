package it.leonexiii.gestionale.identita;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API pubblica del modulo per gli altri moduli: dati essenziali delle persone. */
@Service
public class Persone {

    private final PersonaRepository repo;
    private final LegameRepository legami;

    Persone(PersonaRepository repo, LegameRepository legami) {
        this.repo = repo;
        this.legami = legami;
    }

    // ── Anagrafica completa ─────────────────────────────────────────────────

    public record Anagrafica(UUID id, String nome, String cognome, LocalDate dataNascita, String sesso, String luogoNascita, String codiceFiscale,
                             String indirizzo, String cap, String citta, String provincia, String nazione, String note, String email, String telefono) {}

    public record ModificaAnagrafica(String nome, String cognome, LocalDate dataNascita, String sesso, String luogoNascita, String codiceFiscale,
                                     String indirizzo, String cap, String citta, String provincia, String nazione, String note) {}

    @Transactional(readOnly = true)
    public Anagrafica anagrafica(UUID id) {
        return anagrafica(trova(id));
    }

    private static Anagrafica anagrafica(Persona p) {
        return new Anagrafica(p.getId(), p.getNome(), p.getCognome(), p.getDataNascita(), p.getSesso(), p.getLuogoNascita(), p.getCodiceFiscale(),
            p.getIndirizzo(), p.getCap(), p.getCitta(), p.getProvincia(), p.getNazione(), p.getNote(), p.getEmail(), p.getTelefono());
    }

    @Transactional
    public Anagrafica aggiornaAnagrafica(UUID id, ModificaAnagrafica in) {
        Persona p = trova(id);
        String nome = obbligatorio(in.nome(), "il nome", 80);
        String cognome = obbligatorio(in.cognome(), "il cognome", 80);
        if (in.dataNascita() != null && in.dataNascita().isAfter(LocalDate.now(UtenteService.FUSO))) throw new IllegalArgumentException("La data di nascita è nel futuro.");
        String sesso = facoltativo(in.sesso(), 1);
        if (sesso != null && !sesso.equals("M") && !sesso.equals("F")) throw new IllegalArgumentException("Sesso: M o F.");
        String cf = CodiceFiscale.normalizza(in.codiceFiscale());
        if (cf != null && repo.findByCodiceFiscale(cf).filter(x -> !x.getId().equals(id)).isPresent())
            throw new it.leonexiii.gestionale.condiviso.Conflitto("Questo codice fiscale è già di un'altra persona: cercala per non creare doppioni.");
        String provincia = facoltativo(in.provincia(), 2);
        p.anagrafica(nome, cognome, in.dataNascita(), sesso, facoltativo(in.luogoNascita(), 80), cf, facoltativo(in.indirizzo(), 150),
            facoltativo(in.cap(), 10), facoltativo(in.citta(), 80), provincia == null ? null : provincia.toUpperCase(java.util.Locale.ROOT),
            facoltativo(in.nazione(), 60), facoltativo(in.note(), 1000));
        repo.flush();
        return anagrafica(p);
    }

    private static String obbligatorio(String s, String cosa, int max) {
        String t = s == null ? "" : s.strip();
        if (t.isEmpty()) throw new IllegalArgumentException("Indica " + cosa + ".");
        if (t.length() > max) throw new IllegalArgumentException("Troppo lungo: " + cosa + ".");
        return t;
    }

    private static String facoltativo(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.strip();
        if (t.length() > max) throw new IllegalArgumentException("Testo troppo lungo (massimo " + max + " caratteri).");
        return t;
    }

    private Persona trova(UUID id) {
        return repo.findById(id).orElseThrow(() -> new it.leonexiii.gestionale.condiviso.NonTrovato("Persona non trovata."));
    }

    // ── Tutori e nucleo famiglia ────────────────────────────────────────────

    /** Un familiare: un tutore, una persona di cui si è tutori, o un fratello (stesso tutore; senza legame proprio). */
    public record Familiare(UUID legameId, UUID personaId, String nome, String cognome, LocalDate dataNascita, String relazione,
                            boolean comunicazioni, boolean delegatoRitiro, String email, String telefono) {}

    public record NuovoTutore(UUID tutoreId, String nome, String cognome, String codiceFiscale, String email, String telefono,
                              String relazione, Boolean comunicazioni, Boolean delegatoRitiro) {}

    @Transactional(readOnly = true)
    public List<Familiare> tutoriDi(UUID personaId) {
        List<Legame> l = legami.attiviDi(List.of(personaId));
        Map<UUID, Persona> p = repo.findAllById(l.stream().map(Legame::getTutoreId).toList()).stream().collect(Collectors.toMap(Persona::getId, x -> x));
        return l.stream().filter(x -> p.containsKey(x.getTutoreId())).map(x -> familiare(x, p.get(x.getTutoreId())))
            .sorted(java.util.Comparator.comparing(Familiare::cognome).thenComparing(Familiare::nome)).toList();
    }

    @Transactional(readOnly = true)
    public List<Familiare> assistitiDi(UUID tutoreId) {
        List<Legame> l = legami.attiviCome(List.of(tutoreId));
        Map<UUID, Persona> p = repo.findAllById(l.stream().map(Legame::getPersonaId).toList()).stream().collect(Collectors.toMap(Persona::getId, x -> x));
        return l.stream().filter(x -> p.containsKey(x.getPersonaId())).map(x -> familiare(x, p.get(x.getPersonaId())))
            .sorted(java.util.Comparator.comparing(Familiare::cognome).thenComparing(Familiare::nome)).toList();
    }

    /** Chi ha almeno un tutore in comune (fratelli, cugini seguiti dagli stessi nonni…). */
    @Transactional(readOnly = true)
    public List<Familiare> fratelliDi(UUID personaId) {
        List<UUID> tutori = legami.attiviDi(List.of(personaId)).stream().map(Legame::getTutoreId).toList();
        if (tutori.isEmpty()) return List.of();
        List<UUID> altri = legami.attiviCome(tutori).stream().map(Legame::getPersonaId).filter(x -> !x.equals(personaId)).distinct().toList();
        return repo.findAllById(altri).stream()
            .map(x -> new Familiare(null, x.getId(), x.getNome(), x.getCognome(), x.getDataNascita(), null, false, false, x.getEmail(), x.getTelefono()))
            .sorted(java.util.Comparator.comparing(Familiare::cognome).thenComparing(Familiare::nome)).toList();
    }

    private static Familiare familiare(Legame l, Persona p) {
        return new Familiare(l.getId(), p.getId(), p.getNome(), p.getCognome(), p.getDataNascita(), l.getRelazione(), l.isComunicazioni(), l.isDelegatoRitiro(),
            p.getEmail(), p.getTelefono());
    }

    /** Un tutore già in anagrafica (tutoreId, o trovato per codice fiscale) o nuovo (nome, cognome, contatti). */
    @Transactional
    public UUID aggiungiTutore(UUID personaId, NuovoTutore in) {
        trova(personaId);
        Persona tutore;
        String cf = CodiceFiscale.normalizza(in.codiceFiscale());
        if (in.tutoreId() != null) tutore = trova(in.tutoreId());
        else if (cf != null && repo.findByCodiceFiscale(cf).isPresent()) tutore = repo.findByCodiceFiscale(cf).orElseThrow();
        else {
            tutore = repo.save(new Persona(obbligatorio(in.nome(), "il nome del tutore", 80), obbligatorio(in.cognome(), "il cognome del tutore", 80), null));
            tutore.anagrafica(tutore.getNome(), tutore.getCognome(), null, null, null, cf, null, null, null, null, null, null);
            Contatti c = verificaContatti(in.email(), in.telefono());
            tutore.contatti(c.email(), c.telefono());
        }
        Legame.Relazione r;
        try {
            r = Legame.Relazione.valueOf(String.valueOf(in.relazione()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Relazione non valida: MADRE, PADRE, TUTORE, NONNO, ALTRO.");
        }
        UUID tutoreId = tutore.getId();
        if (legami.attiviDi(List.of(personaId)).stream().anyMatch(l -> l.getTutoreId().equals(tutoreId)))
            throw new it.leonexiii.gestionale.condiviso.Conflitto("È già tra i tutori.");
        repo.flush();
        return legami.saveAndFlush(new Legame(personaId, tutoreId, r, in.comunicazioni() == null || in.comunicazioni(),
            in.delegatoRitiro() == null || in.delegatoRitiro(), LocalDate.now(UtenteService.FUSO))).getId();
    }

    @Transactional
    public void modificaLegame(UUID legameId, String relazione, boolean comunicazioni, boolean delegatoRitiro) {
        Legame l = legami.findById(legameId).orElseThrow(() -> new it.leonexiii.gestionale.condiviso.NonTrovato("Tutore non trovato."));
        try {
            l.modifica(Legame.Relazione.valueOf(String.valueOf(relazione)), comunicazioni, delegatoRitiro);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Relazione non valida: MADRE, PADRE, TUTORE, NONNO, ALTRO.");
        }
    }

    @Transactional
    public void chiudiLegame(UUID legameId) {
        Legame l = legami.findById(legameId).orElseThrow(() -> new it.leonexiii.gestionale.condiviso.NonTrovato("Tutore non trovato."));
        LocalDate oggi = LocalDate.now(UtenteService.FUSO);
        if (!l.getDal().isBefore(oggi)) legami.delete(l);
        else l.chiudi(oggi);
    }

    /**
     * A chi scrivere per una persona: i tutori che ricevono le comunicazioni e hanno un'email; se non ce ne sono, la
     * persona stessa. (Per i promemoria delle scadenze.)
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<String>> destinatari(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, List<Legame>> perPersona = legami.attiviDi(ids).stream().filter(Legame::isComunicazioni).collect(Collectors.groupingBy(Legame::getPersonaId));
        Set<UUID> tutte = new java.util.HashSet<>(ids);
        perPersona.values().forEach(l -> l.forEach(x -> tutte.add(x.getTutoreId())));
        Map<UUID, Persona> p = repo.findAllById(tutte).stream().collect(Collectors.toMap(Persona::getId, x -> x));
        Map<UUID, List<String>> out = new java.util.HashMap<>();
        for (UUID id : ids) {
            List<String> email = perPersona.getOrDefault(id, List.of()).stream().map(l -> p.get(l.getTutoreId())).filter(java.util.Objects::nonNull)
                .map(Persona::getEmail).filter(e -> e != null && !e.isBlank()).distinct().toList();
            if (email.isEmpty() && p.containsKey(id) && p.get(id).getEmail() != null) email = List.of(p.get(id).getEmail());
            out.put(id, email);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> nomiCompleti(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return repo.findAllById(ids).stream().collect(Collectors.toMap(Persona::getId, Persona::nomeCompleto));
    }

    /** Chi ha il permesso nell'impianto oggi (es. «Fa i turni»: lo staff della piscina, per assegnare i turni). */
    @Transactional(readOnly = true)
    public List<PersonaBreve> conPermesso(Permesso permesso, UUID impiantoId) {
        return repo.conPermesso(permesso.name(), impiantoId, LocalDate.now(UtenteService.FUSO)).stream()
            .map(p -> new PersonaBreve(p.getId(), p.nomeCompleto()))
            .toList();
    }

    /** Quanto serve per mostrare una persona in un elenco (es. gli iscritti a un corso, con l'età). */
    public record PersonaScheda(UUID id, String nome, String cognome, LocalDate dataNascita) {
        public String nomeCompleto() {
            return nome + " " + cognome;
        }
    }

    @Transactional(readOnly = true)
    public Map<UUID, PersonaScheda> schede(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return repo.findAllById(ids).stream()
            .collect(Collectors.toMap(Persona::getId, p -> new PersonaScheda(p.getId(), p.getNome(), p.getCognome(), p.getDataNascita())));
    }

    @Transactional(readOnly = true)
    public List<PersonaScheda> cerca(String testo, int quante) {
        String t = testo == null ? "" : testo.strip().toLowerCase(java.util.Locale.ROOT);
        if (t.length() < 2) return List.of();
        String cf = t.replaceAll("\\s", "").toUpperCase(java.util.Locale.ROOT);
        return repo.cerca("%" + t.replace("%", "").replace("_", "") + "%", cf, org.springframework.data.domain.PageRequest.of(0, quante)).stream()
            .map(p -> new PersonaScheda(p.getId(), p.getNome(), p.getCognome(), p.getDataNascita())).toList();
    }

    /** Nuova persona senza account (un cliente): nome e cognome obbligatori, data di nascita facoltativa. */
    @Transactional
    public PersonaScheda crea(String nome, String cognome, LocalDate dataNascita) {
        String n = nome == null ? "" : nome.strip();
        String c = cognome == null ? "" : cognome.strip();
        if (n.isEmpty() || c.isEmpty()) throw new IllegalArgumentException("Indica nome e cognome.");
        if (n.length() > 80 || c.length() > 80) throw new IllegalArgumentException("Nome o cognome troppo lunghi.");
        if (dataNascita != null && dataNascita.isAfter(LocalDate.now(UtenteService.FUSO))) throw new IllegalArgumentException("La data di nascita è nel futuro.");
        Persona p = repo.saveAndFlush(new Persona(n, c, dataNascita));
        return new PersonaScheda(p.getId(), p.getNome(), p.getCognome(), p.getDataNascita());
    }

    /** Email e telefono di una persona (per i minori, finché non ci sono i tutori, quelli della famiglia). */
    public record Contatti(String email, String telefono) {}

    @Transactional(readOnly = true)
    public Map<UUID, Contatti> contatti(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return repo.findAllById(ids).stream().collect(Collectors.toMap(Persona::getId, p -> new Contatti(p.getEmail(), p.getTelefono())));
    }

    @Transactional
    public Contatti aggiornaContatti(UUID id, String email, String telefono) {
        Persona p = trova(id);
        Contatti c = verificaContatti(email, telefono);
        p.contatti(c.email(), c.telefono());
        return c;
    }

    private static Contatti verificaContatti(String email, String telefono) {
        String e = email == null || email.isBlank() ? null : email.strip();
        String t = telefono == null || telefono.isBlank() ? null : telefono.strip();
        if (e != null && (e.length() > 200 || !e.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))) throw new IllegalArgumentException("Email non valida.");
        if (t != null && (t.length() > 40 || !t.matches("[0-9+ ./()-]{5,40}"))) throw new IllegalArgumentException("Telefono non valido.");
        return new Contatti(e, t);
    }

    @Transactional(readOnly = true)
    public boolean haPermesso(UUID personaId, Permesso permesso, UUID impiantoId) {
        return conPermesso(permesso, impiantoId).stream().anyMatch(p -> p.id().equals(personaId));
    }
}
