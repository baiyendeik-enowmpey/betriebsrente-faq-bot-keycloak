package de.team.betriebsrente.rag;

import java.io.File;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
public class SeedDataInitializer {
    private static final Logger log = LoggerFactory.getLogger(SeedDataInitializer.class);

    static final List<String> SEED_PFADE = List.of(
            "testdaten/fachwissen_seed.json",
            "/app/testdaten/fachwissen_seed.json",
            "src/main/resources/testdaten/fachwissen_seed.json");

    @Bean
    @Order(0) // vor der Qualitätsprüfung (EvalRunner)
    public ApplicationRunner seedOnEmpty(VektorIndexService vektorIndex, SeedSyncService seedSync) {
        return args -> {
            try {
                // Zuerst den Index an ein ggf. geändertes Embedding-Modell anpassen, dann Seed-Daten abgleichen.
                vektorIndex.abgleichen();
                File seedDatei = SEED_PFADE.stream().map(File::new).filter(File::isFile).findFirst().orElse(null);
                if (seedDatei == null) {
                    log.info("Kein Seed-File gefunden – Fachwissen bitte über /admin hochladen");
                } else if (seedSync.synchronisiere(seedDatei) == SeedSyncService.Ergebnis.AKTUELL) {
                    log.info("Seed-Daten sind aktuell ({})", seedDatei.getAbsolutePath());
                }
                int ergaenzt = seedSync.ergaenzeFehlendeGueltigkeit();
                if (ergaenzt > 0) {
                    log.info("Gültigkeit bei {} Textabschnitten ohne Angabe als unbefristet ergänzt", ergaenzt);
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Seed-Daten konnten nicht geladen werden", e);
            }
        };
    }
}
