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

    record GruppoDto(UUID id, String nome, String descrizione, boolean attivo, long persone) {}

    record NuovoGruppo(String nome, String descrizione, Boolean attivo) {}

    record Membro(UUID appartenenzaId, UUID personaId, String nome, String cognome, LocalDate dataNascita, LocalDate dal) {}

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
        return gruppi.findAllByOrderByNome().stream()
            .map(g -> new GruppoDto(g.getId(), g.getNome(), g.getDescrizione(), g.isAttivo(), conta.getOrDefault(g.getId(), 0L))).toList();
    }

    @Transactional
    UUID crea(NuovoGruppo in) {
        return gruppi.saveAndFlush(new GruppoClienti(in.nome(), in.descrizione())).getId();
    }

    @Transactional
    void modifica(UUID id, NuovoGruppo in) {
        GruppoClienti g = gruppo(id);
        g.modifica(in.nome(), in.descrizione(), in.attivo() == null || in.attivo());
        gruppi.flush();
    }

    @Transactional(readOnly = true)
    List<Membro> membri(UUID gruppoId) {
        gruppo(gruppoId);
        List<AppartenenzaGruppo> attive = appartenenze.attiveDelGruppo(gruppoId);
        Map<UUID, Persone.PersonaScheda> schede = persone.schede(attive.stream().map(AppartenenzaGruppo::getPersonaId).toList());
        return attive.stream().filter(a -> schede.containsKey(a.getPersonaId())).map(a -> {
            Persone.PersonaScheda p = schede.get(a.getPersonaId());
            return new Membro(a.getId(), p.id(), p.nome(), p.cognome(), p.dataNascita(), a.getDal());
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
