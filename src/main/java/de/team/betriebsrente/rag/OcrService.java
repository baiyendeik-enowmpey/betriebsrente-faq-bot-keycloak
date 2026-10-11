package de.team.betriebsrente.rag;

import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.awt.image.BufferedImage;

@Service
public class OcrService {

    @Value("${app.ocr.enabled:true}")
    boolean enabled;
    @Value("${app.ocr.language:deu+eng}")
    String lang;

    public String ocrPdf(byte[] pdfBytes) throws Exception {
        if (!enabled)
            return "";
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("/usr/share/tesseract-ocr/5/tessdata");
        // Fallback Pfade für Docker
        try {
            tesseract.setLanguage(lang);
        } catch (Exception e) {
            tesseract.setLanguage("eng");
        }
        tesseract.setOcrEngineMode(1);

        StringBuilder sb = new StringBuilder();
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pages = Math.min(doc.getNumberOfPages(), 20); // Limit für Performance
            for (int i = 0; i < pages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 300);
                String text = tesseract.doOCR(image);
                sb.append(text).append("\n\n");
            }
        }
        return sb.toString();
    }
}
