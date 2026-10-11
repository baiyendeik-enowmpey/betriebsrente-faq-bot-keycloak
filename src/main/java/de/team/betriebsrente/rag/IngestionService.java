package de.team.betriebsrente.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.team.betriebsrente.model.Fachwissen;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import java.io.File;
import java.util.*;

@Service
public class IngestionService {
    private final VectorStore vectorStore;
    private final ChunkingService chunkingService;
    private final EntityManager em;
    private final ObjectMapper mapper = new ObjectMapper();

    public IngestionService(VectorStore vectorStore, ChunkingService chunkingService, EntityManager em) {
        this.vectorStore = vectorStore;
        this.chunkingService = chunkingService;
        this.em = em;
    }

    @Transactional
    public void ingestFromJson(File jsonFile) throws Exception {
        List<Map<String, Object>> entries = mapper.readValue(jsonFile, new TypeReference<>() {
        });
        for (Map<String, Object> e : entries) {
            if (e == null) {
                throw new IllegalArgumentException("Each seed entry must be a JSON object");
            }
            Fachwissen fw = new Fachwissen();
            String title = requiredString(e, "titel");
            String category = requiredString(e, "kategorie");
            String originalText = optionalString(e, "original_text",
                    "Titel: " + title + "\nKategorie: " + category);
            String quelle = optionalString(e, "quelle", "intern");
            String searchableText = searchableText(originalText, e.get("training_phrases"));
            fw.setTitel(title);
            fw.setKategorie(category);
            fw.setOriginalText(originalText);
            fw.setQuelle(quelle);
            em.persist(fw);
            em.flush();
            Map<String, Object> meta = new HashMap<>();
            meta.put("fachwissen_id", fw.getId());
            meta.put("titel", fw.getTitel());
            meta.put("kategorie", fw.getKategorie());
            meta.put("quelle", fw.getQuelle());
            vectorStore.add(chunkingService.chunk(searchableText, meta));
        }
    }

    @NonNull
    private String requiredString(@NonNull Map<String, Object> entry, @NonNull String key) {
        Object value = entry.get(key);
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException("Missing or non-string seed field: " + key);
    }

    @NonNull
    private String optionalString(@NonNull Map<String, Object> entry, @NonNull String key,
            @NonNull String fallback) {
        Object value = entry.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException("Seed field " + key + " must be a string");
    }

    @NonNull
    private String searchableText(@NonNull String originalText, Object trainingPhrases) {
        if (trainingPhrases == null) {
            return originalText;
        }
        if (!(trainingPhrases instanceof List<?> phrases)) {
            throw new IllegalArgumentException("Seed field training_phrases must be an array");
        }
        List<String> values = new ArrayList<>();
        for (Object phrase : phrases) {
            if (!(phrase instanceof String text)) {
                throw new IllegalArgumentException("Seed field training_phrases must contain only strings");
            }
            values.add(text);
        }
        return values.isEmpty() ? originalText
                : originalText + "\n\nBeispielfragen:\n" + String.join("\n", values);
    }
}
