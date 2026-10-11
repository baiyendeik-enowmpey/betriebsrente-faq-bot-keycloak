package de.team.betriebsrente.rag;

import jakarta.persistence.EntityManager;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.File;

@Configuration
public class SeedDataInitializer {

    @Bean
    public ApplicationRunner seedOnEmpty(IngestionService ingestionService, EntityManager em) {
        return args -> {
            try {
                Long count = (Long) em.createQuery("SELECT COUNT(f) FROM Fachwissen f").getSingleResult();
                if (count == 0) {
                    System.out.println("=== Keine Fachdaten vorhanden - importiere Seed Daten ===");
                    String[] paths = {
                            "testdaten/fachwissen_seed.json",
                            "/app/testdaten/fachwissen_seed.json",
                            "src/main/resources/testdaten/fachwissen_seed.json"
                    };
                    for (String p : paths) {
                        File seedFile = new File(p);
                        if (seedFile.isFile()) {
                            ingestionService.ingestFromJson(seedFile);
                            System.out.println("Seed Import erfolgreich: " + seedFile.getAbsolutePath());
                            return;
                        }
                    }
                    System.out.println("Kein Seed File gefunden - bitte manuell über /admin hochladen");
                } else {
                    System.out.println("Fachwissen vorhanden: " + count + " Einträge");
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Seed-Daten konnten nicht geladen werden", e);
            }
        };
    }
}
