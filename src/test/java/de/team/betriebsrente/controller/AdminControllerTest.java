package de.team.betriebsrente.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.team.betriebsrente.model.Fachwissen;
import de.team.betriebsrente.rag.ChunkingService;
import de.team.betriebsrente.rag.DocumentParserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;

class AdminControllerTest {

    @Test
    @SuppressWarnings("null") // Mockito's generic matchers are not annotated for Spring AI's @NonNull parameters.
    void uploadPersistsDocumentAndAddsChunks() throws Exception {
        DocumentParserService parser = mock(DocumentParserService.class);
        ChunkingService chunking = mock(ChunkingService.class);
        VectorStore vectorStore = mock(VectorStore.class);
        EntityManager entityManager = mock(EntityManager.class);
        String text = "Detailed pension guidance ".repeat(4);
        when(parser.parse(any())).thenReturn(new DocumentParserService.ParsedDoc(text, "guide.txt", 1));
        when(chunking.chunk(anyString(), anyMap())).thenReturn(List.of(new Document("chunk")));
        doAnswer(invocation -> {
            invocation.<Fachwissen>getArgument(0).setId(23L);
            return null;
        }).when(entityManager).persist(any(Fachwissen.class));
        var file = new MockMultipartFile("file", "guide.txt", "text/plain", text.getBytes());

        Map<String, Object> result = new AdminController(parser, chunking, vectorStore, entityManager)
                .upload(file, "Steuer", "", null);

        assertEquals(true, result.get("success"));
        assertEquals("guide.txt", result.get("titel"));
        assertEquals(1, result.get("chunks"));
        verify(entityManager).flush();
        verify(vectorStore).add(anyList());
    }

    @Test
    void uploadRejectsTextThatIsTooShort() throws Exception {
        DocumentParserService parser = mock(DocumentParserService.class);
        EntityManager entityManager = mock(EntityManager.class);
        when(parser.parse(any())).thenReturn(new DocumentParserService.ParsedDoc("short text", "guide.txt", 1));

        Map<String, Object> result = new AdminController(
                parser, mock(ChunkingService.class), mock(VectorStore.class), entityManager)
                .upload(new MockMultipartFile("file", "guide.txt", "text/plain", new byte[0]), "Steuer", "", null);

        assertTrue(result.containsKey("error"));
        verify(entityManager, org.mockito.Mockito.never()).persist(any());
    }

    @Test
    void deleteRemovesChunksAndDocument() {
        EntityManager entityManager = mock(EntityManager.class);
        Query chunkQuery = mock(Query.class);
        Query documentQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(chunkQuery);
        when(entityManager.createQuery(anyString())).thenReturn(documentQuery);
        when(chunkQuery.setParameter(anyString(), any())).thenReturn(chunkQuery);
        when(documentQuery.setParameter(anyString(), any())).thenReturn(documentQuery);
        when(chunkQuery.executeUpdate()).thenReturn(1);
        when(documentQuery.executeUpdate()).thenReturn(1);

        Map<String, String> result = new AdminController(
                mock(DocumentParserService.class), mock(ChunkingService.class), mock(VectorStore.class), entityManager)
                .delete(23L);

        assertEquals("gelöscht", result.get("success"));
    }
}
