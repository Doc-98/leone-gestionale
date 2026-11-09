package it.leonexiii.gestionale.cassa;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Il riepilogo A4 di una chiusura di cassa, da firmare e conservare: cassa e periodo, chi ha aperto e chiuso, il conto
 * dei contanti (fondo, entrati, restituiti, versamenti, spese, aggiunte, attesi, contati, differenza), quanto si è
 * lasciato e versato, i totali per metodo, i movimenti a mano, le note e le firme.
 */
final class PdfChiusura {

    private static final Color INCHIOSTRO = new Color(0x1b, 0x26, 0x2c);
    private static final Color GRIGIO = new Color(0x5d, 0x65, 0x5f);
    private static final Color FILO = new Color(0xdd, 0xd6, 0xc8);
    private static final Color CARTA = new Color(0xf4, 0xf1, 0xea);
    private static final Color ROSSO = new Color(0xb4, 0x2b, 0x1e);
    private static final Color VERDE = new Color(0x1f, 0x6b, 0x45);
    private static final DateTimeFormatter ORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(CassaService.FUSO);

    private PdfChiusura() {}

    static String euro(BigDecimal importo) {
        String s = importo.setScale(2, RoundingMode.HALF_UP).toPlainString();
        boolean meno = s.startsWith("-");
        if (meno) s = s.substring(1);
        String intero = s.substring(0, s.indexOf('.')), dec = s.substring(s.indexOf('.') + 1);
        StringBuilder g = new StringBuilder();
        for (int i = 0; i < intero.length(); i++) {
            if (i > 0 && (intero.length() - i) % 3 == 0) g.append('.');
            g.append(intero.charAt(i));
        }
        return (meno ? "−" : "") + g + "," + dec + " €";
    }

    static byte[] crea(CassaService.SessioneDto s, String societa) {
        var out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 44, 44);
        PdfWriter.getInstance(doc, out);
        doc.addTitle("Chiusura di cassa " + s.cassa() + " " + ORA.format(s.chiusaIl()));
        doc.addCreator("Leone Gestionale");
        doc.open();
        Font titolo = font(Font.BOLD, 15, INCHIOSTRO), normale = font(Font.NORMAL, 10, INCHIOSTRO), piccolo = font(Font.NORMAL, 8.5f, GRIGIO),
            etichetta = font(Font.BOLD, 7.5f, GRIGIO), grassetto = font(Font.BOLD, 10, INCHIOSTRO);
        var c = s.conti();

        doc.add(new Paragraph(societa == null || societa.isBlank() ? "Chiusura di cassa" : societa, titolo));
        doc.add(new Paragraph("CHIUSURA DI CASSA · " + s.cassa().toUpperCase(), etichetta));
        doc.add(spazio(6));
        doc.add(new Paragraph("Dal " + quando(s.dal()) + " al " + quando(s.chiusaIl()), normale));
        String chi = (s.esplicita() && s.apertaDa() != null ? "Aperta da " + s.apertaDa() + " alle " + ORA.format(s.apertaIl()) + " · " : "")
            + "Chiusa da " + (s.chiusaDa() == null ? "—" : s.chiusaDa());
        doc.add(new Paragraph(chi, piccolo));
        doc.add(spazio(14));

