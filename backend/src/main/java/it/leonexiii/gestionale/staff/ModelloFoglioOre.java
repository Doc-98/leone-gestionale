package it.leonexiii.gestionale.staff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Il modello Excel «FOGLIO ORE MENSILE COLLABORATORI SPORTIVI» della società (resources/foglio-ore/modello.xlsx).
 * Righe 12–42: i giorni 1–31; riga 11: le voci (colonne C–T); riga 43: i totali. In alto data, nome e mese.
 * Le mansioni senza una voce del modello ricevono una colonna propria dopo l'ultima voce; dopo ancora
 * una colonna «Sostituzioni» con i colleghi sostituiti.
 */
@Component
class ModelloFoglioOre {

    static final String[] MESI = { "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno", "Luglio", "Agosto",
        "Settembre", "Ottobre", "Novembre", "Dicembre" };

    private static final int RIGA_VOCI = 10;      // riga 11
    private static final int RIGA_GIORNO_1 = 11;  // riga 12
    private static final int RIGA_TOTALE = 42;    // riga 43
    private static final int COLONNA_GIORNO = 1;  // B
    private static final int PRIMA_VOCE = 2;      // C
    private static final String CELLA_DATA = "E4";
    private static final String CELLA_NOME = "E5";
    private static final String CELLA_MESE = "F6";

    /** Ore di un giorno in una colonna: la chiave è la voce del modello o il nome della mansione. */
    record Compilazione(String nome, YearMonth mese, LocalDate data,
                        Map<String, SortedMap<Integer, BigDecimal>> orePerVoce, SortedMap<Integer, List<String>> note) {}

    private final byte[] modello;
    private final List<String> voci;
    private final int ultimaVoce;

