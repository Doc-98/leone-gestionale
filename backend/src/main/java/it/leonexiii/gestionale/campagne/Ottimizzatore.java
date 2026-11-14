package it.leonexiii.gestionale.campagne;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L'assegnazione dei turni di un corso (ADR 0026, «L'ottimizzatore»).
 *
 * <ol>
 *   <li>Le assegnazioni bloccate a mano dalla segreteria restano dove sono.</li>
 *   <li>Si scorre la graduatoria: a ognuno un posto in uno dei turni che ha indicato. Se i suoi turni sono pieni si
 *       prova a spostare chi è già sistemato su un altro dei turni che aveva indicato (cammino aumentante preso
 *       nell'ordine della graduatoria): nessuno perde il posto, nessuno finisce in un turno che non ha scelto, e chi sta
 *       più in basso non scavalca mai chi sta più in alto.</li>
 *   <li>A parità di persone sistemate si migliorano le preferenze: si cercano catene di spostamenti che abbassano la
 *       somma delle posizioni delle scelte (cicli di costo negativo), finché non ce ne sono più.</li>
 *   <li>Infine i fratelli che hanno chiesto lo stesso turno ci vanno, se è tra le loro scelte e non peggiora nessun
 *       altro.</li>
 * </ol>
 *
 * Non tocca il database: riceve la graduatoria e i posti, restituisce le assegnazioni e gli spostamenti da spiegare.
 */
public final class Ottimizzatore {

    /**
     * Una richiesta, nella sua posizione in graduatoria.
     *
     * @param turni i turni che le vanno bene, dal preferito (per «qualsiasi turno» tutti quelli del corso)
     * @param fratello un'altra richiesta con cui vorrebbe stare nello stesso turno, o null
     * @param bloccata il turno fissato a mano dalla segreteria, o null
     */
    public record Richiesta(UUID id, List<UUID> turni, UUID fratello, UUID bloccata) {
        public Richiesta {
            turni = List.copyOf(turni);
        }

        public static Richiesta di(UUID id, List<UUID> turni) {
            return new Richiesta(id, turni, null, null);
        }
    }

    /** Chi è stato spostato e perché: per far posto a chi (o per una scelta migliore, o per il fratello). */
    public record Spostamento(UUID richiesta, UUID da, UUID a, Motivo motivo, UUID perChi) {}

    public enum Motivo { FAR_POSTO, SCELTA_MIGLIORE, FRATELLO }

    /** @param assegnazioni richiesta → turno, nell'ordine della graduatoria; @param fuori chi non ha trovato posto */
    public record Esito(Map<UUID, UUID> assegnazioni, List<UUID> fuori, List<Spostamento> spostamenti) {}

    private final List<Richiesta> graduatoria;
    private final Map<UUID, Integer> posti;
    private final Map<UUID, Integer> occupati = new HashMap<>();
    private final UUID[] assegnato;
    private final boolean[] fissa;
    private final List<Spostamento> spostamenti = new ArrayList<>();

    private Ottimizzatore(List<Richiesta> graduatoria, Map<UUID, Integer> posti) {
        this.graduatoria = List.copyOf(graduatoria);
        this.posti = Map.copyOf(posti);
        this.assegnato = new UUID[graduatoria.size()];
        this.fissa = new boolean[graduatoria.size()];
    }

    /** I posti sono quelli di ogni turno ancora da assegnare in questa campagna (già tolte riconferme e iscritti). */
    public static Esito assegna(List<Richiesta> graduatoria, Map<UUID, Integer> posti) {
        Ottimizzatore o = new Ottimizzatore(graduatoria, posti);
        o.bloccate();
        for (int i = 0; i < o.graduatoria.size(); i++) if (!o.fissa[i]) o.sistema(i);
        o.miglioraPreferenze();
        o.fratelli();
        return o.esito();
    }

    // ── 1. bloccate ───────────────────────────────────────────────────────

    private void bloccate() {
        for (int i = 0; i < graduatoria.size(); i++) {
            UUID t = graduatoria.get(i).bloccata();
            if (t == null) continue;
            if (!posti.containsKey(t)) throw new IllegalArgumentException("Turno bloccato sconosciuto: " + t);
            fissa[i] = true;
            assegnato[i] = t;
            occupati.merge(t, 1, Integer::sum);
        }
    }

    // ── 2. cammini aumentanti nell'ordine della graduatoria ─────────────────

    private void sistema(int i) {
        // prima un posto libero tra le sue scelte, senza spostare nessuno (le preferenze si sistemano al passo 3)
        for (UUID t : graduatoria.get(i).turni()) {
            if (posti.containsKey(t) && liberi(t) > 0) {
                assegnato[i] = t;
                occupati.merge(t, 1, Integer::sum);
                return;
            }
        }
        List<Spostamento> prova = new ArrayList<>();
        if (cerca(i, new boolean[graduatoria.size()], new HashMap<>(), prova, graduatoria.get(i).id())) spostamenti.addAll(prova);
    }

    /** Cerca un posto per i (nei suoi turni, in ordine di preferenza), spostando altri se serve. */
    private boolean cerca(int i, boolean[] visitati, Map<UUID, Boolean> turniVisti, List<Spostamento> catena, UUID perChi) {
        for (UUID t : graduatoria.get(i).turni()) {
            if (!posti.containsKey(t) || t.equals(assegnato[i]) || turniVisti.containsKey(t)) continue;
            turniVisti.put(t, true);
            if (liberi(t) > 0) {
                muovi(i, t, catena, perChi);
                return true;
            }
            for (int j = 0; j < graduatoria.size(); j++) {
                if (j == i || fissa[j] || visitati[j] || !t.equals(assegnato[j])) continue;
                visitati[j] = true;
                if (cerca(j, visitati, turniVisti, catena, perChi)) {
                    // j ha lasciato t: ora c'è posto per i
                    muovi(i, t, catena, perChi);
                    return true;
                }
            }
        }
        return false;
    }

    private void muovi(int i, UUID verso, List<Spostamento> catena, UUID perChi) {
        UUID da = assegnato[i];
        if (da != null) {
            occupati.merge(da, -1, Integer::sum);
            catena.add(new Spostamento(graduatoria.get(i).id(), da, verso, Motivo.FAR_POSTO, perChi));
        }
        assegnato[i] = verso;
        occupati.merge(verso, 1, Integer::sum);
    }

    private int liberi(UUID t) {
        return posti.getOrDefault(t, 0) - occupati.getOrDefault(t, 0);
    }

    // ── 3. preferenze: cicli di costo negativo (Bellman-Ford sui turni) ───

    private void miglioraPreferenze() {
        List<UUID> turni = new ArrayList<>(posti.keySet());
        turni.sort(null);
        int n = turni.size(), libero = n; // nodo in più: «un posto libero da qualche parte»
        Map<UUID, Integer> indice = new HashMap<>();
        for (int k = 0; k < n; k++) indice.put(turni.get(k), k);
        for (int giro = 0; giro < 10_000; giro++) {
            // archi: spostare la richiesta r dal turno a al turno b costa rank(b) − rank(a)
            List<int[]> archi = new ArrayList<>(); // {da, a, costo, richiesta}
            for (int r = 0; r < graduatoria.size(); r++) {
                if (fissa[r] || assegnato[r] == null) continue;
                List<UUID> scelte = graduatoria.get(r).turni();
                int ra = scelte.indexOf(assegnato[r]);
                for (int rb = 0; rb < scelte.size(); rb++) {
                    Integer b = indice.get(scelte.get(rb));
                    if (b == null || rb == ra) continue;
                    archi.add(new int[] {indice.get(assegnato[r]), b, rb - ra, r});
                }
            }
            for (int k = 0; k < n; k++) {
                archi.add(new int[] {libero, k, 0, -1});                         // qualunque turno può liberare un posto
                if (liberi(turni.get(k)) > 0) archi.add(new int[] {k, libero, 0, -1}); // e lo si può occupare
            }
            int[] ciclo = cicloNegativo(n + 1, archi);
            if (ciclo == null) return;
            applica(ciclo, archi, turni);
        }
    }

    /** Un ciclo di costo negativo come elenco di indici di archi, o null. */
    private static int[] cicloNegativo(int nodi, List<int[]> archi) {
        long[] dist = new long[nodi];
        int[] arcoPrima = new int[nodi];
        Arrays.fill(arcoPrima, -1);
        int ultimo = -1;
        for (int it = 0; it < nodi; it++) {
            ultimo = -1;
            for (int e = 0; e < archi.size(); e++) {
                int[] a = archi.get(e);
                if (dist[a[0]] + a[2] < dist[a[1]]) {
                    dist[a[1]] = dist[a[0]] + a[2];
                    arcoPrima[a[1]] = e;
                    ultimo = a[1];
                }
            }
            if (ultimo == -1) return null;
        }
        int v = ultimo;
        for (int i = 0; i < nodi; i++) v = archi.get(arcoPrima[v])[0];
        List<Integer> ciclo = new ArrayList<>();
        int u = v;
        do {
            int e = arcoPrima[u];
            ciclo.add(e);
            u = archi.get(e)[0];
        } while (u != v);
        return ciclo.stream().mapToInt(Integer::intValue).toArray();
    }

    private void applica(int[] ciclo, List<int[]> archi, List<UUID> turni) {
        for (int e : ciclo) {
            int[] a = archi.get(e);
            int r = a[3];
            if (r < 0) continue;
            UUID da = assegnato[r], verso = turni.get(a[1]);
            occupati.merge(da, -1, Integer::sum);
            occupati.merge(verso, 1, Integer::sum);
            assegnato[r] = verso;
            spostamenti.add(new Spostamento(graduatoria.get(r).id(), da, verso, Motivo.SCELTA_MIGLIORE, null));
        }
    }

    // ── 4. fratelli nello stesso turno ──────────────────────────────────────

    private void fratelli() {
        Map<UUID, Integer> posizione = new HashMap<>();
        for (int i = 0; i < graduatoria.size(); i++) posizione.put(graduatoria.get(i).id(), i);
        for (int s = 0; s < graduatoria.size(); s++) {
            UUID f = graduatoria.get(s).fratello();
            Integer r = f == null ? null : posizione.get(f);
            if (r == null || fissa[s] || assegnato[s] == null || assegnato[r] == null || assegnato[s].equals(assegnato[r])) continue;
            UUID meta = assegnato[r];
            if (!graduatoria.get(s).turni().contains(meta)) continue;
            UUID da = assegnato[s];
            if (liberi(meta) > 0) {
                sposta(s, meta, r);
                continue;
            }
            // scambio con qualcuno del turno del fratello che accetta il turno di s senza scendere nelle sue scelte
            for (int q = 0; q < graduatoria.size(); q++) {
                if (q == r || q == s || fissa[q] || !meta.equals(assegnato[q])) continue;
                List<UUID> sq = graduatoria.get(q).turni();
                if (!sq.contains(da) || sq.indexOf(da) > sq.indexOf(meta)) continue;
                sposta(q, da, s);
                sposta(s, meta, r);
                break;
            }
        }
    }

    private void sposta(int i, UUID verso, int perChi) {
        UUID da = assegnato[i];
        occupati.merge(da, -1, Integer::sum);
        occupati.merge(verso, 1, Integer::sum);
        assegnato[i] = verso;
        spostamenti.add(new Spostamento(graduatoria.get(i).id(), da, verso, Motivo.FRATELLO, graduatoria.get(perChi).id()));
    }

    /**
     * Gli spostamenti da spiegare, uno per richiesta: da dove era stata messa la prima volta a dove resta. Chi torna al
     * turno di partenza non ha niente da spiegare.
     */
    private List<Spostamento> netti() {
        Map<UUID, Spostamento> primo = new LinkedHashMap<>(), ultimo = new HashMap<>();
        for (Spostamento sp : spostamenti) {
            primo.putIfAbsent(sp.richiesta(), sp);
            ultimo.put(sp.richiesta(), sp);
        }
        List<Spostamento> out = new ArrayList<>();
        for (Spostamento p : primo.values()) {
            Spostamento u = ultimo.get(p.richiesta());
            if (p.da().equals(u.a())) continue;
            Spostamento motivo = p.motivo() == Motivo.FAR_POSTO ? p : u;
            out.add(new Spostamento(p.richiesta(), p.da(), u.a(), motivo.motivo(), motivo.perChi()));
        }
        return out;
    }

    private Esito esito() {
        Map<UUID, UUID> ass = new LinkedHashMap<>();
        List<UUID> fuori = new ArrayList<>();
        for (int i = 0; i < graduatoria.size(); i++) {
            if (assegnato[i] != null) ass.put(graduatoria.get(i).id(), assegnato[i]);
            else fuori.add(graduatoria.get(i).id());
        }
        return new Esito(ass, fuori, netti());
    }
}
