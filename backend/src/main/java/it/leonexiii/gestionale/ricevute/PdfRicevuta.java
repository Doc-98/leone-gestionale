package it.leonexiii.gestionale.ricevute;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import it.leonexiii.gestionale.societa.Societa;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Il PDF A4 di una ricevuta (o fattura): intestazione della società, numero e data, chi paga e per chi, le righe, il
 * totale e il metodo, la dicitura, lo spazio per la firma e il riquadro della marca da bollo. Annullata: scritta in
 * diagonale.
 */
final class PdfRicevuta {

    private static final Color INCHIOSTRO = new Color(0x1b, 0x26, 0x2c);
    private static final Color GRIGIO = new Color(0x5d, 0x65, 0x5f);
    private static final Color FILO = new Color(0xdd, 0xd6, 0xc8);
    private static final Color CARTA = new Color(0xf4, 0xf1, 0xea);
    private static final Color ROSSO = new Color(0xb4, 0x2b, 0x1e);

    private PdfRicevuta() {}

    static String euro(BigDecimal importo) {
        String s = importo.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String intero = s.substring(0, s.indexOf('.')), dec = s.substring(s.indexOf('.') + 1);
        StringBuilder g = new StringBuilder();
        boolean meno = intero.startsWith("-");
        if (meno) intero = intero.substring(1);
        for (int i = 0; i < intero.length(); i++) {
            if (i > 0 && (intero.length() - i) % 3 == 0) g.append('.');
            g.append(intero.charAt(i));
        }
        return (meno ? "-" : "") + g + "," + dec + " €";
    }

    static byte[] crea(Ricevuta r, Serie.Tipo tipo, Societa.Dati societa, boolean firma) {
        var out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 44, 44);
        PdfWriter w = PdfWriter.getInstance(doc, out);
        String chi = tipo == Serie.Tipo.FATTURA ? "Fattura" : "Ricevuta";
        doc.addTitle(chi + " " + r.getNumeroTesto());
        doc.addCreator("Leone Gestionale");
        doc.open();

        Font titolo = font(Font.BOLD, 15, INCHIOSTRO), normale = font(Font.NORMAL, 10, INCHIOSTRO), piccolo = font(Font.NORMAL, 8.5f, GRIGIO),
            etichetta = font(Font.BOLD, 7.5f, GRIGIO), grassetto = font(Font.BOLD, 11, INCHIOSTRO), numero = font(Font.NORMAL, 15, INCHIOSTRO);

        // intestazione: la società a sinistra, numero e data a destra
        PdfPTable testa = tabella(new float[] {3.2f, 2f});
        PdfPCell soc = cella();
        soc.addElement(new Paragraph(vuotoA(societa.ragioneSociale(), societa.nome()), titolo));
        String indirizzo = Stream.of(societa.indirizzo(), Stream.of(societa.cap(), societa.citta(), vuoto(societa.provincia()) ? null : "(" + societa.provincia() + ")")
            .filter(x -> !vuoto(x)).collect(Collectors.joining(" "))).filter(x -> !vuoto(x)).collect(Collectors.joining(" · "));
        if (!indirizzo.isEmpty()) soc.addElement(new Paragraph(indirizzo, normale));
        String fiscali = Stream.of(vuoto(societa.partitaIva()) ? null : "P. IVA " + societa.partitaIva(),
            vuoto(societa.codiceFiscale()) || Objects.equals(societa.codiceFiscale(), societa.partitaIva()) ? null : "C.F. " + societa.codiceFiscale(),
            societa.email()).filter(x -> !vuoto(x)).collect(Collectors.joining(" · "));
        if (!fiscali.isEmpty()) soc.addElement(new Paragraph(fiscali, normale));
        testa.addCell(soc);
        PdfPCell num = cella();
        num.addElement(destra(new Paragraph(chi.toUpperCase(), etichetta)));
        num.addElement(destra(new Paragraph("n. " + r.getNumeroTesto(), numero)));
        num.addElement(destra(new Paragraph((vuoto(societa.citta()) ? "" : societa.citta() + ", ") + RicevuteService.DATA.format(r.getData()), normale)));
        testa.addCell(num);
        doc.add(testa);
        doc.add(spazio(16));

