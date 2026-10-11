package de.team.betriebsrente.rag;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.ApplicationRunner;

class SeedDataInitializerTest {

    @Test
    void skipsImportWhenKnowledgeAlreadyExists() throws Exception {
        IngestionService ingestionService = mock(IngestionService.class);
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(2L);

        ApplicationRunner runner = new SeedDataInitializer().seedOnEmpty(ingestionService, entityManager);
        runner.run(new DefaultApplicationArguments());

        verifyNoInteractions(ingestionService);
    }

    @Test
    void exposesSeedImportFailuresInsteadOfReportingMissingFile() throws Exception {
        IngestionService ingestionService = mock(IngestionService.class);
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);
        doThrow(new IllegalStateException("seed import failed"))
                .when(ingestionService).ingestFromJson(ArgumentMatchers.any(File.class));

        ApplicationRunner runner = new SeedDataInitializer().seedOnEmpty(ingestionService, entityManager);

        assertThrows(IllegalStateException.class, () -> runner.run(new DefaultApplicationArguments()));
    }
}
