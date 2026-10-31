package de.team.betriebsrente.eval;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Qualitätsprüfung aus dem Admin-Bereich. Bewusst nur der schnelle Suchmodus (wenige Sekunden);
 * der Antwortmodus mit Sprachmodell dauert 20–60 s je Frage und läuft über {@link EvalRunner}.
 */
@RestController
@RequestMapping("/admin/qualitaet")
public class QualitaetController {
    private final EvalService evalService;

    public QualitaetController(EvalService evalService) {
        this.evalService = evalService;
    }

    @PostMapping
    public EvalService.Bericht pruefen() {
        return evalService.pruefe(EvalKatalog.lade(EvalKatalog.findeDatei()), EvalService.Modus.SUCHE);
    }
}
