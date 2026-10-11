package de.team.betriebsrente.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class RagServiceTest {

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

        RagService.Answer answer = new RagService(vectorStore, builder).ask("question", "steuer");

        assertEquals("Answer", answer.antwort());
        assertEquals(List.of(matching), answer.quellen());
    }

    @Test
    void askIncludesAllMatchingDocumentsWhenAllCategoriesAreSelected() {
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

        RagService.Answer answer = new RagService(vectorStore, builder)
                .ask("Beitragsnachweis und die Rentenversicherung.", "Alle");

        assertEquals("Die DRV erhält keine monatlichen Beitragsnachweise.", answer.antwort());
        assertEquals(List.of(beitragsnachweis), answer.quellen());
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
}
