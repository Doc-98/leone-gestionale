package it.leonexiii.gestionale.clienti;

import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.listino.Listino;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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

    /**
     * «ordine»: la posizione nell'elenco (1, 2, 3…), anche ordine delle precedenze; «precedenza»: 1 = la più forte, null =
     * nessuna. «eliminabile», «motivoBlocco» e «eliminazionePesante» (ha persone o prezzi nel listino: si elimina con la
     * finestra delle conseguenze) li ha solo chi gestisce le impostazioni, gli altri li ricevono vuoti.
     */
    record GruppoDto(UUID id, String nome, String descrizione, boolean attivo, long persone,
                     Integer precedenza, Integer giorniPerPagare, boolean dichiarabile, String fraseDichiarazione, long daVerificare,
                     int ordine, Boolean eliminabile, String motivoBlocco, Boolean eliminazionePesante) {}

    /**
     * Senza i campi dei vantaggi (vecchi client) restano come sono. conPrecedenza: accesa o spenta, le precedenze di tutti
     * si ricalcolano dall'ordine dell'elenco (la precedenza è la posizione tra i gruppi che ne hanno una).
     */
    record NuovoGruppo(String nome, String descrizione, Boolean attivo,
                       Boolean conPrecedenza, Integer giorniPerPagare, Boolean dichiarabile, String fraseDichiarazione) {}

    /** L'ordine voluto dei gruppi: gli id di tutti, uno per uno. */
    record Ordine(List<UUID> ids) {}

    /**
     * Cosa succede eliminando il gruppo, con i numeri veri. «pesante»: ha persone o prezzi nel listino, si elimina con la
     * finestra delle conseguenze. «persone»: appartenenze attive (anche da verificare); «daVerificare»: quelle non ancora
     * verificate; «prezzi»: prezzi del listino per il gruppo in tutte le stagioni; «offerte»: i nomi di quelle offerte.
     * «conseguenze»: le righe da mostrare, col plurale giusto.
     */
    record Eliminazione(String gruppo, boolean pesante, boolean bloccata, String motivoBlocco, long persone, long daVerificare,
                        int prezzi, List<String> offerte, List<String> conseguenze) {}

    record Membro(UUID appartenenzaId, UUID personaId, String nome, String cognome, LocalDate dataNascita, LocalDate dal, boolean verificata) {}

    record DaVerificare(UUID appartenenzaId, UUID gruppoId, String gruppo, String frase, UUID personaId, String nome, String cognome,
                        LocalDate dataNascita, LocalDate dal) {}

    private final GruppoClientiRepository gruppi;
    private final AppartenenzaGruppoRepository appartenenze;
    private final Persone persone;
    private final Listino listino;
    private final Clock orologio;

    GruppiService(GruppoClientiRepository gruppi, AppartenenzaGruppoRepository appartenenze, Persone persone, Listino listino, Clock orologio) {
        this.gruppi = gruppi;
        this.appartenenze = appartenenze;
        this.persone = persone;
        this.listino = listino;
        this.orologio = orologio;
    }

    /** Nell'ordine dell'elenco; «conBlocchi»: chi gestisce le impostazioni riceve anche se (e come) ogni gruppo si elimina. */
    @Transactional(readOnly = true)
    List<GruppoDto> elenco(boolean conBlocchi) {
        Map<UUID, Long> conta = new HashMap<>();
        for (Object[] r : appartenenze.contaAttive()) conta.put((UUID) r[0], (Long) r[1]);
        Map<UUID, Long> daVerificare = new HashMap<>();
        for (Object[] r : appartenenze.contaDaVerificare()) daVerificare.put((UUID) r[0], (Long) r[1]);
        return gruppi.findAllByOrderByOrdineAscNomeAsc().stream().map(g -> {
            long persone = conta.getOrDefault(g.getId(), 0L);
            // con la copia del nome nello storico un gruppo non è mai bloccato: «eliminabile» è sempre vero (ADR 0029)
            Boolean eliminabile = conBlocchi ? true : null;
            Boolean pesante = conBlocchi ? persone > 0 || listino.prezziDelGruppo(g.getId()).prezzi() > 0 : null;
            return new GruppoDto(g.getId(), g.getNome(), g.getDescrizione(), g.isAttivo(), persone,
                g.getPrecedenza(), g.getGiorniPerPagare(), g.isDichiarabile(), g.getFraseDichiarazione(), daVerificare.getOrDefault(g.getId(), 0L),
                g.getOrdine(), eliminabile, null, pesante);
        }).toList();
    }

    /** Il gruppo nuovo va in fondo all'elenco. */
    @Transactional
    UUID crea(NuovoGruppo in) {
        GruppoClienti nuovo = new GruppoClienti(in.nome(), in.descrizione());
        nuovo.modifica(in.nome(), in.descrizione(), in.attivo() == null || in.attivo()); // un gruppo può nascere spento
        applicaVantaggi(nuovo, in);
        nuovo.ordina(gruppi.findAll().stream().mapToInt(GruppoClienti::getOrdine).max().orElse(0) + 1);
        if (Boolean.TRUE.equals(in.conPrecedenza())) nuovo.precedenza(1); // il numero vero lo dà il ricalcolo
        GruppoClienti g = gruppi.saveAndFlush(nuovo); // con l'id già assegnato è un merge: si lavora sull'istanza gestita
        if (Boolean.TRUE.equals(in.conPrecedenza())) ricalcolaPrecedenze();
        return g.getId();
    }

    @Transactional
    void modifica(UUID id, NuovoGruppo in) {
        GruppoClienti g = gruppo(id);
        g.modifica(in.nome(), in.descrizione(), in.attivo() == null || in.attivo());
        applicaVantaggi(g, in);
        if (in.conPrecedenza() != null && in.conPrecedenza() != (g.getPrecedenza() != null)) {
            g.precedenza(in.conPrecedenza() ? 1 : null); // il numero vero lo dà il ricalcolo
            ricalcolaPrecedenze();
        }
        gruppi.flush();
    }

    /** Il nuovo ordine dell'elenco (e quindi delle precedenze): vanno elencati tutti i gruppi, una volta sola. */
    @Transactional
    List<GruppoDto> ordina(Ordine in) {
        List<GruppoClienti> tutti = gruppi.findAll();
        List<UUID> ids = in == null || in.ids() == null ? List.of() : in.ids();
        if (ids.size() != tutti.size() || new HashSet<>(ids).size() != ids.size()
            || !tutti.stream().map(GruppoClienti::getId).allMatch(ids::contains))
            throw new IllegalArgumentException("L'ordine deve contenere, una volta sola, tutti i gruppi.");
        Map<UUID, GruppoClienti> perId = new HashMap<>();
        tutti.forEach(g -> perId.put(g.getId(), g));
        for (int i = 0; i < ids.size(); i++) perId.get(ids.get(i)).ordina(i + 1);
        ricalcolaPrecedenze();
        gruppi.flush();
        return elenco(true);
    }

    /** La precedenza di un gruppo è la sua posizione tra i gruppi che ne hanno una, nell'ordine dell'elenco. */
    private void ricalcolaPrecedenze() {
        int n = 0;
        for (GruppoClienti g : gruppi.findAllByOrderByOrdineAscNomeAsc()) g.precedenza(g.getPrecedenza() == null ? null : ++n);
    }

    /** Dopo un'eliminazione: le posizioni dell'elenco tornano 1, 2, 3… senza buchi, e le precedenze di conseguenza. */
    private void ricompattaOrdine() {
        int n = 0;
        for (GruppoClienti g : gruppi.findAllByOrderByOrdineAscNomeAsc()) g.ordina(++n);
        ricalcolaPrecedenze();
    }

    /** Cosa succede eliminando il gruppo: le conseguenze, con i numeri veri (la finestra le mostra prima di confermare). */
    @Transactional(readOnly = true)
    Eliminazione eliminazione(UUID id) {
        GruppoClienti g = gruppo(id);
        List<AppartenenzaGruppo> attive = appartenenze.attiveDelGruppo(id);
        long daVerificare = attive.stream().filter(a -> !a.isVerificata()).count();
        Listino.PrezziDelGruppo prezzi = listino.prezziDelGruppo(id);
        long persone = attive.size();
        List<String> conseguenze = new ArrayList<>();
        if (persone > 0) conseguenze.add(persone == 1 ? "1 persona esce dal gruppo" : persone + " persone escono dal gruppo");
        if (daVerificare > 0)
            conseguenze.add(daVerificare == 1 ? "1 dichiarazione da verificare si chiude" : daVerificare + " dichiarazioni da verificare si chiudono");
        if (prezzi.prezzi() > 0) {
            List<String> nomi = prezzi.offerte();
            String quali = nomi.size() > 3 ? String.join(", ", nomi.subList(0, 3)) + " e altre " + (nomi.size() - 3) : String.join(", ", nomi);
            conseguenze.add(prezzi.prezzi() == 1 ? "1 prezzo del listino per questo gruppo sparisce (" + quali + ")"
                : prezzi.prezzi() + " prezzi del listino per questo gruppo spariscono (" + quali + ")");
        }
        List<String> senza = prezzi.senzaPrezzi();
        if (senza.size() == 1) conseguenze.add("L'offerta «" + senza.getFirst() + "» resta senza prezzi e si spegne: riaccendila dopo averle dato un prezzo.");
        else if (senza.size() > 1) {
            List<String> tra = senza.stream().map(n -> "«" + n + "»").toList();
            conseguenze.add("Le offerte " + String.join(", ", tra.subList(0, tra.size() - 1)) + " e " + tra.getLast()
                + " restano senza prezzi e si spengono: riaccendile dopo aver dato loro un prezzo.");
        }
        return new Eliminazione(g.getNome(), persone > 0 || prezzi.prezzi() > 0, false, null, persone, daVerificare,
            prezzi.prezzi(), prezzi.offerte(), conseguenze);
    }

    /**
     * Elimina il gruppo. Senza conseguenze basta; con persone o prezzi nel listino serve {@code pesante} (la finestra con le
     * conseguenze), altrimenti 409: un gruppo che nel frattempo si è riempito non sparisce con due tocchi. Con {@code pesante},
     * in una transazione: le persone escono (l'ultimo giorno è ieri; chi era entrato oggi è come se non fosse mai entrato),
     * lo storico delle appartenenze tiene la copia del nome, i prezzi del gruppo spariscono dal listino (ADR 0029).
     */
    @Transactional
    void elimina(UUID id, boolean pesante) {
        GruppoClienti g = gruppoBloccato(id);
        List<AppartenenzaGruppo> attive = appartenenze.attiveDelGruppo(id);
        int prezzi = listino.prezziDelGruppo(id).prezzi();
        if ((!attive.isEmpty() || prezzi > 0) && !pesante) {
            List<String> cosa = new ArrayList<>();
            if (!attive.isEmpty()) cosa.add(attive.size() == 1 ? "1 persona" : attive.size() + " persone");
            if (prezzi > 0) cosa.add(prezzi == 1 ? "1 prezzo nel listino" : prezzi + " prezzi nel listino");
            throw new Conflitto("Non si può eliminare così: «" + g.getNome() + "» ha " + String.join(" e ", cosa) + ".\n"
                + "Usa di nuovo il cestino: si apre la finestra con le conseguenze.");
        }
        LocalDate oggi = LocalDate.now(orologio);
        for (AppartenenzaGruppo a : attive) if (a.chiudi(oggi)) appartenenze.delete(a);
        appartenenze.flush();
        // le chiuse (anche di prima) ricordano il nome del gruppo e non lo puntano più
        for (AppartenenzaGruppo a : appartenenze.findByGruppoId(id)) a.gruppoEliminato(g.getNome());
        appartenenze.flush();
        listino.eliminaPrezziDelGruppo(id);
        gruppi.delete(g);
        gruppi.flush();
        ricompattaOrdine();
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
        GruppoClienti g = gruppoBloccato(gruppoId);
        if (!g.isAttivo() || !g.isDichiarabile()) throw new Conflitto("Questo gruppo non si può dichiarare.");
        return appartenenze.attiveDi(personaId).stream().filter(a -> gruppoId.equals(a.getGruppoId())).findFirst()
            .map(AppartenenzaGruppo::getId)
            .orElseGet(() -> appartenenze.saveAndFlush(AppartenenzaGruppo.dichiarata(gruppoId, personaId, LocalDate.now(orologio))).getId());
    }

    private static void applicaVantaggi(GruppoClienti g, NuovoGruppo in) {
        if (in.giorniPerPagare() == null && in.dichiarabile() == null && in.fraseDichiarazione() == null && in.conPrecedenza() == null) return;
        g.vantaggi(in.giorniPerPagare(), Boolean.TRUE.equals(in.dichiarabile()), in.fraseDichiarazione());
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
        GruppoClienti g = gruppoBloccato(gruppoId);
        if (!g.isAttivo()) throw new Conflitto("Il gruppo «" + g.getNome() + "» non è più attivo.");
        if (appartenenze.attiveDi(personaId).stream().anyMatch(a -> gruppoId.equals(a.getGruppoId())))
            throw new Conflitto("È già nel gruppo «" + g.getNome() + "».");
        return appartenenze.saveAndFlush(new AppartenenzaGruppo(gruppoId, personaId, LocalDate.now(orologio))).getId();
    }

    @Transactional
    void togli(UUID appartenenzaId) {
        AppartenenzaGruppo a = appartenenze.findById(appartenenzaId).orElseThrow(() -> new NonTrovato("Non è in questo gruppo."));
        if (a.chiudi(LocalDate.now(orologio))) appartenenze.delete(a);
    }

    /** Come {@link #gruppo} ma blocca la riga: serve a chi cambia le persone o i prezzi del gruppo mentre qualcuno potrebbe eliminarlo. */
    private GruppoClienti gruppoBloccato(UUID id) {
        return gruppi.bloccato(id).orElseThrow(() -> new NonTrovato("Gruppo non trovato."));
    }

    private GruppoClienti gruppo(UUID id) {
        return gruppi.findById(id).orElseThrow(() -> new NonTrovato("Gruppo non trovato."));
    }
}
