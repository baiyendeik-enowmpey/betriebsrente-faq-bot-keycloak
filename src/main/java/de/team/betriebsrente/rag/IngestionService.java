package de.team.betriebsrente.rag;

import de.team.betriebsrente.model.Fachwissen;
import de.team.betriebsrente.model.Gueltigkeit;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.ai.vectorstore.VectorStore;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class IngestionService {
    /** Kennzeichnung für Einträge aus der Seed-Datei; nur diese werden beim Seed-Abgleich ersetzt. */
    public static final String ERSTELLT_VON_SEED = "seed";
    public static final String ERSTELLT_VON_JSON_IMPORT = "json-import";

    private final VectorStore vectorStore;
    private final ChunkingService chunkingService;
    private final EntityManager em;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public IngestionService(VectorStore vectorStore, ChunkingService chunkingService, EntityManager em) {
        this.vectorStore = vectorStore;
        this.chunkingService = chunkingService;
        this.em = em;
    }

    @Transactional
    public int ingestFromJson(File jsonFile) throws Exception {
        return ingestFromJson(jsonFile, ERSTELLT_VON_JSON_IMPORT);
    }

    /**
     * Importiert Fachwissen aus einer JSON-Liste.
     *
     * @return Anzahl der importierten Einträge
     */
    @Transactional
    public int ingestFromJson(File jsonFile, String erstelltVon) throws Exception {
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
            LocalDate gueltigAb = optionalDate(e, "gueltig_ab");
            LocalDate gueltigBis = optionalDate(e, "gueltig_bis");
            String fehler = Gueltigkeit.pruefe(gueltigAb, gueltigBis);
            if (fehler != null) {
                throw new IllegalArgumentException("Seed-Eintrag '" + title + "': " + fehler);
            }
            fw.setTitel(title);
            fw.setKategorie(category);
            fw.setOriginalText(originalText);
            fw.setQuelle(quelle);
            fw.setGueltigAb(gueltigAb);
            fw.setGueltigBis(gueltigBis);
            fw.setErstelltVon(erstelltVon);
            em.persist(fw);
            em.flush();
            Map<String, Object> meta = new HashMap<>();
            meta.put("fachwissen_id", fw.getId());
            meta.put("titel", fw.getTitel());
            meta.put("kategorie", fw.getKategorie());
            meta.put("quelle", fw.getQuelle());
            Gueltigkeit.inMetadaten(meta, gueltigAb, gueltigBis);
            vectorStore.add(chunkingService.chunk(searchableText, meta));
        }
        return entries.size();
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

    private LocalDate optionalDate(@NonNull Map<String, Object> entry, @NonNull String key) {
        Object value = entry.get(key);
        if (value == null || (value instanceof String text && text.isBlank())) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Seed-Feld " + key + " muss ein Datum im Format JJJJ-MM-TT sein", ex);
        }
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
