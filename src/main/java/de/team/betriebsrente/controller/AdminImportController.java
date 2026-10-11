package de.team.betriebsrente.controller;

import de.team.betriebsrente.rag.IngestionService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import java.io.File;

@Component
public class AdminImportController implements ApplicationRunner {
    private final IngestionService ingestionService;

    public AdminImportController(IngestionService s) {
        this.ingestionService = s;
    }

    public void run(ApplicationArguments args) throws Exception {
        if (args.containsOption("import")) {
            String path = args.getOptionValues("import").get(0);
            System.out.println("Importiere " + path);
            ingestionService.ingestFromJson(new File(path));
            System.out.println("Import fertig.");
        }
    }
}
