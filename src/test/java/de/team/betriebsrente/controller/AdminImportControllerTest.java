
package de.team.betriebsrente.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.team.betriebsrente.rag.IngestionService;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class AdminImportControllerTest {

    @Test
    void importsFileWhenImportOptionIsProvided() throws Exception {
        IngestionService ingestionService = mock(IngestionService.class);

        new AdminImportController(ingestionService).run(new DefaultApplicationArguments("--import=seed.json"));

        verify(ingestionService).ingestFromJson(new File("seed.json"));
    }

    @Test
    void doesNotImportWithoutImportOption() throws Exception {
        IngestionService ingestionService = mock(IngestionService.class);

        new AdminImportController(ingestionService).run(new DefaultApplicationArguments("--spring.profiles.active=test"));

        verifyNoInteractions(ingestionService);
    }
}
