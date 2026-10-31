package de.team.betriebsrente.eval;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Testfragen-Katalog (testdaten/eval_dataset.json).
 *
 * <p>Fachfragen nennen die erwartete(n) Quelle(n) und Pflichtbegriffe; ein Pflichtbegriff darf
 * Varianten enthalten ({@code "7 Tage|sieben Tage"}). Fachfremde Fragen tragen
 * {@code "erwartet": "keine_fachinfo"} und müssen abgelehnt werden.
 */
public record EvalKatalog(String beschreibung, Schwellen schwellen, List<Frage> fragen) {

    public static final List<String> PFADE = List.of(
            "testdaten/eval_dataset.json",
            "/app/testdaten/eval_dataset.json");

    public record Schwellen(double trefferQuote, double ablehnungsQuote, double antwortQuote) {
        static final Schwellen STANDARD = new Schwellen(0.9, 0.8, 0.8);
    }

    public record Frage(String id, String frage, String kategorie, List<String> erwarteteQuellen,
            List<String> mussEnthalten, boolean erwartetKeineFachinfo) {
    }

    /** Sucht den Katalog an den Standardpfaden. */
    public static File findeDatei() {
        return PFADE.stream().map(File::new).filter(File::isFile).findFirst()
                .orElseThrow(() -> new IllegalStateException("Kein Testfragen-Katalog gefunden: " + PFADE));
    }

    public static EvalKatalog lade(File datei) {
        Map<String, Object> json = JsonMapper.builder().build().readValue(datei, new TypeReference<>() {
        });
        Schwellen schwellen = Schwellen.STANDARD;
        if (json.get("schwellen") instanceof Map<?, ?> s) {
            schwellen = new Schwellen(
                    zahl(s.get("treffer_quote"), Schwellen.STANDARD.trefferQuote()),
                    zahl(s.get("ablehnungs_quote"), Schwellen.STANDARD.ablehnungsQuote()),
                    zahl(s.get("antwort_quote"), Schwellen.STANDARD.antwortQuote()));
        }
        List<Frage> fragen = new ArrayList<>();
        if (!(json.get("fragen") instanceof List<?> liste)) {
            throw new IllegalArgumentException("Katalog braucht eine Liste 'fragen'");
        }
        for (Object eintrag : liste) {
            if (!(eintrag instanceof Map<?, ?> f) || !(f.get("frage") instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException("Jede Frage braucht das Feld 'frage': " + eintrag);
            }
            String id = f.get("id") instanceof String s ? s : "Q" + (fragen.size() + 1);
            boolean negativ = "keine_fachinfo".equals(f.get("erwartet"));
            List<String> quellen = texte(f.get("erwartete_quelle"));
            if (!negativ && quellen.isEmpty()) {
                throw new IllegalArgumentException("Fachfrage " + id + " braucht 'erwartete_quelle'");
            }
            fragen.add(new Frage(id, text,
                    f.get("kategorie") instanceof String k && !k.isBlank() ? k : "Alle",
                    quellen, texte(f.get("muss_enthalten")), negativ));
        }
        return new EvalKatalog(
                json.get("beschreibung") instanceof String b ? b : "", schwellen, List.copyOf(fragen));
    }

    private static List<String> texte(Object wert) {
        if (wert == null) {
            return List.of();
        }
        if (wert instanceof String s) {
            return List.of(s);
        }
        if (wert instanceof List<?> l) {
            return l.stream().map(String::valueOf).toList();
        }
        throw new IllegalArgumentException("Erwartet Text oder Liste: " + wert);
    }

    private static double zahl(Object wert, double standard) {
        return wert instanceof Number n ? n.doubleValue() : standard;
    }
}
