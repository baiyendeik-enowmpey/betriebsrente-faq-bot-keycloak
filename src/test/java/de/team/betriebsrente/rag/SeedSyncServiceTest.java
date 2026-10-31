package de.team.betriebsrente.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

class SeedSyncServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void skipsImportWhenSeedFileIsUnchanged() throws Exception {
        File seed = seedFile();
        IngestionService ingestion = mock(IngestionService.class);
        VectorStore vectorStore = mock(VectorStore.class);
        EntityManager em = mock(EntityManager.class);
        Query lastHash = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(lastHash);
        when(lastHash.getResultList()).thenReturn(List.of(SeedSyncService.sha256(seed)));

        var result = new SeedSyncService(ingestion, vectorStore, em).synchronisiere(seed);

        assertEquals(SeedSyncService.Ergebnis.AKTUELL, result);
        verify(ingestion, never()).ingestFromJson(any(File.class), anyString());
        verify(vectorStore, never()).delete(any(Filter.Expression.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void replacesSeedEntriesAndTheirVectorsWhenSeedFileChanged() throws Exception {
        File seed = seedFile();
        IngestionService ingestion = mock(IngestionService.class);
        VectorStore vectorStore = mock(VectorStore.class);
        EntityManager em = mock(EntityManager.class);
        Query nativeQuery = mock(Query.class);
        Query deleteQuery = mock(Query.class);
        TypedQuery<Long> idQuery = mock(TypedQuery.class);
        when(em.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of("alte-pruefsumme"));
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(idQuery);
        when(idQuery.setParameter(anyString(), any())).thenReturn(idQuery);
        when(idQuery.getResultList()).thenReturn(List.of(3L, 4L));
        when(em.createQuery(anyString())).thenReturn(deleteQuery);
        when(deleteQuery.setParameter(anyString(), any())).thenReturn(deleteQuery);
        when(ingestion.ingestFromJson(seed, IngestionService.ERSTELLT_VON_SEED)).thenReturn(30);

        var result = new SeedSyncService(ingestion, vectorStore, em).synchronisiere(seed);

        assertEquals(SeedSyncService.Ergebnis.IMPORTIERT, result);
        verify(vectorStore).delete(new FilterExpressionBuilder().in("fachwissen_id", 3L, 4L).build());
        verify(deleteQuery).setParameter("ids", List.of(3L, 4L));
        verify(ingestion).ingestFromJson(seed, IngestionService.ERSTELLT_VON_SEED);
        verify(nativeQuery).setParameter("hash", SeedSyncService.sha256(seed));
        verify(nativeQuery).setParameter("anzahl", 30);
    }

    private File seedFile() throws Exception {
        Path json = tempDir.resolve("seed.json");
        Files.writeString(json, "[{\"titel\":\"A\",\"kategorie\":\"Steuer\",\"original_text\":\"Text\"}]");
        return json.toFile();
    }
}
