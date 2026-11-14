package it.leonexiii.gestionale.campagne;

import static org.assertj.core.api.Assertions.assertThat;

import it.leonexiii.gestionale.campagne.Ottimizzatore.Esito;
import it.leonexiii.gestionale.campagne.Ottimizzatore.Motivo;
import it.leonexiii.gestionale.campagne.Ottimizzatore.Richiesta;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * L'ottimizzatore dei turni (ADR 0026): casi piccoli controllati a mano e proprietà verificate su casi casuali,
 * confrontandolo con la ricerca esaustiva.
 */
class OttimizzatoreTest {

    private static final UUID LUN = id("lun"), MAR = id("mar"), GIO = id("gio"), SAB = id("sab");
    private static final UUID LUCA = id("luca"), SARA = id("sara"), ANNA = id("anna"), LEO = id("leo"), MIA = id("mia");

    private static UUID id(String s) {
        return UUID.nameUUIDFromBytes(s.getBytes());
    }

    @Test
    void ognunoIlSuoTurnoPreferitoSeCePosto() {
        Esito e = Ottimizzatore.assegna(List.of(Richiesta.di(LUCA, List.of(MAR, GIO)), Richiesta.di(SARA, List.of(GIO))), Map.of(MAR, 1, GIO, 1));
        assertThat(e.assegnazioni()).containsEntry(LUCA, MAR).containsEntry(SARA, GIO);
        assertThat(e.fuori()).isEmpty();
        assertThat(e.spostamenti()).isEmpty();
    }

    @Test
    void spostaChiHaUnAltraSceltaPerFarPosto() {
        // «Luca va bene martedì o giovedì, Sara solo martedì: Luca passa a giovedì, Sara entra»
        Esito e = Ottimizzatore.assegna(List.of(Richiesta.di(LUCA, List.of(MAR, GIO)), Richiesta.di(SARA, List.of(MAR))), Map.of(MAR, 1, GIO, 1));
        assertThat(e.assegnazioni()).containsEntry(LUCA, GIO).containsEntry(SARA, MAR);
        assertThat(e.fuori()).isEmpty();
        assertThat(e.spostamenti()).singleElement().satisfies(s -> {
            assertThat(s.richiesta()).isEqualTo(LUCA);
            assertThat(s.da()).isEqualTo(MAR);
            assertThat(s.a()).isEqualTo(GIO);
            assertThat(s.motivo()).isEqualTo(Motivo.FAR_POSTO);
            assertThat(s.perChi()).isEqualTo(SARA);
        });
    }

    @Test
    void chiStaPiuInBassoNonScavalca() {
        // un solo posto: va al primo in graduatoria, anche se il secondo non avrebbe alternative
        Esito e = Ottimizzatore.assegna(List.of(Richiesta.di(LUCA, List.of(MAR)), Richiesta.di(SARA, List.of(MAR))), Map.of(MAR, 1));
        assertThat(e.assegnazioni()).containsOnlyKeys(LUCA);
        assertThat(e.fuori()).containsExactly(SARA);
    }

    @Test
    void lAssegnazioneBloccataResta() {
        Esito e = Ottimizzatore.assegna(List.of(
            Richiesta.di(LUCA, List.of(MAR, GIO)),
            new Richiesta(SARA, List.of(MAR, GIO), null, GIO)), Map.of(MAR, 1, GIO, 1));
        assertThat(e.assegnazioni()).containsEntry(SARA, GIO).containsEntry(LUCA, MAR);
        // bloccata su un turno pieno: chi viene dopo resta fuori, nessuno la sposta
        Esito pieno = Ottimizzatore.assegna(List.of(Richiesta.di(LUCA, List.of(GIO)), new Richiesta(SARA, List.of(MAR), null, GIO)), Map.of(GIO, 1, MAR, 0));
        assertThat(pieno.assegnazioni()).containsEntry(SARA, GIO).doesNotContainKey(LUCA);
    }

    @Test
    void aParitaSiPreferisconoLePrimeScelte() {
        // Anna prende sabato (unico posto libero) prima che Leo arrivi; poi Leo, che voleva solo sabato, fa spostare Anna
        // sulla sua seconda scelta; alla fine ognuno ha la scelta migliore possibile
        Esito e = Ottimizzatore.assegna(List.of(
            Richiesta.di(ANNA, List.of(SAB, LUN)),
            Richiesta.di(LEO, List.of(SAB)),
            Richiesta.di(MIA, List.of(LUN, SAB))), Map.of(SAB, 1, LUN, 2));
        assertThat(e.assegnazioni()).containsEntry(LEO, SAB).containsEntry(ANNA, LUN).containsEntry(MIA, LUN);
    }

    @Test
    void iFratelliNelloStessoTurnoSeLoHannoChiesto() {
        Esito e = Ottimizzatore.assegna(List.of(
            Richiesta.di(LEO, List.of(GIO, LUN)),
            new Richiesta(ANNA, List.of(LUN, GIO), LEO, null)), Map.of(LUN, 2, GIO, 2));
        assertThat(e.assegnazioni()).containsEntry(LEO, GIO).containsEntry(ANNA, GIO);
        assertThat(e.spostamenti()).singleElement().satisfies(s -> assertThat(s.motivo()).isEqualTo(Motivo.FRATELLO));
        // ma non su un turno che non hanno scelto
        Esito no = Ottimizzatore.assegna(List.of(
            Richiesta.di(LEO, List.of(GIO)),
            new Richiesta(ANNA, List.of(LUN), LEO, null)), Map.of(LUN, 2, GIO, 2));
        assertThat(no.assegnazioni()).containsEntry(LEO, GIO).containsEntry(ANNA, LUN);
    }

