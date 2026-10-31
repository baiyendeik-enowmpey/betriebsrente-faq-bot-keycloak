package de.team.betriebsrente.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Gültigkeitszeitraum von Fachwissen.
 *
 * <p>In den Vektor-Metadaten werden die Grenzen als Zahl {@code yyyyMMdd} abgelegt, damit die
 * pgvector-Filter ({@code <=}, {@code >=}) sie vergleichen können. Offene Grenzen werden durch
 * {@link #OFFEN_AB} bzw. {@link #OFFEN_BIS} dargestellt.
 */
public final class Gueltigkeit {
    public static final String META_AB = "gueltig_ab";
    public static final String META_BIS = "gueltig_bis";
    public static final int OFFEN_AB = 0;
    public static final int OFFEN_BIS = 99991231;
    /** Ab so vielen Tagen vor Ablauf wird ein Dokument im Admin als „läuft bald ab“ markiert. */
    public static final int WARNUNG_TAGE = 60;

    public enum Status { UNBEFRISTET, GUELTIG, LAEUFT_BALD_AB, ABGELAUFEN, NOCH_NICHT_GUELTIG }

    private Gueltigkeit() {
    }

    public static int schluessel(LocalDate datum, int offenerWert) {
        return datum == null ? offenerWert
                : datum.getYear() * 10000 + datum.getMonthValue() * 100 + datum.getDayOfMonth();
    }

    public static int schluessel(LocalDate datum) {
        return schluessel(datum, OFFEN_AB);
    }

    /** Wandelt einen Metadaten-Schlüssel zurück in ein Datum; offene Grenzen ergeben {@code null}. */
    public static LocalDate datum(Object schluessel) {
        if (!(schluessel instanceof Number zahl)) {
            return null;
        }
        int wert = zahl.intValue();
        if (wert == OFFEN_AB || wert == OFFEN_BIS) {
            return null;
        }
        return LocalDate.of(wert / 10000, wert / 100 % 100, wert % 100);
    }

    public static void inMetadaten(Map<String, Object> metadaten, LocalDate ab, LocalDate bis) {
        metadaten.put(META_AB, schluessel(ab, OFFEN_AB));
        metadaten.put(META_BIS, schluessel(bis, OFFEN_BIS));
    }

    public static Status status(LocalDate ab, LocalDate bis, LocalDate heute) {
        if (ab != null && heute.isBefore(ab)) {
            return Status.NOCH_NICHT_GUELTIG;
        }
        if (bis == null) {
            return ab == null ? Status.UNBEFRISTET : Status.GUELTIG;
        }
        if (heute.isAfter(bis)) {
            return Status.ABGELAUFEN;
        }
        return ChronoUnit.DAYS.between(heute, bis) <= WARNUNG_TAGE ? Status.LAEUFT_BALD_AB : Status.GUELTIG;
    }

    /** Prüft einen vom Benutzer eingegebenen Zeitraum; liefert eine Fehlermeldung oder {@code null}. */
    public static String pruefe(LocalDate ab, LocalDate bis) {
        if (ab != null && bis != null && bis.isBefore(ab)) {
            return "„Gültig bis“ darf nicht vor „Gültig ab“ liegen.";
        }
        return null;
    }
}
