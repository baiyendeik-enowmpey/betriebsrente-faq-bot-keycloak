package de.team.betriebsrente.e2e;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import de.team.betriebsrente.controller.ChatController;
import de.team.betriebsrente.rag.RagService;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.ai.document.Document;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ChatUiE2ETest {
    private static final String QUESTION = "Wie wird Einmalkapital versteuert?";
    private static final String ANSWER = "Die Testantwort zur Besteuerung.";
    private static final String BASE_URL = System.getProperty("e2e.baseUrl",
            System.getenv().getOrDefault("E2E_BASE_URL", "http://localhost:8091"));

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeClass
    public void startBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @BeforeMethod
    public void createPage() {
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterMethod(alwaysRun = true)
    public void closePage() {
        if (context != null) {
            context.close();
        }
    }

    @AfterClass(alwaysRun = true)
    public void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @Test
    public void loginAndChatDisplaysLoadingStateAnswerAndDuration() throws Exception {
        RagService ragService = mock(RagService.class);
        Document source = new Document("Hinweise zur Besteuerung von Einmalkapital.",
                Map.of("titel", "E2E-Steuerhinweis", "kategorie", "Steuer", "quelle", "E2E-Testquelle"));
        when(ragService.ask(QUESTION, "Steuer"))
                .thenReturn(new RagService.Answer(ANSWER, List.of(source)));

        page.addInitScript("""
                const originalFetch = window.fetch.bind(window);
                window.fetch = (resource, options) => {
                    const url = typeof resource === 'string' ? resource : resource.url;
                    if (url === '/api/chat') {
                        return new Promise((resolve, reject) => {
                            setTimeout(() => originalFetch(resource, options).then(resolve, reject), 500);
                        });
                    }
                    return originalFetch(resource, options);
                };
                """);
        page.route("https://cdn.tailwindcss.com/**", Route::abort);
        page.route("**/api/chat", route -> route.fulfill(new Route.FulfillOptions()
                    .setStatus(200)
                    .setContentType("application/json")
                    .setBody(createChatResponse(ragService))));

        page.navigate(BASE_URL + "/login");
        page.locator("#keycloakLogin").click();
        page.locator("input[name='username']").fill(
                System.getenv().getOrDefault("E2E_USERNAME", "team"));
        page.locator("input[name='password']").fill(
                System.getenv().getOrDefault("E2E_PASSWORD", "Willkommen2024!"));
        page.locator("#kc-login").click();
        page.locator("#frage").waitFor();

        page.locator("#kategorie").selectOption("Steuer");
        page.locator("#frage").fill(QUESTION);
        page.locator("#senden").click();

        page.locator("#bot-loading").waitFor();
        assertTrue(page.locator("#senden").isDisabled(), "Send button stays disabled while the answer loads");

        page.getByText(ANSWER).waitFor();
        assertTrue(page.locator("#quellen").getByText("E2E-Steuerhinweis").isVisible(), "Source title is shown");
        assertTrue(page.locator("#quellen")
                        .getByText("Steuer", new Locator.GetByTextOptions().setExact(true)).isVisible(),
                "Source category is shown");
        page.locator("#chatverlauf p")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile(
                        "^Antwortzeit \\(inkl\\. Übertragung\\): (?:\\d+ ms|\\d+,\\d s)$")))
                .waitFor();
        assertTrue(page.locator("#bot-loading").count() == 0, "Loading indicator is removed after the answer");
        verify(ragService).ask(QUESTION, "Steuer");
    }

    private String createChatResponse(RagService ragService) {
        try {
            return new ObjectMapper().writeValueAsString(
                    new ChatController(ragService).chat(Map.of("frage", QUESTION, "kategorie", "Steuer")));
        } catch (Exception e) {
            throw new IllegalStateException("Could not create the stubbed chat response", e);
        }
    }
}
