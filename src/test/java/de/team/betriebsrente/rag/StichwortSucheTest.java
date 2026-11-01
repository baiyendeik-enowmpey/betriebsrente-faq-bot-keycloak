package de.team.betriebsrente.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import org.junit.jupiter.api.Test;

class StichwortSucheTest {

    @Test
    void keepsOnlyStrongHitsTitleOrTwoTermsAndRanksRareTermsHigher() {
        EntityManager em = mock(EntityManager.class);
        Query lexeme = mock(Query.class);
        Query treffer = mock(Query.class);
        Query anzahl = mock(Query.class);
        when(em.createNativeQuery(contains("to_tsvector('german', :frage)"))).thenReturn(lexeme);
        when(em.createNativeQuery(contains("WITH d AS"))).thenReturn(treffer);
        when(em.createNativeQuery("SELECT count(*) FROM vector_store")).thenReturn(anzahl);
        when(lexeme.setParameter(anyString(), any())).thenReturn(lexeme);
        when(treffer.setParameter(anyString(), any())).thenReturn(treffer);
        // „gibt“ und „wieviel“ sind Fragewörter und dürfen nicht in die Suche eingehen.
        when(lexeme.getSingleResult()).thenReturn("gibt meldegrund penpay wieviel portal");
        when(anzahl.getSingleResult()).thenReturn(30L);
        when(treffer.getResultList()).thenReturn(List.<Object>of(
                (Object) new Object[] { "titel-treffer", "Text", "{\"titel\":\"Die 4 Meldegründe\"}", "meldegrund", true },
                (Object) new Object[] { "ein-wort-im-text", "Internet-Portal", "{\"titel\":\"Einrichtung\"}", "portal", false },
                (Object) new Object[] { "zwei-worte", "Text", "{\"titel\":\"Andere\"}", "meldegrund portal", false }));

        List<StichwortSuche.Treffer> ergebnis = new StichwortSuche(em).suche("Wieviele Meldegründe gibt es in Penpay");

        assertEquals(List.of("zwei-worte", "titel-treffer"), ergebnis.stream().map(StichwortSuche.Treffer::id).toList(),
                "Einzelnes Wort nur im Text ist kein starker Treffer; zwei Begriffe wiegen mehr als einer");
        assertEquals("Die 4 Meldegründe", ergebnis.get(1).metadaten().get("titel"));
        verify(treffer).setParameter(eq("q"), eq("{\"meldegrund\",\"penpay\",\"portal\"}"));
    }

    @Test
    void rarerTermsWeighMore() {
        assertTrue(StichwortSuche.idf(1, 30) > StichwortSuche.idf(10, 30));
        assertTrue(StichwortSuche.idf(29, 30) > 0);
    }
}
