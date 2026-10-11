package de.team.betriebsrente.rag;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatExportServiceTest {

    private final ChatExportService service = new ChatExportService();

    @Test
    void exportsChatMessagesAsPdf() throws Exception {
        byte[] pdf = service.exportChatAsPdf(
                List.of(Map.of("frage", "Question", "antwort", "Answer", "quellen", "Source")),
                "team-user");

        assertPdf(pdf);
    }

    @Test
    void exportsAuditRowsAsPdf() throws Exception {
        byte[] pdf = service.exportAuditAsPdf(
                List.of(Map.of("username", "team-user", "action", "upload", "details", "added file")));

        assertPdf(pdf);
    }

    private void assertPdf(byte[] pdf) {
        assertTrue(new String(pdf, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF-"));
    }
}
