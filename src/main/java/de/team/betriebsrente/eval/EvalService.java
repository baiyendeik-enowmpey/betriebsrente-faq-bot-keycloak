package de.team.betriebsrente.eval;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.team.betriebsrente.rag.RagService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

/**
 * Prüft die Antwortqualität des Bots gegen den Testfragen-Katalog.
 *
 * <ul>
 * <li>{@link Modus#SUCHE}: nur die Vektorsuche (schnell, ohne Sprachmodell) – ist die erwartete
 * Quelle unter den Treffern, bzw. findet eine fachfremde Frage keine Treffer?</li>
 * <li>{@link Modus#ANTWORT}: zusätzlich die vollständige Antwort des Sprachmodells – enthält sie
 * alle Pflichtbegriffe, bzw. lehnt sie eine fachfremde Frage ab? (dauert je Frage 20–60 s)</li>
 * </ul>
 */
@Service
public class EvalService {
    static final String ABLEHNUNG = "keine freigegebene Fachinfo";
    private static final int ANTWORT_VORSCHAU = 300;

    public enum Modus { SUCHE, ANTWORT }

    public record FrageErgebnis(String id, String frage, boolean fachfremd, List<String> erwarteteQuellen,
            List<String> gefundeneQuellen, boolean sucheOk, Boolean antwortOk, List<String> fehlendeBegriffe,
            String antwort, long dauerMs) {

        /** Gesamturteil je Frage: Suche und – falls geprüft – Antwort. */
        @JsonProperty("bestanden")
        public boolean bestanden() {
            return fachfremd ? (antwortOk != null ? antwortOk : sucheOk)
                    : sucheOk && (antwortOk == null || antwortOk);
        }
    }

    public record Bericht(Instant zeitpunkt, Modus modus, int fachfragen, int fachfremdeFragen,
            double trefferQuote, double ablehnungsQuote, Double antwortQuote, EvalKatalog.Schwellen schwellen,
            boolean bestanden, long dauerMs, List<FrageErgebnis> ergebnisse) {
    }

    private final RagService ragService;

    public EvalService(RagService ragService) {
        this.ragService = ragService;
    }

    public Bericht pruefe(EvalKatalog katalog, Modus modus) {
        long start = System.currentTimeMillis();
        List<FrageErgebnis> ergebnisse = new ArrayList<>();
        for (EvalKatalog.Frage frage : katalog.fragen()) {
            ergebnisse.add(pruefeFrage(frage, modus));
        }
        List<FrageErgebnis> fach = ergebnisse.stream().filter(e -> !e.fachfremd()).toList();
        List<FrageErgebnis> fremd = ergebnisse.stream().filter(FrageErgebnis::fachfremd).toList();

        double trefferQuote = quote(fach.stream().filter(FrageErgebnis::sucheOk).count(), fach.size());
        // Fachfremde Fragen gelten als abgelehnt, wenn die Suche nichts findet bzw. (Modus ANTWORT)
        // die Antwort die Ablehnung enthält.
        double ablehnungsQuote = quote(fremd.stream().filter(FrageErgebnis::bestanden).count(), fremd.size());
        Double antwortQuote = modus == Modus.ANTWORT
                ? quote(fach.stream().filter(e -> Boolean.TRUE.equals(e.antwortOk())).count(), fach.size())
                : null;
        EvalKatalog.Schwellen s = katalog.schwellen();
        boolean bestanden = trefferQuote >= s.trefferQuote() && ablehnungsQuote >= s.ablehnungsQuote()
                && (antwortQuote == null || antwortQuote >= s.antwortQuote());
        return new Bericht(Instant.now(), modus, fach.size(), fremd.size(), trefferQuote, ablehnungsQuote,
                antwortQuote, s, bestanden, System.currentTimeMillis() - start, List.copyOf(ergebnisse));
    }

    FrageErgebnis pruefeFrage(EvalKatalog.Frage frage, Modus modus) {
        long start = System.currentTimeMillis();
        List<String> gefunden = titel(ragService.sucheQuellen(frage.frage(), frage.kategorie()));
        boolean sucheOk = frage.erwartetKeineFachinfo()
                ? gefunden.isEmpty()
                : gefunden.stream().anyMatch(frage.erwarteteQuellen()::contains);

        Boolean antwortOk = null;
        List<String> fehlend = List.of();
        String antwort = null;
        if (modus == Modus.ANTWORT) {
            antwort = Objects.requireNonNullElse(ragService.ask(frage.frage(), frage.kategorie()).antwort(), "");
            if (frage.erwartetKeineFachinfo()) {
                antwortOk = enthaelt(antwort, ABLEHNUNG);
            } else {
                fehlend = fehlendeBegriffe(antwort, frage.mussEnthalten());
                antwortOk = fehlend.isEmpty();
            }
            if (antwort.length() > ANTWORT_VORSCHAU) {
                antwort = antwort.substring(0, ANTWORT_VORSCHAU) + "…";
            }
        }
        return new FrageErgebnis(frage.id(), frage.frage(), frage.erwartetKeineFachinfo(), frage.erwarteteQuellen(),
                gefunden, sucheOk, antwortOk, fehlend, antwort, System.currentTimeMillis() - start);
    }

    /** Pflichtbegriffe, von denen keine Variante in der Antwort vorkommt. */
    static List<String> fehlendeBegriffe(String antwort, List<String> pflicht) {
        return pflicht.stream()
                .filter(begriff -> Arrays.stream(begriff.split("\\|")).noneMatch(v -> enthaelt(antwort, v)))
                .toList();
    }

    /** Vergleich ohne Groß-/Kleinschreibung und ohne Leerzeichen („§ 34“ = „§34“, „7 Tagen“ ⊃ „7 Tage“). */
    static boolean enthaelt(String text, String begriff) {
        return normalisiere(text).contains(normalisiere(begriff));
    }

    private static String normalisiere(String s) {
        return s == null ? "" : s.toLowerCase(Locale.GERMAN).replaceAll("\\s+", "");
    }

    private static List<String> titel(List<Document> docs) {
        return docs.stream().map(d -> String.valueOf(d.getMetadata().getOrDefault("titel", "-"))).toList();
    }

    private static double quote(long ok, int gesamt) {
        return gesamt == 0 ? 1.0 : (double) ok / gesamt;
    }
}
