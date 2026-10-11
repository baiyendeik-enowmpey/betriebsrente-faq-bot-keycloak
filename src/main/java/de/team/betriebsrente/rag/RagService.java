package de.team.betriebsrente.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {
        private final VectorStore vectorStore;
        private final ChatClient chatClient;

        public RagService(VectorStore vectorStore, ChatClient.Builder builder) {
                this.vectorStore = vectorStore;
                this.chatClient = builder.build();
        }

        public record Answer(String antwort, List<Document> quellen) {
        }

        public Answer ask(String frage, String kategorieFilter) {
                // Threshold von 0.72 -> 0.55 gesenkt, topK 6, sonst kommt bei leerer DB sofort
                // "keine Fachinfo"
                SearchRequest req = SearchRequest.builder()
                                .query(frage == null ? "" : frage)
                                .topK(3)
                                .similarityThreshold(0.35) // niedriger = findet auch bei wenig Daten
                                .build();
                List<Document> docs = vectorStore.similaritySearch(req);
                if (docs == null) {
                        docs = List.of();
                }

                // Wenn Filter gesetzt, nach kategorie filtern
                if (kategorieFilter != null && !kategorieFilter.isBlank()
                                && !"Alle".equalsIgnoreCase(kategorieFilter)) {
                        docs = docs.stream()
                                        .filter(d -> kategorieFilter
                                                        .equalsIgnoreCase((String) d.getMetadata().get("kategorie")))
                                        .toList();
                }

                String context = docs.stream()
                                .map(d -> String.format("[%s | %s | Quelle: %s]\n%s",
                                                d.getMetadata().get("titel"),
                                                d.getMetadata().get("kategorie"),
                                                d.getMetadata().get("quelle"),
                                                d.getText()))
                                .collect(Collectors.joining("\n\n---\n\n"));

                String system = """
                                Du bist Fachexperte fuer Betriebsrentenauszahlung im internen Team.
                                Regeln:
                                1. Antworte NUR basierend auf dem KONTEXT. Erfinde nichts.
                                2. Wenn KONTEXT leer ist oder mit 'KEIN KONTEXT' markiert ist, sage EXAKT:
                                   'Dazu liegt keine freigegebene Fachinfo vor. Bitte Fachbereich fragen.'
                                3. Wenn KONTEXT vorhanden ist, antworte strukturiert:
                                   - Kurze Antwort in 2 Saetzen
                                   - Details mit Paragraphen
                                   - Hinweis zu Freibeträgen / Fünftelregelung wenn relevant
                                4. Nenne immer Quelle in Klammern.
                                KONTEXT: {context}
                                """;

                String ctxParam = context.isBlank() ? "KEIN KONTEXT" : context;
                String antwort = chatClient.prompt()
                                .system(s -> s.text(system).param("context", ctxParam))
                                .user(frage == null ? "" : frage)
                                .call().content();

                return new Answer(antwort, docs);
        }
}
