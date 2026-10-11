package de.team.betriebsrente.controller;

import de.team.betriebsrente.rag.RagService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller
public class ChatController {
    private final RagService ragService;

    public ChatController(RagService ragService) {
        this.ragService = ragService;
    }

    @GetMapping("/")
    public String index(Model model, Authentication authentication) {
        model.addAttribute("kategorien",
                List.of("Auszahlung", "Steuer", "Unverfallbarkeit", "Arbeitgeberwechsel", "Alle"));
        model.addAttribute("isAdmin", authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())));
        return "chat";
    }

    @PostMapping("/api/chat")
    @ResponseBody
    public Map<String, Object> chat(@RequestBody Map<String, String> body) {
        var result = ragService.ask(body.get("frage"), body.get("kategorie"));
        Map<String, Object> resp = new HashMap<>();
        resp.put("antwort", result.antwort());
        resp.put("quellen", result.quellen().stream().map(d -> {
            String text = Objects.requireNonNullElse(d.getText(), "");
            return Map.of(
                    "titel", d.getMetadata().getOrDefault("titel", "-"),
                    "kategorie", d.getMetadata().getOrDefault("kategorie", "-"),
                    "text", text.substring(0, Math.min(400, text.length())) + "...");
        }).toList());
        return resp;
    }
}
