package de.team.betriebsrente.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.team.betriebsrente.model.Fachwissen;
import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

class IngestionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void importsJsonAndUsesDefaultSourceWhenMissing() throws Exception {
        VectorStore vectorStore = mock(VectorStore.class);
        ChunkingService chunking = mock(ChunkingService.class);
        EntityManager entityManager = mock(EntityManager.class);
        stubChunking(chunking);
        doAnswer(invocation -> {
            invocation.<Fachwissen>getArgument(0).setId(41L);
            return null;
        }).when(entityManager).persist(any(Fachwissen.class));
        Path json = tempDir.resolve("knowledge.json");
        Files.writeString(json, """
                [{"titel":"Guide","kategorie":"Steuer","original_text":"Pension guidance","training_phrases":["How does the pension work?"]}]
                """);

        new IngestionService(vectorStore, chunking, entityManager).ingestFromJson(json.toFile());

        verify(entityManager).flush();
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verifyChunkingCapturedInput(chunking, textCaptor);
        org.junit.jupiter.api.Assertions.assertTrue(textCaptor.getValue().contains("How does the pension work?"));
        verifyVectorStoreReceivedChunks(vectorStore);
        var captor = org.mockito.ArgumentCaptor.forClass(Fachwissen.class);
        verify(entityManager).persist(captor.capture());
        assertEquals("Guide", captor.getValue().getTitel());
        assertEquals("intern", captor.getValue().getQuelle());
        assertEquals("Pension guidance", captor.getValue().getOriginalText());
    }

    @Test
    void importsFaqSeedWithoutOriginalTextUsingTitleAndTrainingPhrases() throws Exception {
        VectorStore vectorStore = mock(VectorStore.class);
        ChunkingService chunking = mock(ChunkingService.class);
        EntityManager entityManager = mock(EntityManager.class);
        stubChunking(chunking);
        doAnswer(invocation -> {
            invocation.<Fachwissen>getArgument(0).setId(42L);
            return null;
        }).when(entityManager).persist(any(Fachwissen.class));
        Path json = tempDir.resolve("faq.json");
        Files.writeString(json, """
                [{"titel":"Rentenversicherung","kategorie":"Beiträge","training_phrases":["Wo melde ich den Beitragsnachweis?"]}]
                """);

        new IngestionService(vectorStore, chunking, entityManager).ingestFromJson(json.toFile());

        var knowledgeCaptor = org.mockito.ArgumentCaptor.forClass(Fachwissen.class);
        verify(entityManager).persist(knowledgeCaptor.capture());
        assertEquals("Titel: Rentenversicherung\nKategorie: Beiträge",
                knowledgeCaptor.getValue().getOriginalText());
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verifyChunkingCapturedInput(chunking, textCaptor);
        org.junit.jupiter.api.Assertions.assertTrue(textCaptor.getValue().contains("Wo melde ich den Beitragsnachweis?"));
        verifyVectorStoreReceivedChunks(vectorStore);
    }

    // Mockito matchers and captors return null sentinels while setting up or verifying an invocation.
    @SuppressWarnings("null")
    private static void stubChunking(ChunkingService chunking) {
        when(chunking.chunk(anyString(), anyMap())).thenReturn(List.of(new Document("chunk")));
    }

    @SuppressWarnings("null")
    private static void verifyChunkingCapturedInput(ChunkingService chunking, ArgumentCaptor<String> textCaptor) {
        verify(chunking).chunk(textCaptor.capture(), anyMap());
    }

    @SuppressWarnings("null")
    private static void verifyVectorStoreReceivedChunks(VectorStore vectorStore) {
        verify(vectorStore).add(anyList());
    }
}
