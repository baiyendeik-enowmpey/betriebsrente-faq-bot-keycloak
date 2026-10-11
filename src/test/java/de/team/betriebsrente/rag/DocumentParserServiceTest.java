package de.team.betriebsrente.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentParserServiceTest {

    @Test
    void parsesAndCleansPlainText() throws Exception {
        var file = new MockMultipartFile(
                "file",
                "guidance.txt",
                "text/plain",
                " Pension\r\nguidance.\n\n\nNext paragraph. ".getBytes(StandardCharsets.UTF_8));

        var parsed = new DocumentParserService().parse(file);

        assertEquals("guidance.txt", parsed.fileName());
        assertEquals("Pension\nguidance.\n\nNext paragraph.", parsed.text());
        assertEquals(1, parsed.pages());
    }

    @Test
    void parsesMarkdownAsText() throws Exception {
        var file = new MockMultipartFile("file", "notes.md", "text/markdown", "# Topic\nDetails".getBytes(StandardCharsets.UTF_8));

        var parsed = new DocumentParserService().parse(file);

        assertEquals("# Topic\nDetails", parsed.text());
        assertEquals(1, parsed.pages());
    }
}