        // chi paga e per chi
        PdfPTable chiPer = tabella(new float[] {1, 1});
        chiPer.setSpacingBefore(4);
        chiPer.addCell(riquadro("RICEVUTO DA", r.getIntestatario(), etichetta, grassetto, normale, false));
        List<Ricevuta.Soggetto> per = r.getBeneficiari().stream().filter(b -> !b.id().equals(r.getIntestatario().id())).toList();
        if (!per.isEmpty()) chiPer.addCell(riquadroBeneficiari(per, etichetta, grassetto, normale));
        else chiPer.addCell(cella());
        doc.add(chiPer);
        doc.add(spazio(14));

        // le righe
        boolean piuPersone = r.getBeneficiari().size() > 1;
        PdfPTable righe = tabella(piuPersone ? new float[] {4f, 2f, 1.4f} : new float[] {5.6f, 1.4f});
        righe.setHeaderRows(1);
        righe.addCell(intestazioneRiga("DESCRIZIONE", etichetta, Element.ALIGN_LEFT));
        if (piuPersone) righe.addCell(intestazioneRiga("PER", etichetta, Element.ALIGN_LEFT));
        righe.addCell(intestazioneRiga("IMPORTO", etichetta, Element.ALIGN_RIGHT));
        for (Ricevuta.Riga riga : r.getRighe()) {
            righe.addCell(rigaCella(riga.descrizione(), normale, Element.ALIGN_LEFT));
            if (piuPersone) righe.addCell(rigaCella(riga.per(), normale, Element.ALIGN_LEFT));
            righe.addCell(rigaCella(euro(riga.importo()), normale, Element.ALIGN_RIGHT));
        }
        doc.add(righe);
        doc.add(spazio(10));

        // pagato con · totale
        PdfPTable tot = tabella(new float[] {3.2f, 2f});
        PdfPCell come = cella();
        come.addElement(new Paragraph(new Chunk("Pagato con ", grassetto)));
        come.addElement(new Paragraph(r.getMetodi() + " il " + RicevuteService.DATA.format(r.getData()), normale));
        tot.addCell(come);
        PdfPCell totale = cella();
        Paragraph t = new Paragraph();
        t.add(new Chunk("Totale   ", normale));
        t.add(new Chunk(euro(r.getTotale()), font(Font.BOLD, 16, INCHIOSTRO)));
        totale.addElement(destra(t));
        tot.addCell(totale);
        doc.add(tot);

        // la dicitura, la firma e la marca da bollo, in fondo alla pagina
        PdfPTable fondo = tabella(new float[] {4.6f, 1.2f});
        PdfPCell sinistra = cella();
        if (!vuoto(r.getDicitura())) {
            PdfPTable box = tabella(new float[] {1});
            PdfPCell c = new PdfPCell(new Phrase(r.getDicitura(), font(Font.NORMAL, 9, new Color(0x3c, 0x46, 0x40))));
            c.setBackgroundColor(CARTA);
            c.setBorder(Rectangle.NO_BORDER);
            c.setPadding(9);
            box.addCell(c);
            sinistra.addElement(box);
        }
        if (firma) {
            sinistra.addElement(spazio(30));
            PdfPTable f = tabella(new float[] {1});
            f.setWidthPercentage(55);
            f.setHorizontalAlignment(Element.ALIGN_LEFT);
            PdfPCell linea = new PdfPCell(new Phrase("Per la società", piccolo));
            linea.setBorder(Rectangle.TOP);
            linea.setBorderColor(INCHIOSTRO);
            linea.setPaddingTop(4);
            f.addCell(linea);
            sinistra.addElement(f);
        }
        fondo.addCell(sinistra);
        PdfPCell bollo = cella();
        if (r.getBollo() != null) {
            PdfPTable b = tabella(new float[] {1});
            PdfPCell q = new PdfPCell(new Phrase("Marca da bollo\n" + euro(r.getBollo()), piccolo));
            q.setFixedHeight(84);
            q.setHorizontalAlignment(Element.ALIGN_CENTER);
            q.setVerticalAlignment(Element.ALIGN_MIDDLE);
            q.setBorderColor(new Color(0xb9, 0xb1, 0xa3));
            q.setBorderWidth(1.2f);
            b.addCell(q);
            bollo.addElement(b);
        }
        fondo.addCell(bollo);
        fondo.setTotalWidth(doc.getPageSize().getWidth() - 96);
        fondo.writeSelectedRows(0, -1, 48, 44 + 26 + fondo.getTotalHeight(), w.getDirectContent());