        // il conto dei contanti
        PdfPTable conto = tabella(new float[] {4f, 1.6f});
        riga(conto, "Fondo cassa iniziale", euro(c.fondo()), normale);
        if (s.fondoAtteso() != null && s.fondoAtteso().compareTo(s.fondo()) != 0)
            riga(conto, "   (lasciato alla chiusura prima: " + euro(s.fondoAtteso()) + ")", "", piccolo);
        riga(conto, "+ Contanti incassati", euro(c.entrati()), normale);
        riga(conto, "− Contanti restituiti", euro(c.usciti()), normale);
        if (c.versamenti().signum() > 0) riga(conto, "− Versamenti durante il turno", euro(c.versamenti()), normale);
        if (c.spese().signum() > 0) riga(conto, "− Spese pagate con la cassa", euro(c.spese()), normale);
        if (c.aggiunte().signum() > 0) riga(conto, "+ Aggiunte", euro(c.aggiunte()), normale);
        totale(conto, "Contanti attesi", euro(s.attesi()), grassetto);
        riga(conto, "Contati", euro(s.contati()), grassetto);
        if (s.primoConteggio() != null && s.primoConteggio().compareTo(s.contati()) != 0)
            riga(conto, "   (primo conteggio, prima di ricontare: " + euro(s.primoConteggio()) + ")", "", piccolo);
        int segno = s.differenza().signum();
        Font fd = font(Font.BOLD, 10, segno == 0 ? VERDE : ROSSO);
        riga(conto, segno == 0 ? "Differenza: la cassa torna" : segno > 0 ? "Differenza: in più" : "Differenza: mancano", (segno > 0 ? "+" : "") + euro(s.differenza()), fd);
        riga(conto, "Lasciato in cassa per la prossima volta", euro(s.lasciato()), normale);
        riga(conto, "Versato (tolto dal cassetto)", euro(s.versato()), normale);
        doc.add(conto);
        doc.add(spazio(16));

        // per metodo
        if (!c.perMetodo().isEmpty()) {
            doc.add(new Paragraph("INCASSI PER METODO · " + (c.pagamenti() == 1 ? "1 pagamento" : c.pagamenti() + " pagamenti"), etichetta));
            doc.add(spazio(4));
            PdfPTable met = tabella(new float[] {3f, 1.4f, 1.4f});
            intestazione(met, etichetta, "METODO", "ENTRATE", "USCITE");
            for (var m : c.perMetodo()) {
                met.addCell(cella(m.metodo() + (m.contanti() ? "  (nel cassetto)" : ""), normale, Element.ALIGN_LEFT));
                met.addCell(cella(euro(m.entrate()), normale, Element.ALIGN_RIGHT));
                met.addCell(cella(m.uscite().signum() == 0 ? "—" : euro(m.uscite()), normale, Element.ALIGN_RIGHT));
            }
            doc.add(met);
            doc.add(spazio(16));
        }

        // riscontri con il POS
        if (s.riscontri() != null && !s.riscontri().isEmpty()) {
            doc.add(new Paragraph("RISCONTRO CON IL POS", etichetta));
            doc.add(spazio(4));
            PdfPTable ris = tabella(new float[] {2.4f, 1.3f, 1.3f, 1.3f});
            intestazione(ris, etichetta, "METODO", "ATTESO", "SCONTRINO", "DIFFERENZA");
            for (var r : s.riscontri()) {
                ris.addCell(cella(r.metodo(), normale, Element.ALIGN_LEFT));
                ris.addCell(cella(euro(r.atteso()), normale, Element.ALIGN_RIGHT));
                ris.addCell(cella(r.dichiarato() == null ? "non scritto" : euro(r.dichiarato()), normale, Element.ALIGN_RIGHT));
                int sd = r.differenza().signum();
                ris.addCell(cella(r.dichiarato() == null ? "—" : sd == 0 ? "torna" : (sd > 0 ? "+" : "") + euro(r.differenza()),
                    font(Font.BOLD, 10, r.dichiarato() == null || sd == 0 ? INCHIOSTRO : ROSSO), Element.ALIGN_RIGHT));
            }
            doc.add(ris);
            doc.add(spazio(16));
        }

