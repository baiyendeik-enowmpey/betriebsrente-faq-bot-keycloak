package de.team.betriebsrente.rag;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Baut den Vektor-Index neu auf, wenn sich das Embedding-Modell geändert hat.
 *
 * <p>Vektoren verschiedener Modelle sind nicht vergleichbar. Deshalb werden bei einem Modellwechsel
 * alle Textabschnitte aus {@code vector_store} (Inhalt und Metadaten bleiben erhalten – auch die
 * Beispielfragen der Seed-Daten und Admin-Uploads) mit dem neuen Modell neu eingebettet. Ändert sich
 * die Dimension, wird die Spalte {@code embedding} angepasst.
 */
@Service
public class VektorIndexService {
    private static final Logger log = LoggerFactory.getLogger(VektorIndexService.class);

    public enum Ergebnis { AKTUELL, NEU_AUFGEBAUT }

    private final VectorStore vectorStore;
    private final EntityManager em;
    private final String modell;
    private final int dimensionen;
    private final JsonMapper json = JsonMapper.builder().build();

    public VektorIndexService(VectorStore vectorStore, EntityManager em,
            @Value("${spring.ai.ollama.embedding.options.model}") String modell,
            @Value("${spring.ai.vectorstore.pgvector.dimensions}") int dimensionen) {
        this.vectorStore = vectorStore;
        this.em = em;
        this.modell = modell;
        this.dimensionen = dimensionen;
    }

    @Transactional
    public Ergebnis abgleichen() {
        List<?> letzter = em.createNativeQuery(
                        "SELECT embedding_modell, dimensionen FROM vektor_index ORDER BY id DESC LIMIT 1")
                .getResultList();
        if (!letzter.isEmpty()) {
            Object[] zeile = (Object[]) letzter.get(0);
            if (modell.equals(zeile[0]) && dimensionen == ((Number) zeile[1]).intValue()) {
                return Ergebnis.AKTUELL;
            }
            log.info("Embedding-Modell geändert: {} ({} Dim.) -> {} ({} Dim.) – Vektor-Index wird neu aufgebaut",
                    zeile[0], zeile[1], modell, dimensionen);
        } else {
            log.info("Kein Protokoll zum Vektor-Index – Index wird mit {} ({} Dim.) neu aufgebaut", modell, dimensionen);
        }

        List<Document> abschnitte = ladeAbschnitte();
        em.createNativeQuery("DELETE FROM vector_store").executeUpdate();
        // Leere Tabelle: Typänderung ist ohne Datenkonvertierung möglich; der HNSW-Index wird dabei neu erstellt.
        em.createNativeQuery("ALTER TABLE vector_store ALTER COLUMN embedding TYPE vector(" + dimensionen + ")")
                .executeUpdate();
        if (!abschnitte.isEmpty()) {
            vectorStore.add(abschnitte);
        }
        em.createNativeQuery(
                        "INSERT INTO vektor_index (embedding_modell, dimensionen, abschnitte) VALUES (:m, :d, :a)")
                .setParameter("m", modell)
                .setParameter("d", dimensionen)
                .setParameter("a", abschnitte.size())
                .executeUpdate();
        log.info("Vektor-Index neu aufgebaut: {} Textabschnitte mit {} eingebettet", abschnitte.size(), modell);
        return Ergebnis.NEU_AUFGEBAUT;
    }

    private List<Document> ladeAbschnitte() {
        List<?> zeilen = em.createNativeQuery("SELECT content, CAST(metadata AS text) FROM vector_store")
                .getResultList();
        List<Document> abschnitte = new ArrayList<>();
        for (Object z : zeilen) {
            Object[] zeile = (Object[]) z;
            Map<String, Object> metadaten = zeile[1] == null ? Map.of()
                    : json.readValue((String) zeile[1], new TypeReference<Map<String, Object>>() {
                    });
            abschnitte.add(new Document(String.valueOf(zeile[0]), metadaten));
        }
        return abschnitte;
    }
}
