package de.team.betriebsrente.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.team.betriebsrente.rag.RagService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class EvalServiceTest {

    private static final EvalKatalog.Frage FACH = new EvalKatalog.Frage("F01",
            "Wie hoch ist der KV-Freibetrag 2026?", "Alle", List.of("Grenzwerte 2026", "Freibetrag KV/PV 2026"),
            List.of("197,75", "freibetrag|freigrenze"), false);
    private static final EvalKatalog.Frage FREMD = new EvalKatalog.Frage("N01",
            "Wie backe ich Apfelkuchen?", "Alle", List.of(), List.of(), true);

    @Test
    void searchModeChecksExpectedSourceAndRejectionWithoutCallingTheModel() {
        RagService rag = mock(RagService.class);
        when(rag.sucheQuellen(FACH.frage(), "Alle")).thenReturn(List.of(doc("Andere Quelle"), doc("Freibetrag KV/PV 2026")));
        when(rag.sucheQuellen(FREMD.frage(), "Alle")).thenReturn(List.of(doc("Zufallstreffer")));

        var bericht = new EvalService(rag).pruefe(katalog(FACH, FREMD), EvalService.Modus.SUCHE);

        assertEquals(1.0, bericht.trefferQuote());
        assertEquals(0.0, bericht.ablehnungsQuote(), "Fachfremde Frage mit Treffern gilt nicht als abgelehnt");
        assertNull(bericht.antwortQuote());
        assertFalse(bericht.bestanden());
        assertEquals(List.of("Andere Quelle", "Freibetrag KV/PV 2026"), bericht.ergebnisse().get(0).gefundeneQuellen());
        verify(rag, never()).ask(anyString(), anyString());
    }

    @Test
    void answerModeChecksKeywordsAndModelRefusal() {
        RagService rag = mock(RagService.class);
        when(rag.sucheQuellen(FACH.frage(), "Alle")).thenReturn(List.of(doc("Grenzwerte 2026")));
        when(rag.sucheQuellen(FREMD.frage(), "Alle")).thenReturn(List.of(doc("Zufallstreffer")));
        when(rag.ask(FACH.frage(), "Alle")).thenReturn(new RagService.Answer(
                "Der Freibetrag liegt 2026 bei 197,75 EUR im Monat.", List.of()));
        when(rag.ask(FREMD.frage(), "Alle")).thenReturn(new RagService.Answer(
                "Dazu liegt keine freigegebene Fachinfo vor. Bitte Onboarding-Paten fragen.", List.of()));

        var bericht = new EvalService(rag).pruefe(katalog(FACH, FREMD), EvalService.Modus.ANTWORT);

        assertEquals(1.0, bericht.trefferQuote());
        assertEquals(1.0, bericht.antwortQuote());
        assertEquals(1.0, bericht.ablehnungsQuote(), "Ablehnung durch das Modell zählt im Antwortmodus");
        assertTrue(bericht.bestanden());
    }

    @Test
    void reportsMissingKeywordsAndFailsThreshold() {
        RagService rag = mock(RagService.class);
        when(rag.sucheQuellen(FACH.frage(), "Alle")).thenReturn(List.of(doc("Grenzwerte 2026")));
        when(rag.ask(FACH.frage(), "Alle")).thenReturn(new RagService.Answer("Der Freibetrag ist gestiegen.", List.of()));

        var bericht = new EvalService(rag).pruefe(katalog(FACH), EvalService.Modus.ANTWORT);

        assertEquals(List.of("197,75"), bericht.ergebnisse().get(0).fehlendeBegriffe());
        assertEquals(0.0, bericht.antwortQuote());
        assertFalse(bericht.ergebnisse().get(0).bestanden());
        assertFalse(bericht.bestanden());
    }

    @Test
    void keywordMatchingIgnoresCaseAndWhitespaceAndAcceptsVariants() {
        assertTrue(EvalService.enthaelt("Frist: innerhalb von 7 Tagen", "7 Tage"));
        assertTrue(EvalService.enthaelt("nach §34 EStG", "§ 34"));
        assertTrue(EvalService.fehlendeBegriffe("eine WOCHE bzw. sieben Tage", List.of("7 Tage|sieben Tage")).isEmpty());
        assertEquals(List.of("6 Monate"), EvalService.fehlendeBegriffe("drei Monate", List.of("6 Monate")));
    }

    private static EvalKatalog katalog(EvalKatalog.Frage... fragen) {
        return new EvalKatalog("Test", new EvalKatalog.Schwellen(0.9, 0.8, 0.8), List.of(fragen));
    }

    private static Document doc(String titel) {
        return new Document("Text", Map.of("titel", titel));
    }
}