        // movimenti a mano
        var mov = s.movimenti() == null ? java.util.List.<CassaService.MovimentoDto>of() : s.movimenti().stream().filter(m -> !m.annullato()).toList();
        if (!mov.isEmpty()) {
            doc.add(new Paragraph("SOLDI TOLTI O MESSI A MANO", etichetta));
            doc.add(spazio(4));
            PdfPTable t = tabella(new float[] {1.4f, 3.6f, 1.4f});
            intestazione(t, etichetta, "ORA", "COSA", "IMPORTO");
            for (var m : mov) {
                t.addCell(cella(ORA.format(m.registratoIl()).substring(11), normale, Element.ALIGN_LEFT));
                String tipo = switch (m.tipo()) { case "VERSAMENTO" -> "Versamento"; case "SPESA" -> "Spesa"; default -> "Aggiunta"; };
                t.addCell(cella(tipo + (m.descrizione().equals(tipo) ? "" : ": " + m.descrizione()) + (m.registratoDa() == null ? "" : " (" + m.registratoDa() + ")"),
                    normale, Element.ALIGN_LEFT));
                t.addCell(cella(("AGGIUNTA".equals(m.tipo()) ? "+" : "−") + euro(m.importo()), normale, Element.ALIGN_RIGHT));
            }
            doc.add(t);
            doc.add(spazio(16));
        }

        if (s.note() != null && !s.note().isBlank()) {
            PdfPTable box = tabella(new float[] {1});
            PdfPCell n = new PdfPCell(new Phrase("Note: " + s.note(), font(Font.NORMAL, 9, INCHIOSTRO)));
            n.setBackgroundColor(CARTA);
            n.setBorder(Rectangle.NO_BORDER);
            n.setPadding(9);
            box.addCell(n);
            doc.add(box);
            doc.add(spazio(16));
        }

        // le firme
        doc.add(spazio(28));
        PdfPTable firme = tabella(new float[] {1, 0.3f, 1});
        firme.addCell(firma("Chi ha chiuso la cassa", piccolo));
        firme.addCell(vuota());
        firme.addCell(firma(s.controllataIl() != null ? "Controllata da " + s.controllataDa() + " il " + ORA.format(s.controllataIl())
            + (s.notaControllo() != null ? ": " + s.notaControllo() : "") : s.daControllare() ? "Chi ha controllato (da controllare)" : "Chi ha controllato", piccolo));
        doc.add(firme);
        doc.close();
        return out.toByteArray();
    }

    private static String quando(Instant i) {
        return ORA.format(i);
    }

    private static void riga(PdfPTable t, String cosa, String quanto, Font f) {
        t.addCell(cella(cosa, f, Element.ALIGN_LEFT));
        t.addCell(cella(quanto, f, Element.ALIGN_RIGHT));
    }

    private static void totale(PdfPTable t, String cosa, String quanto, Font f) {
        for (String testo : new String[] {cosa, quanto}) {
            PdfPCell c = cella(testo, f, testo.equals(cosa) ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            c.setBorder(Rectangle.TOP);
            c.setBorderColor(INCHIOSTRO);
            t.addCell(c);
        }
    }

    private static void intestazione(PdfPTable t, Font f, String... testi) {
        for (int i = 0; i < testi.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(testi[i], f));
            c.setBorder(Rectangle.BOTTOM);
            c.setBorderColor(FILO);
            c.setPaddingBottom(5);
            c.setHorizontalAlignment(i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            t.addCell(c);
        }
        t.setHeaderRows(1);
    }

    private static PdfPCell cella(String testo, Font f, int allinea) {
        PdfPCell c = new PdfPCell(new Phrase(testo, f));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(FILO);
        c.setPaddingTop(5);
        c.setPaddingBottom(6);
        c.setHorizontalAlignment(allinea);
        return c;
    }

    private static PdfPCell firma(String testo, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(testo, f));
        c.setBorder(Rectangle.TOP);
        c.setBorderColor(INCHIOSTRO);
        c.setPaddingTop(4);
        return c;
    }

    private static PdfPCell vuota() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private static PdfPTable tabella(float[] colonne) {
        PdfPTable t = new PdfPTable(colonne);
        t.setWidthPercentage(100);
        return t;
    }

    private static Paragraph spazio(float alto) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(alto);
        return p;
    }

    private static Font font(int stile, float misura, Color colore) {
        return new Font(Font.HELVETICA, misura, stile, colore);
    }
}
