package de.team.betriebsrente.rag;

import com.lowagie.text.Cell;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Table;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class ChatExportService {
    public byte[] exportChatAsPdf(List<Map<String, String>> chatMessages, String username) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        PdfWriter.getInstance(document, baos);
        document.open();
        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Font h2Font = new Font(Font.HELVETICA, 12, Font.BOLD);
        Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
        Font smallFont = new Font(Font.HELVETICA, 8, Font.ITALIC);
        Paragraph title = new Paragraph("Betriebsrente FAQ - Chat Protokoll", titleFont);
        title.setSpacingAfter(10);
        document.add(title);
        Paragraph meta = new Paragraph(
                String.format("Erstellt fuer: %s | Datum: %s | System: Betriebsrente Bot (lokal)", username,
                        java.time.LocalDate.now().format(DateTimeFormatter.ISO_DATE)),
                smallFont);
        meta.setSpacingAfter(20);
        document.add(meta);
        Paragraph intro = new Paragraph(
                "Dieses Dokument enthaelt die wichtigsten Fragen und Antworten zur Betriebsrentenauszahlung. Alle Antworten basieren auf freigegebenem Fachwissen.",
                normalFont);
        intro.setSpacingAfter(20);
        document.add(intro);
        for (int i = 0; i < chatMessages.size(); i++) {
            Map<String, String> msg = chatMessages.get(i);
            String frage = msg.getOrDefault("frage", "");
            String antwort = msg.getOrDefault("antwort", "");
            String quellen = msg.getOrDefault("quellen", "");
            Paragraph qTitle = new Paragraph(String.format("Frage %d:", i + 1), h2Font);
            qTitle.setSpacingBefore(15);
            document.add(qTitle);
            Paragraph q = new Paragraph(frage, new Font(Font.HELVETICA, 11, Font.BOLD));
            q.setSpacingAfter(5);
            document.add(q);
            Paragraph a = new Paragraph(antwort, normalFont);
            a.setSpacingAfter(5);
            document.add(a);
            if (!quellen.isBlank()) {
                Paragraph src = new Paragraph("Quelle: " + quellen, smallFont);
                src.setSpacingAfter(10);
                document.add(src);
            }
            document.add(new Paragraph("---", smallFont));
        }
        Paragraph footer = new Paragraph(
                "\nHinweis: Automatisch generiert. Bei Unklarheiten Fachbereich kontaktieren. Stand: "
                        + java.time.LocalDate.now(),
                smallFont);
        footer.setSpacingBefore(20);
        document.add(footer);
        document.close();
        return baos.toByteArray();
    }

    public byte[] exportAuditAsPdf(List<Map<String, Object>> audits) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 40, 40, 40, 40);
        PdfWriter.getInstance(document, baos);
        document.open();
        Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
        Font headerFont = new Font(Font.HELVETICA, 9, Font.BOLD);
        Font normalFont = new Font(Font.HELVETICA, 8, Font.NORMAL);
        document.add(new Paragraph("Audit-Log - Betriebsrente Bot", titleFont));
        document.add(new Paragraph("Export: " + java.time.LocalDateTime.now() + "\n\n", normalFont));
        Table table = new Table(7);
        table.setWidth(100);
        table.setWidths(new float[] { 15, 12, 10, 18, 10, 8, 27 });
        String[] headers = { "Zeit", "User", "Action", "Datei/Kategorie", "OCR", "Chunks", "Details" };
        for (String h : headers) {
            Cell c = new Cell(new Phrase(h, headerFont));
            c.setBackgroundColor(new java.awt.Color(230, 230, 230));
            table.addCell(c);
        }
        for (Map<String, Object> a : audits) {
            table.addCell(new Phrase(String.valueOf(a.getOrDefault("timestamp", "")), normalFont));
            table.addCell(new Phrase(String.valueOf(a.getOrDefault("username", "")), normalFont));
            table.addCell(new Phrase(String.valueOf(a.getOrDefault("action", "")), normalFont));
            table.addCell(new Phrase(String.valueOf(a.getOrDefault("fileName", a.getOrDefault("kategorie", ""))),
                    normalFont));
            table.addCell(new Phrase(String.valueOf(a.getOrDefault("ocrUsed", "")), normalFont));
            table.addCell(new Phrase(String.valueOf(a.getOrDefault("chunkCount", "")), normalFont));
            table.addCell(new Phrase(String.valueOf(a.getOrDefault("details", "")), normalFont));
        }
        document.add(table);
        document.close();
        return baos.toByteArray();
    }
}
