package de.team.betriebsrente.e2e;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Dialog;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class AdminUiE2ETest {
    private static final String BASE_URL = System.getProperty("e2e.baseUrl",
            System.getenv().getOrDefault("E2E_BASE_URL", "http://localhost:8091"));
    private static final String ADMIN_USERNAME = System.getenv().getOrDefault("E2E_ADMIN_USERNAME", "fachbereich");
    private static final String ADMIN_PASSWORD = System.getenv().getOrDefault("E2E_ADMIN_PASSWORD",
            "Betriebsrente2024!");
    private static final Map<String, Boolean> ADMIN_FUNCTION_COVERAGE = new HashMap<>();

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private CDPSession coverageSession;

    @BeforeClass
    public void startBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        ADMIN_FUNCTION_COVERAGE.clear();
    }

    @BeforeMethod
    public void createPage() {
        context = browser.newContext();
        page = context.newPage();
        page.route("https://cdn.tailwindcss.com/**", Route::abort);
        coverageSession = context.newCDPSession(page);
        coverageSession.send("Debugger.enable");
        coverageSession.send("Profiler.enable");
        JsonObject options = new JsonObject();
        options.addProperty("callCount", true);
        options.addProperty("detailed", true);
        coverageSession.send("Profiler.startPreciseCoverage", options);
    }

    @AfterMethod(alwaysRun = true)
    public void closePage() {
        collectAdminScriptCoverage();
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
        double coverage = calculateAdminFunctionCoverage();
        System.out.printf(Locale.ROOT, "Admin inline JavaScript function coverage: %.1f%%%n", coverage);
        Assert.assertTrue(coverage >= 90.0,
                String.format(Locale.ROOT, "Admin inline JavaScript function coverage is %.1f%%; expected at least 90%%",
                        coverage));
    }

    @Test
    public void adminCanOpenDashboardAndSelectDocument() {
        openAdminPage();

        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Admin - Fachwissen pflegen"))
                .waitFor();
        assertTrue(page.getByText("Gespeicherte Dokumente").isVisible());
        assertTrue(page.getByText("Dokumente", new Page.GetByTextOptions().setExact(true)).isVisible());
        assertTrue(page.getByText("Chunks (650 Tokens, 20% Overlap)").isVisible());

        page.locator("#file").setInputFiles(new FilePayload("admin-guide.txt", "text/plain",
                "Informationen zur betrieblichen Altersversorgung.".getBytes()));
        assertTrue(page.locator("#fileName").isVisible());
        assertTrue(page.locator("#fileName").textContent().contains("admin-guide.txt"));
        assertFalse(page.locator("#dropText").isVisible());
    }

    @Test
    public void uploadDisplaysSuccessfulImportStatus() {
        openAdminPage();
        page.route("**/admin/upload", route -> route.fulfill(new Route.FulfillOptions()
                .setStatus(200)
                .setContentType("application/json")
                .setBody("""
                        {"success":true,"titel":"E2E-Dokument","chunks":2,
                         "message":"'E2E-Dokument' importiert: 120 Zeichen -> 2 Chunks"}
                        """)));

        submitDocument();

        assertTrue(page.locator("#status").isVisible());
        assertTrue(page.locator("#status").getAttribute("class").contains("bg-green-50"));
        assertTrue(page.locator("#status").textContent().contains("E2E-Dokument"));
        assertTrue(page.locator("#lastImport").textContent().contains("E2E-Dokument - 2 Chunks"));
        page.waitForTimeout(2000);
        assertEquals(page.locator("#lastImport").textContent(), "-",
                "A successful upload reloads the dashboard");
    }

    @Test
    public void uploadDisplaysServerError() {
        openAdminPage();
        page.route("**/admin/upload", route -> route.fulfill(new Route.FulfillOptions()
                .setStatus(200)
                .setContentType("application/json")
                .setBody("{\"error\":\"Dokument konnte nicht verarbeitet werden\"}")));

        submitDocument();

        assertTrue(page.locator("#status").isVisible());
        assertTrue(page.locator("#status").getAttribute("class").contains("bg-red-50"));
        assertTrue(page.locator("#status").textContent()
                .contains("Fehler: Dokument konnte nicht verarbeitet werden"));
    }

    @Test
    public void deletingDocumentRequiresConfirmation() {
        openAdminPage();
        page.locator("tbody").evaluate("""
                tbody => {
                    const row = document.createElement('tr');
                    row.innerHTML = '<td colspan="4">E2E-Dokument</td>' +
                        '<td><button data-id="987654321" onclick="loeschen(this)">Löschen</button></td>';
                    tbody.appendChild(row);
                }
                """);

        AtomicBoolean deleteRequested = new AtomicBoolean();
        page.onceDialog(Dialog::dismiss);
        page.locator("button[data-id='987654321']").click();
        assertFalse(deleteRequested.get(), "Cancelling confirmation must not send a delete request");

        page.route("**/admin/987654321", route -> {
            deleteRequested.set(true);
            route.fulfill(new Route.FulfillOptions()
                    .setStatus(200)
                    .setContentType("application/json")
                    .setBody("{\"success\":\"gelöscht\"}"));
        });
        page.onceDialog(Dialog::accept);
        page.locator("button[data-id='987654321']").click();

        assertTrue(deleteRequested.get(), "Confirming deletion sends the delete request");
    }

    private void openAdminPage() {
        page.navigate(BASE_URL + "/login");
        page.locator("#keycloakLogin").click();
        page.locator("input[name='username']").fill(ADMIN_USERNAME);
        page.locator("input[name='password']").fill(ADMIN_PASSWORD);
        page.locator("#kc-login").click();
        page.locator("#frage").waitFor();
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Adminbereich")).click();
        page.locator("#uploadForm").waitFor();
        page.addStyleTag(new Page.AddStyleTagOptions().setContent(".hidden { display: none !important; }"));
    }

    private void submitDocument() {
        page.locator("#file").setInputFiles(new FilePayload("admin-guide.txt", "text/plain",
                "Informationen zur betrieblichen Altersversorgung.".getBytes()));
        page.locator("input[name='titel']").fill("E2E-Dokument");
        page.locator("select[name='kategorie']").selectOption("Steuer");
        page.locator("input[name='quelle']").fill("E2E-Quelle");
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Importieren & Chunking starten")).click();
    }

    private void collectAdminScriptCoverage() {
        if (coverageSession == null) {
            return;
        }
        JsonArray scripts = coverageSession.send("Profiler.takePreciseCoverage").getAsJsonArray("result");
        for (JsonElement scriptElement : scripts) {
            JsonObject script = scriptElement.getAsJsonObject();
            if (!script.has("url") || !script.get("url").getAsString().matches(".*/admin(?:\\?.*)?$")) {
                continue;
            }
            JsonObject sourceParameters = new JsonObject();
            sourceParameters.addProperty("scriptId", script.get("scriptId").getAsString());
            String source = coverageSession.send("Debugger.getScriptSource", sourceParameters)
                    .get("scriptSource").getAsString();
            if (source.isBlank()) {
                continue;
            }

            for (JsonElement functionElement : script.getAsJsonArray("functions")) {
                JsonObject function = functionElement.getAsJsonObject();
                JsonArray ranges = function.getAsJsonArray("ranges");
                if (ranges.isEmpty()) {
                    continue;
                }
                JsonObject outerRange = ranges.get(0).getAsJsonObject();
                int start = outerRange.get("startOffset").getAsInt();
                int end = outerRange.get("endOffset").getAsInt();
                String functionName = function.get("functionName").getAsString();
                if (start == 0 && end == source.length()
                        && (functionName.isBlank() || "(program)".equals(functionName))) {
                    continue;
                }
                String id = source + functionName + start + ":" + end;
                boolean executed = false;
                for (JsonElement range : ranges) {
                    executed |= range.getAsJsonObject().get("count").getAsInt() > 0;
                }
                ADMIN_FUNCTION_COVERAGE.merge(id, executed, Boolean::logicalOr);
            }
        }
        coverageSession = null;
    }

    private static double calculateAdminFunctionCoverage() {
        long coveredFunctions = ADMIN_FUNCTION_COVERAGE.values().stream().filter(Boolean::booleanValue).count();
        return ADMIN_FUNCTION_COVERAGE.isEmpty()
                ? 0.0
                : 100.0 * coveredFunctions / ADMIN_FUNCTION_COVERAGE.size();
    }

}
