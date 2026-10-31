package de.team.betriebsrente.rag;

import de.team.betriebsrente.model.Gueltigkeit;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RagService {
    static final int TOP_K = 3;
    /**
     * Mindestähnlichkeit (Kosinus) eines Abschnitts – abhängig vom Embedding-Modell. Gemessen mit
     * nomic-embed-text-v2-moe (10/2026): bei 0,33–0,39 bleibt die Trefferquote bei 93,9 % und alle
     * fachfremden Testfragen werden abgelehnt. Überschreibbar per app.rag.similarity-threshold.
     */
    static final double DEFAULT_SIMILARITY_THRESHOLD = 0.37;

    /**
     * Antwort, wenn keine gültige, freigegebene Fachinfo gefunden wurde. Das Modell wird dann gar
     * nicht erst gefragt, damit es nichts erfinden kann (Text aus dem früheren Eskalations-Dialog).
     */
    static final String KEINE_FACHINFO = """
            Dazu liegt keine freigegebene Fachinfo vor.

            Tipp: Formuliere die Frage mit Fachbegriffen, z. B. Meldegrund, Beitragsgrenzen für KV/PV \
            oder Kinderdaten-Abruf über das DaBPV – oder wähle beim Kategorie-Filter „Alle“.

            Bevor im Abrechnungssystem ein Fehler passiert oder ein Datensatz falsch storniert wird, \
            wende dich bitte direkt an deine Onboarding-Paten oder den Teamleiter. Notiere dir am besten \
            den betroffenen Rentnerfall und die eventuelle Fehlermeldung aus der Software.""";

    private static final String SYSTEM_PROMPT = """
            Du bist Fachexperte fuer Betriebsrentenauszahlung im internen Team.
            Regeln:
            1. Antworte NUR basierend auf dem KONTEXT. Erfinde nichts.
            2. Wenn der KONTEXT die Frage nicht beantwortet, sage EXAKT:
               'Dazu liegt keine freigegebene Fachinfo vor. Bitte Onboarding-Paten oder Teamleiter fragen.'
            3. Antworte strukturiert:
               - Kurze Antwort in 2 Saetzen
               - Details mit Paragraphen
               - Hinweis zu Freibeträgen / Fünftelregelung wenn relevant
            4. Nenne immer Quelle in Klammern.
            KONTEXT: {context}
            """;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final Clock clock;
    private final double similarityThreshold;

    @Autowired
    public RagService(VectorStore vectorStore, ChatClient.Builder builder,
            @Value("${app.rag.similarity-threshold:0.37}") double similarityThreshold) {
        this(vectorStore, builder, Clock.systemDefaultZone(), similarityThreshold);
    }

    RagService(VectorStore vectorStore, ChatClient.Builder builder, Clock clock) {
        this(vectorStore, builder, clock, DEFAULT_SIMILARITY_THRESHOLD);
    }

    RagService(VectorStore vectorStore, ChatClient.Builder builder, Clock clock, double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.chatClient = builder.build();
        this.clock = clock;
        this.similarityThreshold = similarityThreshold;
    }

    public record Answer(String antwort, List<Document> quellen) {
    }

    public Answer ask(String frage, String kategorieFilter) {
        String query = frage == null ? "" : frage.strip();
        List<Document> docs = sucheQuellen(query, kategorieFilter);
        if (docs.isEmpty()) {
            return new Answer(KEINE_FACHINFO, List.of());
        }

        String context = docs.stream()
                .map(d -> String.format("[%s | %s | Quelle: %s]\n%s",
                        d.getMetadata().get("titel"),
                        d.getMetadata().get("kategorie"),
                        d.getMetadata().get("quelle"),
                        d.getText()))
                .collect(Collectors.joining("\n\n---\n\n"));

        String antwort = chatClient.prompt()
                .system(s -> s.text(SYSTEM_PROMPT).param("context", context))
                .user(query)
                .call().content();

        return new Answer(antwort, docs);
    }

    /**
     * Sucht die Kontext-Abschnitte für eine Frage – genau so, wie sie {@link #ask} verwendet,
     * aber ohne das Sprachmodell aufzurufen (z. B. für die Qualitätsprüfung).
     */
    public List<Document> sucheQuellen(String frage, String kategorieFilter) {
        String query = frage == null ? "" : frage.strip();
        boolean kategorieAktiv = kategorieFilter != null && !kategorieFilter.isBlank()
                && !"Alle".equalsIgnoreCase(kategorieFilter);

        SearchRequest req = SearchRequest.builder()
                .query(query)
                .topK(TOP_K)
                .similarityThreshold(similarityThreshold)
                .filterExpression(suchfilter(kategorieAktiv ? kategorieFilter.strip() : null))
                .build();
        List<Document> docs = vectorStore.similaritySearch(req);
        if (docs == null) {
            docs = List.of();
        }
        // Sicherheitsnetz (z.B. abweichende Groß-/Kleinschreibung bei Mock-/Fremd-Stores)
        if (kategorieAktiv) {
            docs = docs.stream()
                    .filter(d -> kategorieFilter.strip()
                            .equalsIgnoreCase(String.valueOf(d.getMetadata().get("kategorie"))))
                    .toList();
        }
        return docs;
    }

    /** Nur heute gültiges Fachwissen; optional zusätzlich auf eine Kategorie eingeschränkt. */
    Filter.Expression suchfilter(String kategorie) {
        int heute = Gueltigkeit.schluessel(LocalDate.now(clock));
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op gueltig = b.and(
                b.lte(Gueltigkeit.META_AB, heute),
                b.gte(Gueltigkeit.META_BIS, heute));
        return (kategorie == null ? gueltig : b.and(b.eq("kategorie", kategorie), gueltig)).build();
    }
}