    ModelloFoglioOre() {
        try (InputStream in = new ClassPathResource("foglio-ore/modello.xlsx").getInputStream()) {
            modello = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Modello del foglio ore mancante", e);
        }
        try (var wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(modello))) {
            Sheet s = wb.getSheetAt(0);
            controlla(s);
            Row r = s.getRow(RIGA_VOCI);
            List<String> elenco = new ArrayList<>();
            int c = PRIMA_VOCE;
            for (; r.getCell(c) != null && r.getCell(c).getCellType() == CellType.STRING && !r.getCell(c).getStringCellValue().isBlank(); c++)
                elenco.add(r.getCell(c).getStringCellValue().strip());
            voci = Collections.unmodifiableList(elenco);
            ultimaVoce = c - 1;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Le intestazioni di colonna del modello (es. «ASSISTENTE SPOGLIATOIO»). */
    List<String> voci() {
        return voci;
    }

    /** La voce se è tra quelle del modello (vuota = nessuna); errore altrimenti. */
    String voceValida(String voce) {
        if (voce == null || voce.isBlank()) return null;
        String v = voce.strip();
        if (!voci.contains(v)) throw new IllegalArgumentException("«" + v + "» non è una colonna del foglio ore.");
        return v;
    }

    byte[] compila(Compilazione c) {
        try (var wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(modello)); var out = new ByteArrayOutputStream()) {
            Sheet s = wb.getSheetAt(0);
            int giorniNelMese = c.mese().lengthOfMonth();
            Formati formati = new Formati(wb);

            // colonne: le voci del modello, poi le mansioni senza voce, poi le sostituzioni
            Map<String, Integer> colonne = new LinkedHashMap<>();
            for (int i = 0; i < voci.size(); i++) colonne.put(voci.get(i), PRIMA_VOCE + i);
            int prossima = ultimaVoce + 1;
            for (String voce : c.orePerVoce().keySet()) {
                if (colonne.containsKey(voce)) continue;
                aggiungiColonna(s, prossima, voce);
                colonne.put(voce, prossima++);
            }
            Integer colonnaNote = null;
            if (!c.note().isEmpty()) {
                aggiungiColonna(s, prossima, "Sostituzioni");
                colonnaNote = prossima;
            }

            for (var voce : c.orePerVoce().entrySet()) {
                int col = colonne.get(voce.getKey());
                for (var g : voce.getValue().entrySet()) {
                    if (g.getKey() < 1 || g.getKey() > giorniNelMese) throw new IllegalArgumentException("Giorno fuori dal mese: " + g.getKey());
                    Cell ore = cella(s, RIGA_GIORNO_1 + g.getKey() - 1, col);
                    ore.setCellValue(g.getValue().doubleValue());
                    formati.applica(ore, "0.00");
                }
                String lettera = CellReference.convertNumToColString(col);
                Cell totale = cella(s, RIGA_TOTALE, col);
                totale.setCellFormula("SUM(" + lettera + (RIGA_GIORNO_1 + 1) + ":" + lettera + (RIGA_TOTALE) + ")");
                formati.applica(totale, "0.00");
            }
            if (colonnaNote != null) {
                for (var n : c.note().entrySet())
                    cella(s, RIGA_GIORNO_1 + n.getKey() - 1, colonnaNote).setCellValue(String.join("; ", n.getValue()));
            }

            Cell data = imposta(s, CELLA_DATA);
            data.setCellValue(c.data());
            formati.applica(data, "dd/mm/yyyy");
            imposta(s, CELLA_NOME).setCellValue(c.nome());
            imposta(s, CELLA_MESE).setCellValue(MESI[c.mese().getMonthValue() - 1] + " " + c.mese().getYear());
            wb.setSheetName(0, MESI[c.mese().getMonthValue() - 1] + " " + String.valueOf(c.mese().getYear()).substring(2));
            wb.setForceFormulaRecalculation(true);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Lo stile della cella con un altro formato numerico, senza toccare bordi e colori del modello. */
    private static final class Formati {
        private final XSSFWorkbook wb;
        private final Map<String, CellStyle> cache = new java.util.HashMap<>();

        Formati(XSSFWorkbook wb) {
            this.wb = wb;
        }

        void applica(Cell c, String formato) {
            CellStyle base = c.getCellStyle();
            String chiave = base.getIndex() + "|" + formato;
            CellStyle stile = cache.computeIfAbsent(chiave, k -> {
                CellStyle nuovo = wb.createCellStyle();
                nuovo.cloneStyleFrom(base);
                nuovo.setDataFormat(wb.createDataFormat().getFormat(formato));
                return nuovo;
            });
            c.setCellStyle(stile);
        }
    }

    /** Nuova colonna con lo stesso aspetto dell'ultima voce del modello. */
    private void aggiungiColonna(Sheet s, int col, String intestazione) {
        for (int r = RIGA_VOCI; r <= RIGA_TOTALE; r++) {
            Row riga = s.getRow(r);
            Cell modelloCella = riga == null ? null : riga.getCell(ultimaVoce);
            CellStyle stile = modelloCella == null ? null : modelloCella.getCellStyle();
            Cell nuova = cella(s, r, col);
            if (stile != null) nuova.setCellStyle(stile);
        }
        cella(s, RIGA_VOCI, col).setCellValue(intestazione);
        s.setColumnWidth(col, s.getColumnWidth(ultimaVoce));
    }

    private static Cell cella(Sheet s, int riga, int col) {
        Row r = s.getRow(riga);
        if (r == null) r = s.createRow(riga);
        Cell c = r.getCell(col);
        return c != null ? c : r.createCell(col);
    }

    private static Cell imposta(Sheet s, String ref) {
        CellReference r = new CellReference(ref);
        return cella(s, r.getRow(), r.getCol());
    }

    private static void controlla(Sheet s) {
        for (int g : new int[] { 1, 15, 31 }) {
            Cell c = s.getRow(RIGA_GIORNO_1 + g - 1).getCell(COLONNA_GIORNO);
            if (c == null || c.getCellType() != CellType.NUMERIC || (int) c.getNumericCellValue() != g)
                throw new IllegalStateException("Modello del foglio ore non riconosciuto (giorno " + g + ").");
        }
    }
}