        // piede
        PdfContentByte cb = w.getDirectContent();
        cb.setColorStroke(FILO);
        cb.moveTo(48, 58);
        cb.lineTo(doc.getPageSize().getWidth() - 48, 58);
        cb.stroke();
        com.lowagie.text.pdf.ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(chi + " " + r.getNumeroTesto() + " · pagamento del "
            + RicevuteService.DATA.format(r.getData()), piccolo), 48, 46, 0);

        if (!r.attiva()) annullata(cb, doc, r.getMotivoAnnullamento());
        doc.close();
        return out.toByteArray();
    }

    private static void annullata(PdfContentByte cb, Document doc, String motivo) {
        try {
            BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, false);
            cb.saveState();
            PdfGState g = new PdfGState();
            g.setFillOpacity(0.18f);
            cb.setGState(g);
            cb.beginText();
            cb.setFontAndSize(bf, 92);
            cb.setColorFill(ROSSO);
            cb.showTextAligned(Element.ALIGN_CENTER, "ANNULLATA", doc.getPageSize().getWidth() / 2, doc.getPageSize().getHeight() / 2, 45);
            cb.endText();
            cb.restoreState();
            if (!vuoto(motivo))
                com.lowagie.text.pdf.ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase("Annullata: " + motivo, font(Font.BOLD, 8.5f, ROSSO)),
                    doc.getPageSize().getWidth() - 48, 46, 0);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static PdfPCell riquadro(String etichetta, Ricevuta.Soggetto s, Font fe, Font fn, Font f, boolean destra) {
        PdfPCell c = bordata();
        c.addElement(new Paragraph(etichetta, fe));
        c.addElement(new Paragraph(s.nomeCompleto(), fn));
        if (s.codiceFiscale() != null) c.addElement(new Paragraph("C.F. " + s.codiceFiscale(), f));
        if (s.indirizzo() != null) c.addElement(new Paragraph(s.indirizzo(), f));
        return c;
    }

    private static PdfPCell riquadroBeneficiari(List<Ricevuta.Soggetto> per, Font fe, Font fn, Font f) {
        PdfPCell c = bordata();
        c.addElement(new Paragraph("PER L'ATTIVITÀ DI", fe));
        for (Ricevuta.Soggetto s : per) {
            c.addElement(new Paragraph(s.nomeCompleto(), fn));
            String dati = Stream.of(s.codiceFiscale() == null ? null : "C.F. " + s.codiceFiscale(),
                s.dataNascita() == null ? null : "nato/a il " + RicevuteService.DATA.format(s.dataNascita())).filter(Objects::nonNull).collect(Collectors.joining(" · "));
            if (!dati.isEmpty()) c.addElement(new Paragraph(dati, f));
        }
        return c;
    }

    private static PdfPCell bordata() {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(FILO);
        c.setPadding(10);
        c.setPaddingTop(6);
        return c;
    }

    private static PdfPCell intestazioneRiga(String testo, Font f, int allinea) {
        PdfPCell c = new PdfPCell(new Phrase(testo, f));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(INCHIOSTRO);
        c.setBorderWidth(1.2f);
        c.setPaddingBottom(6);
        c.setHorizontalAlignment(allinea);
        return c;
    }

    private static PdfPCell rigaCella(String testo, Font f, int allinea) {
        PdfPCell c = new PdfPCell(new Phrase(testo == null ? "" : testo, f));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(new Color(0xe6, 0xe0, 0xd4));
        c.setPaddingTop(7);
        c.setPaddingBottom(8);
        c.setHorizontalAlignment(allinea);
        return c;
    }

    private static PdfPTable tabella(float[] colonne) {
        PdfPTable t = new PdfPTable(colonne);
        t.setWidthPercentage(100);
        return t;
    }

    private static PdfPCell cella() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(0);
        return c;
    }

    private static Paragraph destra(Paragraph p) {
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private static Paragraph spazio(float alto) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(alto);
        return p;
    }

    private static Font font(int stile, float misura, Color colore) {
        return new Font(Font.HELVETICA, misura, stile, colore);
    }

    private static String vuotoA(String a, String b) {
        return vuoto(a) ? b : a;
    }

    private static boolean vuoto(String s) {
        return s == null || s.isBlank();
    }
}
