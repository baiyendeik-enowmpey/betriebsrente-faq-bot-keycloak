package de.team.betriebsrente.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OcrServiceTest {

    @Test
    void returnsEmptyTextWhenOcrIsDisabled() throws Exception {
        OcrService service = new OcrService();
        service.enabled = false;

        assertEquals("", service.ocrPdf(new byte[0]));
    }
}
