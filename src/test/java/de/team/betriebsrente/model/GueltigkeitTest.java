package de.team.betriebsrente.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GueltigkeitTest {
    private static final LocalDate HEUTE = LocalDate.of(2026, 10, 31);

    @Test
    void storesOpenBoundsAsExtremeKeysInMetadata() {
        Map<String, Object> meta = new HashMap<>();
        Gueltigkeit.inMetadaten(meta, null, null);
        assertEquals(Gueltigkeit.OFFEN_AB, meta.get(Gueltigkeit.META_AB));
        assertEquals(Gueltigkeit.OFFEN_BIS, meta.get(Gueltigkeit.META_BIS));

        Gueltigkeit.inMetadaten(meta, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertEquals(20260101, meta.get(Gueltigkeit.META_AB));
        assertEquals(20261231, meta.get(Gueltigkeit.META_BIS));
    }

    @Test
    void convertsKeysBackToDates() {
        assertEquals(LocalDate.of(2026, 12, 31), Gueltigkeit.datum(20261231));
        assertNull(Gueltigkeit.datum(Gueltigkeit.OFFEN_BIS));
        assertNull(Gueltigkeit.datum(null));
    }

    @Test
    void derivesStatusRelativeToToday() {
        assertEquals(Gueltigkeit.Status.UNBEFRISTET, Gueltigkeit.status(null, null, HEUTE));
        assertEquals(Gueltigkeit.Status.GUELTIG, Gueltigkeit.status(LocalDate.of(2026, 1, 1), null, HEUTE));
        // Warnschwelle 60 Tage: 30.12. liegt 60 Tage entfernt, 31.12. bereits 61 Tage.
        assertEquals(Gueltigkeit.Status.LAEUFT_BALD_AB,
                Gueltigkeit.status(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 30), HEUTE));
        assertEquals(Gueltigkeit.Status.GUELTIG,
                Gueltigkeit.status(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), HEUTE));
        assertEquals(Gueltigkeit.Status.GUELTIG, Gueltigkeit.status(null, LocalDate.of(2027, 6, 30), HEUTE));
        assertEquals(Gueltigkeit.Status.ABGELAUFEN, Gueltigkeit.status(null, LocalDate.of(2026, 10, 30), HEUTE));
        assertEquals(Gueltigkeit.Status.NOCH_NICHT_GUELTIG,
                Gueltigkeit.status(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), HEUTE));
    }

    @Test
    void rejectsEndBeforeStart() {
        assertNotNull(Gueltigkeit.pruefe(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1)));
        assertNull(Gueltigkeit.pruefe(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        assertNull(Gueltigkeit.pruefe(null, null));
    }
}
