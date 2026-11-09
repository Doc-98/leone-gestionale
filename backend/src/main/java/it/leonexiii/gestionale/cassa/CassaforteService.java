package it.leonexiii.gestionale.cassa;

import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Il conto della cassaforte e dei versamenti in banca (ADR 0018, parte 11, se acceso nelle impostazioni): quello che
 * alle chiusure va in cassaforte (versato + versamenti durante il turno) resta lì finché qualcuno non segna di averlo
 * portato in banca, con il giorno e la distinta. Un versamento annullato rimette le chiusure in cassaforte.
 */
@Service
class CassaforteService {

    record VersamentoDto(UUID id, LocalDate data, BigDecimal importo, String distinta, String note, String registratoDa, Instant registratoIl,
                         boolean annullato) {}

    record Cassaforte(BigDecimal totale, List<CassaService.SessioneDto> chiusure, List<VersamentoDto> versamenti) {}

    record NuovoVersamento(LocalDate data, String distinta, String note, List<UUID> chiusure) {}

    private final CassaService cassa;
    private final SessioneRepository sessioni;
    private final VersamentoBancaRepository versamenti;
    private final Clock orologio;

    CassaforteService(CassaService cassa, SessioneRepository sessioni, VersamentoBancaRepository versamenti, Clock orologio) {
        this.cassa = cassa;
        this.sessioni = sessioni;
        this.versamenti = versamenti;
        this.orologio = orologio;
    }

    @Transactional(readOnly = true)
    Cassaforte cassaforte() {
        ImpostazioniCassa imp = acceso();
        var dentro = cassa.inCassaforte(imp);
        BigDecimal totale = dentro.stream().map(Sessione::getPerCassaforte).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        var ultimi = versamenti.findTop10ByOrderByDataDescRegistratoIlDesc();
        Map<UUID, String> nomi = cassa.nomi(ultimi.stream().map(VersamentoBanca::getRegistratoDa).toList());
        return new Cassaforte(totale, cassa.leggere(dentro).reversed(), ultimi.stream().map(v -> dto(v, nomi)).toList());
    }

    /** Portati in banca: le chiusure scelte (tutte quelle in cassaforte, se non se ne sceglie nessuna). */
    @Transactional
    VersamentoDto versa(NuovoVersamento in, UUID da) {
        ImpostazioniCassa imp = acceso();
        if (in == null || in.data() == null) throw new IllegalArgumentException("Scrivi il giorno in cui i contanti sono andati in banca.");
        LocalDate oggi = LocalDate.now(orologio.withZone(CassaService.FUSO));
        if (in.data().isAfter(oggi)) throw new IllegalArgumentException("Il versamento non può essere in un giorno che deve ancora venire.");
        var dentro = cassa.inCassaforte(imp);
        Set<UUID> scelte = in.chiusure() == null || in.chiusure().isEmpty() ? null : new HashSet<>(in.chiusure());
        var prese = dentro.stream().filter(s -> scelte == null || scelte.contains(s.getId())).toList();
        if (scelte != null && prese.size() != scelte.size()) throw new Conflitto("Alcune chiusure non sono più in cassaforte: ricarica la pagina.");
        if (prese.isEmpty()) throw new IllegalArgumentException("In cassaforte non c'è niente da portare in banca.");
        BigDecimal importo = prese.stream().map(Sessione::getPerCassaforte).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        VersamentoBanca v = versamenti.saveAndFlush(new VersamentoBanca(in.data(), importo, in.distinta(), in.note(), da, orologio.instant()));
        prese.forEach(s -> s.inBanca(v.getId()));
        sessioni.saveAllAndFlush(prese);
        return dto(v, cassa.nomi(Stream.of(da).toList()));
    }

    @Transactional
    VersamentoDto annulla(UUID id, UUID da) {
        VersamentoBanca v = versamenti.findById(id).orElseThrow(() -> new NonTrovato("Versamento non trovato."));
        v.annulla(orologio.instant(), da);
        var chiusure = sessioni.findByVersamentoId(id);
        chiusure.forEach(s -> s.inBanca(null));
        sessioni.saveAllAndFlush(chiusure);
        versamenti.flush();
        return dto(v, cassa.nomi(Stream.of(v.getRegistratoDa()).toList()));
    }

    private ImpostazioniCassa acceso() {
        ImpostazioniCassa imp = cassa.imp();
        if (!imp.isBanca()) throw new Conflitto("Il conto della cassaforte e della banca è spento (impostazioni della cassa).");
        return imp;
    }

    private static VersamentoDto dto(VersamentoBanca v, Map<UUID, String> nomi) {
        return new VersamentoDto(v.getId(), v.getData(), v.getImporto(), v.getDistinta(), v.getNote(),
            v.getRegistratoDa() == null ? null : nomi.get(v.getRegistratoDa()), v.getRegistratoIl(), v.getAnnullatoIl() != null);
    }
}
