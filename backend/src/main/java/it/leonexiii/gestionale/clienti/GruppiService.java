package it.leonexiii.gestionale.clienti;

import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.Persone;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * I gruppi di clienti: li definisce chi ha «Impostazioni della società», ci mette le persone chi ha «Clienti».
 * Prendono il posto della «categoria» del vecchio gestionale (ADR 0018).
 */
@Service
class GruppiService {

    record GruppoDto(UUID id, String nome, String descrizione, boolean attivo, long persone,
                     Integer precedenza, Integer giorniPerPagare, boolean dichiarabile, String fraseDichiarazione, long daVerificare) {}

    /**
     * Senza i campi dei vantaggi (vecchi client) restano come sono. conPrecedenza: accesa mette il gruppo in fondo
     * all'ordine delle precedenze (se non c'era già), spenta lo toglie e ricompatta l'ordine.
     */
    record NuovoGruppo(String nome, String descrizione, Boolean attivo,
                       Boolean conPrecedenza, Integer giorniPerPagare, Boolean dichiarabile, String fraseDichiarazione) {}

    record OrdinePrecedenze(List<UUID> ordine) {}

    record Membro(UUID appartenenzaId, UUID personaId, String nome, String cognome, LocalDate dataNascita, LocalDate dal, boolean verificata) {}

    record DaVerificare(UUID appartenenzaId, UUID gruppoId, String gruppo, String frase, UUID personaId, String nome, String cognome,
                        LocalDate dataNascita, LocalDate dal) {}

    private final GruppoClientiRepository gruppi;
    private final AppartenenzaGruppoRepository appartenenze;
    private final Persone persone;
    private final Clock orologio;

    GruppiService(GruppoClientiRepository gruppi, AppartenenzaGruppoRepository appartenenze, Persone persone, Clock orologio) {
        this.gruppi = gruppi;
        this.appartenenze = appartenenze;
        this.persone = persone;
        this.orologio = orologio;
    }

    @Transactional(readOnly = true)
    List<GruppoDto> elenco() {
        Map<UUID, Long> conta = new HashMap<>();
        for (Object[] r : appartenenze.contaAttive()) conta.put((UUID) r[0], (Long) r[1]);
        Map<UUID, Long> daVerificare = new HashMap<>();
        for (Object[] r : appartenenze.contaDaVerificare()) daVerificare.put((UUID) r[0], (Long) r[1]);
        return gruppi.findAllByOrderByNome().stream()
            .map(g -> new GruppoDto(g.getId(), g.getNome(), g.getDescrizione(), g.isAttivo(), conta.getOrDefault(g.getId(), 0L),
                g.getPrecedenza(), g.getGiorniPerPagare(), g.isDichiarabile(), g.getFraseDichiarazione(), daVerificare.getOrDefault(g.getId(), 0L)))
            .toList();
    }

    @Transactional
    UUID crea(NuovoGruppo in) {
        GruppoClienti nuovo = new GruppoClienti(in.nome(), in.descrizione());
        applicaVantaggi(nuovo, in);
        GruppoClienti g = gruppi.saveAndFlush(nuovo); // con l'id già assegnato è un merge: si lavora sull'istanza gestita
        if (Boolean.TRUE.equals(in.conPrecedenza())) precedenza(g, true);
        return g.getId();
    }

    @Transactional
    void modifica(UUID id, NuovoGruppo in) {
        GruppoClienti g = gruppo(id);
        g.modifica(in.nome(), in.descrizione(), in.attivo() == null || in.attivo());
        applicaVantaggi(g, in);
        if (in.conPrecedenza() != null) precedenza(g, in.conPrecedenza());
        gruppi.flush();
    }

    /** Il nuovo ordine dei gruppi con precedenza: vanno elencati tutti, una volta sola. */
    @Transactional
    void ordinaPrecedenze(OrdinePrecedenze in) {
        List<GruppoClienti> conPrecedenza = gruppi.findAll().stream().filter(g -> g.getPrecedenza() != null).toList();
        List<UUID> ordine = in.ordine() == null ? List.of() : in.ordine();
        if (ordine.size() != conPrecedenza.size() || new java.util.HashSet<>(ordine).size() != ordine.size()
            || !conPrecedenza.stream().map(GruppoClienti::getId).allMatch(ordine::contains))
            throw new IllegalArgumentException("L'ordine deve contenere, una volta sola, tutti i gruppi con precedenza.");
        Map<UUID, GruppoClienti> perId = new HashMap<>();
        conPrecedenza.forEach(g -> perId.put(g.getId(), g));
        for (int i = 0; i < ordine.size(); i++) perId.get(ordine.get(i)).precedenza(i + 1);
        gruppi.flush();
    }

