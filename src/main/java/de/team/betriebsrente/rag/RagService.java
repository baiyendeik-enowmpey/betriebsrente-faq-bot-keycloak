package de.team.betriebsrente.rag;

import de.team.betriebsrente.model.Gueltigkeit;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * Mindestähnlichkeit für Abschnitte, die zusätzlich einen starken Stichworttreffer haben (Titel
     * oder ≥ 2 Fachbegriffe). Gemessen: 0,15–0,20 → 100 % Treffer und 100 % Ablehnung im Testkatalog.
     */
    static final double DEFAULT_STICHWORT_MINDESTAEHNLICHKEIT = 0.20;
    /** Anzahl Vektor-Kandidaten für die Zusammenführung mit der Stichwortsuche. */
    static final int VEKTOR_KANDIDATEN = 20;
    /** Konstante der Reciprocal Rank Fusion (üblicher Wert 60). */
    static final int RRF_K = 60;

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
    /** {@code null} = reine Vektorsuche. */
    private final StichwortSuche stichwortSuche;
    private final double stichwortMindestaehnlichkeit;

    @Autowired
    public RagService(VectorStore vectorStore, ChatClient.Builder builder, StichwortSuche stichwortSuche,
            @Value("${app.rag.similarity-threshold:0.37}") double similarityThreshold,
            @Value("${app.rag.stichwortsuche.aktiv:true}") boolean stichwortsucheAktiv,
            @Value("${app.rag.stichwortsuche.mindestaehnlichkeit:0.20}") double stichwortMindestaehnlichkeit) {
        this(vectorStore, builder, stichwortsucheAktiv ? stichwortSuche : null, Clock.systemDefaultZone(),
                similarityThreshold, stichwortMindestaehnlichkeit);
    }

    RagService(VectorStore vectorStore, ChatClient.Builder builder, Clock clock) {
        this(vectorStore, builder, null, clock, DEFAULT_SIMILARITY_THRESHOLD, DEFAULT_STICHWORT_MINDESTAEHNLICHKEIT);
    }

    RagService(VectorStore vectorStore, ChatClient.Builder builder, StichwortSuche stichwortSuche, Clock clock,
            double similarityThreshold, double stichwortMindestaehnlichkeit) {
        this.vectorStore = vectorStore;
        this.chatClient = builder.build();
        this.stichwortSuche = stichwortSuche;
        this.clock = clock;
        this.similarityThreshold = similarityThreshold;
        this.stichwortMindestaehnlichkeit = stichwortMindestaehnlichkeit;
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
     *
     * <p>Hybride Suche: Ein Abschnitt kommt in Frage, wenn seine Vektor-Ähnlichkeit die Schwelle
     * erreicht <b>oder</b> er einen starken Stichworttreffer hat und zumindest die niedrigere
     * Stichwort-Mindestähnlichkeit erreicht. Die Reihenfolge ergibt sich aus beiden Ranglisten
     * (Reciprocal Rank Fusion). Gültigkeits- und Kategorie-Filter gelten für beide Wege, weil nur
     * Abschnitte aus der gefilterten Vektor-Kandidatenliste verwendet werden.
     */
    public List<Document> sucheQuellen(String frage, String kategorieFilter) {
        String query = frage == null ? "" : frage.strip();
        boolean kategorieAktiv = kategorieFilter != null && !kategorieFilter.isBlank()
                && !"Alle".equalsIgnoreCase(kategorieFilter);
        boolean hybrid = stichwortSuche != null;

        SearchRequest req = SearchRequest.builder()
                .query(query)
                .topK(hybrid ? VEKTOR_KANDIDATEN : TOP_K)
                .similarityThreshold(hybrid ? Math.min(similarityThreshold, stichwortMindestaehnlichkeit)
                        : similarityThreshold)
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
        return hybrid ? zusammenfuehren(docs, stichwortSuche.suche(query)) : docs;
    }

    /** Reciprocal Rank Fusion von Vektor-Kandidaten (absteigend nach Ähnlichkeit) und Stichworttreffern. */
    List<Document> zusammenfuehren(List<Document> vektor, List<StichwortSuche.Treffer> stichwort) {
        Map<String, Integer> stichwortRang = new HashMap<>();
        for (int i = 0; i < stichwort.size(); i++) {
            stichwortRang.putIfAbsent(stichwort.get(i).id(), i + 1);
        }
        Map<Document, Double> punkte = new LinkedHashMap<>();
        for (int i = 0; i < vektor.size(); i++) {
            Document d = vektor.get(i);
            Integer kRang = stichwortRang.get(d.getId());
            boolean vektorReicht = d.getScore() == null || d.getScore() >= similarityThreshold;
            if (vektorReicht || kRang != null) {
                punkte.put(d, 1.0 / (RRF_K + i + 1) + (kRang == null ? 0 : 1.0 / (RRF_K + kRang)));
            }
        }
        return punkte.entrySet().stream()
                .sorted(Map.Entry.<Document, Double>comparingByValue().reversed())
                .limit(TOP_K)
                .map(Map.Entry::getKey)
                .toList();
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
