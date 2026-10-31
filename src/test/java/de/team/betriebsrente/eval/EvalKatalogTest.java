package de.team.betriebsrente.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class EvalKatalogTest {

    @TempDir
    Path tempDir;

    /**
     * Schutz gegen veraltete Testfragen: Jede erwartete Quelle muss es in der Seed-Datei geben
     * (z. B. nach dem Umbenennen eines Eintrags), und jeder Wissenseintrag wird mindestens einmal geprüft.
     */
    @Test
    void bundledCatalogMatchesSeedKnowledge() {
        EvalKatalog katalog = EvalKatalog.lade(new File("testdaten/eval_dataset.json"));
        List<Map<String, Object>> seed = JsonMapper.builder().build()
                .readValue(new File("testdaten/fachwissen_seed.json"), new TypeReference<>() {
                });
        Set<String> seedTitel = seed.stream().map(e -> String.valueOf(e.get("titel"))).collect(Collectors.toSet());

        Set<String> geprueft = new HashSet<>();
        for (EvalKatalog.Frage frage : katalog.fragen()) {
            for (String quelle : frage.erwarteteQuellen()) {
                assertTrue(seedTitel.contains(quelle), frage.id() + ": unbekannte Quelle '" + quelle + "'");
                geprueft.add(quelle);
            }
            assertFalse(!frage.erwartetKeineFachinfo() && frage.mussEnthalten().isEmpty(),
                    frage.id() + " braucht Pflichtbegriffe");
        }
        assertEquals(seedTitel, geprueft, "Jeder Wissenseintrag sollte durch mindestens eine Testfrage abgedeckt sein");
        assertTrue(katalog.fragen().stream().anyMatch(EvalKatalog.Frage::erwartetKeineFachinfo));
        assertEquals(0.9, katalog.schwellen().trefferQuote());
    }

    /** Jeder Vorschlag unter „Häufige Fragen“ im Chat muss durch die Qualitätsprüfung abgedeckt sein. */
    @Test
    void everySuggestedChatQuestionIsPartOfTheCatalog() throws Exception {
        String chat = Files.readString(Path.of("src/main/resources/templates/chat.html"));
        List<String> vorschlaege = java.util.regex.Pattern.compile("data-question=\"([^\"]+)\"").matcher(chat)
                .results().map(m -> m.group(1)).toList();
        Set<String> katalogFragen = EvalKatalog.lade(new File("testdaten/eval_dataset.json")).fragen().stream()
                .filter(f -> !f.erwartetKeineFachinfo()).map(EvalKatalog.Frage::frage).collect(Collectors.toSet());

        assertFalse(vorschlaege.isEmpty());
        for (String vorschlag : vorschlaege) {
            assertTrue(katalogFragen.contains(vorschlag), "Vorschlag ohne Testfrage im Katalog: " + vorschlag);
        }
    }

    @Test
    void parsesSingleSourceStringsDefaultsAndRejectsIncompleteQuestions() throws Exception {
        Path ok = tempDir.resolve("ok.json");
        Files.writeString(ok, """
                {"fragen":[{"frage":"Was ist ZMV?","erwartete_quelle":"ZMV","muss_enthalten":["Krankenkasse"]},
                           {"id":"N1","frage":"Wetter?","erwartet":"keine_fachinfo"}]}
                """);
        EvalKatalog katalog = EvalKatalog.lade(ok.toFile());
        assertEquals(List.of("ZMV"), katalog.fragen().get(0).erwarteteQuellen());
        assertEquals("Alle", katalog.fragen().get(0).kategorie());
        assertEquals("Q1", katalog.fragen().get(0).id());
        assertTrue(katalog.fragen().get(1).erwartetKeineFachinfo());
        assertEquals(EvalKatalog.Schwellen.STANDARD, katalog.schwellen());

        Path ohneQuelle = tempDir.resolve("ohne.json");
        Files.writeString(ohneQuelle, "{\"fragen\":[{\"frage\":\"Was ist ZMV?\"}]}");
        assertThrows(IllegalArgumentException.class, () -> EvalKatalog.lade(ohneQuelle.toFile()));
    }
}
