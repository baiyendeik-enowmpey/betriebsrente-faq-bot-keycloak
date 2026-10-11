package de.team.betriebsrente.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.test.util.ReflectionTestUtils;

class ChunkingServiceTest {

    @Test
    void chunkAddsIndexesCountsAndTokenEstimates() {
        ChunkingService service = new ChunkingService();
        ReflectionTestUtils.setField(service, "chunkSize", 650);
        ReflectionTestUtils.setField(service, "overlap", 130);

        var chunks = service.chunk(
                "A short piece of pension guidance.", Objects.requireNonNull(Map.of("titel", "Guide")));

        assertEquals(1, chunks.size());
        Document chunk = chunks.get(0);
        String chunkText = Objects.requireNonNull(chunk.getText());
        assertEquals("Guide", chunk.getMetadata().get("titel"));
        assertEquals(0, chunk.getMetadata().get("chunk_index"));
        assertEquals(1, chunk.getMetadata().get("chunk_count"));
        assertEquals(chunkText.length() / 4, chunk.getMetadata().get("token_estimate"));
        assertFalse(chunkText.isBlank());
    }
}
