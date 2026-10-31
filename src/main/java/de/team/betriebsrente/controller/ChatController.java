package de.team.betriebsrente.controller;

import de.team.betriebsrente.model.Gueltigkeit;
import de.team.betriebsrente.rag.RagService;
import de.team.betriebsrente.repository.FachwissenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
public class ChatController {
    static final String ALLE = "Alle";
    static final int MAX_FRAGE_LAENGE = 2000;
    private static final int QUELLEN_VORSCHAU = 400;
    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final RagService ragService;
    private final FachwissenRepository fachwissenRepository;

    public ChatController(RagService ragService, FachwissenRepository fachwissenRepository) {
        this.ragService = ragService;
        this.fachwissenRepository = fachwissenRepository;
    }

    @GetMapping("/")
    public String index(Model model, Authentication authentication) {
        List<String> kategorien = new ArrayList<>();
        kategorien.add(ALLE);
        kategorien.addAll(fachwissenRepository.findKategorien());
        model.addAttribute("kategorien", kategorien);
        model.addAttribute("isAdmin", authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())));
        return "chat";
    }

    @PostMapping("/api/chat")
    @ResponseBody
    public Map<String, Object> chat(@RequestBody Map<String, String> body) {
        String frage = Objects.requireNonNullElse(body.get("frage"), "").strip();
        if (frage.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Frage darf nicht leer sein");
        }
        if (frage.length() > MAX_FRAGE_LAENGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Frage ist zu lang (max. " + MAX_FRAGE_LAENGE + " Zeichen)");
        }
        var result = ragService.ask(frage, body.get("kategorie"));
        Map<String, Object> resp = new HashMap<>();
        resp.put("antwort", result.antwort());
        resp.put("quellen", result.quellen().stream().map(d -> {
            String text = Objects.requireNonNullElse(d.getText(), "");
            Map<String, Object> quelle = new HashMap<>();
            quelle.put("titel", d.getMetadata().getOrDefault("titel", "-"));
            quelle.put("kategorie", d.getMetadata().getOrDefault("kategorie", "-"));
            quelle.put("text", text.length() > QUELLEN_VORSCHAU ? text.substring(0, QUELLEN_VORSCHAU) + "..." : text);
            LocalDate gueltigBis = Gueltigkeit.datum(d.getMetadata().get(Gueltigkeit.META_BIS));
            quelle.put("gueltigBis", gueltigBis == null ? null : gueltigBis.format(DATUM));
            return quelle;
        }).toList());
        return resp;
    }
}
