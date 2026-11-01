package de.team.betriebsrente.rag;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Stichwortsuche über PostgreSQL-Volltext (deutsche Wortstämme: „Meldegründe“ = „Meldegrund“).
 *
 * <p>Ergänzt die Vektorsuche bei wörtlichen Fachbegriffen, Paragrafen oder Kürzeln, die das
 * Embedding-Modell nicht zuverlässig zuordnet. Damit zufällige Wortübereinstimmungen keine
 * fachfremden Treffer erzeugen, gilt ein Treffer nur als <b>stark</b>, wenn ein Fachbegriff der
 * Frage im <b>Titel</b> vorkommt oder mindestens <b>zwei verschiedene</b> Fachbegriffe übereinstimmen.
 * Typische Fragewörter („gibt“, „wieviel“, „muss“ …) zählen nicht. Gewichtet wird nach Seltenheit
 * des Begriffs (IDF), damit „Meldegrund“ mehr zählt als „Betriebsrente“.
 */
@Service
public class StichwortSuche {

    /** Wortstämme typischer Frage- und Füllwörter (deutscher Snowball-Stemmer), die kein Fachbegriff sind. */
    static final Set<String> FRAGEWORTE = Set.of(
            "ab", "bekomm", "bekommt", "brauch", "darf", "durf", "gab", "geb", "gelt", "gibt", "gilt", "heut",
            "lauf", "lauft", "mach", "morg", "muss", "passi", "passiert", "viel", "wann", "warum", "wieviel",
            "wofur", "woran", "word", "wozu");

    /** Stark übereinstimmender Abschnitt mit Gewicht (Summe der IDF-Werte der getroffenen Begriffe). */
    public record Treffer(String id, String inhalt, Map<String, Object> metadaten, double gewicht,
            List<String> begriffe) {
    }

    private final EntityManager em;

    public StichwortSuche(EntityManager em) {
        this.em = em;
    }

    /** Liefert die stark übereinstimmenden Abschnitte, absteigend nach Gewicht. */
    public List<Treffer> suche(String frage) {
        List<String> begriffe = begriffe(frage);
        if (begriffe.isEmpty()) {
            return List.of();
        }
        String array = "{" + String.join(",", begriffe.stream().map(StichwortSuche::arrayElement).toList()) + "}";
        @SuppressWarnings("unchecked")
        List<Object[]> zeilen = em.createNativeQuery("""
                WITH d AS (
                  SELECT id, metadata, content,
                         tsvector_to_array(to_tsvector('german', coalesce(metadata->>'titel', ''))) AS tlex,
                         tsvector_to_array(to_tsvector('german',
                             coalesce(metadata->>'titel', '') || ' ' || content)) AS alex
                    FROM vector_store)
                SELECT CAST(d.id AS text), d.content, CAST(d.metadata AS text),
                       array_to_string(ARRAY(SELECT unnest(d.alex) INTERSECT SELECT unnest(CAST(:q AS text[]))), ' '),
                       d.tlex && CAST(:q AS text[])
                  FROM d
                 WHERE d.alex && CAST(:q AS text[])
                """).setParameter("q", array).getResultList();
        long gesamt = ((Number) em.createNativeQuery("SELECT count(*) FROM vector_store").getSingleResult()).longValue();

        // Dokumenthäufigkeit je Begriff: Jeder Abschnitt mit einem Begriff ist in der Ergebnismenge enthalten.
        Map<String, Integer> df = new HashMap<>();
        List<Object[]> geparst = new ArrayList<>();
        for (Object[] z : zeilen) {
            Set<String> getroffen = new HashSet<>(Arrays.asList(String.valueOf(z[3]).split(" ")));
            getroffen.remove("");
            getroffen.forEach(b -> df.merge(b, 1, Integer::sum));
            geparst.add(new Object[] { z[0], z[1], z[2], getroffen, Boolean.TRUE.equals(z[4]) });
        }

        List<Treffer> treffer = new ArrayList<>();
        for (Object[] z : geparst) {
            @SuppressWarnings("unchecked")
            Set<String> getroffen = (Set<String>) z[3];
            boolean titel = (Boolean) z[4];
            if (!titel && getroffen.size() < 2) {
                continue;
            }
            double gewicht = getroffen.stream().mapToDouble(b -> idf(df.get(b), gesamt)).sum();
            treffer.add(new Treffer((String) z[0], (String) z[1], Metadaten.lese((String) z[2]), gewicht,
                    getroffen.stream().sorted().toList()));
        }
        treffer.sort((a, b) -> Double.compare(b.gewicht(), a.gewicht()));
        return treffer;
    }

    /** Fachbegriffe der Frage als deutsche Wortstämme, ohne Fragewörter. */
    List<String> begriffe(String frage) {
        if (frage == null || frage.isBlank()) {
            return List.of();
        }
        Object ergebnis = em.createNativeQuery(
                        "SELECT array_to_string(tsvector_to_array(to_tsvector('german', :frage)), ' ')")
                .setParameter("frage", frage).getSingleResult();
        return Arrays.stream(String.valueOf(ergebnis).split(" "))
                .filter(b -> !b.isBlank() && !FRAGEWORTE.contains(b))
                .toList();
    }

    static double idf(int dokumente, long gesamt) {
        return Math.log(1 + (gesamt - dokumente + 0.5) / (dokumente + 0.5));
    }

    /** Element für ein PostgreSQL-Array-Literal: in Anführungszeichen, \ und " maskiert. */
    private static String arrayElement(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
