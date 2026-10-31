package de.team.betriebsrente.rag;

import de.team.betriebsrente.model.Gueltigkeit;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

/**
 * Hält die Seed-Einträge der Wissensbasis mit der Seed-Datei synchron.
 *
 * <p>Ändert sich die Datei (erkannt über eine SHA-256-Prüfsumme), werden alle Einträge mit
 * {@code erstellt_von = 'seed'} samt ihrer Vektoren entfernt und neu importiert. Von Admins
 * hochgeladene Dokumente bleiben unberührt.
 */
@Service
public class SeedSyncService {
    private static final Logger log = LoggerFactory.getLogger(SeedSyncService.class);

    public enum Ergebnis { AKTUELL, IMPORTIERT }

    private final IngestionService ingestionService;
    private final VectorStore vectorStore;
    private final EntityManager em;

    public SeedSyncService(IngestionService ingestionService, VectorStore vectorStore, EntityManager em) {
        this.ingestionService = ingestionService;
        this.vectorStore = vectorStore;
        this.em = em;
    }

    @Transactional
    public Ergebnis synchronisiere(File seedDatei) throws Exception {
        String hash = sha256(seedDatei);
        List<?> letzter = em.createNativeQuery("SELECT hash FROM seed_import ORDER BY id DESC LIMIT 1")
                .getResultList();
        if (!letzter.isEmpty() && hash.equals(letzter.get(0))) {
            return Ergebnis.AKTUELL;
        }
        int entfernt = entferneSeedEintraege();
        int importiert = ingestionService.ingestFromJson(seedDatei, IngestionService.ERSTELLT_VON_SEED);
        em.createNativeQuery("INSERT INTO seed_import (hash, eintraege) VALUES (:hash, :anzahl)")
                .setParameter("hash", hash)
                .setParameter("anzahl", importiert)
                .executeUpdate();
        log.info("Seed-Daten synchronisiert: {} alte Einträge entfernt, {} importiert ({})",
                entfernt, importiert, seedDatei.getAbsolutePath());
        return Ergebnis.IMPORTIERT;
    }

    /**
     * Ergänzt Vektoren ohne Gültigkeitsangabe (z. B. Uploads aus älteren Versionen) um offene
     * Grenzen, damit der Gültigkeitsfilter der Suche sie weiterhin findet.
     */
    @Transactional
    public int ergaenzeFehlendeGueltigkeit() {
        String offen = "{\"%s\": %d, \"%s\": %d}".formatted(
                Gueltigkeit.META_AB, Gueltigkeit.OFFEN_AB, Gueltigkeit.META_BIS, Gueltigkeit.OFFEN_BIS);
        return em.createNativeQuery("""
                UPDATE vector_store
                   SET metadata = (metadata::jsonb || CAST(:offen AS jsonb))::json
                 WHERE NOT jsonb_exists(metadata::jsonb, :schluessel)
                """)
                .setParameter("offen", offen)
                .setParameter("schluessel", Gueltigkeit.META_BIS)
                .executeUpdate();
    }

    private int entferneSeedEintraege() {
        List<Long> ids = em.createQuery(
                        "SELECT f.id FROM Fachwissen f WHERE f.erstelltVon IS NULL OR f.erstelltVon = :seed",
                        Long.class)
                .setParameter("seed", IngestionService.ERSTELLT_VON_SEED)
                .getResultList();
        if (ids.isEmpty()) {
            return 0;
        }
        vectorStore.delete(new FilterExpressionBuilder().in("fachwissen_id", ids.toArray()).build());
        em.createNativeQuery("DELETE FROM fachwissen_chunks WHERE fachwissen_id IN (:ids)")
                .setParameter("ids", ids).executeUpdate();
        em.createQuery("DELETE FROM Fachwissen f WHERE f.id IN :ids")
                .setParameter("ids", ids).executeUpdate();
        return ids.size();
    }

    static String sha256(File datei) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(datei.toPath()));
        return HexFormat.of().formatHex(digest);
    }
}
