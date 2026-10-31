package de.team.betriebsrente.eval;

import java.io.File;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kommandozeilen-Lauf der Qualitätsprüfung, z. B. im Container:
 *
 * <pre>
 * docker compose run --rm app --eval                       # nur Suche (schnell)
 * docker compose run --rm app --eval --eval-modus=antwort  # inkl. Sprachmodell (langsam)
 * </pre>
 *
 * Optionen: {@code --eval=<katalog.json>}, {@code --eval-bericht=<datei.json>}. Die Anwendung beendet
 * sich danach mit Exit-Code 0 (Schwellen erreicht) bzw. 1 (nicht erreicht).
 */
@Component
@Order(100) // nach dem Seed-Abgleich
public class EvalRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    private final EvalService evalService;
    private final ApplicationContext context;

    public EvalRunner(EvalService evalService, ApplicationContext context) {
        this.evalService = evalService;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("eval")) {
            return;
        }
        int exitCode = ausfuehren(args);
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    int ausfuehren(ApplicationArguments args) throws Exception {
        List<String> pfad = args.getOptionValues("eval");
        File katalogDatei = pfad == null || pfad.isEmpty() || pfad.get(0).isBlank()
                ? EvalKatalog.findeDatei() : new File(pfad.get(0));
        EvalService.Modus modus = args.containsOption("eval-modus")
                ? EvalService.Modus.valueOf(args.getOptionValues("eval-modus").get(0).toUpperCase(Locale.ROOT))
                : EvalService.Modus.SUCHE;

        log.info("Qualitätsprüfung ({}) mit {}", modus, katalogDatei.getAbsolutePath());
        EvalService.Bericht bericht = evalService.pruefe(EvalKatalog.lade(katalogDatei), modus);

        String ziel = args.containsOption("eval-bericht") ? args.getOptionValues("eval-bericht").get(0)
                : "eval-bericht.json";
        JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build().writeValue(new File(ziel), bericht);
        System.out.println(zusammenfassung(bericht));
        System.out.println("Bericht: " + new File(ziel).getAbsolutePath());
        return bericht.bestanden() ? 0 : 1;
    }

    static String zusammenfassung(EvalService.Bericht b) {
        StringBuilder sb = new StringBuilder("\n=== Qualitätsprüfung Renten-Fachbot (" + b.modus() + ") ===\n");
        for (EvalService.FrageErgebnis e : b.ergebnisse()) {
            sb.append(String.format(Locale.ROOT, "%s %-4s %-70.70s -> %s%s%n",
                    e.bestanden() ? "OK  " : "FAIL", e.id(), e.frage(),
                    e.gefundeneQuellen().isEmpty() ? "(keine Treffer)" : String.join(" | ", e.gefundeneQuellen()),
                    e.fehlendeBegriffe().isEmpty() ? "" : "  fehlt: " + e.fehlendeBegriffe()));
        }
        sb.append(String.format(Locale.GERMAN, "%nTrefferquote Suche:   %5.1f %% (Ziel %.0f %%, %d Fachfragen)%n",
                b.trefferQuote() * 100, b.schwellen().trefferQuote() * 100, b.fachfragen()));
        sb.append(String.format(Locale.GERMAN, "Ablehnungsquote:      %5.1f %% (Ziel %.0f %%, %d fachfremde Fragen)%n",
                b.ablehnungsQuote() * 100, b.schwellen().ablehnungsQuote() * 100, b.fachfremdeFragen()));
        if (b.antwortQuote() != null) {
            sb.append(String.format(Locale.GERMAN, "Antwortquote:         %5.1f %% (Ziel %.0f %%)%n",
                    b.antwortQuote() * 100, b.schwellen().antwortQuote() * 100));
        }
        sb.append(b.bestanden() ? "ERGEBNIS: BESTANDEN" : "ERGEBNIS: NICHT BESTANDEN")
                .append(String.format(Locale.GERMAN, " (%.1f s)%n", b.dauerMs() / 1000.0));
        return sb.toString();
    }
}