    @Test
    void turniSconosciutiOSenzaPostiNonSiUsano() {
        Esito e = Ottimizzatore.assegna(List.of(Richiesta.di(LUCA, List.of(id("altro"), SAB))), Map.of(SAB, 0));
        assertThat(e.fuori()).containsExactly(LUCA);
    }

    // ── proprietà su casi casuali, contro la ricerca esaustiva ─────────────

    @Test
    void comeLaRicercaEsaustivaSuCasiCasuali() {
        Random rnd = new Random(20261114);
        UUID[] turni = {LUN, MAR, GIO};
        for (int caso = 0; caso < 1500; caso++) {
            int n = 1 + rnd.nextInt(6);
            Map<UUID, Integer> posti = new HashMap<>();
            for (UUID t : turni) posti.put(t, rnd.nextInt(3));
            List<Richiesta> grad = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                List<UUID> scelte = new ArrayList<>(List.of(turni));
                java.util.Collections.shuffle(scelte, rnd);
                scelte = scelte.subList(0, 1 + rnd.nextInt(turni.length));
                UUID bloccata = rnd.nextInt(8) == 0 ? scelte.get(0) : null;
                grad.add(new Richiesta(id("r" + caso + "-" + i), scelte, null, bloccata));
            }
            // le bloccate devono stare nei posti, come garantisce la segreteria
            Map<UUID, Integer> usati = new HashMap<>();
            for (Richiesta r : grad) if (r.bloccata() != null) usati.merge(r.bloccata(), 1, Integer::sum);
            usati.forEach((t, k) -> posti.merge(t, 0, (a, b) -> Math.max(a, k)));

            Esito e = Ottimizzatore.assegna(grad, posti);
            String descr = "caso " + caso + ": " + grad + " posti " + posti + " → " + e.assegnazioni();

            // nessuno in un turno non scelto, posti mai superati, bloccate rispettate
            Map<UUID, Integer> conta = new HashMap<>();
            for (Richiesta r : grad) {
                UUID t = e.assegnazioni().get(r.id());
                if (t == null) continue;
                assertThat(r.turni()).as(descr).contains(t);
                if (r.bloccata() != null) assertThat(t).as(descr).isEqualTo(r.bloccata());
                conta.merge(t, 1, Integer::sum);
            }
            conta.forEach((t, k) -> assertThat(k).as(descr).isLessThanOrEqualTo(posti.get(t)));
            for (Richiesta r : grad) if (r.bloccata() != null) assertThat(e.assegnazioni()).as(descr).containsKey(r.id());

            // stessi sistemati della soluzione migliore nell'ordine della graduatoria, e somma delle scelte minima
            int[] migliore = esaustiva(grad, posti);
            boolean[] sistemati = new boolean[n];
            int somma = 0;
            for (int i = 0; i < n; i++) {
                UUID t = e.assegnazioni().get(grad.get(i).id());
                sistemati[i] = t != null;
                if (t != null) somma += grad.get(i).turni().indexOf(t);
            }
            for (int i = 0; i < n; i++) assertThat(sistemati[i]).as(descr + " (posizione " + i + ")").isEqualTo(migliore[i] == 1);
            assertThat(somma).as(descr + " somma delle scelte").isEqualTo(migliore[n]);
        }
    }

    /**
     * La soluzione migliore provando tutte le combinazioni: prima chi è sistemato, confrontando la graduatoria dall'alto
     * (ordine lessicografico), poi la somma delle posizioni delle scelte. Restituisce [sistemato per ognuno..., somma].
     */
    private static int[] esaustiva(List<Richiesta> grad, Map<UUID, Integer> posti) {
        int n = grad.size();
        int[] scelta = new int[n]; // -1 = fuori, altrimenti indice nelle sue scelte
        int[][] best = {null};
        prova(0, grad, posti, scelta, new HashMap<>(), best);
        return best[0];
    }

    private static void prova(int i, List<Richiesta> grad, Map<UUID, Integer> posti, int[] scelta, Map<UUID, Integer> usati, int[][] best) {
        int n = grad.size();
        if (i == n) {
            int[] cand = new int[n + 1];
            for (int k = 0; k < n; k++) {
                cand[k] = scelta[k] >= 0 ? 1 : 0;
                if (scelta[k] >= 0) cand[n] += scelta[k];
            }
            if (best[0] == null || meglio(cand, best[0])) best[0] = cand;
            return;
        }
        Richiesta r = grad.get(i);
        List<Integer> opzioni = new ArrayList<>();
        if (r.bloccata() != null) opzioni.add(r.turni().indexOf(r.bloccata()));
        else {
            for (int k = 0; k < r.turni().size(); k++) opzioni.add(k);
            opzioni.add(-1);
        }
        for (int k : opzioni) {
            if (k >= 0) {
                UUID t = r.turni().get(k);
                if (usati.getOrDefault(t, 0) >= posti.getOrDefault(t, 0)) continue;
                usati.merge(t, 1, Integer::sum);
                scelta[i] = k;
                prova(i + 1, grad, posti, scelta, usati, best);
                usati.merge(t, -1, Integer::sum);
            } else {
                scelta[i] = -1;
                prova(i + 1, grad, posti, scelta, usati, best);
            }
        }
    }

    private static boolean meglio(int[] a, int[] b) {
        int n = a.length - 1;
        for (int k = 0; k < n; k++) if (a[k] != b[k]) return a[k] > b[k];
        return a[n] < b[n];
    }
}
