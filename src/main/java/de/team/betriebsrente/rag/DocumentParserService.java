package de.team.betriebsrente.rag;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
@Service
public class DocumentParserService {

    public record ParsedDoc(String text, String fileName, int pages) {
    }

    public ParsedDoc parse(MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        String name = originalFilename == null ? "" : originalFilename.toLowerCase();
        if (name.endsWith(".pdf")) {
            try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                String text = stripper.getText(doc);
                return new ParsedDoc(clean(text), file.getOriginalFilename(), doc.getNumberOfPages());
            }
        } else if (name.endsWith(".docx") || name.endsWith(".doc")) {
            try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
                StringBuilder sb = new StringBuilder();
                doc.getParagraphs().forEach(p -> sb.append(p.getText()).append("\n\n"));
                doc.getTables().forEach(t -> t.getRows().forEach(r -> {
                    r.getTableCells().forEach(c -> sb.append(c.getText()).append(" | "));
                    sb.append("\n");
                }));
                return new ParsedDoc(clean(sb.toString()), file.getOriginalFilename(), 1);
            }
        } else {
            // txt, md
            return new ParsedDoc(clean(new String(file.getBytes())), file.getOriginalFilename(), 1);
        }
    }

    private String clean(String t) {
        return t.replaceAll("\r", "").replaceAll("\n{3,}", "\n\n").trim();
    }
}
