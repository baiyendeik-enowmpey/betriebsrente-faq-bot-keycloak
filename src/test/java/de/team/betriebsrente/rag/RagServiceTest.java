package de.team.betriebsrente.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

class RagServiceTest {

    /** Fester Stichtag 31.10.2026, damit der Gültigkeitsfilter deterministisch ist. */
    private static final Clock STICHTAG = Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneId.of("Europe/Berlin"));

    @Test
    void askFiltersDocumentsByCategoryAndReturnsModelAnswer() {
        VectorStore vectorStore = mock(VectorStore.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
        Document matching = new Document("Tax guidance", Objects.requireNonNull(Map.of(
                "titel", "Tax", "kategorie", "Steuer", "quelle", "Handbook")));
        Document other = new Document("Payout guidance", Objects.requireNonNull(Map.of(
                "titel", "Payout", "kategorie", "Auszahlung", "quelle", "Policy")));
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(request);
        stubChatResponse(request, response, "Answer");
        stubSimilaritySearch(vectorStore, List.of(matching, other));

        RagService.Answer answer = new RagService(vectorStore, builder, STICHTAG).ask("question", "Steuer");

        assertEquals("Answer", answer.antwort());
        assertEquals(List.of(matching), answer.quellen());
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        assertEquals(b.and(b.eq("kategorie", "Steuer"),
                        b.and(b.lte("gueltig_ab", 20261031), b.gte("gueltig_bis", 20261031))).build(),
                captureSearchRequest(vectorStore).getFilterExpression());
    }

    @Test
    void askOnlySearchesCurrentlyValidKnowledgeWhenAllCategoriesAreSelected() {
        VectorStore vectorStore = mock(VectorStore.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
        Document beitragsnachweis = new Document("DRV audits reporting", Objects.requireNonNull(Map.of(
                "titel", "Beitragsnachweis und die Rentenversicherung", "kategorie", "Behörden", "quelle", "Seed")));
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(request);
        stubChatResponse(request, response, "Die DRV erhält keine monatlichen Beitragsnachweise.");
        stubSimilaritySearch(vectorStore, List.of(beitragsnachweis));

        RagService.Answer answer = new RagService(vectorStore, builder, STICHTAG)
                .ask("Beitragsnachweis und die Rentenversicherung.", "Alle");

        assertEquals("Die DRV erhält keine monatlichen Beitragsnachweise.", answer.antwort());
        assertEquals(List.of(beitragsnachweis), answer.quellen());
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        SearchRequest search = captureSearchRequest(vectorStore);
        assertEquals(b.and(b.lte("gueltig_ab", 20261031), b.gte("gueltig_bis", 20261031)).build(),
                search.getFilterExpression());
        assertEquals(RagService.DEFAULT_SIMILARITY_THRESHOLD, search.getSimilarityThreshold());
    }

    @Test
    void askAnswersWithFixedHintAndSkipsModelWhenNothingValidIsFound() {
        VectorStore vectorStore = mock(VectorStore.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(builder.build()).thenReturn(chatClient);
        stubSimilaritySearch(vectorStore, List.of());

        RagService.Answer answer = new RagService(vectorStore, builder, STICHTAG).ask("Unbekanntes Thema", "Alle");

        assertEquals(RagService.KEINE_FACHINFO, answer.antwort());
        assertTrue(answer.antwort().contains("Onboarding-Paten oder den Teamleiter"));
        assertEquals(List.of(), answer.quellen());
        verifyNoInteractions(chatClient);
    }

    @Test
    void hybridSearchAdmitsStrongKeywordHitBelowVectorThresholdAndRanksByFusion() {
        VectorStore vectorStore = mock(VectorStore.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        StichwortSuche stichwort = mock(StichwortSuche.class);
        Document fristen = scored("a", "Gesetzliche Meldefristen", 0.45);
        Document grundlagen = scored("b", "ZMV: Grundlagen", 0.40);
        Document meldegruende = scored("c", "ZMV: Die 4 Meldegründe im System", 0.23); // nur per Stichwort
        Document rauschen = scored("d", "Grenzwerte 2026", 0.22); // weder Vektor- noch Stichworttreffer
        stubSimilaritySearch(vectorStore, List.of(fristen, grundlagen, meldegruende, rauschen));
        when(stichwort.suche("Wieviele Meldegründe gibt es in Penpay")).thenReturn(List.of(
                new StichwortSuche.Treffer("c", "…", Map.of(), 3.03, List.of("meldegrund"))));

        List<Document> quellen = new RagService(vectorStore, builder, stichwort, STICHTAG, 0.37, 0.20)
                .sucheQuellen("Wieviele Meldegründe gibt es in Penpay", "Alle");

        assertEquals(List.of("c", "a", "b"), quellen.stream().map(Document::getId).toList(),
                "Stichworttreffer wird zugelassen und durch beide Ranglisten nach vorn sortiert");
        SearchRequest search = captureSearchRequest(vectorStore);
        assertEquals(RagService.VEKTOR_KANDIDATEN, search.getTopK());
        assertEquals(0.20, search.getSimilarityThreshold(), "Vektorsuche liefert Kandidaten ab der Stichwort-Schwelle");
    }

    @Test
    void hybridSearchRejectsWhenNeitherVectorNorKeywordQualifies() {
        VectorStore vectorStore = mock(VectorStore.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        StichwortSuche stichwort = mock(StichwortSuche.class);
        stubSimilaritySearch(vectorStore, List.of(scored("a", "Einrichtung für Unternehmen", 0.31)));
        when(stichwort.suche(anyString())).thenReturn(List.of());

        assertEquals(List.of(), new RagService(vectorStore, builder, stichwort, STICHTAG, 0.37, 0.20)
                .sucheQuellen("Wie beantrage ich Urlaub im HR-Portal?", "Alle"));
    }

    private static Document scored(String id, String titel, double score) {
        return Document.builder().id(id).text(titel).metadata(Map.of("titel", titel)).score(score).build();
    }

    // Mockito matchers return null sentinels during stubbing, which JDT cannot distinguish from runtime values.
    @SuppressWarnings("null")
    private static void stubChatResponse(ChatClient.ChatClientRequestSpec request,
            ChatClient.CallResponseSpec response, String content) {
        when(request.system(ArgumentMatchers.<Consumer<ChatClient.PromptSystemSpec>>any())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(response);
        when(response.content()).thenReturn(content);
    }

    // The search request matcher is likewise only a Mockito stubbing sentinel.
    @SuppressWarnings("null")
    private static void stubSimilaritySearch(VectorStore vectorStore, List<Document> documents) {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(documents);
    }

    private static SearchRequest captureSearchRequest(VectorStore vectorStore) {
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        return captor.getValue();
    }
}