    @Transactional(readOnly = true)
    List<DaVerificare> daVerificare() {
        List<AppartenenzaGruppo> tutte = appartenenze.daVerificare();
        Map<UUID, GruppoClienti> perId = new HashMap<>();
        gruppi.findAll().forEach(g -> perId.put(g.getId(), g));
        Map<UUID, Persone.PersonaScheda> schede = persone.schede(tutte.stream().map(AppartenenzaGruppo::getPersonaId).toList());
        return tutte.stream().filter(a -> schede.containsKey(a.getPersonaId())).map(a -> {
            GruppoClienti g = perId.get(a.getGruppoId());
            Persone.PersonaScheda p = schede.get(a.getPersonaId());
            return new DaVerificare(a.getId(), g.getId(), g.getNome(), g.getFraseDichiarazione(), p.id(), p.nome(), p.cognome(), p.dataNascita(), a.getDal());
        }).toList();
    }

    /** La segreteria conferma un'appartenenza dichiarata da una famiglia: da qui dà i vantaggi del gruppo. */
    @Transactional
    void verifica(UUID appartenenzaId, UUID chi) {
        AppartenenzaGruppo a = appartenenze.findById(appartenenzaId).orElseThrow(() -> new NonTrovato("Non è in questo gruppo."));
        if (a.getAl() != null) throw new Conflitto("Non è più in questo gruppo.");
        if (a.isVerificata()) throw new Conflitto("È già verificata.");
        a.verifica(chi, orologio.instant());
    }

    /** Una famiglia dichiara di far parte di un gruppo dichiarabile: nasce da verificare (serve alle richieste, ADR 0026). */
    @Transactional
    UUID dichiara(UUID personaId, UUID gruppoId) {
        GruppoClienti g = gruppo(gruppoId);
        if (!g.isAttivo() || !g.isDichiarabile()) throw new Conflitto("Questo gruppo non si può dichiarare.");
        return appartenenze.attiveDi(personaId).stream().filter(a -> a.getGruppoId().equals(gruppoId)).findFirst()
            .map(AppartenenzaGruppo::getId)
            .orElseGet(() -> appartenenze.saveAndFlush(AppartenenzaGruppo.dichiarata(gruppoId, personaId, LocalDate.now(orologio))).getId());
    }

    private static void applicaVantaggi(GruppoClienti g, NuovoGruppo in) {
        if (in.giorniPerPagare() == null && in.dichiarabile() == null && in.fraseDichiarazione() == null && in.conPrecedenza() == null) return;
        g.vantaggi(in.giorniPerPagare(), Boolean.TRUE.equals(in.dichiarabile()), in.fraseDichiarazione());
    }

    /** Accende o spegne la precedenza: in fondo all'ordine, oppure fuori con l'ordine ricompattato. */
    private void precedenza(GruppoClienti g, boolean accesa) {
        if (accesa == (g.getPrecedenza() != null)) return;
        List<GruppoClienti> altri = gruppi.findAll().stream().filter(x -> x.getPrecedenza() != null && !x.getId().equals(g.getId()))
            .sorted(Comparator.comparing(GruppoClienti::getPrecedenza)).toList();
        if (accesa) {
            g.precedenza(altri.size() + 1);
        } else {
            g.precedenza(null);
            for (int i = 0; i < altri.size(); i++) altri.get(i).precedenza(i + 1);
        }
    }

    @Transactional(readOnly = true)
    List<Membro> membri(UUID gruppoId) {
        gruppo(gruppoId);
        List<AppartenenzaGruppo> attive = appartenenze.attiveDelGruppo(gruppoId);
        Map<UUID, Persone.PersonaScheda> schede = persone.schede(attive.stream().map(AppartenenzaGruppo::getPersonaId).toList());
        return attive.stream().filter(a -> schede.containsKey(a.getPersonaId())).map(a -> {
            Persone.PersonaScheda p = schede.get(a.getPersonaId());
            return new Membro(a.getId(), p.id(), p.nome(), p.cognome(), p.dataNascita(), a.getDal(), a.isVerificata());
        }).sorted(Comparator.comparing(Membro::cognome).thenComparing(Membro::nome)).toList();
    }

    @Transactional
    UUID aggiungi(UUID personaId, UUID gruppoId) {
        if (persone.schede(List.of(personaId)).isEmpty()) throw new NonTrovato("Persona non trovata.");
        GruppoClienti g = gruppo(gruppoId);
        if (!g.isAttivo()) throw new Conflitto("Il gruppo «" + g.getNome() + "» non è più attivo.");
        if (appartenenze.attiveDi(personaId).stream().anyMatch(a -> a.getGruppoId().equals(gruppoId)))
            throw new Conflitto("È già nel gruppo «" + g.getNome() + "».");
        return appartenenze.saveAndFlush(new AppartenenzaGruppo(gruppoId, personaId, LocalDate.now(orologio))).getId();
    }

    @Transactional
    void togli(UUID appartenenzaId) {
        AppartenenzaGruppo a = appartenenze.findById(appartenenzaId).orElseThrow(() -> new NonTrovato("Non è in questo gruppo."));
        if (a.chiudi(LocalDate.now(orologio))) appartenenze.delete(a);
    }

    private GruppoClienti gruppo(UUID id) {
        return gruppi.findById(id).orElseThrow(() -> new NonTrovato("Gruppo non trovato."));
    }
}
