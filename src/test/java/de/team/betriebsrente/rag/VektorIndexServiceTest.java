package de.team.betriebsrente.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

class VektorIndexServiceTest {

    @Test
    void keepsIndexWhenModelAndDimensionAreUnchanged() {
        VectorStore vectorStore = mock(VectorStore.class);
        EntityManager em = mock(EntityManager.class);
        Query protokoll = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(protokoll);
        when(protokoll.getResultList()).thenReturn(List.<Object>of((Object) new Object[] { "nomic-embed-text-v2-moe", 768 }));

        var ergebnis = new VektorIndexService(vectorStore, em, "nomic-embed-text-v2-moe", 768).abgleichen();

        assertEquals(VektorIndexService.Ergebnis.AKTUELL, ergebnis);
        verify(vectorStore, never()).add(anyList());
        verify(em, never()).createNativeQuery("DELETE FROM vector_store");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reEmbedsAllChunksWithTheirMetadataWhenModelChanged() {
        VectorStore vectorStore = mock(VectorStore.class);
        EntityManager em = mock(EntityManager.class);
        Query protokoll = mock(Query.class);
        Query abschnitte = mock(Query.class);
        Query aenderung = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(aenderung);
        when(em.createNativeQuery("SELECT embedding_modell, dimensionen FROM vektor_index ORDER BY id DESC LIMIT 1"))
                .thenReturn(protokoll);
        when(em.createNativeQuery("SELECT content, CAST(metadata AS text) FROM vector_store")).thenReturn(abschnitte);
        when(protokoll.getResultList()).thenReturn(List.<Object>of((Object) new Object[] { "nomic-embed-text", 768 }));
        when(abschnitte.getResultList()).thenReturn(List.<Object>of((Object) new Object[] {
                "Grenzwerte 2026 ...\n\nBeispielfragen:\nWie hoch ist der Freibetrag?",
                "{\"titel\":\"Grenzwerte 2026\",\"fachwissen_id\":7,\"gueltig_bis\":20261231}" }));
        when(aenderung.setParameter(anyString(), any())).thenReturn(aenderung);

        var ergebnis = new VektorIndexService(vectorStore, em, "nomic-embed-text-v2-moe", 1024).abgleichen();

        assertEquals(VektorIndexService.Ergebnis.NEU_AUFGEBAUT, ergebnis);
        verify(em).createNativeQuery("DELETE FROM vector_store");
        verify(em).createNativeQuery("ALTER TABLE vector_store ALTER COLUMN embedding TYPE vector(1024)");
        ArgumentCaptor<List<Document>> neu = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(neu.capture());
        Document doc = neu.getValue().get(0);
        assertEquals("Grenzwerte 2026 ...\n\nBeispielfragen:\nWie hoch ist der Freibetrag?", doc.getText());
        assertEquals("Grenzwerte 2026", doc.getMetadata().get("titel"));
        assertEquals(20261231, doc.getMetadata().get("gueltig_bis"));
        verify(aenderung).setParameter("m", "nomic-embed-text-v2-moe");
        verify(aenderung).setParameter("d", 1024);
        verify(aenderung).setParameter("a", 1);
    }
}
