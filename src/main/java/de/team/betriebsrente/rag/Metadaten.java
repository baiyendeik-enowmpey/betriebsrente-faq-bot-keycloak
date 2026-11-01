package de.team.betriebsrente.rag;

import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Liest die JSON-Metadaten eines Abschnitts aus {@code vector_store}. */
final class Metadaten {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Metadaten() {
    }

    static Map<String, Object> lese(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return JSON.readValue(json, new TypeReference<Map<String, Object>>() {
        });
    }
}
